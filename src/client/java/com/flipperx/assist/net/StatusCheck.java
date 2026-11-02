package com.flipperx.assist.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class StatusCheck {
    public static final String PAGE = "https://status.flipperx.digital";
    private static final String ENDPOINT = PAGE + "/index.json";
    private static final String SECTION = "assist";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public record Incident(String id, String severity) {
        public String label() {
            return switch (severity) {
                case "downtime" -> "Outage";
                case "maintenance" -> "Maintenance";
                default -> "Degraded";
            };
        }

        public String text() {
            return switch (severity) {
                case "downtime" -> "Some systems are down.";
                case "maintenance" -> "Some systems are down for maintenance.";
                default -> "Some systems are degraded.";
            };
        }

        public String key() {
            return id + ":" + severity;
        }
    }

    private StatusCheck() {}

    public static CompletableFuture<Incident> fetch(String userAgent) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET().build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new IllegalStateException("status page answered " + response.statusCode());
            }
            return parse(response.body(), System.currentTimeMillis());
        });
    }

    public static Incident parse(String body, long now) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray included = root.has("included") && root.get("included").isJsonArray()
                ? root.getAsJsonArray("included") : new JsonArray();
        Map<String, JsonObject> updates = new HashMap<>();
        Map<String, String> sections = new HashMap<>();
        Map<String, JsonObject> resources = new HashMap<>();
        List<JsonObject> reports = new ArrayList<>();
        for (JsonElement e : included) {
            if (!e.isJsonObject()) continue;
            JsonObject x = e.getAsJsonObject();
            String type = string(x, "type");
            String id = string(x, "id");
            if ("status_update".equals(type)) updates.put(id, x);
            else if ("status_page_section".equals(type)) sections.put(id, orEmpty(string(attributes(x), "name")));
            else if ("status_page_resource".equals(type)) resources.put(id, x);
            else if ("status_report".equals(type)) reports.add(x);
        }

        Incident worst = null;
        long worstSince = 0;
        for (JsonObject r : reports) {
            JsonObject a = attributes(r);
            String state = string(a, "aggregate_state");
            if ("resolved".equals(state) || "operational".equals(state)) continue;
            long start = time(string(a, "starts_at"));
            long end = time(string(a, "ends_at"));
            if (start > now) continue;
            if (end > 0 && end <= now) continue;
            JsonObject latest = latestUpdate(r, updates);
            JsonArray listed = latest != null && attributes(latest).has("affected_resources")
                    && attributes(latest).get("affected_resources").isJsonArray()
                    ? attributes(latest).getAsJsonArray("affected_resources")
                    : a.has("affected_resources") && a.get("affected_resources").isJsonArray()
                    ? a.getAsJsonArray("affected_resources") : new JsonArray();
            String severity;
            if (listed.isEmpty()) {
                severity = known(state) ? state : "degraded";
            } else {
                severity = null;
                for (JsonElement ref : listed) {
                    if (!ref.isJsonObject()) continue;
                    String status = string(ref.getAsJsonObject(), "status");
                    if (!known(status) || !inSection(resources.get(string(ref.getAsJsonObject(), "status_page_resource_id")), sections)) continue;
                    if (severity == null || rank(status) > rank(severity)) severity = status;
                }
                if (severity == null) continue;
            }
            if (worst == null || rank(severity) > rank(worst.severity())
                    || (rank(severity) == rank(worst.severity()) && start > worstSince)) {
                worst = new Incident(string(r, "id"), severity);
                worstSince = start;
            }
        }
        if (worst != null) return worst;

        List<String> down = new ArrayList<>();
        for (Map.Entry<String, JsonObject> e : resources.entrySet()) {
            if ("downtime".equals(string(attributes(e.getValue()), "status")) && inSection(e.getValue(), sections)) {
                down.add(e.getKey());
            }
        }
        if (down.isEmpty()) return null;
        down.sort(null);
        return new Incident("auto:" + String.join(",", down), "downtime");
    }

    private static boolean inSection(JsonObject resource, Map<String, String> sections) {
        if (resource == null) return false;
        String section = sections.get(string(attributes(resource), "status_page_section_id"));
        return section != null && SECTION.equalsIgnoreCase(section);
    }

    private static JsonObject latestUpdate(JsonObject report, Map<String, JsonObject> updates) {
        JsonObject best = null;
        long latest = -1;
        JsonObject rel = report.has("relationships") && report.get("relationships").isJsonObject()
                ? report.getAsJsonObject("relationships") : null;
        if (rel == null || !rel.has("status_updates") || !rel.get("status_updates").isJsonObject()) return null;
        JsonObject su = rel.getAsJsonObject("status_updates");
        if (!su.has("data") || !su.get("data").isJsonArray()) return null;
        for (JsonElement ref : su.getAsJsonArray("data")) {
            if (!ref.isJsonObject()) continue;
            JsonObject u = updates.get(string(ref.getAsJsonObject(), "id"));
            if (u == null) continue;
            long at = time(string(attributes(u), "published_at"));
            if (at > latest) {
                latest = at;
                best = u;
            }
        }
        return best;
    }

    private static boolean known(String status) {
        return "downtime".equals(status) || "degraded".equals(status) || "maintenance".equals(status);
    }

    private static int rank(String severity) {
        return switch (severity) {
            case "downtime" -> 3;
            case "degraded" -> 2;
            default -> 1;
        };
    }

    private static JsonObject attributes(JsonObject x) {
        return x.has("attributes") && x.get("attributes").isJsonObject() ? x.getAsJsonObject("attributes") : new JsonObject();
    }

    private static long time(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }
}
