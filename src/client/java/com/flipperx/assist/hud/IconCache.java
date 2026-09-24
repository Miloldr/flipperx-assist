package com.flipperx.assist.hud;

import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IconCache {
    private IconCache() {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private static final Path DIR =
            FabricLoader.getInstance().getGameDir().resolve("bzassist").resolve("icons");
    private static final Duration MAX_AGE = Duration.ofDays(7);
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private static final Map<String, Identifier> READY = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> INFLIGHT = new ConcurrentHashMap<>();

    public static Identifier get(String itemId, String url) {
        if (itemId == null || url == null) return null;
        Identifier ready = READY.get(itemId);
        if (ready != null) return ready;
        if (INFLIGHT.putIfAbsent(itemId, Boolean.TRUE) != null) return null;
        load(itemId, url);
        return null;
    }

    private static void load(String itemId, String url) {
        Thread.ofVirtual().start(() -> {
            Path file = DIR.resolve(URLEncoder.encode(itemId, StandardCharsets.UTF_8) + ".png");
            byte[] png = fresh(file) ? read(file) : null;
            if (png == null) {
                png = download(url);
                if (png != null) store(file, png);
                else png = read(file);
            }
            if (png == null) return;
            byte[] image = png;
            Minecraft.getInstance().execute(() -> upload(itemId, image));
        });
    }

    private static boolean fresh(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant().isAfter(Instant.now().minus(MAX_AGE));
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] read(Path file) {
        try {
            byte[] png = Files.readAllBytes(file);
            return isPng(png) ? png : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] download(String url) {
        try {
            HttpResponse<byte[]> res = HTTP.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return res.statusCode() == 200 && isPng(res.body()) ? res.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void store(Path file, byte[] png) {
        try {
            Files.createDirectories(DIR);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, png);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {
        }
    }

    private static boolean isPng(byte[] data) {
        if (data == null || data.length < PNG_MAGIC.length) return false;
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (data[i] != PNG_MAGIC[i]) return false;
        }
        return true;
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
