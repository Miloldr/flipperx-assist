package com.flipperx.assist.screen;

import com.flipperx.assist.AssistClient;
import com.flipperx.assist.Chat;
import com.flipperx.assist.config.ModConfig;
import com.flipperx.assist.game.Compat;
import com.flipperx.assist.hud.AssistHud;
import com.flipperx.assist.hud.ItemIcon;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class SummaryScreen extends Screen {
    private boolean byPlayer;
    private static final int PAD = 10;
    private static final int MAX_W = 300;
    private static final int LINE = 11;
    private static final int BIG = 3;
    private static final int CHART_H = 70;
    private static final int CHART_MIN_H = 40;
    private static final int FOOT_GAP = 9;
    private static final int BUTTON_H = 20;

    private static final int CARD = 0x121212;
    private static final int TRACK = 0x2A2A2A;
    private static final int GRID = 0x222222;
    private static final int BUTTON = 0x1E1E1E;

    private final Summary s;
    private final long openedAt = System.currentTimeMillis();
    private final double yMin, yMax, yStep;

    private int chartH = CHART_H;
    private int cardX0, cardY0, cardX1, cardY1;
    private int closeX0, closeY0, closeX1, closeY1;
    private int shareX0, shareY0, shareX1, shareY1;
    private int flexX0, flexY0, flexX1, flexY1;
    private boolean flexShown;
    private int flexStage;
    private int sellX0, sellY0, sellX1, sellY1;
    private int shotX0, shotY0, shotX1, shotY1;
    private boolean shotShown;
    private int shotStage;
    private long shotAt;
    private Snapshot.Saved shot;
    private static final long COPIED_MS = 2500;
    private static final long SAVED_MS = 6000;
    private static final int CLOSE = 14;
    private static final String SHARE = "Replay";
    private static final String SCREENSHOT = "Screenshot";
    private static final int GLOW_SESSIONS = 3;
    private boolean glow;
    private boolean sellInline;
    private float a = 1f;

    public SummaryScreen(Summary summary) {
        super(Component.literal("Session summary"));
        this.s = summary;
        ModConfig cfg = ModConfig.get();
        if (summary.shareUrl() != null && !cfg.replayClicked && cfg.replayGlows < GLOW_SESSIONS) {
            glow = true;
            cfg.replayGlows++;
            cfg.save();
        }
        double lo = 0, hi = 0;
        for (float[] p : summary.series()) {
            lo = Math.min(lo, p[1]);
            hi = Math.max(hi, p[1]);
        }
        double range = Math.max(1, hi - lo);
        yStep = niceStep(range / 4.0);
        yMin = lo < 0 ? lo - range * 0.06 : 0;
        yMax = hi + range * 0.08;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, AssistHud.argb(0x000000, 0.38f * appear()));
    }

    private float appear() {
        return AssistHud.easeOut3(Math.min(1f, (System.currentTimeMillis() - openedAt) / 240f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (shotStage > 0 || flexStage > 0) {
            mouseX = -1;
            mouseY = -1;
            if (shotStage == 1) shotStage = 2;
            if (flexStage == 1) flexStage = 2;
        }
        long t = System.currentTimeMillis() - openedAt;
        a = appear();
        Font f = font;
        int w = Math.min(MAX_W, width - 24);
        int inner = w - PAD * 2;
        List<FormattedCharSequence> held = s.held() == null ? List.of()
                : f.split(Component.literal(s.held()), inner);
        int sellW = s.sellOff() == null ? 0 : f.width(s.sellOff()) + 20;
        sellInline = s.sellOff() != null && held.size() == 1 && f.width(held.get(0)) + 8 + sellW <= inner;
        chartH = CHART_H;
        int h = height(held.size());
        if (h > height - 16) {
            chartH = Math.max(CHART_MIN_H, CHART_H - (h - (height - 16)));
            h = height(held.size());
        }
        int x0 = (width - w) / 2;
        int y0 = Math.max(8, (height - h) / 2) + Math.round((1f - a) * 6f);
        cardX0 = x0; cardY0 = y0; cardX1 = x0 + w; cardY1 = y0 + h;

        g.fill(x0, y0, x0 + w, y0 + h, AssistHud.argb(CARD, 0.96f * a));
        box(g, x0, y0, x0 + w, y0 + h, color(AssistHud.BORDER));
        int left = x0 + PAD, right = x0 + w - PAD, y = y0 + PAD;

        g.text(f, "Session over", left, y, color(AssistHud.DIM), false);
        closeX1 = right + 3; closeX0 = closeX1 - CLOSE; closeY0 = y - 3; closeY1 = closeY0 + CLOSE;
        Flex.closeX(g, closeX0, closeY0, CLOSE, hit(mouseX, mouseY, closeX0, closeY0, closeX1, closeY1), a);
        String length = duration(s.runSeconds());
        g.text(f, length, closeX0 - 6 - f.width(length), y, color(AssistHud.DIM), false);
        y += LINE + 3;

        float k = AssistHud.easeOut3(Math.min(1f, Math.max(0f, (t - 120f) / 900f)));
        g.pose().pushMatrix();
        g.pose().translate(left, y);
        g.pose().scale(BIG, BIG);
        g.text(f, AssistHud.plus(s.profit() * k), 0, 0, color(AssistHud.AMBER), false);
        g.pose().popMatrix();
        y += 8 * BIG + 5;

        int cx = left;
        if (s.perHour() != null) {
            cx = word(g, f, AssistHud.compact(s.perHour()), cx, y, AssistHud.TEXT);
            cx = word(g, f, "/h", cx, y, AssistHud.DIM) + 10;
        }
        cx = word(g, f, String.valueOf(s.sales()), cx, y, AssistHud.TEXT);
        word(g, f, s.sales() == 1 ? " sale" : " sales", cx, y, AssistHud.DIM);
        y += LINE + 8;

        chart(g, f, left, y, right, t, mouseX, mouseY);
        y += chartH + 8;

        if (s.best() != null) {
            Summary.Best b = s.best();
            int bx = word(g, f, "Best item", left, y + 4, AssistHud.DIM) + 8;
            if (ItemIcon.draw(g, b.look(), b.itemId(), b.icon(), bx, y, AssistHud.argb(0xFFFFFF, a))) {
                bx += 20;
            }
            bx = word(g, f, b.itemName() == null ? "" : b.itemName(), bx, y + 4, AssistHud.TEXT);
            if (b.quantity() > 0) word(g, f, "  " + b.quantity() + "x", bx, y + 4, AssistHud.DIM);
            String gain = AssistHud.plus(b.profit());
            g.text(f, gain, right - f.width(gain), y + 4, color(AssistHud.AMBER), false);
            y += 16 + 6;
        }

        if (s.goal() != null) {
            Summary.Goal gl = s.goal();
            double target = Math.max(1, gl.target());
            int before = (int) Math.floor(Math.min(1, gl.before() / target) * 100);
            int after = (int) Math.floor(Math.min(1, gl.after() / target) * 100);
            g.text(f, gl.label() == null ? "" : gl.label(), left, y, color(AssistHud.TEXT), false);
            String moved = gl.after() >= target ? "reached" : before == after ? after + "%" : before + "% to " + after + "%";
            g.text(f, moved, right - f.width(moved), y, color(AssistHud.DIM), false);
            y += LINE;
            float grow = AssistHud.easeOut3(Math.min(1f, Math.max(0f, (t - 500f) / 900f)));
            double fb = Math.min(1, gl.before() / target), fa = Math.min(1, gl.after() / target);
            int span = right - left;
            g.fill(left, y, right, y + 3, color(TRACK));
            int now = (int) Math.round(span * (fb + (fa - fb) * grow));
            if (now > 0) g.fill(left, y, left + now, y + 3, color(AssistHud.AMBER));
            int was = (int) Math.round(span * fb);
            if (was > 0) g.fill(left, y, left + was, y + 3, color(0x9A6A26));
            y += 3 + 8;
        }

        if (s.speed() != null) y = speed(g, f, s.speed(), left, right, y, t);

        if (s.sellOff() != null) {
            if (sellInline) {
                int ty = y + (BUTTON_H - 7) / 2;
                g.text(f, held.get(0), left, ty, color(AssistHud.TEXT), false);
                sellButton(g, f, right - sellW, y, right, mouseX, mouseY);
                y += BUTTON_H;
            } else {
                for (FormattedCharSequence line : held) {
                    g.text(f, line, left, y, color(AssistHud.TEXT), false);
                    y += LINE;
                }
                y += 4;
                sellButton(g, f, right - sellW, y, right, mouseX, mouseY);
                y += BUTTON_H;
            }
        } else
        for (FormattedCharSequence line : held) {
            g.text(f, line, left, y, color(AssistHud.DIM), false);
            y += LINE;
        }
        if (!held.isEmpty()) y += 4;

        y += 3;
        g.fill(left, y, right, y + 1, color(AssistHud.BORDER));
        y += 1 + FOOT_GAP;
        int textY = y + (BUTTON_H - 7) / 2;
        int buttonsLeft = right + 4;
        if (s.shareUrl() != null) {
            shareX1 = buttonsLeft - 4; shareX0 = shareX1 - f.width(SHARE) - 20;
            shareY0 = y; shareY1 = y + BUTTON_H;
            if (glow) glow(g, shareX0, shareY0, shareX1, shareY1, t);
            button(g, f, SHARE, shareX0, shareY0, shareX1, shareY1, textY, mouseX, mouseY);
            if (glow) {
                float p = pulse(t);
                box(g, shareX0, shareY0, shareX1, shareY1, color(AssistHud.AMBER, 0.45f + 0.55f * p));
                g.text(f, SHARE, shareX0 + (shareX1 - shareX0 - f.width(SHARE)) / 2, textY,
                        color(AssistHud.AMBER), false);
            }
            buttonsLeft = shareX0;
        }
        flexShown = false;
        if (s.flex() != null) {
            String label = Flex.label(s.flex());
            flexX1 = buttonsLeft - 4;
            flexX0 = flexX1 - Math.max(f.width(label), f.width(s.flex().label())) - 20;
            flexY0 = y; flexY1 = y + BUTTON_H;
            flexShown = flexX0 >= left;
            if (flexShown) {
                flexButton(g, f, label, textY, mouseX, mouseY);
                buttonsLeft = flexX0;
            }
        }
        long sinceShot = System.currentTimeMillis() - shotAt;
        String shotLabel = sinceShot >= COPIED_MS ? SCREENSHOT : shot == null ? "Failed" : "Saved";
        shotX1 = buttonsLeft - 4; shotX0 = shotX1 - f.width(SCREENSHOT) - 20;
        shotY0 = y; shotY1 = y + BUTTON_H;
        shotShown = shotX0 >= left;
        if (shotShown) {
            button(g, f, shotLabel, shotX0, shotY0, shotX1, shotY1, textY, mouseX, mouseY);
            buttonsLeft = shotX0;
        }
        if (sinceShot < SAVED_MS) {
            String where = shot == null ? "Could not save it"
                    : shot.downloads() ? "Saved to Downloads" : "Saved to screenshots";
            if (left + f.width(where) + 6 <= buttonsLeft) {
                g.text(f, where, left, textY, color(shot == null ? AssistHud.DIM : AssistHud.AMBER), false);
            }
        }
    }

    private void flexButton(GuiGraphicsExtractor g, Font f, String label, int textY, int mouseX, int mouseY) {
        boolean live = Flex.pressable(s.flex());
        boolean hover = live && hit(mouseX, mouseY, flexX0, flexY0, flexX1, flexY1);
        g.fill(flexX0, flexY0, flexX1, flexY1, color(hover ? 0x2A2216 : BUTTON));
        box(g, flexX0, flexY0, flexX1, flexY1, color(live ? AssistHud.AMBER : 0x3A3A3A));
        g.text(f, label, flexX0 + (flexX1 - flexX0 - f.width(label)) / 2, textY,
                color(live ? AssistHud.AMBER : AssistHud.DIM), false);
    }

    private static boolean hit(double mx, double my, int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    private void sellButton(GuiGraphicsExtractor g, Font f, int x0, int y0, int x1, int mouseX, int mouseY) {
        int y1 = y0 + BUTTON_H;
        sellX0 = x0; sellY0 = y0; sellX1 = x1; sellY1 = y1;
        int textY = y0 + (BUTTON_H - 7) / 2;
        boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
        g.fill(x0, y0, x1, y1, color(hover ? 0x2E2E2E : BUTTON));
        box(g, x0, y0, x1, y1, color(AssistHud.AMBER));
        g.text(f, s.sellOff(), x0 + (x1 - x0 - f.width(s.sellOff())) / 2, textY, color(AssistHud.AMBER), false);
    }

    private void button(GuiGraphicsExtractor g, Font f, String label, int x0, int y0, int x1, int y1, int textY,
                        int mouseX, int mouseY) {
        boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
        g.fill(x0, y0, x1, y1, color(hover ? 0x2E2E2E : BUTTON));
        box(g, x0, y0, x1, y1, color(hover ? 0x5A5A5A : 0x3A3A3A));
        g.text(f, label, x0 + (x1 - x0 - f.width(label)) / 2, textY,
                color(hover ? AssistHud.AMBER : AssistHud.TEXT), false);
    }

    private static float pulse(long t) {
        return 0.5f - 0.5f * (float) Math.cos(t / 1400.0 * Math.PI * 2);
    }

    private void glow(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, long t) {
        float p = pulse(t);
        for (int i = 4; i >= 1; i--) {
            float alpha = (0.05f + 0.1f * p) * (5 - i) / 4f;
            g.fill(x0 - i, y0 - i, x1 + i, y1 + i, color(AssistHud.AMBER, alpha));
        }
    }

    private int speed(GuiGraphicsExtractor g, Font f, Summary.Speed sp, int left, int right, int y, long t) {
        int vx = word(g, f, "Speed", left, y, AssistHud.DIM) + 8;
        word(g, f, sp.tier() == null ? "" : sp.tier(), vx, y, AssistHud.AMBER);
        if (sp.best()) {
            String pb = "New personal best";
            g.text(f, pb, right - f.width(pb), y, color(AssistHud.AMBER), false);
        }
        y += LINE;

        float grow = AssistHud.easeOut3(Math.min(1f, Math.max(0f, (t - 500f) / 900f)));
        int n = sp.tiers(), gap = 2;
        int segW = (right - left - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            int x0 = left + i * (segW + gap);
            int x1 = i == n - 1 ? right : x0 + segW;
            g.fill(x0, y, x1, y + 3, color(TRACK));
            float lit = (float) Math.min(1, Math.max(0, sp.fill() * grow * n - i));
            if (lit > 0) g.fill(x0, y, x0 + Math.round((x1 - x0) * lit), y + 3, color(AssistHud.AMBER));
        }
        y += 3 + 6;

        if (sp.week() != null) {
            word(g, f, "Other players", left, y, AssistHud.DIM);
            g.text(f, sp.week(), right - f.width(sp.week()), y, color(AssistHud.TEXT), false);
            y += LINE;
        }
        if (sp.own() != null || sp.recent().length > 0) {
            int bx = word(g, f, "Your sessions", left, y, AssistHud.DIM) + 8;
            int ownX = sp.own() == null ? right : right - f.width(sp.own());
            int count = sp.recent().length + 1;
            int fit = Math.max(1, (ownX - 8 - bx + 1) / 3);
            for (int i = Math.max(0, count - fit); i < count; i++) {
                boolean now = i == count - 1;
                double sec = now ? sp.seconds() : sp.recent()[i];
                int h = 2 + (int) Math.round(6 * Math.min(1, Math.max(0, (1.6 - sec) / 1.1)));
                g.fill(bx, y + 8 - h, bx + 2, y + 8, color(now ? AssistHud.AMBER : 0x5A5A5A));
                bx += 3;
            }
            if (sp.own() != null) g.text(f, sp.own(), ownX, y, color(AssistHud.TEXT), false);
            y += LINE;
        }
        return y + 4;
    }

    private int speedHeight() {
        Summary.Speed sp = s.speed();
        if (sp == null) return 0;
        int h = LINE + 3 + 6 + 4;
        if (sp.week() != null) h += LINE;
        if (sp.own() != null || sp.recent().length > 0) h += LINE;
        return h;
    }

    private int height(int heldLines) {
        int h = PAD * 2;
        h += LINE + 3;
        h += 8 * BIG + 5;
        h += LINE + 8;
        h += chartH + 8;
        if (s.best() != null) h += 16 + 6;
        if (s.goal() != null) h += LINE + 3 + 8;
        h += heldLines * LINE;
        if (s.sellOff() != null) h += sellInline ? BUTTON_H - LINE : BUTTON_H + 4;
        if (heldLines > 0) h += 4;
        h += speedHeight();
        h += 3 + 1 + FOOT_GAP + BUTTON_H;
        return h;
    }

    private void chart(GuiGraphicsExtractor g, Font f, int left, int top, int right, long t, int mouseX, int mouseY) {
        float[][] pts = s.series();
        double total = Math.max(1, s.runSeconds());
        int axisW = 0;
        for (double v = firstTick(); v <= yMax; v += yStep) axisW = Math.max(axisW, f.width(axisLabel(v)));
        int plotL = left + axisW + 5, plotR = right, plotT = top + 4, plotB = top + chartH - 12;

        for (double v = firstTick(); v <= yMax; v += yStep) {
            int yy = y(v, plotT, plotB);
            g.fill(plotL, yy, plotR, yy + 1, color(v == 0 ? 0x333333 : GRID));
            String label = axisLabel(v);
            g.text(f, label, plotL - 5 - f.width(label), yy - 4, color(AssistHud.DIM), false);
        }
        int labelY = plotB + 4;
        g.text(f, "0min", plotL, labelY, color(AssistHud.DIM), false);
        String mid = duration(Math.round(total / 2));
        g.text(f, mid, (plotL + plotR) / 2 - f.width(mid) / 2, labelY, color(AssistHud.DIM), false);
        String end = duration(total);
        g.text(f, end, plotR - f.width(end), labelY, color(AssistHud.DIM), false);
        if (pts.length == 0) return;

        float reveal = AssistHud.easeOut3(Math.min(1f, Math.max(0f, (t - 80f) / 1000f)));
        int clipR = plotL + Math.round((plotR - plotL) * reveal);
        int zero = y(0, plotT, plotB);
        g.enableScissor(plotL - 2, plotT - 3, clipR + 1, plotB + 3);
        for (int i = 0; i < pts.length; i++) {
            int xa = x(pts[i][0], total, plotL, plotR);
            int xb = i + 1 < pts.length ? x(pts[i + 1][0], total, plotL, plotR) : plotR;
            int ya = y(pts[i][1], plotT, plotB);
            if (xb > xa) {
                g.fill(xa, Math.min(ya, zero), xb, Math.max(ya, zero), color(AssistHud.AMBER, 0.12f));
                g.fill(xa, ya, xb, ya + 1, color(AssistHud.AMBER));
            }
            if (i + 1 < pts.length) {
                int yb = y(pts[i + 1][1], plotT, plotB);
                g.fill(xb, Math.min(ya, yb), xb + 1, Math.max(ya, yb) + 1, color(AssistHud.AMBER));
            }
        }
        g.disableScissor();

        if (mouseX >= plotL && mouseX <= plotR && mouseY >= plotT - 4 && mouseY <= plotB + 2 && reveal >= 1f) {
            double at = (double) (mouseX - plotL) / (plotR - plotL) * total;
            int idx = 0;
            for (int i = 0; i < pts.length; i++) if (pts[i][0] <= at) idx = i;
            int hy = y(pts[idx][1], plotT, plotB);
            g.fill(mouseX, plotT, mouseX + 1, plotB, color(AssistHud.TEXT, 0.3f));
            g.fill(mouseX - 2, hy - 2, mouseX + 3, hy + 3, color(CARD));
            g.fill(mouseX - 1, hy - 1, mouseX + 2, hy + 2, color(AssistHud.TEXT));
            g.setComponentTooltipForNextFrame(f, List.of(
                    Component.literal(AssistHud.compact(pts[idx][1])).withStyle(st -> st.withColor(AssistHud.AMBER)),
                    Component.literal(duration(Math.round(at)) + " in").withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
        }
    }

    private double firstTick() {
        return Math.ceil(yMin / yStep) * yStep;
    }

    private static String axisLabel(double v) {
        return v == 0 ? "0" : AssistHud.compact(v).replace(".0M", "M").replace(".0B", "B");
    }

    private int y(double v, int top, int bottom) {
        return (int) Math.round(bottom - (v - yMin) / (yMax - yMin) * (bottom - top));
    }

    private static int x(double seconds, double total, int left, int right) {
        return (int) Math.round(left + Math.clamp(seconds / total, 0, 1) * (right - left));
    }

    private static double niceStep(double raw) {
        double pow = Math.pow(10, Math.floor(Math.log10(raw)));
        for (double m : new double[]{1, 2, 2.5, 5, 10}) {
            if (m * pow >= raw) return m * pow;
        }
        return 10 * pow;
    }

    static String duration(double seconds) {
        long m = Math.round(seconds / 60.0);
        if (m < 60) return m + "min";
        return (m / 60) + "h " + (m % 60) + "min";
    }

    private int word(GuiGraphicsExtractor g, Font f, String text, int x, int y, int rgb) {
        g.text(f, text, x, y, color(rgb), false);
        return x + f.width(text);
    }

    private static void box(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int argb) {
        g.fill(x0, y0, x1, y0 + 1, argb);
        g.fill(x0, y1 - 1, x1, y1, argb);
        g.fill(x0, y0, x0 + 1, y1, argb);
        g.fill(x1 - 1, y0, x1, y1, argb);
    }

    private int color(int rgb) {
        return AssistHud.argb(rgb, a);
    }

    private int color(int rgb, float alpha) {
        return AssistHud.argb(rgb, alpha * a);
    }

    @Override
    public void tick() {
        if (flexStage == 2) {
            flexStage = 0;
            Flex.capture(s.flex());
        }
        if (shotStage != 2) return;
        shotStage = 0;
        Snapshot.take(saved -> {
            shot = saved;
            shotAt = System.currentTimeMillis();
            announce(saved);
        });
    }

    private static void announce(Snapshot.Saved saved) {
        ChatComponent chat = Compat.chat();
        if (chat == null) return;
        if (saved == null) {
            chat.addClientSystemMessage(Chat.line("Could not save the screenshot."));
            return;
        }
        String path = saved.file().toAbsolutePath().toString();
        Component name = Component.literal(saved.file().getFileName().toString()).withStyle(st -> st
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenFile(path))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(path))));
        String where = saved.downloads() ? "Screenshot saved to Downloads: " : "Screenshot saved to your screenshots folder: ";
        chat.addClientSystemMessage(Chat.line(Component.literal(where).append(name)));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        if (shotShown && mx >= shotX0 && mx < shotX1 && my >= shotY0 && my < shotY1) {
            if (shotStage == 0) shotStage = 1;
            return true;
        }
        if (s.sellOff() != null && mx >= sellX0 && mx < sellX1 && my >= sellY0 && my < sellY1) {
            AssistClient client = AssistClient.get();
            if (client != null) client.finish("", true);
            onClose();
            return true;
        }
        if (s.shareUrl() != null && hit(mx, my, shareX0, shareY0, shareX1, shareY1)) {
            AssistClient client = AssistClient.get();
            if (client != null) client.openReplay(s.shareUrl(), s.shareId());
            glow = false;
            ModConfig cfg = ModConfig.get();
            if (!cfg.replayClicked) {
                cfg.replayClicked = true;
                cfg.save();
            }
            return true;
        }
        if (flexShown && flexStage == 0 && hit(mx, my, flexX0, flexY0, flexX1, flexY1)) {
            if (Flex.press(s.flex())) flexStage = 1;
            return true;
        }
        if (hit(mx, my, closeX0, closeY0, closeX1, closeY1)) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    protected void init() {
        super.init();
        byPlayer = false;
        AssistClient client = AssistClient.get();
        if (client != null) client.summaryShown();
    }

    @Override
    public void onClose() {
        byPlayer = true;
        super.onClose();
    }

    @Override
    public void removed() {
        if (flexStage != 0) Flex.cancel();
        AssistClient client = AssistClient.get();
        if (client != null && (byPlayer || !client.summaryPushedOff())) client.summaryClosed();
        super.removed();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int mods = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER;
        boolean modifier = event.key() >= GLFW.GLFW_KEY_LEFT_SHIFT && event.key() <= GLFW.GLFW_KEY_RIGHT_SUPER;
        if (modifier || (event.modifiers() & mods) != 0) return super.keyPressed(event);
        AssistClient client = AssistClient.get();
        if (client != null && client.handleKey(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
