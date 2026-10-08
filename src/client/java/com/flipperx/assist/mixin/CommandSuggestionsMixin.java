package com.flipperx.assist.mixin;

import com.flipperx.assist.AssistClient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow
    @Final
    private Screen screen;

    @Shadow
    @Final
    private EditBox input;

    @Inject(method = "showSuggestions", at = @At("HEAD"), cancellable = true)
    private void bzassist$noList(boolean immediateNarration, CallbackInfo ci) {
        if (bzassist$owned()) ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void bzassist$noUsage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (bzassist$owned()) ci.cancel();
    }

    @Inject(method = "formatChat", at = @At("HEAD"), cancellable = true)
    private void bzassist$plainText(String text, int offset, CallbackInfoReturnable<FormattedCharSequence> cir) {
        if (bzassist$owned()) cir.setReturnValue(null);
    }

    @Unique
    private boolean bzassist$owned() {
        if (!(screen instanceof ChatScreen) || input == null) return false;
        AssistClient client = AssistClient.get();
        return client != null && client.tabCommand(input.getValue()) != null;
    }
}
