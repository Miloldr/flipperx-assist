package com.flipperx.assist.screen;

import com.flipperx.assist.AssistClient;
import com.flipperx.assist.hud.AssistHud;
import com.flipperx.assist.hud.IconCache;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class SummaryScreen extends Screen {
    private static final int PAD = 10;
    private static final int MAX_W = 300;
    private static final int LINE = 11;
    private static final int BIG = 3;
    private static final int CHART_H = 70;
    private static final int CHART_MIN_H = 40;

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
    private float a = 1f;

    public SummaryScreen(Summary summary) {
        super(Component.literal("Session summary"));
        this.s = summary;
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
        long t = System.currentTimeMillis() - openedAt;
        a = appear();
        Font f = font;
        int w = Math.min(MAX_W, width - 24);
        int inner = w - PAD * 2;
        List<FormattedCharSequence> held = s.held() == null ? List.of()
                : f.split(Component.literal(s.held()), inner);
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
        String length = duration(s.runSeconds());
        g.text(f, length, right - f.width(length), y, color(AssistHud.DIM), false);
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
            int bx = word(g, f, "Best flip", left, y + 4, AssistHud.DIM) + 8;
            Identifier icon = IconCache.get(b.itemId(), b.icon());
            if (icon != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, icon, bx, y, 0f, 0f, 16, 16, 16, 16,
                        AssistHud.argb(0xFFFFFF, a));
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

        for (FormattedCharSequence line : held) {
            g.text(f, line, left, y, color(AssistHud.DIM), false);
            y += LINE;
        }
        if (!held.isEmpty()) y += 4;

        g.fill(left, y, right, y + 1, color(AssistHud.BORDER));
        y += 7;
        g.text(f, "Esc or any movement key closes this", left, y + 2, color(AssistHud.DIM), false);
        String close = "Close";
        int bw = f.width(close) + 16;
        closeX0 = right - bw; closeX1 = right; closeY0 = y - 3; closeY1 = y + 12;
        boolean hover = mouseX >= closeX0 && mouseX < closeX1 && mouseY >= closeY0 && mouseY < closeY1;
        g.fill(closeX0, closeY0, closeX1, closeY1, color(hover ? 0x2E2E2E : BUTTON));
        box(g, closeX0, closeY0, closeX1, closeY1, color(hover ? 0x5A5A5A : 0x3A3A3A));
        g.text(f, close, closeX0 + 8, y + 1, color(hover ? AssistHud.AMBER : AssistHud.TEXT), false);
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
        if (heldLines > 0) h += 4;
        h += 7 + 12;
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
        g.text(f, "0m", plotL, labelY, color(AssistHud.DIM), false);
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

        Summary.Best b = s.best();
        if (b != null) {
            int bx = x(b.x(), total, plotL, plotR), by = y(b.cum(), plotT, plotB);
            if (bx <= clipR) {
                g.fill(bx - 2, by - 2, bx + 3, by + 3, color(CARD));
                g.fill(bx - 1, by - 1, bx + 2, by + 2, color(AssistHud.AMBER));
            }
        }

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
        if (m < 60) return m + "m";
        return (m / 60) + "h " + (m % 60) + "m";
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        boolean onClose = mx >= closeX0 && mx < closeX1 && my >= closeY0 && my < closeY1;
        boolean outside = mx < cardX0 || mx >= cardX1 || my < cardY0 || my >= cardY1;
        if (onClose || outside) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        Options o = Minecraft.getInstance().options;
        if (o.keyUp.matches(event) || o.keyDown.matches(event) || o.keyLeft.matches(event)
                || o.keyRight.matches(event) || o.keyJump.matches(event) || o.keyShift.matches(event)
                || o.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        AssistClient client = AssistClient.get();
        if (client != null && client.handleKey(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
