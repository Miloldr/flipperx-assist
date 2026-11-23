package com.flipperx.assist.screen;

import com.flipperx.assist.AssistClient;
import com.flipperx.assist.game.Compat;
import com.flipperx.assist.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class HeldMenu extends Screen {
    public static final long SIGN_MS = 1500;
    private static final int OFF = -10_000;
    private static final long DIM_AFTER_MS = 100;
    private static final int DIM = 0x80000000;

    private final long since = System.currentTimeMillis();
    private final long forMs;
    private AbstractContainerScreen<?> menu;
    private long closedAt;

    public HeldMenu(AbstractContainerScreen<?> menu, long forMs, long closedAt) {
        super(Component.empty());
        this.menu = menu;
        this.forMs = forMs;
        this.closedAt = closedAt;
    }

    public AbstractContainerScreen<?> menu() {
        return menu;
    }

    public void closed() {
        if (closedAt == 0) closedAt = System.currentTimeMillis();
    }

    public long closedAt() {
        return closedAt;
    }

    @Override
    public void tick() {
        if (System.currentTimeMillis() - since > forMs) Compat.setScreen(null);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (menu != null && !background(menu, graphics, delta)) menu = null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (menu != null && !contents(menu, graphics, delta, closedAt)) menu = null;
    }

    public static boolean drawOver(AbstractContainerScreen<?> menu, GuiGraphicsExtractor graphics, float delta,
                                   long closedAt) {
        graphics.nextStratum();
        if (!background(menu, graphics, delta)) return false;
        graphics.nextStratum();
        return contents(menu, graphics, delta, closedAt);
    }

    private static boolean background(AbstractContainerScreen<?> menu, GuiGraphicsExtractor graphics, float delta) {
        try {
            menu.extractBackground(graphics, OFF, OFF, delta);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean contents(AbstractContainerScreen<?> menu, GuiGraphicsExtractor graphics, float delta,
                                    long closedAt) {
        try {
            menu.extractRenderState(graphics, OFF, OFF, delta);
            if (closedAt == 0 || System.currentTimeMillis() - closedAt < DIM_AFTER_MS) return true;
            AbstractContainerScreenAccessor at = (AbstractContainerScreenAccessor) menu;
            int left = at.bzassist$getLeftPos(), top = at.bzassist$getTopPos();
            graphics.nextStratum();
            graphics.fill(left, top, left + at.bzassist$getImageWidth(), top + at.bzassist$getImageHeight(), DIM);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (Minecraft.getInstance().options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        if (super.keyPressed(event)) return true;
        AssistClient client = AssistClient.get();
        if (client != null) client.handleKey(event);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }
}
