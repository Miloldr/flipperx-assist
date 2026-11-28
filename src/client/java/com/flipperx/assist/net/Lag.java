package com.flipperx.assist.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class Lag {
    public static final int REPORT = 'r';
    public static final int ANSWER = 'a';
    public static final int SHOWN = 's';
    public static final int SCREEN = 'o';
    public static final int PACKET = 'm';
    public static final int CLICK = 'c';
    public static final int SHOWN_ANSWER = 0;
    public static final int SHOWN_AHEAD = 1;
    public static final int SHOWN_EARLY = 2;
    public static final int MAX_EVENTS = 600;
    public static final long EVERY_MS = 30_000;
    static final long PACKET_GAP_MS = 50;

    private boolean on;
    private JsonArray events = new JsonArray();
    private int count;
    private long base;
    private long lastPacket;

    public synchronized void on(boolean value) {
        on = value;
        clear();
    }

    public synchronized boolean on() {
        return on;
    }

    public void report(long seq) {
        add(REPORT, seq, 0);
    }

    public void answer(long seq) {
        add(ANSWER, seq, 0);
    }

    public void shown(int how, String label) {
        add(SHOWN, how, label == null ? 0 : label.hashCode() & 0xFFFF);
    }

    public void screen() {
        add(SCREEN, 0, 0);
    }

    public void click(boolean onStep, int slot) {
        add(CLICK, onStep ? 1 : 0, slot);
    }

    public void packet() {
        packet(System.currentTimeMillis());
    }

    public synchronized void packet(long now) {
        boolean first = now - lastPacket > PACKET_GAP_MS;
        lastPacket = now;
        if (first) put(PACKET, now, 0, 0);
    }

    private void add(int kind, long a, long b) {
        add(kind, System.currentTimeMillis(), a, b);
    }

    public synchronized void add(int kind, long now, long a, long b) {
        put(kind, now, a, b);
    }

    private void put(int kind, long now, long a, long b) {
        if (!on || count >= MAX_EVENTS) return;
        if (count == 0) base = now;
        events.add(String.valueOf((char) kind));
        events.add(now - base);
        events.add(a);
        events.add(b);
        count++;
    }

    public JsonObject take(boolean now) {
        return take(now, System.currentTimeMillis());
    }

    public synchronized JsonObject take(boolean force, long now) {
        if (count == 0) return null;
        if (!force && count < MAX_EVENTS && now - base < EVERY_MS) return null;
        JsonObject o = new JsonObject();
        o.addProperty("type", "lag");
        o.addProperty("t0", base);
        o.addProperty("sent", now);
        o.add("ev", events);
        clear();
        return o;
    }

    private void clear() {
        events = new JsonArray();
        count = 0;
    }
}
