package com.flipperx.assist;

import com.flipperx.assist.hud.ItemIcon;
import com.google.gson.JsonObject;

public final class AssistState {
    public record Step(String kind, String text, int slot, String click,
                       String label, String hint, String itemId, String itemName, String icon,
                       ItemIcon.Look look) {
        public static final Step NONE =
                new Step("wait", "", -1, "left", "", "", null, null, null, null);

        public static Step from(JsonObject o) {
            return new Step(
                    str(o, "kind", "wait"),
                    str(o, "text", ""),
                    o.has("slot") && !o.get("slot").isJsonNull() ? o.get("slot").getAsInt() : -1,
                    str(o, "click", "left"),
                    str(o, "label", ""),
                    str(o, "hint", ""),
                    str(o, "item_id", null),
                    str(o, "item_name", null),
                    str(o, "icon", null),
                    ItemIcon.Look.from(o.get("look")));
        }

        private static String str(JsonObject o, String key, String fallback) {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
        }

        public boolean isCommand() { return "command".equals(kind); }
        public boolean isClick()   { return "click".equals(kind); }
        public boolean isSign()    { return "sign".equals(kind); }
        public boolean isNpc()     { return "npc".equals(kind); }
        public boolean isPlayers() {
            return isCommand() || isNpc() || isClick() || isSign() || "close".equals(kind);
        }

        public Step ahead(int atSlot, Step current) {
            boolean same = current != null && itemId != null && itemId.equals(current.itemId);
            return new Step(kind, text, atSlot, click, label, hint, itemId,
                    same ? current.itemName : itemName, same ? current.icon : icon,
                    same ? current.look : look);
        }

        public boolean reads(Step other) {
            return other != null && kind.equals(other.kind) && text.equals(other.text)
                    && label.equals(other.label) && slot == other.slot && click.equals(other.click);
        }
    }

    private volatile Step step = Step.NONE;
    private volatile Step previous = Step.NONE;
    private volatile long stepSince = 0;
    private volatile boolean current;
    private volatile boolean running = false;
    private volatile boolean linked = false;
    private volatile double profit = 0;
    private volatile Double profitPerHour = null;
    public record Goal(String label, double target, double progress, Double etaHours) {}
    private volatile Goal goal = null;
    public record Limit(double used, double cap, double left, Double hoursLeft) {}
    private volatile Limit limit = null;
    public record Finish(String label, String right, double solid, double light, boolean time) {}
    private volatile Finish finish = null;
    public record Row(String label, String value, String right) {}
    public record Mark(int slot, String name, int background, String lore, int loreColor) {}
    private volatile java.util.List<Mark> marks = java.util.List.of();
    private volatile java.util.List<Row> rows = java.util.List.of();
    private volatile String cookieWarning = null;
    private volatile String notice = null;
    private volatile long noticeUntil = 0;
    private volatile long noticeSince = 0;

    public Step step() { return step; }
    public Step previous() { return previous; }
    public long stepSince() { return stepSince; }

    public void step(Step s) { set(s); }

    private void set(Step s) {
        Step next = s == null ? Step.NONE : s;
        if (!next.reads(step)) {
            previous = step;
            stepSince = System.currentTimeMillis();
        }
        step = next;
        current = next != Step.NONE;
    }

    public boolean current() { return current; }
    public void current(boolean value) { current = value; }

    public void receiveStep(Step incoming, boolean pending) {
        if (!pending || step == Step.NONE) set(incoming);
        if (pending) current = false;
    }

    public boolean running() { return running; }
    public void running(boolean r) { this.running = r; }

    private static final long RESUME_WINDOW_MS = 180_000;

    private volatile String status = "Run /flipperx login to link this account.";
    private volatile long resumeUntil = 0;
    public String status() { return status; }
    public void status(String value) { status = value; }
    public void disconnected() {
        if (running) resumeUntil = System.currentTimeMillis() + RESUME_WINDOW_MS;
        linked = false;
        running = false;
        marks = java.util.List.of();
        set(Step.NONE);
        status("Connection lost. Reconnecting...");
    }
    public boolean takeResume() {
        boolean resume = System.currentTimeMillis() < resumeUntil;
        resumeUntil = 0;
        return resume;
    }
    public void forgetResume() { resumeUntil = 0; }
    public boolean resumePending() { return System.currentTimeMillis() < resumeUntil; }
    public void reset() {
        disconnected();
        resumeUntil = 0;
        profit = 0;
        profitPerHour = null;
        goal = null;
        limit = null;
        finish = null;
        rows = java.util.List.of();
        cookieWarning = null;
        notice = null;
    }

    public boolean linked() { return linked; }
    public void linked(boolean l) { this.linked = l; }

    public double profit() { return profit; }
    public Double profitPerHour() { return profitPerHour; }
    public Goal goal() { return goal; }
    public Limit limit() { return limit; }
    public Finish finish() { return finish; }
    public java.util.List<Row> rows() { return rows; }

