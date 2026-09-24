package com.flipperx.assist;

import com.google.gson.JsonObject;

public final class AssistState {
    public record Step(String kind, String text, int slot, String click,
                       String label, String hint, String itemId, String itemName, String icon) {
        public static final Step NONE =
                new Step("wait", "", -1, "left", "", "", null, null, null);

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
                    str(o, "icon", null));
        }

        private static String str(JsonObject o, String key, String fallback) {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
        }

        public boolean isCommand() { return "command".equals(kind); }
        public boolean isClick()   { return "click".equals(kind); }
        public boolean isSign()    { return "sign".equals(kind); }
        public boolean isPlayers() { return isCommand() || isClick() || isSign() || "close".equals(kind); }

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

    private volatile String status = "Run /assist login to link this account.";
    private volatile long statusSince = System.currentTimeMillis();
    private volatile long resumeUntil = 0;
    public String status() { return status; }
    public void status(String value) {
        if (!value.equals(status)) statusSince = System.currentTimeMillis();
        status = value;
    }
    public long statusSince() { return statusSince; }
    public void disconnected() {
        if (running) resumeUntil = System.currentTimeMillis() + RESUME_WINDOW_MS;
        linked = false;
        running = false;
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
        cookieWarning = null;
        notice = null;
    }

    public boolean linked() { return linked; }
    public void linked(boolean l) { this.linked = l; }

    public double profit() { return profit; }
    public Double profitPerHour() { return profitPerHour; }
    public Goal goal() { return goal; }
    public String cookieWarning() { return cookieWarning; }

    public void hud(JsonObject hud) {
        if (hud == null) return;
        if (hud.has("running")) this.running = hud.get("running").getAsBoolean();
        if (hud.has("stopped_reason") && !hud.get("stopped_reason").isJsonNull()) {
            this.running = false;
            status("Stopped: " + hud.get("stopped_reason").getAsString() + ".");
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
    }

    private static String cookieWarningFrom(JsonObject hud) {
        if (!hud.has("cookie") || hud.get("cookie").isJsonNull()) return null;
        JsonObject c = hud.getAsJsonObject("cookie");
        boolean active = c.has("active") && c.get("active").getAsBoolean();
        if (!active) return "No booster cookie. Buy one, flips are worth much less without it.";
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
