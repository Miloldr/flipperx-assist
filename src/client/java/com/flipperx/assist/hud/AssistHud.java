package com.flipperx.assist.hud;

import com.flipperx.assist.AssistState;
import com.flipperx.assist.AssistState.Step;
import com.flipperx.assist.config.ModConfig;
import com.flipperx.assist.game.GameUtil;
import com.flipperx.assist.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public final class AssistHud {
    private AssistHud() {}

    private static final int PAD = 6;
    private static final int LINE = 11;
    private static final int MAX_WIDTH = 280;
    private static final int GOAL_BAR = 4;

    private static final int BG = 0x121212;
    private static final float BG_ALPHA = 0xD0 / 255f;
    public static final int BORDER = 0x2A2A2A;
    public static final int AMBER = 0xE8A33D;
    public static final int TEXT = 0xE8E8E8;
    public static final int DIM = 0x8A8A8A;
    public static final int WARN = 0xD9534F;
    private static final int TRACK = 0x2A2A2A;

    private static final float FADE_MS = 180f;
    private static final float NOTICE_FADE_MS = 400f;
    private static final float MOVE_TAU_MS = 70f;
    private static final float DIM_TAU_MS = 120f;
    private static final float HIGHLIGHT_TAU_MS = 55f;
    private static final float GOAL_TAU_MS = 220f;
    private static final float DIMMED = 0.55f;
    private static final float COUNT_MS = 650f;
    private static final float GLOW_MS = 1100f;
    private static final float DELTA_MS = 2200f;
    private static final long IDLE_SHOW_MS = 20_000;

    private static boolean dragging = false;
    private static int dragDx, dragDy;

    private static int drawnX, drawnY, drawnWidth, drawnHeight;
    private static Screen drawnScreen;
    private static float moneyX = Float.NaN, moneyY;
    private static float goalX = Float.NaN, goalY;

    private static long lastFrameNs;
    private static float px = Float.NaN, py, ph;
    private static float dim = 1f;
    private static float hx = Float.NaN, hy;
    private static Screen highlightScreen;
    private static long lastHighlightNs;
    private static double shownProfit = Double.NaN, countFrom, countTo;
    private static long countAt, glowAt;
    private static float goalShown = -1f;
    private static double delta;
    private static long deltaAt;

    private record Line(FormattedCharSequence text, int color) {}

    public static void render(GuiGraphicsExtractor g, AssistState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) {
            drawnHeight = 0;
            moneyX = goalX = Float.NaN;
            return;
        }
        long nowNs = System.nanoTime();
        float dt = lastFrameNs == 0 ? 0f : Math.min(100f, (nowNs - lastFrameNs) / 1_000_000f);
        lastFrameNs = nowNs;
        long now = System.currentTimeMillis();
        boolean running = state.running();
        if (!running && !showWhileOff(state, now)) {
            drawnHeight = 0;
            moneyX = goalX = Float.NaN;
            return;
        }

        ModConfig cfg = ModConfig.get();
        int width = Math.min(MAX_WIDTH, Math.max(80, g.guiWidth() - 8));
        int inner = width - PAD * 2;
        Step step = state.step();

        String title = running ? headline(step) : "Bazaar Assist";
        String detail = running ? detail(step) : null;
        String hint = running ? step.hint() : state.status();
        boolean iconSpace = running && step.itemId() != null;
        Identifier icon = running ? IconCache.get(step.itemId(), step.icon()) : null;

        float age = now - state.stepSince();
        float fade = running ? ease(Math.min(1f, age / FADE_MS)) : 1f;
        List<Line> titles = new ArrayList<>();
        addLines(titles, title, inner - (iconSpace ? 20 : 0), AMBER);
        List<Line> oldTitles = new ArrayList<>();
        if (running && fade < 1f && state.previous() != Step.NONE) {
            addLines(oldTitles, headline(state.previous()), inner - (iconSpace ? 20 : 0), AMBER);
        }

        List<Line> lines = new ArrayList<>();
        addLines(lines, detail, inner, TEXT);
        addLines(lines, hint, inner, DIM);
        String notice = state.notice();
        int noticeStart = lines.size();
        addLines(lines, notice, inner, AMBER);
        int noticeEnd = lines.size();
        if (state.linked()) addLines(lines, state.cookieWarning(), inner, WARN);

        boolean money = state.linked() && (state.profit() != 0 || running);
        AssistState.Goal goal = state.linked() ? state.goal() : null;

        int titleHeight = Math.max(iconSpace ? 16 : LINE, titles.size() * LINE);
        int footHeight = (money ? LINE : 0) + (goal != null ? LINE + GOAL_BAR : 0);
        int targetHeight = PAD * 2 + titleHeight + lines.size() * LINE + footHeight;

        int[] anchor = anchor(g, cfg, width, targetHeight);
        if (Float.isNaN(px) || dragging) {
            px = anchor[0]; py = anchor[1];
            if (Float.isNaN(ph)) ph = targetHeight;
        } else {
            float k = approach(dt, MOVE_TAU_MS);
            px += (anchor[0] - px) * k;
            py += (anchor[1] - py) * k;
        }
        ph += (targetHeight - ph) * approach(dt, MOVE_TAU_MS);
        if (Math.abs(ph - targetHeight) < 0.5f) ph = targetHeight;

        float dimTarget = running && !state.current() && step.isPlayers() ? DIMMED : 1f;
        dim += (dimTarget - dim) * approach(dt, DIM_TAU_MS);

        int x = Math.round(px), y = Math.round(py), height = Math.round(ph);
        drawnX = x; drawnY = y; drawnWidth = width; drawnHeight = height;
        drawnScreen = GameUtil.currentScreen();

        g.fill(x, y, x + width, y + height, argb(BG, BG_ALPHA * dim));
        int border = argb(BORDER, dim);
        g.fill(x, y, x + width, y + 1, border);
        g.fill(x, y + height - 1, x + width, y + height, border);
        g.fill(x, y, x + 1, y + height, border);
        g.fill(x + width - 1, y, x + width, y + height, border);

        g.enableScissor(x, y, x + width, y + height);
        if (icon != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, icon, x + PAD, y + PAD, 0f, 0f, 16, 16, 16, 16,
                   argb(0xFFFFFF, dim));
        }
        int textX = x + PAD + (iconSpace ? 20 : 0);
        int cursor = y + PAD;
        int slide = Math.round((1f - fade) * 4f);
        for (Line line : oldTitles) {
            g.text(mc.font, line.text(), textX, cursor - (4 - slide), argb(line.color(), (1f - fade) * dim), false);
            cursor += LINE;
        }
        cursor = y + PAD;
        for (Line line : titles) {
            g.text(mc.font, line.text(), textX, cursor + slide, argb(line.color(), fade * dim), false);
            cursor += LINE;
        }
        cursor = y + PAD + titleHeight;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            float alpha = dim;
            if (i >= noticeStart && i < noticeEnd) alpha *= noticeAlpha(state, now);
            g.text(mc.font, line.text(), x + PAD, cursor, argb(line.color(), alpha), false);
            cursor += LINE;
        }
        moneyX = goalX = Float.NaN;
        if (money) {
            drawMoney(g, mc.font, state, x + PAD, x + width - PAD, cursor, now);
            cursor += LINE;
        }
        if (goal != null) {
            drawGoal(g, mc.font, goal, x + PAD, x + width - PAD, cursor, dt);
        }
        g.disableScissor();
    }

    private static void drawMoney(GuiGraphicsExtractor g, Font font, AssistState state, int left, int right,
                                  int top, long now) {
        double target = state.profit();
        if (Double.isNaN(shownProfit)) {
            shownProfit = countFrom = countTo = target;
        } else if (target != countTo) {
            countFrom = shownProfit;
            countTo = target;
            countAt = now;
            if (target > countFrom) glowAt = now;
        }
        float t = Math.min(1f, (now - countAt) / COUNT_MS);
        shownProfit = countFrom + (countTo - countFrom) * easeOut3(t);
        float glow = glowAt == 0 ? 0f : 1f - Math.min(1f, (now - glowAt) / GLOW_MS);

        String label = "Profit ";
        g.text(font, label, left, top, argb(DIM, dim), false);
        int lw = font.width(label);
        String value = compact(shownProfit);
        g.text(font, value, left + lw, top, argb(mix(TEXT, AMBER, glow), dim), false);
        int vw = font.width(value);
        moneyX = left + lw + vw / 2f;
        moneyY = top;
        if (deltaAt != 0 && now - deltaAt < DELTA_MS) {
            float a = now - deltaAt;
            float in = Math.min(1f, a / 140f);
            float out = a > DELTA_MS * 0.7f ? 1f - (a - DELTA_MS * 0.7f) / (DELTA_MS * 0.3f) : 1f;
            int lift = Math.round((1f - easeOut3(Math.min(1f, a / 260f))) * 3f);
            g.text(font, plus(delta), left + lw + vw + 5, top + lift,
                   argb(delta < 0 ? WARN : AMBER, in * out * dim), false);
        }
        Double rate = state.profitPerHour();
        String r = rate == null ? "this session" : compact(rate) + "/h";
        g.text(font, r, right - font.width(r), top, argb(DIM, dim), false);
    }

    private static void drawGoal(GuiGraphicsExtractor g, Font font, AssistState.Goal goal, int left, int right,
                                 int top, float dt) {
        float frac = goal.target() > 0 ? (float) Math.clamp(goal.progress() / goal.target(), 0.0, 1.0) : 0f;
        if (goalShown < 0) goalShown = frac;
        goalShown += (frac - goalShown) * approach(dt, GOAL_TAU_MS);
        String name = goal.label() == null || goal.label().isBlank() ? compact(goal.target()) : goal.label();
        int pct = (int) Math.floor(frac * 100);
        String r = frac >= 1f ? "reached" : pct + "%" + (goal.etaHours() != null
                ? ", about " + hours(goal.etaHours()) + " left" : "");
        g.text(font, name, left, top, argb(TEXT, dim), false);
        g.text(font, r, right - font.width(r), top, argb(DIM, dim), false);
        int bar = top + LINE;
        g.fill(left, bar, right, bar + 2, argb(TRACK, dim));
        int filled = Math.round((right - left) * goalShown);
        if (filled > 0) g.fill(left, bar, left + filled, bar + 2, argb(AMBER, dim));
        goalX = left + filled;
        goalY = bar;
    }

    public static float[] moneyAnchor() {
        return Float.isNaN(moneyX) || drawnHeight == 0 ? null : new float[]{moneyX, moneyY};
    }

    public static float[] goalAnchor() {
        return Float.isNaN(goalX) || drawnHeight == 0 ? null : new float[]{goalX, goalY};
    }

    public static float[] underAnchor() {
        return drawnHeight == 0 ? null : new float[]{drawnX + drawnWidth / 2f, drawnY + drawnHeight};
    }

    public static void showDelta(double profit) {
        long now = System.currentTimeMillis();
        delta = now - deltaAt < DELTA_MS * 0.7f ? delta + profit : profit;
        deltaAt = now;
    }

    private static boolean showWhileOff(AssistState state, long now) {
        if (now - state.statusSince() < IDLE_SHOW_MS || state.resumePending()) return true;
        if (state.notice() != null) return true;
        Screen screen = GameUtil.currentScreen();
        return screen instanceof AbstractContainerScreen<?>
                && GameUtil.screenTitle().toLowerCase().contains("bazaar");
    }

    private static float noticeAlpha(AssistState state, long now) {
        float in = Math.min(1f, (now - state.noticeSince()) / NOTICE_FADE_MS);
        float out = Math.min(1f, (state.noticeUntil() - now) / NOTICE_FADE_MS);
        return ease(Math.max(0f, Math.min(in, out)));
    }

    private static int[] anchor(GuiGraphicsExtractor g, ModConfig cfg, int width, int height) {
        int maxX = Math.max(0, g.guiWidth() - width);
        int maxY = Math.max(0, g.guiHeight() - height);
        if (cfg.hudX >= 0) {
            return new int[]{Math.clamp(cfg.hudX, 0, maxX), Math.clamp(cfg.hudY, 0, maxY)};
        }
        Screen screen = GameUtil.currentScreen();
        if (screen instanceof AbstractContainerScreen<?> cs) {
            AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) cs;
            int left = acc.bzassist$getLeftPos();
            int top = acc.bzassist$getTopPos();
            int right = left + acc.bzassist$getImageWidth() + 6;
            if (right + width <= g.guiWidth()) {
                return new int[]{right, Math.clamp(top, 0, maxY)};
            }
            if (left - width - 6 >= 0) {
                return new int[]{left - width - 6, Math.clamp(top, 0, maxY)};
            }
        }
        return new int[]{(g.guiWidth() - width) / 2, Math.clamp(cfg.hudY, 0, maxY)};
    }

    public static void renderHighlight(GuiGraphicsExtractor g, Screen screen, int slotX, int slotY) {
        long nowNs = System.nanoTime();
        float dt = lastHighlightNs == 0 ? 0f : Math.min(100f, (nowNs - lastHighlightNs) / 1_000_000f);
        lastHighlightNs = nowNs;
        if (Float.isNaN(hx) || highlightScreen != screen) {
            hx = slotX; hy = slotY;
            highlightScreen = screen;
        } else {
            float k = approach(dt, HIGHLIGHT_TAU_MS);
            hx += (slotX - hx) * k;
            hy += (slotY - hy) * k;
        }
        int x = Math.round(hx), y = Math.round(hy);
        float pulse = 0.72f + 0.28f * (float) Math.sin(System.currentTimeMillis() / 1000.0 * Math.PI * 2 / 1.6);
        int amber = argb(AMBER, pulse);
        g.fill(x - 1, y - 1, x + 17, y, amber);
        g.fill(x - 1, y + 16, x + 17, y + 17, amber);
        g.fill(x - 1, y, x, y + 16, amber);
        g.fill(x + 16, y, x + 17, y + 16, amber);
        g.fill(x, y, x + 16, y + 16, argb(AMBER, 0.25f * pulse));
    }

    public static void clearHighlight() {
        hx = Float.NaN;
        highlightScreen = null;
    }

    private static void addLines(List<Line> lines, String text, int width, int color) {
        if (text == null || text.isBlank()) return;
        for (FormattedCharSequence line : Minecraft.getInstance().font.split(Component.literal(text), width)) {
            lines.add(new Line(line, color));
        }
    }

    private static String headline(Step step) {
        return switch (step.kind()) {
            case "command" -> "Type /" + step.text();
            case "click" -> step.label();
            case "sign" -> "Type " + grouped(step.text()) + " on the sign";
            case "close" -> step.label();
            case "wait" -> step.label().isEmpty() ? "One moment" : step.label();
            case "unreachable", "gated" -> step.label();
            case "done" -> step.label().isEmpty() ? "Nothing to do right now" : step.label();
            default -> step.label();
        };
    }

    private static String detail(Step step) {
        if (step.isCommand()) return step.label().isEmpty() ? null : step.label();
        String item = step.itemName();
        if (item == null || item.isBlank()) return null;
        if (step.isClick() || step.isSign()) {
            return step.label().toLowerCase().contains(item.toLowerCase()) ? null : item;
        }
        return null;
    }

    private static String grouped(String digits) {
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) return digits;
        StringBuilder out = new StringBuilder();
        int n = digits.length();
        for (int i = 0; i < n; i++) {
            if (i > 0 && (n - i) % 3 == 0) out.append(',');
            out.append(digits.charAt(i));
        }
        return out.toString();
    }

    public static String compact(double n) {
        String sign = n < 0 ? "-" : "";
        double a = Math.abs(n);
        if (a >= 1_000_000_000L) return sign + String.format("%.2fB", a / 1_000_000_000d);
        if (a >= 1_000_000L) return sign + String.format("%.1fM", a / 1_000_000d);
        if (a >= 1_000L) return sign + String.format("%.0fk", a / 1_000d);
        return sign + String.format("%.0f", a);
    }

    public static String plus(double n) {
        return n >= 0 ? "+" + compact(n) : compact(n);
    }

    public static String hours(double h) {
        if (h < 1) return Math.max(1, Math.round(h * 60)) + "m";
        return Math.round(h) + "h";
    }

    public static float approach(float dtMs, float tauMs) {
        return 1f - (float) Math.exp(-dtMs / tauMs);
    }

    public static float ease(float t) {
        t = Math.clamp(t, 0f, 1f);
        return 1f - (1f - t) * (1f - t);
    }

    public static float easeOut3(float t) {
        t = Math.clamp(t, 0f, 1f);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    public static int argb(int rgb, float alpha) {
        int a = Math.round(255f * Math.clamp(alpha, 0f, 1f));
        if (a < 8) a = 8;
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int rgbA, int rgbB, float t) {
        t = Math.clamp(t, 0f, 1f);
        int r = Math.round(((rgbA >> 16) & 0xFF) * (1 - t) + ((rgbB >> 16) & 0xFF) * t);
        int gr = Math.round(((rgbA >> 8) & 0xFF) * (1 - t) + ((rgbB >> 8) & 0xFF) * t);
        int b = Math.round((rgbA & 0xFF) * (1 - t) + (rgbB & 0xFF) * t);
        return (r << 16) | (gr << 8) | b;
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int guiWidth) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || drawnHeight == 0
                || drawnScreen != GameUtil.currentScreen()) return false;
        if (mouseX < drawnX || mouseX >= drawnX + drawnWidth
                || mouseY < drawnY || mouseY >= drawnY + drawnHeight) return false;
        dragging = true;
        dragDx = (int) mouseX - drawnX;
        dragDy = (int) mouseY - drawnY;
        return true;
    }

    public static boolean mouseDragged(double mouseX, double mouseY) {
        if (!dragging) return false;
        if (drawnScreen != GameUtil.currentScreen()) { mouseReleased(); return false; }
        ModConfig cfg = ModConfig.get();
        var window = Minecraft.getInstance().getWindow();
        cfg.hudX = Math.clamp((int) mouseX - dragDx, 0, Math.max(0, window.getGuiScaledWidth() - drawnWidth));
        cfg.hudY = Math.clamp((int) mouseY - dragDy, 0, Math.max(0, window.getGuiScaledHeight() - drawnHeight));
        return true;
    }

    public static boolean mouseReleased() {
        if (!dragging) return false;
        dragging = false;
        ModConfig.get().save();
        return true;
    }
}
