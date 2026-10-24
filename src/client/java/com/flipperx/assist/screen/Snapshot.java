package com.flipperx.assist.screen;

import com.flipperx.assist.game.Compat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;

final class Snapshot {
    record Saved(Path file, boolean downloads) {}

    private Snapshot() {}

    static void take(Consumer<Saved> done) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget target = Compat.mainRenderTarget();
        if (target == null) {
            done.accept(null);
            return;
        }
        Screenshot.takeScreenshot(target, image ->
                Util.ioPool().execute(() -> {
                    Saved saved;
                    try (image) {
                        saved = write(image, mc);
                    }
                    mc.execute(() -> done.accept(saved));
                }));
    }

    private static Saved write(NativeImage image, Minecraft mc) {
        String name = "flipperx-" + Util.getFilenameFormattedDateTime();
        Path downloads = downloads();
        if (downloads != null) {
            try {
                return new Saved(save(image, downloads, name), true);
            } catch (IOException | RuntimeException ignored) {
            }
        }
        try {
            Path dir = mc.gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR);
            Files.createDirectories(dir);
            return new Saved(save(image, dir, name), false);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Path save(NativeImage image, Path dir, String name) throws IOException {
        Path file = dir.resolve(name + ".png");
        for (int i = 1; Files.exists(file); i++) file = dir.resolve(name + "_" + i + ".png");
        image.writeToFile(file);
        return file;
    }

    static Path downloads() {
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) return null;
        Path homeDir = Path.of(home);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win") && !os.contains("mac")) {
            Path xdg = xdgDownloads(homeDir);
            if (xdg != null && Files.isDirectory(xdg)) return xdg;
        }
        Path dir = homeDir.resolve("Downloads");
        return Files.isDirectory(dir) ? dir : null;
    }

    private static Path xdgDownloads(Path home) {
        String config = System.getenv("XDG_CONFIG_HOME");
        Path file = (config == null || config.isBlank() ? home.resolve(".config") : Path.of(config))
                .resolve("user-dirs.dirs");
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (!line.startsWith("XDG_DOWNLOAD_DIR=")) continue;
                String value = line.substring("XDG_DOWNLOAD_DIR=".length()).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                if (value.startsWith("$HOME")) return home.resolve(value.substring(5).replaceFirst("^/+", ""));
                if (value.startsWith("/")) return Path.of(value);
                return null;
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return null;
    }
}
