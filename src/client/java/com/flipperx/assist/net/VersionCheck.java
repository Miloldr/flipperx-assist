package com.flipperx.assist.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

public final class VersionCheck {
    private static final String ENDPOINT = System.getProperty("bzassist.dev") != null
            ? "http://127.0.0.1:8000/public/assistmod/version"
            : "https://api.flipperx.digital/public/assistmod/version";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private VersionCheck() {}

    public static String current() {
        return FabricLoader.getInstance().getModContainer("bzassist")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("");
    }

    public static void fetch(Executor executor, BiConsumer<String, String> onNewer) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "bzassist/" + current())
                .GET().build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
            if (response.statusCode() != 200) return;
            JsonObject o = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!o.has("version") || o.get("version").isJsonNull()) return;
            String latest = o.get("version").getAsString();
            String url = o.has("url") && !o.get("url").isJsonNull()
                    ? o.get("url").getAsString() : "https://flipperx.digital/assistmod";
            if (compare(latest, current()) > 0) executor.execute(() -> onNewer.accept(latest, url));
        }).exceptionally(t -> null);
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
