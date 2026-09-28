package com.flipperx.assist.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class VersionCheck {
    private static final String ENDPOINT = System.getProperty("bzassist.dev") != null
            ? "http://127.0.0.1:8000/public/assistmod/version"
            : "https://api.flipperx.digital/public/assistmod/version";

    public static final String PAGE = "https://flipperx.digital/assistmod";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public record Release(String version, String page, URI download, String sha256, long size) {
        public boolean installable() {
            return download != null && sha256 != null && !sha256.isEmpty();
        }
    }

    private VersionCheck() {}

    public static String current() {
        return FabricLoader.getInstance().getModContainer("bzassist")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("");
    }

    public static CompletableFuture<Release> latest() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "bzassist/" + current())
                .GET().build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new IllegalStateException("version check answered " + response.statusCode());
            }
            return parse(response.body());
        });
    }

    public static void fetch(Executor executor, Consumer<Release> onNewer) {
        latest().thenAccept(release -> {
            if (release != null && compare(release.version(), current()) > 0) {
                executor.execute(() -> onNewer.accept(release));
            }
        }).exceptionally(t -> null);
    }

    public static Release parse(String body) {
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        String version = string(o, "version");
        if (version == null) return null;
        String page = string(o, "url");
        long size = o.has("size") && !o.get("size").isJsonNull() ? o.get("size").getAsLong() : -1;
        return new Release(version, page == null ? PAGE : page, sameHost(string(o, "download")),
                string(o, "sha256"), size);
    }

    private static URI sameHost(String path) {
        if (path == null) return null;
        URI base = URI.create(ENDPOINT);
        URI resolved;
        try {
            resolved = base.resolve(path);
        } catch (IllegalArgumentException e) {
            return null;
        }
        boolean same = Objects.equals(base.getScheme(), resolved.getScheme())
                && Objects.equals(base.getHost(), resolved.getHost())
                && base.getPort() == resolved.getPort();
        return same ? resolved : null;
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    public static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? leading(x[i]) : 0;
            int q = i < y.length ? leading(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int leading(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) end++;
        if (end == 0) return 0;
        try {
            return Integer.parseInt(part.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
