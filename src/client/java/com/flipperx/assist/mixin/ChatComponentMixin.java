package com.flipperx.assist.mixin;

import com.flipperx.assist.screen.Flex;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void bzassist$hideForFlex(GuiGraphicsExtractor g, Font font, int tick, int mouseX, int mouseY,
                                      ChatComponent.DisplayMode mode, boolean focused, CallbackInfo ci) {
        if (Flex.hidingChat()) ci.cancel();
    }
}
