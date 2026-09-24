package com.flipperx.assist.hud;

import com.flipperx.assist.game.GameUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

public final class ProfitPops {
    private ProfitPops() {}

    private static final long CLICK_WINDOW_MS = 4000;
    private static final float LIFE_MS = 1300f;
    private static final float BIG_LIFE_MS = 1700f;
    private static final float POP_IN_MS = 170f;
    private static final float RISE = 20f;
    private static final float BIG_RISE = 26f;
    private static final long MERGE_MS = 1200;

    private static final int LOSS = 0xD27C78;
    private static final int SMALL = 0xDADADA;

    private record Pop(String text, double amount, float x, float y, long born, Screen screen, int color,
                       int scale, float rise) {
        boolean big() { return scale > 1; }
    }

    private static final List<Pop> POPS = new ArrayList<>();
    private static long clickAt;
    private static float clickX, clickY;
    private static Screen clickScreen;

    public static void noteClick(Screen screen, float x, float y) {
        clickAt = System.currentTimeMillis();
        clickX = x + 8f;
        clickY = y;
        clickScreen = screen;
    }

    public static void spawn(double profit) {
        long now = System.currentTimeMillis();
        Screen screen = GameUtil.currentScreen();
        boolean clicked = clickScreen != null && clickScreen == screen && now - clickAt < CLICK_WINDOW_MS;
        if (!clicked && AssistHud.moneyAnchor() != null) {
            AssistHud.showDelta(profit);
            return;
        }
        float x, y;
        if (clicked) {
            x = clickX;
            y = clickY;
        } else {
            var w = Minecraft.getInstance().getWindow();
            x = w.getGuiScaledWidth() / 2f;
            y = w.getGuiScaledHeight() / 3f;
        }
        Pop last = POPS.isEmpty() ? null : POPS.getLast();
        if (last != null && last.rise() > 0 && last.screen() == screen && now - last.born() < MERGE_MS
                && Math.abs(last.x() - x) < 1f && Math.abs(last.y() - y) < 1f) {
            POPS.removeLast();
            profit += last.amount();
        }
        int color = profit < 0 ? LOSS : profit < 100_000 ? SMALL : AssistHud.AMBER;
        int scale = profit >= 1_000_000 ? 2 : 1;
        POPS.add(new Pop(AssistHud.plus(profit), profit, x, y, now, screen, color, scale,
                scale > 1 ? BIG_RISE : profit < 0 ? RISE * 0.6f : RISE));
        while (POPS.size() > 24) POPS.removeFirst();
    }

    public static void banner(String text) {
        float[] under = AssistHud.underAnchor();
        float x, y;
        if (under != null) {
            x = under[0];
            y = under[1] + 24f;
        } else {
            var w = Minecraft.getInstance().getWindow();
            x = w.getGuiScaledWidth() / 2f;
            y = w.getGuiScaledHeight() / 3f;
        }
        POPS.add(new Pop(text, 0, x, y, System.currentTimeMillis(), GameUtil.currentScreen(), AssistHud.AMBER,
                2, -8f));
    }

    public static void render(GuiGraphicsExtractor g) {
        if (POPS.isEmpty()) return;
        long now = System.currentTimeMillis();
        Screen screen = GameUtil.currentScreen();
        Font font = Minecraft.getInstance().font;
        POPS.removeIf(p -> now - p.born() > (p.big() ? BIG_LIFE_MS : LIFE_MS));
        for (Pop p : POPS) {
            if (p.screen() != screen) continue;
            float life = p.big() ? BIG_LIFE_MS : LIFE_MS;
            float t = (now - p.born()) / life;
            float rise = p.rise() * AssistHud.easeOut3(t);
            float alpha = Math.min(1f, (now - p.born()) / 90f) * (t < 0.62f ? 1f : 1f - (t - 0.62f) / 0.38f);
            float scale = p.scale();
            if (p.big()) {
                float k = Math.min(1f, (now - p.born()) / POP_IN_MS);
                scale *= 1f + 0.3f * (1f - AssistHud.easeOut3(k));
            }
            int w = font.width(p.text());
            g.pose().pushMatrix();
            g.pose().translate(p.x(), p.y() - 3f - rise);
            g.pose().scale(scale, scale);
            int left = -(w + 1) / 2;
            g.fill(left - 2, -10, left + w + 2, 1, AssistHud.argb(0x000000, 0.62f * alpha));
            g.text(font, p.text(), left, -8, AssistHud.argb(p.color(), alpha), false);
            g.pose().popMatrix();
        }
    }
}
