package com.flipperx.assist.hud;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IconCache {
    private IconCache() {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private static final Map<String, Identifier> READY = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> INFLIGHT = new ConcurrentHashMap<>();

    public static Identifier get(String itemId, String url) {
        if (itemId == null || url == null) return null;
        Identifier ready = READY.get(itemId);
        if (ready != null) return ready;
        if (INFLIGHT.putIfAbsent(itemId, Boolean.TRUE) != null) return null;
        fetch(itemId, url);
        return null;
    }

    private static void fetch(String itemId, String url) {
        Thread.ofVirtual().start(() -> {
            try {
                HttpResponse<byte[]> res = HTTP.send(
                        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() != 200) return;
                byte[] png = res.body();
                Minecraft.getInstance().execute(() -> upload(itemId, png));
            } catch (Exception ignored) {
            }
        });
    }

    private static void upload(String itemId, byte[] png) {
        try {
            NativeImage image = NativeImage.read(png);
            DynamicTexture texture = new DynamicTexture(() -> "bzassist/" + itemId, image);
            Identifier id = Identifier.fromNamespaceAndPath(
                    "bzassist", "icon/" + itemId.toLowerCase().replaceAll("[^a-z0-9_./-]", "_"));
            Minecraft.getInstance().getTextureManager().register(id, texture);
            READY.put(itemId, id);
        } catch (Exception ignored) {
        }
    }
}
