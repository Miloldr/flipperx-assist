package com.flipperx.assist.screen;

import com.flipperx.assist.AssistClient;
import com.flipperx.assist.game.Compat;
import com.flipperx.assist.hud.AssistHud;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Flex {
    public record Offer(String id, String label, String url) {
        static Offer from(JsonElement e) {
            if (e == null || !e.isJsonObject()) return null;
            JsonObject o = e.getAsJsonObject();
            String label = str(o, "label");
            if (label == null) return null;
            return new Offer(str(o, "id"), label, str(o, "url"));
        }
    }

    private static final int MAX_BYTES = 2_400_000;
    private static final int[] WIDTHS = {1920, 1600, 1280, 960};
    private static final Map<String, String> STATUS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> DONE = new ConcurrentHashMap<>();
    private static volatile boolean hideChat;

    private Flex() {}

    public static boolean hidingChat() {
        return hideChat;
    }

    static String label(Offer o) {
        if (o.id() == null) return o.label();
        String status = STATUS.get(o.id());
        return status == null ? o.label() : status;
    }

    static boolean pressable(Offer o) {
        if (o.id() == null) return o.url() != null;
        return !STATUS.containsKey(o.id()) || Boolean.FALSE.equals(DONE.get(o.id()));
    }

    static boolean busy(Offer o) {
        return o.id() != null && STATUS.containsKey(o.id()) && !DONE.containsKey(o.id());
    }

    static boolean press(Offer o) {
        if (!pressable(o)) return false;
        if (o.id() == null) {
            if (o.url() != null) Compat.openUri(URI.create(o.url()));
            return false;
        }
        DONE.remove(o.id());
        hideChat = true;
        return true;
    }

    static void cancel() {
        hideChat = false;
    }

    static void capture(Offer o) {
        STATUS.put(o.id(), "Posting...");
        Minecraft mc = Minecraft.getInstance();
        RenderTarget target = Compat.mainRenderTarget();
        if (target == null) {
            hideChat = false;
            result(o.id(), false, "Screenshot failed");
            return;
        }
        try {
            shoot(mc, target, o);
        } catch (Throwable t) {
            hideChat = false;
            result(o.id(), false, "Screenshot failed");
        }
    }

    private static void shoot(Minecraft mc, RenderTarget target, Offer o) {
        Screenshot.takeScreenshot(target, image -> {
            hideChat = false;
            Util.ioPool().execute(() -> {
                String data;
                try (image) {
                    data = encode(image);
                } catch (Throwable t) {
                    data = null;
                }
                String payload = data;
                mc.execute(() -> {
                    AssistClient client = AssistClient.get();
                    if (payload == null || client == null || !client.sendFlex(o.id(), payload)) {
                        result(o.id(), false, payload == null ? "Screenshot failed" : "Not connected");
                    }
                });
            });
        });
    }

    public static void result(String id, boolean ok, String text) {
        if (id == null) return;
        STATUS.put(id, text == null || text.isBlank() ? (ok ? "Posted" : "Try again") : text);
        DONE.put(id, ok);
    }

    private static String encode(NativeImage image) throws Exception {
        for (int width : WIDTHS) {
            int w = Math.min(width, image.getWidth());
            int h = Math.max(1, Math.round(image.getHeight() * (float) w / image.getWidth()));
            byte[] bytes;
            try (NativeImage small = new NativeImage(w, h, false)) {
                image.resizeSubRectTo(0, 0, image.getWidth(), image.getHeight(), small);
                bytes = jpeg(small);
                if (bytes == null) bytes = png(small);
            }
            if (bytes != null && bytes.length <= MAX_BYTES) return Base64.getEncoder().encodeToString(bytes);
            if (w < width) break;
        }
        return null;
    }

    private static byte[] jpeg(NativeImage image) {
        try {
            BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            out.setRGB(0, 0, image.getWidth(), image.getHeight(), image.getPixels(), 0, image.getWidth());
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.9f);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(bytes)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(out, null, null), param);
            } finally {
                writer.dispose();
            }
            return bytes.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] png(NativeImage image) {
        Path file = null;
        try {
            file = Files.createTempFile("bzassist-flex", ".png");
            image.writeToFile(file);
            return Files.readAllBytes(file);
        } catch (Throwable t) {
            return null;
        } finally {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    static void closeX(GuiGraphicsExtractor g, int x0, int y0, int size, boolean hover, float a) {
        if (hover) g.fill(x0, y0, x0 + size, y0 + size, AssistHud.argb(0x3A1716, a));
        int red = AssistHud.argb(hover ? 0xFF5F5A : AssistHud.WARN, a);
        int inset = 3, n = size - inset * 2;
        for (int i = 0; i < n; i++) {
            g.fill(x0 + inset + i, y0 + inset + i, x0 + inset + i + 2, y0 + inset + i + 1, red);
            g.fill(x0 + inset + n - 2 - i, y0 + inset + i, x0 + inset + n - i, y0 + inset + i + 1, red);
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }
}
