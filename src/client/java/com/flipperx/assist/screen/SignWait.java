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

public final class SignWait extends Screen {
    private static final long MAX_MS = 1500;
    private static final int OFF = -10_000;
    private static final int DIM = 0x80000000;

    private final long since = System.currentTimeMillis();
    private AbstractContainerScreen<?> menu;

    public SignWait(AbstractContainerScreen<?> menu) {
        super(Component.empty());
        this.menu = menu;
    }

    public AbstractContainerScreen<?> menu() {
        return menu;
    }

    @Override
    public void tick() {
        if (System.currentTimeMillis() - since > MAX_MS) Compat.setScreen(null);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (menu == null) return;
        try {
            menu.extractBackground(graphics, OFF, OFF, delta);
        } catch (Throwable t) {
            menu = null;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (menu == null) return;
        try {
            menu.extractRenderState(graphics, OFF, OFF, delta);
            AbstractContainerScreenAccessor at = (AbstractContainerScreenAccessor) menu;
            int left = at.bzassist$getLeftPos(), top = at.bzassist$getTopPos();
            graphics.nextStratum();
            graphics.fill(left, top, left + at.bzassist$getImageWidth(), top + at.bzassist$getImageHeight(), DIM);
        } catch (Throwable t) {
            menu = null;
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
