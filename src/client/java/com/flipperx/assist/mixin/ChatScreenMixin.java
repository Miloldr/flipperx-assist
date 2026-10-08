package com.flipperx.assist.mixin;

import com.flipperx.assist.AssistClient;
import com.flipperx.assist.AssistState.Step;
import com.flipperx.assist.game.Location;
import com.flipperx.assist.hud.GhostText;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow
    protected EditBox input;

    @Shadow
    private CommandSuggestions commandSuggestions;

    @Unique
    private static final String TAB = "Tab";

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void bzassist$tabFill(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.key() != GLFW.GLFW_KEY_TAB || input == null) return;
        AssistClient client = AssistClient.get();
        String command = client == null ? null : client.tabCommand(input.getValue());
        if (command == null) return;
        if (!command.equals(input.getValue())) {
            input.setValue(command);
            input.moveCursorToEnd(false);
            client.tabFilled();
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void bzassist$quietVanilla(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                       CallbackInfo ci) {
        if (input == null || commandSuggestions == null) return;
        AssistClient client = AssistClient.get();
        if (client == null || client.tabCommand(input.getValue()) == null) return;
        commandSuggestions.hide();
        input.setSuggestion(null);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void bzassist$drawGhost(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                    CallbackInfo ci) {
        if (input == null || !Location.skyBlock() || !AssistClient.state().running()
                || !AssistClient.state().current()) return;
        Step step = AssistClient.state().step();
        if (!step.isCommand()) return;

        Minecraft mc = Minecraft.getInstance();
        String typed = input.getValue();
        GhostText.Render render = GhostText.of(typed, step.text());
        if (!render.hasGhost() && !render.hasRed()) return;

        EditBoxAccessor box = (EditBoxAccessor) input;
        int shown = Math.clamp(box.bzassist$getDisplayPos(), 0, typed.length());
        String visible = typed.substring(shown);
        int originX = box.bzassist$getTextX();
        int y = box.bzassist$getTextY();

        if (render.hasGhost()) {
            int ghostX = originX + mc.font.width(visible);
            graphics.text(mc.font, render.ghost(), ghostX, y, 0xFF6E6E6E, false);
            AssistClient client = AssistClient.get();
            if (client != null && client.tabChip()) {
                graphics.text(mc.font, TAB, ghostX + mc.font.width(render.ghost()) + 6, y, 0xFF8A8A8A, false);
            }
        } else {
            int from = Math.max(render.redFrom(), shown);
            if (from >= typed.length()) return;
            String wrong = typed.substring(from);
            int wrongX = originX + mc.font.width(typed.substring(shown, from));
            graphics.text(mc.font, wrong, wrongX, y, 0xFFD9534F, false);
        }
    }
}
