package com.flipperx.assist.screen;

import com.flipperx.assist.hud.ItemIcon;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public record PassiveSummary(String title, String span, double total, double since, String sinceLabel,
                             long[] times, double[] profits, Summary.Best best, String bestLabel,
                             String notifyLabel, String notifyCommand) {

    public static PassiveSummary from(JsonObject o) {
        JsonArray pts = o.has("points") && o.get("points").isJsonArray() ? o.getAsJsonArray("points") : new JsonArray();
        long[] times = new long[pts.size()];
        double[] profits = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            JsonObject p = pts.get(i).getAsJsonObject();
            times[i] = p.has("t") ? p.get("t").getAsLong() : 0;
            profits[i] = num(p, "profit");
        }
        Summary.Best best = null;
        if (o.has("best") && o.get("best").isJsonObject()) {
            JsonObject b = o.getAsJsonObject("best");
            best = new Summary.Best(str(b, "item_id"), str(b, "item_name"), str(b, "icon"),
                    ItemIcon.Look.from(b.get("look")),
                    b.has("quantity") ? b.get("quantity").getAsInt() : 0, num(b, "profit"));
        }
        String notifyLabel = null, notifyCommand = null;
        if (o.has("notify") && o.get("notify").isJsonObject()) {
            JsonObject n = o.getAsJsonObject("notify");
            notifyLabel = str(n, "label");
            notifyCommand = str(n, "command");
        }
        return new PassiveSummary(orElse(str(o, "title"), "Passive flips"), orElse(str(o, "span"), "this week"),
                num(o, "total"), num(o, "since"), orElse(str(o, "since_label"), "since last check-in"),
                times, profits, best, orElse(str(o, "best_label"), "Best item"), notifyLabel, notifyCommand);
    }

    private static String orElse(String v, String fallback) {
        return v == null ? fallback : v;
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
