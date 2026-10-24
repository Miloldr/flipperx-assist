package com.flipperx.assist.screen;

import com.flipperx.assist.AssistClient;
import com.flipperx.assist.hud.AssistHud;
import com.flipperx.assist.hud.ItemIcon;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

public final class PassiveScreen extends Screen {
    private static final int PAD = 10;
    private static final int MAX_W = 300;
    private static final int LINE = 11;
    private static final int BIG = 3;
    private static final int CHART_H = 64;
    private static final int FOOT_GAP = 9;
    private static final int BUTTON_H = 20;
    private static final long WEEK_MS = 7L * 24 * 3600 * 1000;

    private static final int CARD = 0x121212;
    private static final int GRID = 0x1C1C1C;
    private static final int BASE = 0x333333;
    private static final int BUTTON = 0x1E1E1E;
    private static final int TRACK = 0x2A2A2A;
    private static final int CLOSE = 14;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH);

    private final PassiveSummary s;
    private final long openedAt = System.currentTimeMillis();
    private final long now = System.currentTimeMillis();
    private final double top;
    private float a = 1f;
    private boolean notified;
    private int cardX0, cardY0, cardX1, cardY1;
    private int closeX0, closeY0, closeX1, closeY1;
    private int notifyX0, notifyY0, notifyX1, notifyY1;
    private int flexX0, flexY0, flexX1, flexY1;
    private boolean flexShown;
    private int flexStage;

    public PassiveScreen(PassiveSummary summary) {
        super(Component.literal("Passive flips"));
        this.s = summary;
        double cum = 0, hi = 0;
        for (double p : summary.profits()) {
            cum += p;
            hi = Math.max(hi, cum);
        }
        top = Math.max(1, hi) * 1.12;
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

    private int cardHeight() {
        int h = PAD * 2;
        h += LINE + 3;
        h += 8 * BIG + 6;
        h += LINE + 8;
        h += CHART_H + 4 + LINE + 6;
        if (s.best() != null) h += 16 + 6;
        if (s.goal() != null) h += LINE + 3 + 8;
        h += 3 + 1 + FOOT_GAP + BUTTON_H;
        return h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (flexStage > 0) {
            mouseX = -1;
            mouseY = -1;
            if (flexStage == 1) flexStage = 2;
        }
        long t = System.currentTimeMillis() - openedAt;
        a = appear();
        Font f = font;
        int w = Math.min(MAX_W, width - 24);
        int h = cardHeight();
        int x0 = (width - w) / 2;
        int y0 = Math.max(8, (height - h) / 2) + Math.round((1f - a) * 6f);
        cardX0 = x0; cardY0 = y0; cardX1 = x0 + w; cardY1 = y0 + h;

        g.fill(x0, y0, x0 + w, y0 + h, AssistHud.argb(CARD, 0.96f * a));
        box(g, x0, y0, x0 + w, y0 + h, color(AssistHud.BORDER));
        int left = x0 + PAD, right = x0 + w - PAD, y = y0 + PAD;

        g.text(f, s.title(), left, y, color(AssistHud.DIM), false);
        closeX1 = right + 3; closeX0 = closeX1 - CLOSE; closeY0 = y - 3; closeY1 = closeY0 + CLOSE;
        Flex.closeX(g, closeX0, closeY0, CLOSE, hit(mouseX, mouseY, closeX0, closeY0, closeX1, closeY1), a);
        g.text(f, s.span(), closeX0 - 6 - f.width(s.span()), y, color(AssistHud.DIM), false);
        y += LINE + 3;

        float k = AssistHud.easeOut3(Math.min(1f, Math.max(0f, (t - 120f) / 900f)));
        g.pose().pushMatrix();
        g.pose().translate(left, y);
        g.pose().scale(BIG, BIG);
        g.text(f, AssistHud.plus(s.total() * k), 0, 0, color(AssistHud.AMBER), false);
        g.pose().popMatrix();
        if (s.work() != null) {
            int wx = left + f.width(AssistHud.plus(s.total())) * BIG + 6;
            if (wx + f.width(s.work()) <= right) word(g, f, s.work(), wx, y + 7 * BIG - 7, AssistHud.DIM);
        }
        y += 8 * BIG + 6;

        int cx = word(g, f, AssistHud.plus(s.since()), left, y, AssistHud.AMBER) + 5;
        cx = word(g, f, s.sinceLabel(), cx, y, AssistHud.DIM);
        if (s.took() != null && right - f.width(s.took()) > cx + 8) {
            word(g, f, s.took(), right - f.width(s.took()), y, AssistHud.DIM);
        }
        y += LINE + 8;

        chart(g, f, left, y, right, t, mouseX, mouseY);
        y += CHART_H + 4 + LINE + 6;

        if (s.best() != null) {
            Summary.Best b = s.best();
            int bx = word(g, f, s.bestLabel(), left, y + 4, AssistHud.DIM) + 8;
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
            int pct = (int) Math.floor(Math.min(1, gl.after() / target) * 100);
            g.text(f, gl.label() == null ? "" : gl.label(), left, y, color(AssistHud.TEXT), false);
            String at = gl.after() >= target ? "reached" : pct + "%";
            g.text(f, at, right - f.width(at), y, color(AssistHud.DIM), false);
            y += LINE;
            float grow = AssistHud.easeOut3(Math.min(1f, Math.max(0f, (t - 500f) / 900f)));
            g.fill(left, y, right, y + 3, color(TRACK));
            int fill = (int) Math.round((right - left) * Math.min(1, gl.after() / target) * grow);
            if (fill > 0) g.fill(left, y, left + fill, y + 3, color(AssistHud.AMBER));
            y += 3 + 8;
        }

        y += 3;
        g.fill(left, y, right, y + 1, color(AssistHud.BORDER));
        y += 1 + FOOT_GAP;
        int textY = y + (BUTTON_H - 7) / 2;
        int buttonsLeft = right + 4;
        if (s.notifyLabel() != null) {
            String label = notified ? "Notifications on" : s.notifyLabel();
            notifyX1 = buttonsLeft - 4; notifyX0 = notifyX1 - Math.max(f.width(s.notifyLabel()), f.width("Notifications on")) - 20;
            notifyY0 = y; notifyY1 = y + BUTTON_H;
            button(g, f, label, notifyX0, notifyY0, notifyX1, notifyY1, textY, mouseX, mouseY, !notified);
            buttonsLeft = notifyX0;
        }
        flexShown = false;
        if (s.flex() != null) {
            String label = Flex.label(s.flex());
            flexX1 = buttonsLeft - 4;
            flexX0 = flexX1 - Math.max(f.width(label), f.width(s.flex().label())) - 20;
            flexY0 = y; flexY1 = y + BUTTON_H;
            flexShown = flexX0 >= left;
            if (flexShown) {
                boolean live = Flex.pressable(s.flex());
                boolean hover = live && hit(mouseX, mouseY, flexX0, flexY0, flexX1, flexY1);
                g.fill(flexX0, flexY0, flexX1, flexY1, color(hover ? 0x2E2E2E : BUTTON));
                box(g, flexX0, flexY0, flexX1, flexY1, color(hover ? 0x5A5A5A : 0x3A3A3A));
                g.text(f, label, flexX0 + (flexX1 - flexX0 - f.width(label)) / 2, textY,
                        color(!live ? AssistHud.DIM : hover ? AssistHud.AMBER : AssistHud.TEXT), false);
            }
        }
    }

    private static boolean hit(double mx, double my, int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    @Override
    public void tick() {
        if (flexStage == 2) {
            flexStage = 0;
            Flex.capture(s.flex());
        }
    }

    @Override
    public void removed() {
        if (flexStage != 0) Flex.cancel();
        super.removed();
    }

    private void chart(GuiGraphicsExtractor g, Font f, int left, int topY, int right, long t, int mouseX, int mouseY) {
        int plotL = left, plotR = right, plotT = topY + 3, plotB = topY + CHART_H;
        for (int i = 1; i <= 3; i++) {
            int yy = plotT + (plotB - plotT) * i / 4 - 1;
            g.fill(plotL, yy, plotR, yy + 1, color(GRID));
        }
        g.fill(plotL, plotB, plotR, plotB + 1, color(BASE));

        long start = now - WEEK_MS;
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime day = Instant.ofEpochMilli(start).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone);
        int labelY = plotB + 4;
        int lastLabelR = Integer.MIN_VALUE;
        String nowLabel = "now";
        int nowX = plotR - f.width(nowLabel);
        while (day.toInstant().toEpochMilli() < now) {
            int xx = x(day.toInstant().toEpochMilli(), start, plotL, plotR);
            String label = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            int lx = xx - f.width(label) / 2;
            if (lx > lastLabelR + 6 && lx + f.width(label) < nowX - 6 && lx >= plotL) {
                g.text(f, label, lx, labelY, color(0x5E5E5E), false);
                lastLabelR = lx + f.width(label);
            }
            day = day.plusDays(1);
        }
        g.text(f, nowLabel, nowX, labelY, color(AssistHud.DIM), false);

        float reveal = AssistHud.easeOut3(Math.min(1f, Math.max(0f, (t - 80f) / 1000f)));
        int clipR = plotL + Math.round((plotR - plotL) * reveal);
        long[] ts = s.times();
        double[] ps = s.profits();
        int n = ts.length;
        double cum = 0;
        int prevX = plotL, prevY = y(0, plotT, plotB);
        int[] sx = new int[n], sy = new int[n];
        g.enableScissor(plotL - 1, plotT - 4, clipR + 1, plotB + 1);
        for (int i = 0; i < n; i++) {
            int xi = x(ts[i], start, plotL, plotR);
            g.fill(prevX, Math.min(prevY, plotB), xi, plotB, color(AssistHud.AMBER, 0.08f));
            g.fill(prevX, prevY, xi + 1, prevY + 2, color(AssistHud.AMBER));
            cum += ps[i];
            int yi = y(cum, plotT, plotB);
            g.fill(xi, Math.min(prevY, yi), xi + 2, Math.max(prevY, yi) + 2, color(AssistHud.AMBER));
            sx[i] = xi;
            sy[i] = yi;
            prevX = xi;
            prevY = yi;
        }
        g.fill(prevX, Math.min(prevY, plotB), plotR, plotB, color(AssistHud.AMBER, 0.08f));
        g.fill(prevX, prevY, plotR, prevY + 2, color(AssistHud.AMBER));
        g.disableScissor();

        int hovered = -1;
        if (mouseY >= plotT - 4 && mouseY <= plotB + 2) {
            int best = 5;
            for (int i = 0; i < n && sx[i] <= clipR; i++) {
                int d = Math.abs(mouseX - (sx[i] + 1));
                if (d <= best) {
                    best = d;
                    hovered = i;
                }
            }
        }
        if (n > 0 && sx[n - 1] <= clipR) {
            float pop = AssistHud.easeOut3(Math.min(1f, Math.max(0f, (clipR - sx[n - 1]) / 30f)));
            dot(g, sx[n - 1] + 1, sy[n - 1] + 1, Math.max(1, Math.round(3 * pop)), AssistHud.AMBER);
        }
        if (hovered >= 0 && hovered != n - 1) dot(g, sx[hovered] + 1, sy[hovered] + 1, 2, AssistHud.TEXT);
        if (hovered >= 0 && reveal >= 1f) {
            String when = WHEN.format(Instant.ofEpochMilli(ts[hovered]).atZone(zone));
            g.setComponentTooltipForNextFrame(f, List.of(
                    Component.literal(AssistHud.plus(ps[hovered])).withStyle(st -> st.withColor(AssistHud.AMBER)),
                    Component.literal(when).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    private void dot(GuiGraphicsExtractor g, int cx, int cy, int r, int rgb) {
        g.fill(cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1, color(CARD));
        g.fill(cx - r, cy - r, cx + r, cy + r, color(rgb));
    }

    private int y(double v, int topY, int bottom) {
        return (int) Math.round(bottom - 1 - Math.max(0, v) / top * (bottom - 1 - topY));
    }

    private static int x(long ms, long start, int left, int right) {
        double share = Math.clamp((double) (ms - start) / WEEK_MS, 0, 1);
        return (int) Math.round(left + share * (right - left - 2));
    }

    private void button(GuiGraphicsExtractor g, Font f, String label, int x0, int y0, int x1, int y1, int textY,
                        int mouseX, int mouseY, boolean main) {
        boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
        g.fill(x0, y0, x1, y1, color(hover ? (main ? 0x2A2216 : 0x2E2E2E) : BUTTON));
        int edge = main ? AssistHud.AMBER : (hover ? 0x5A5A5A : 0x3A3A3A);
        box(g, x0, y0, x1, y1, color(edge));
        int text = main || hover ? AssistHud.AMBER : AssistHud.TEXT;
        if (!main && notified && label.equals("Notifications on")) text = AssistHud.DIM;
        g.text(f, label, x0 + (x1 - x0 - f.width(label)) / 2, textY, color(text), false);
    }

    private int word(GuiGraphicsExtractor g, Font f, String text, int x, int yy, int rgb) {
        g.text(f, text, x, yy, color(rgb), false);
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
        if (s.notifyLabel() != null && !notified && mx >= notifyX0 && mx < notifyX1 && my >= notifyY0 && my < notifyY1) {
            AssistClient client = AssistClient.get();
            if (client != null) client.passive("notify");
            notified = true;
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
}
