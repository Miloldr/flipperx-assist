package com.flipperx.assist.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public record Summary(double profit, Double perHour, long runSeconds, int sales, float[][] series,
                      Best best, String held, Goal goal) {
    public record Best(String itemId, String itemName, String icon, int quantity, double profit,
                       float x, double cum) {}

    public record Goal(String label, double target, double before, double after) {}

    public static Summary from(JsonObject o) {
        JsonArray pts = o.has("series") && o.get("series").isJsonArray() ? o.getAsJsonArray("series") : new JsonArray();
        float[][] series = new float[pts.size()][];
        for (int i = 0; i < pts.size(); i++) {
            JsonArray p = pts.get(i).getAsJsonArray();
            series[i] = new float[]{p.get(0).getAsFloat(), p.get(1).getAsFloat()};
        }
        Best best = null;
        if (o.has("best") && o.get("best").isJsonObject()) {
            JsonObject b = o.getAsJsonObject("best");
            best = new Best(str(b, "item_id"), str(b, "item_name"), str(b, "icon"),
                    b.has("quantity") ? b.get("quantity").getAsInt() : 0, num(b, "profit"),
                    (float) num(b, "x"), num(b, "cum"));
        }
        String held = null;
        if (o.has("held") && o.get("held").isJsonObject()) held = str(o.getAsJsonObject("held"), "text");
        Goal goal = null;
        if (o.has("goal") && o.get("goal").isJsonObject()) {
            JsonObject g = o.getAsJsonObject("goal");
            goal = new Goal(str(g, "label"), num(g, "target"), num(g, "before"), num(g, "after"));
        }
        Double perHour = o.has("per_hour") && !o.get("per_hour").isJsonNull() ? o.get("per_hour").getAsDouble() : null;
        return new Summary(num(o, "profit"), perHour, (long) num(o, "run_s"),
                o.has("sales") ? o.get("sales").getAsInt() : 0, series, best, held, goal);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static double num(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0 : e.getAsDouble();
    }
}
