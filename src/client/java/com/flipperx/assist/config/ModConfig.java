package com.flipperx.assist.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("bzassist.json");

    public Map<String, String> tokens = new HashMap<>();
    public int hudX = -1;
    public int hudY = 8;
    public boolean loginReminders = true;
    public boolean autocommand = false;
    public boolean autosign = false;
    public boolean tabFill = false;
    public int tabFills = 0;
    public boolean npcRoute = false;
    public boolean comebackReminders = true;
    public boolean tabFillReset = false;
    public String highlightColor = "#3D8BE8";
    public String highlightShape = "OUTLINE_FILL";
    public int highlightWidth = 2;
    public String highlightPulse = "ALWAYS";
    public boolean highlightBlue = false;
    public int replayGlows = 0;
    public boolean replayClicked = false;
    public String lastVersion;
    public String updateTried;
    public String install;

    private static ModConfig instance;

    public static synchronized ModConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static ModConfig load() {
        try {
            if (Files.exists(PATH)) {
                JsonObject o = GSON.fromJson(Files.readString(PATH), JsonObject.class);
                ModConfig c = GSON.fromJson(o, ModConfig.class);
                if (c != null) {
                    if (c.tokens == null) c.tokens = new HashMap<>();
                    if (!c.tabFillReset) {
                        c.tabFill = false;
                        c.tabFillReset = true;
                        c.save();
                    }
                    if (!c.highlightBlue) {
                        if ("#E8A33D".equalsIgnoreCase(c.highlightColor) && "OUTLINE_FILL".equals(c.highlightShape)
                                && c.highlightWidth == 1 && "ALWAYS".equals(c.highlightPulse)) {
                            c.highlightColor = "#3D8BE8";
                            c.highlightWidth = 2;
                        }
                        c.highlightBlue = true;
                        c.save();
                    }
                    return c;
                }
            }
        } catch (Exception ignored) {
        }
        ModConfig fresh = new ModConfig();
        fresh.tabFillReset = true;
        fresh.highlightBlue = true;
        return fresh;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (Exception ignored) {
        }
    }

    public synchronized String install() {
        if (install == null || install.length() < 16) {
            install = UUID.randomUUID().toString().replace("-", "");
            save();
        }
        return install;
    }

    public String tokenFor(String uuid) {
        return tokens.get(normalise(uuid));
    }

    public void setToken(String uuid, String token) {
        tokens.put(normalise(uuid), token);
        save();
    }

    public void clearToken(String uuid) {
        tokens.remove(normalise(uuid));
        save();
    }

    private static String normalise(String uuid) {
        return uuid == null ? "" : uuid.replace("-", "").toLowerCase();
    }
}
