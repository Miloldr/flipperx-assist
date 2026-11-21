package com.flipperx.assist.screen;

import com.flipperx.assist.hud.AssistHud;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record Lines(List<List<Part>> rows) {
    public static final Lines NONE = new Lines(List.of());
    public static final int GAP = 2;

    public record Part(String text, Double coins, boolean sign, int rgb) {
        String shown() {
            if (coins == null) return text;
            return sign ? AssistHud.plus(coins) : AssistHud.compact(coins);
        }
    }

    public interface Word {
        int draw(String text, int x, int y, int rgb);
    }

    public static Lines from(JsonElement e) {
        if (e == null || !e.isJsonArray()) return NONE;
        List<List<Part>> rows = new ArrayList<>();
        for (JsonElement r : e.getAsJsonArray()) {
            if (!r.isJsonObject() || !r.getAsJsonObject().has("parts")) continue;
            JsonElement ps = r.getAsJsonObject().get("parts");
            if (!ps.isJsonArray()) continue;
            List<Part> parts = new ArrayList<>();
            for (JsonElement p : (JsonArray) ps) {
                if (!p.isJsonObject()) continue;
                JsonObject o = p.getAsJsonObject();
                Double coins = o.has("coins") && !o.get("coins").isJsonNull() ? o.get("coins").getAsDouble() : null;
                String text = o.has("text") && !o.get("text").isJsonNull() ? o.get("text").getAsString() : "";
                boolean sign = o.has("sign") && o.get("sign").getAsBoolean();
                String c = o.has("color") && !o.get("color").isJsonNull() ? o.get("color").getAsString() : "";
                parts.add(new Part(text, coins, sign, rgb(c)));
            }
            if (!parts.isEmpty()) rows.add(parts);
            if (rows.size() >= 4) break;
        }
        return rows.isEmpty() ? NONE : new Lines(List.copyOf(rows));
    }

    public int height(int line) {
        return rows.size() * (line + GAP);
    }

    public int draw(int left, int y, int line, Word w) {
        for (List<Part> row : rows) {
            int x = left;
            for (Part p : row) x = w.draw(p.shown(), x, y, p.rgb());
            y += line + GAP;
        }
        return y;
    }

    private static int rgb(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "amber" -> AssistHud.AMBER;
            case "text" -> AssistHud.TEXT;
            case "red" -> AssistHud.WARN;
            default -> AssistHud.DIM;
        };
    }
}
