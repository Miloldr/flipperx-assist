package com.flipperx.assist.hud;

import com.flipperx.assist.config.ModConfig;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

public final class SlotHighlight {
    public enum Shape {
        OUTLINE_FILL("Outline and fill"), OUTLINE("Outline"), FILL("Fill"), CORNERS("Corners");

        public final String label;

        Shape(String label) { this.label = label; }
    }

    public enum Pulse {
        ALWAYS("Always"), MOVES("When it moves"), OFF("Off");

        public final String label;

        Pulse(String label) { this.label = label; }
    }

    public record Look(int rgb, Shape shape, int width, Pulse pulse) {
        public static final String DEFAULT_COLOR = "#E8A33D";
        public static final Look DEFAULT = new Look(AssistHud.AMBER, Shape.OUTLINE_FILL, 1, Pulse.ALWAYS);

        public static Look of(ModConfig cfg) {
            Integer rgb = parseColor(cfg.highlightColor);
            return new Look(rgb == null ? DEFAULT.rgb : rgb,
                    parse(Shape.class, cfg.highlightShape, DEFAULT.shape),
                    cfg.highlightWidth == 2 ? 2 : 1,
                    parse(Pulse.class, cfg.highlightPulse, DEFAULT.pulse));
        }
    }

    private static final float GLIDE_TAU_MS = 55f;
    private static final double PULSE_S = 1.6;
    private static final long MOVE_PULSE_MS = 3200;
    private static final int CORNER = 5;

    private float x = Float.NaN, y;
    private int targetX, targetY;
    private Object screen;
    private long lastNs, movedAt;

    public void draw(GuiGraphicsExtractor g, Object screen, int slotX, int slotY, Look look) {
        long nowNs = System.nanoTime();
        float dt = lastNs == 0 ? 0f : Math.min(100f, (nowNs - lastNs) / 1_000_000f);
        lastNs = nowNs;
        long now = System.currentTimeMillis();
        if (Float.isNaN(x) || this.screen != screen) {
            x = slotX; y = slotY;
            this.screen = screen;
            movedAt = now;
        } else {
            if (slotX != targetX || slotY != targetY) movedAt = now;
            float k = AssistHud.approach(dt, GLIDE_TAU_MS);
            x += (slotX - x) * k;
            y += (slotY - y) * k;
        }
        targetX = slotX; targetY = slotY;

        int sx = Math.round(x), sy = Math.round(y);
        float alpha = alpha(look.pulse(), now);
        int line = AssistHud.argb(look.rgb(), alpha);
        int w = look.width();
        int x0 = sx - 1, y0 = sy - 1, x1 = sx + 17, y1 = sy + 17;
        switch (look.shape()) {
            case OUTLINE_FILL -> {
                ring(g, x0, y0, x1, y1, w, line);
                g.fill(x0 + w, y0 + w, x1 - w, y1 - w, AssistHud.argb(look.rgb(), 0.25f * alpha));
            }
            case OUTLINE -> ring(g, x0, y0, x1, y1, w, line);
            case FILL -> g.fill(sx, sy, sx + 16, sy + 16, AssistHud.argb(look.rgb(), 0.42f * alpha));
            case CORNERS -> corners(g, x0, y0, x1, y1, w, line);
        }
    }

    public void clear() {
        x = Float.NaN;
        screen = null;
    }

    private float alpha(Pulse pulse, long now) {
        return switch (pulse) {
            case ALWAYS -> 0.72f + 0.28f * (float) Math.sin(now / 1000.0 * Math.PI * 2 / PULSE_S);
            case MOVES -> {
                long since = now - movedAt;
                yield since >= MOVE_PULSE_MS ? 1f
                        : 0.72f + 0.28f * (float) Math.cos(since / 1000.0 * Math.PI * 2 / PULSE_S);
            }
            case OFF -> 1f;
        };
    }

    private static void ring(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int w, int argb) {
        g.fill(x0, y0, x1, y0 + w, argb);
        g.fill(x0, y1 - w, x1, y1, argb);
        g.fill(x0, y0 + w, x0 + w, y1 - w, argb);
        g.fill(x1 - w, y0 + w, x1, y1 - w, argb);
    }

    private static void corners(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int w, int argb) {
        g.fill(x0, y0, x0 + CORNER, y0 + w, argb);
        g.fill(x0, y0 + w, x0 + w, y0 + CORNER, argb);
        g.fill(x1 - CORNER, y0, x1, y0 + w, argb);
        g.fill(x1 - w, y0 + w, x1, y0 + CORNER, argb);
        g.fill(x0, y1 - w, x0 + CORNER, y1, argb);
        g.fill(x0, y1 - CORNER, x0 + w, y1 - w, argb);
        g.fill(x1 - CORNER, y1 - w, x1, y1, argb);
        g.fill(x1 - w, y1 - CORNER, x1, y1 - w, argb);
    }

    public static Integer parseColor(String text) {
        if (text == null) return null;
        String hex = text.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() != 6) return null;
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String formatColor(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
