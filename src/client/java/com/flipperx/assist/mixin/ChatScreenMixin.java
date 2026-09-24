package com.flipperx.assist.mixin;

import com.flipperx.assist.AssistClient;
import com.flipperx.assist.AssistState.Step;
import com.flipperx.assist.hud.GhostText;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow
    protected EditBox input;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void bzassist$drawGhost(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                    CallbackInfo ci) {
        if (input == null || !AssistClient.state().running() || !AssistClient.state().current()) return;
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
            graphics.text(mc.font, render.ghost(), originX + mc.font.width(visible), y, 0xFF6E6E6E, false);
        } else {
            int from = Math.max(render.redFrom(), shown);
            if (from >= typed.length()) return;
            String wrong = typed.substring(from);
            int wrongX = originX + mc.font.width(typed.substring(shown, from));
            graphics.text(mc.font, wrong, wrongX, y, 0xFFD9534F, false);
        }
    }
}