    public void marks(com.google.gson.JsonArray arr) {
        java.util.List<Mark> out = new java.util.ArrayList<>();
        if (arr != null) for (var e : arr) {
            if (!e.isJsonObject()) continue;
            JsonObject m = e.getAsJsonObject();
            if (!m.has("slot") || !m.has("background")) continue;
            out.add(new Mark(m.get("slot").getAsInt(), text(m, "name"), m.get("background").getAsInt(),
                    text(m, "lore"), m.has("lore_color") ? m.get("lore_color").getAsInt() : 0xFFFFFF));
        }
        marks = java.util.List.copyOf(out);
    }

    public Mark mark(int slot, String rawName) {
        if (running || !linked) return null;
        for (Mark m : marks) {
            if (m.slot() == slot && m.name().equals(rawName)) return m;
        }
        return null;
    }
    public boolean finishing() { return running && finish != null; }
    public String cookieWarning() { return cookieWarning; }

    public void hud(JsonObject hud) {
        if (hud == null) return;
        if (hud.has("running")) this.running = hud.get("running").getAsBoolean();
        if (hud.has("stopped_reason") && !hud.get("stopped_reason").isJsonNull()) {
            this.running = false;
            status("Stopped: " + hud.get("stopped_reason").getAsString() + ".");
        }
        if (hud.has("stopped_text") && !hud.get("stopped_text").isJsonNull()) {
            status(hud.get("stopped_text").getAsString());
        }
        if (hud.has("profit") && !hud.get("profit").isJsonNull()) {
            this.profit = hud.get("profit").getAsDouble();
        }
        this.profitPerHour = hud.has("profit_per_hour") && !hud.get("profit_per_hour").isJsonNull()
                ? hud.get("profit_per_hour").getAsDouble() : null;
        this.cookieWarning = cookieWarningFrom(hud);
        this.goal = null;
        if (hud.has("goal") && hud.get("goal").isJsonObject()) {
            JsonObject g = hud.getAsJsonObject("goal");
            this.goal = new Goal(
                    g.has("label") && !g.get("label").isJsonNull() ? g.get("label").getAsString() : "",
                    g.get("target").getAsDouble(),
                    g.has("progress") ? g.get("progress").getAsDouble() : 0,
                    g.has("eta_hours") && !g.get("eta_hours").isJsonNull() ? g.get("eta_hours").getAsDouble() : null);
        }
        this.finish = null;
        if (hud.has("finish") && hud.get("finish").isJsonObject()) {
            JsonObject f = hud.getAsJsonObject("finish");
            this.finish = new Finish(
                    f.has("label") && !f.get("label").isJsonNull() ? f.get("label").getAsString() : "",
                    f.has("right") && !f.get("right").isJsonNull() ? f.get("right").getAsString() : "",
                    f.has("solid") ? f.get("solid").getAsDouble() : 0,
                    f.has("light") ? f.get("light").getAsDouble() : 0,
                    f.has("time") && f.get("time").getAsBoolean());
        }
        java.util.List<Row> rows = new java.util.ArrayList<>();
        if (hud.has("rows") && hud.get("rows").isJsonArray()) {
            for (var e : hud.getAsJsonArray("rows")) {
                if (!e.isJsonObject()) continue;
                JsonObject r = e.getAsJsonObject();
                rows.add(new Row(text(r, "label"), text(r, "value"), text(r, "right")));
            }
        }
        this.rows = java.util.List.copyOf(rows);
        this.limit = null;
        if (hud.has("limit") && hud.get("limit").isJsonObject()) {
            JsonObject l = hud.getAsJsonObject("limit");
            this.limit = new Limit(
                    l.get("used").getAsDouble(),
                    l.get("cap").getAsDouble(),
                    l.get("left").getAsDouble(),
                    l.has("hours_left") && !l.get("hours_left").isJsonNull() ? l.get("hours_left").getAsDouble() : null);
        }
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static String cookieWarningFrom(JsonObject hud) {
        if (!hud.has("cookie") || hud.get("cookie").isJsonNull()) return null;
        JsonObject c = hud.getAsJsonObject("cookie");
        boolean active = c.has("active") && c.get("active").getAsBoolean();
        if (!active) return "No Booster Cookie, so you go through the Bazaar NPC in the hub. "
                + "With one you can type /bz from anywhere, which is faster.";
        String left = c.has("time_left") ? c.get("time_left").getAsString() : "";
        String lower = left.toLowerCase();
        boolean soon = !lower.contains("day") || lower.startsWith("1 day");
        return soon ? "Cookie runs out in " + left + ". Worth buying another." : null;
    }

    public void notice(String text, long millis) {
        if (text == null || text.isBlank()) return;
        this.notice = text;
        this.noticeSince = System.currentTimeMillis();
        this.noticeUntil = noticeSince + millis;
    }

    public String notice() {
        return System.currentTimeMillis() < noticeUntil ? notice : null;
    }

    public long noticeSince() { return noticeSince; }
    public long noticeUntil() { return noticeUntil; }
}
