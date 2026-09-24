package com.flipperx.assist.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("bzassist.json");

    public Map<String, String> tokens = new HashMap<>();
    public int hudX = -1;
    public int hudY = 8;

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
                    return c;
                }
            }
        } catch (Exception ignored) {
        }
        return new ModConfig();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (Exception ignored) {
        }
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
