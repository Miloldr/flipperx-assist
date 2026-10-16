package com.flipperx.assist.net;

import java.util.HashMap;
import java.util.Map;

public final class Reports {
    private static final int MAX_IN_FLIGHT = 4;
    private boolean numbered;
    private boolean waiting;
    private long sent;
    private long answered;
    private final Map<Long, Integer> contexts = new HashMap<>();

    public void reset() {
        numbered = false;
        waiting = false;
        sent = 0;
        answered = 0;
        contexts.clear();
    }

    public void numbered(boolean on) {
        numbered = on;
    }

    public boolean numbered() {
        return numbered;
    }

    public boolean waiting() {
        return waiting;
    }

    public void stopWaiting() {
        waiting = false;
    }

    public boolean holds(boolean changed) {
        if (!waiting) return false;
        return !changed || !numbered || sent - answered >= MAX_IN_FLIGHT;
    }

    public long next() {
        return sent + 1;
    }

    public void sent(long seq, int context) {
        waiting = true;
        if (!numbered) return;
        sent = seq;
        contexts.put(seq, context);
        contexts.keySet().removeIf(k -> k < seq - 4);
    }

    public boolean stale(long seq) {
        return seq >= 0 && seq < sent;
    }

    public void answer(long seq) {
        answered = seq >= 0 ? seq : sent;
        waiting = false;
    }

    public int context(long seq, int fallback) {
        return seq >= 0 ? contexts.getOrDefault(seq, fallback) : fallback;
    }
}
