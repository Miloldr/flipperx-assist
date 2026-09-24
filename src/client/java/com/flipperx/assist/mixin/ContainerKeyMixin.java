package com.flipperx.assist.mixin;

import com.flipperx.assist.AssistClient;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerKeyMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void bzassist$hotkeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        AssistClient client = AssistClient.get();
        if (client != null && client.handleKey(event)) cir.setReturnValue(true);
    }
}
