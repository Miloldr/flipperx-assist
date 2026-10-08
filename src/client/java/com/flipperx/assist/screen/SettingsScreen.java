package com.flipperx.assist.screen;

import com.flipperx.assist.config.ModConfig;
import com.flipperx.assist.hud.AssistHud;
import com.flipperx.assist.hud.SlotHighlight;
import com.flipperx.assist.hud.SlotHighlight.Look;
import com.flipperx.assist.hud.SlotHighlight.Pulse;
import com.flipperx.assist.hud.SlotHighlight.Shape;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class SettingsScreen extends Screen {
    private static final int PAD = 10;
    private static final int MAX_W = 280;
    private static final int LINE = 11;
    private static final int ROW = 18;
    private static final int CONTROL_H = 15;
    private static final int VALUE_W = 96;
    private static final int BUTTON_H = 20;
    private static final int SWATCH = 10;
    private static final int SWATCH_GAP = 3;
    private static final int HEX_W = 52;
    private static final int PREVIEW_ROWS = 3;
    private static final int PREVIEW_W = 176;
    private static final int PREVIEW_H = PREVIEW_ROWS * 18 + 14;
    private static final long PREVIEW_STEP_MS = 1800;

    private static final int CARD = 0x121212;
    private static final int BUTTON = 0x1E1E1E;
    private static final int[] SWATCHES = {
            0xE8A33D, 0xFFFFFF, 0xFFE14D, 0x7DDF3C, 0x3FC8FF, 0x4A6CFF, 0xE64CFF, 0xFF4A4A};
    private static final int[] PREVIEW_PATH = {15, 16, 10};

    private record Hit(int x0, int y0, int x1, int y1, IntConsumer action) {
        boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private final ModConfig cfg = ModConfig.get();
    private final SlotHighlight preview = new SlotHighlight();
    private final ItemStack[] previewItems = new ItemStack[PREVIEW_ROWS * 9];
    private final List<Hit> hits = new ArrayList<>();
    private final long openedAt = System.currentTimeMillis();
    private EditBox hex;
    private int cardX0, cardY0, cardX1, cardY1;
    private float a;

    public SettingsScreen() {
        super(Component.literal("Bazaar Assist settings"));
        for (int i = 0; i < previewItems.length; i++) previewItems[i] = new ItemStack(Items.STAINED_GLASS_PANE.black());
        previewItems[10] = new ItemStack(Items.GOLDEN_HORSE_ARMOR);
        previewItems[11] = new ItemStack(Items.HOPPER);
        previewItems[13] = shiny(Items.DIAMOND_BLOCK);
        previewItems[15] = new ItemStack(Items.FILLED_MAP);
        previewItems[16] = new ItemStack(Items.MAP);
        previewItems[21] = new ItemStack(Items.ARROW);
    }

    private static ItemStack shiny(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    private static int cardHeight() {
        return PAD + LINE + 5 + PREVIEW_H + 8 + ROW * 4 + 4 + 1 + 5 + ROW + 4 + 1 + 8 + BUTTON_H + PAD;
    }

    @Override
    protected void init() {
        int w = Math.min(MAX_W, width - 24);
        int h = cardHeight();
        cardX0 = (width - w) / 2;
        cardY0 = Math.max(8, (height - h) / 2);
        cardX1 = cardX0 + w;
        cardY1 = cardY0 + h;

        int colorRow = cardY0 + PAD + LINE + 5 + PREVIEW_H + 8;
        int boxX = cardX1 - PAD - HEX_W;
        int boxY = colorRow + (ROW - CONTROL_H) / 2;
        hex = new EditBox(font, boxX + 5, boxY + 4, HEX_W - 7, 8, Component.literal("Color"));
        hex.setBordered(false);
        hex.setMaxLength(7);
        hex.setTextColor(0xFFE8E8E8);
        hex.setValue(SlotHighlight.formatColor(Look.of(cfg).rgb()));
        hex.setResponder(text -> {
            Integer rgb = SlotHighlight.parseColor(text);
            if (rgb == null) return;
            String color = SlotHighlight.formatColor(rgb);
            if (color.equals(cfg.highlightColor)) return;
            cfg.highlightColor = color;
            cfg.save();
        });
        addRenderableWidget(hex);
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
        a = appear();
        hits.clear();
        Font f = font;
        Look look = Look.of(cfg);

        g.fill(cardX0, cardY0, cardX1, cardY1, color(CARD));
        box(g, cardX0, cardY0, cardX1, cardY1, color(AssistHud.BORDER));
        int left = cardX0 + PAD, right = cardX1 - PAD, y = cardY0 + PAD;

        g.text(f, "Slot highlight", left, y, color(AssistHud.TEXT), false);
        y += LINE + 5;

        int px = cardX0 + (cardX1 - cardX0 - PREVIEW_W) / 2;
        drawPreview(g, px, y, look);
        y += PREVIEW_H + 8;

        label(g, f, "Color", left, y);
        drawSwatches(g, right - HEX_W - 8, y, look, mouseX, mouseY);
        int boxY = y + (ROW - CONTROL_H) / 2;
        g.fill(right - HEX_W, boxY, right, boxY + CONTROL_H, color(BUTTON));
        box(g, right - HEX_W, boxY, right, boxY + CONTROL_H, color(hex.isFocused() ? 0x5A5A5A : 0x3A3A3A));
        y += ROW;

        label(g, f, "Style", left, y);
        value(g, f, look.shape().label, right, y, mouseX, mouseY, step -> {
            cfg.highlightShape = cycle(Shape.values(), look.shape(), step).name();
            cfg.save();
        });
        y += ROW;

        label(g, f, "Line", left, y);
        value(g, f, look.width() == 2 ? "Thick" : "Thin", right, y, mouseX, mouseY, step -> {
            cfg.highlightWidth = look.width() == 2 ? 1 : 2;
            cfg.save();
        });
        y += ROW;

        label(g, f, "Pulse", left, y);
        value(g, f, look.pulse().label, right, y, mouseX, mouseY, step -> {
            cfg.highlightPulse = cycle(Pulse.values(), look.pulse(), step).name();
            cfg.save();
        });
        y += ROW;

        y += 4;
        g.fill(left, y, right, y + 1, color(AssistHud.BORDER));
        y += 1 + 5;

        label(g, f, "Tab fills the command", left, y);
        value(g, f, cfg.tabFill ? "On" : "Off", right, y, mouseX, mouseY, step -> {
            cfg.tabFill = !cfg.tabFill;
            cfg.save();
        });
        y += ROW;

        y += 4;
        g.fill(left, y, right, y + 1, color(AssistHud.BORDER));
        y += 1 + 8;

        String done = "Done";
        int doneX0 = right - f.width(done) - 24;
        button(g, f, done, doneX0, y, right, y + BUTTON_H, mouseX, mouseY, step -> onClose());
        String reset = "Reset";
        int resetX1 = doneX0 - 4;
        button(g, f, reset, resetX1 - f.width(reset) - 24, y, resetX1, y + BUTTON_H, mouseX, mouseY,
                step -> reset());

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void drawPreview(GuiGraphicsExtractor g, int px, int py, Look look) {
        int w = PREVIEW_W, h = PREVIEW_H;
        g.fill(px, py, px + w, py + h, color(0xC6C6C6));
        g.fill(px, py, px + w - 1, py + 1, color(0xFFFFFF));
        g.fill(px, py, px + 1, py + h - 1, color(0xFFFFFF));
        g.fill(px + 1, py + h - 1, px + w, py + h, color(0x555555));
        g.fill(px + w - 1, py + 1, px + w, py + h, color(0x555555));
        for (int i = 0; i < previewItems.length; i++) {
            int sx = px + 8 + (i % 9) * 18, sy = py + 8 + (i / 9) * 18;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, color(0x8B8B8B));
            g.fill(sx - 1, sy - 1, sx + 16, sy, color(0x373737));
            g.fill(sx - 1, sy, sx, sy + 16, color(0x373737));
            g.fill(sx, sy + 16, sx + 17, sy + 17, color(0xFFFFFF));
            g.fill(sx + 16, sy, sx + 17, sy + 16, color(0xFFFFFF));
            g.item(previewItems[i], sx, sy);
        }
        long t = System.currentTimeMillis() - openedAt;
        int slot = PREVIEW_PATH[(int) ((t / PREVIEW_STEP_MS) % PREVIEW_PATH.length)];
        preview.draw(g, this, px + 8 + (slot % 9) * 18, py + 8 + (slot / 9) * 18, look);
    }

    private void drawSwatches(GuiGraphicsExtractor g, int rightEdge, int rowY, Look look, int mouseX, int mouseY) {
        int span = SWATCHES.length * SWATCH + (SWATCHES.length - 1) * SWATCH_GAP;
        int sx = rightEdge - span;
        int sy = rowY + (ROW - SWATCH) / 2;
        for (int rgb : SWATCHES) {
            boolean picked = rgb == look.rgb();
            boolean hover = mouseX >= sx - 1 && mouseX < sx + SWATCH + 1 && mouseY >= sy - 1 && mouseY < sy + SWATCH + 1;
            if (picked || hover) {
                box(g, sx - 2, sy - 2, sx + SWATCH + 2, sy + SWATCH + 2,
                        color(picked ? AssistHud.TEXT : 0x5A5A5A));
            }
            g.fill(sx, sy, sx + SWATCH, sy + SWATCH, color(rgb));
            int pick = rgb;
            hits.add(new Hit(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, step -> {
                cfg.highlightColor = SlotHighlight.formatColor(pick);
                cfg.save();
                hex.setValue(cfg.highlightColor);
            }));
            sx += SWATCH + SWATCH_GAP;
        }
    }

    private void label(GuiGraphicsExtractor g, Font f, String text, int x, int rowY) {
        g.text(f, text, x, rowY + (ROW - 7) / 2, color(AssistHud.DIM), false);
    }

    private void value(GuiGraphicsExtractor g, Font f, String text, int right, int rowY, int mouseX, int mouseY,
                       IntConsumer action) {
        int y0 = rowY + (ROW - CONTROL_H) / 2;
        button(g, f, text, right - VALUE_W, y0, right, y0 + CONTROL_H, mouseX, mouseY, action);
    }

    private void button(GuiGraphicsExtractor g, Font f, String label, int x0, int y0, int x1, int y1,
                        int mouseX, int mouseY, IntConsumer action) {
        boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
        g.fill(x0, y0, x1, y1, color(hover ? 0x2E2E2E : BUTTON));
        box(g, x0, y0, x1, y1, color(hover ? 0x5A5A5A : 0x3A3A3A));
        g.text(f, label, x0 + (x1 - x0 - f.width(label)) / 2 + 1, y0 + (y1 - y0 - 7) / 2,
                color(hover ? AssistHud.AMBER : AssistHud.TEXT), false);
        hits.add(new Hit(x0, y0, x1, y1, action));
    }

    private void reset() {
        cfg.highlightColor = Look.DEFAULT_COLOR;
        cfg.highlightShape = Look.DEFAULT.shape().name();
        cfg.highlightWidth = Look.DEFAULT.width();
        cfg.highlightPulse = Look.DEFAULT.pulse().name();
        cfg.tabFill = true;
        cfg.save();
        hex.setValue(cfg.highlightColor);
    }

    private static <E> E cycle(E[] values, E current, int step) {
        int i = 0;
        while (i < values.length && values[i] != current) i++;
        return values[Math.floorMod(i + step, values.length)];
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        for (Hit hit : hits) {
            if (!hit.contains(mx, my)) continue;
            hit.action().accept(event.button() == 1 ? -1 : 1);
            if (!hex.isMouseOver(mx, my)) setFocused(null);
            return true;
        }
        if (mx < cardX0 || mx >= cardX1 || my < cardY0 || my >= cardY1) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubled);
    }
}
