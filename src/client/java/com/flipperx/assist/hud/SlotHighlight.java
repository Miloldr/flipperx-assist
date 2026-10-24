package com.flipperx.assist.hud;

import com.flipperx.assist.config.ModConfig;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SlotHighlight {
    public enum Pulse {
        ALWAYS("Always"), MOVES("When it moves"), OFF("Off");

        public final String label;

        Pulse(String label) { this.label = label; }
    }

    public record Look(int rgb, boolean fill, int width, Pulse pulse) {
        public static final String DEFAULT_COLOR = "#3D8BE8";
        public static final Look DEFAULT = new Look(0x3D8BE8, true, 2, Pulse.ALWAYS);
        public static final int RIGHT = 0xE0559E;
        public static final int RIGHT_ALT = 0x4CC38A;

        public int rightRgb() {
            return hueGap(rgb, RIGHT) < 45f ? RIGHT_ALT : RIGHT;
        }

        public static Look of(ModConfig cfg) {
            Integer rgb = parseColor(cfg.highlightColor);
            boolean fill = cfg.highlightFill != null ? cfg.highlightFill
                    : !"OUTLINE".equals(cfg.highlightShape) && !"CORNERS".equals(cfg.highlightShape);
            return new Look(rgb == null ? DEFAULT.rgb : rgb, fill,
                    cfg.highlightWidth == 2 ? 2 : 1,
                    parse(Pulse.class, cfg.highlightPulse, DEFAULT.pulse));
        }
    }

    private static final float GLIDE_TAU_MS = 55f;
    private static final double PULSE_S = 1.6;
    private static final long MOVE_PULSE_MS = 3200;
    private static final int CORNER = 6;

    private float x = Float.NaN, y;
    private int targetX, targetY;
    private Object screen;
    private long lastNs, movedAt;

    public void draw(GuiGraphicsExtractor g, Object screen, int slotX, int slotY, Look look, boolean right) {
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
        int rgb = right ? look.rightRgb() : look.rgb();
        int line = AssistHud.argb(rgb, alpha);
        int w = look.width();
        int x0 = sx - 1, y0 = sy - 1, x1 = sx + 17, y1 = sy + 17;
        if (look.fill()) g.fill(x0 + w, y0 + w, x1 - w, y1 - w, AssistHud.argb(rgb, 0.25f * alpha));
        if (right) corners(g, x0, y0, x1, y1, w, line);
        else ring(g, x0, y0, x1, y1, w, line);
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

    static float hueGap(int a, int b) {
        float d = Math.abs(hue(a) - hue(b));
        return Math.min(d, 360f - d);
    }

    private static float hue(int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255f, g = (rgb >> 8 & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        if (d == 0f) return 0f;
        float h = max == r ? ((g - b) / d) % 6f : max == g ? (b - r) / d + 2f : (r - g) / d + 4f;
        return (h * 60f + 360f) % 360f;
    }

    private static final Pattern HEX = Pattern.compile("(?:#|0x)?([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})");
    private static final Pattern NUMBERS = Pattern.compile("(rgba?|hsla?)?\\(?\\s*([\\d.]+%?)[\\s,]+([\\d.]+%?)[\\s,]+([\\d.]+%?)(?:[\\s,/]+[\\d.]+%?)?\\s*\\)?");

    public static Integer parseColor(String text) {
        if (text == null) return null;
        String t = text.trim().toLowerCase(Locale.ROOT);
        Matcher m = HEX.matcher(t);
        if (m.matches()) {
            String hex = m.group(1);
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
            } else if (hex.length() == 8) {
                hex = t.startsWith("0x") ? hex.substring(2) : hex.substring(0, 6);
            }
            return Integer.parseInt(hex, 16);
        }
        m = NUMBERS.matcher(t);
        if (!m.matches()) return null;
        try {
            if (m.group(1) != null && m.group(1).startsWith("hsl")) {
                return hsl(number(m.group(2), 360), number(m.group(3), 1), number(m.group(4), 1));
            }
            int r = channel(m.group(2)), g = channel(m.group(3)), b = channel(m.group(4));
            return r < 0 || g < 0 || b < 0 ? null : r << 16 | g << 8 | b;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int channel(String s) {
        double v = s.endsWith("%") ? Double.parseDouble(s.substring(0, s.length() - 1)) * 2.55 : Double.parseDouble(s);
        return v < 0 || v > 255.5 ? -1 : (int) Math.round(v);
    }

    private static double number(String s, double full) {
        if (s.endsWith("%")) return Double.parseDouble(s.substring(0, s.length() - 1)) / 100 * full;
        double v = Double.parseDouble(s);
        return full == 1 && v > 1 ? v / 100 : v;
    }

    private static Integer hsl(double h, double s, double l) {
        if (s < 0 || s > 1 || l < 0 || l > 1) return null;
        double c = (1 - Math.abs(2 * l - 1)) * s, hp = (h % 360 + 360) % 360 / 60, x = c * (1 - Math.abs(hp % 2 - 1));
        double[] rgb = hp < 1 ? new double[]{c, x, 0} : hp < 2 ? new double[]{x, c, 0} : hp < 3 ? new double[]{0, c, x}
                : hp < 4 ? new double[]{0, x, c} : hp < 5 ? new double[]{x, 0, c} : new double[]{c, 0, x};
        double m = l - c / 2;
        return (int) Math.round((rgb[0] + m) * 255) << 16 | (int) Math.round((rgb[1] + m) * 255) << 8
                | (int) Math.round((rgb[2] + m) * 255);
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
