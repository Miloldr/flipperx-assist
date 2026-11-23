package com.flipperx.assist.mixin;

import com.flipperx.assist.AssistClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({Gui.class, Minecraft.class})
public abstract class SetScreenMixin {
    @ModifyVariable(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"),
            argsOnly = true, require = 0)
    private Screen bzassist$keepForSign(Screen screen) {
        return AssistClient.screenFor(screen);
    }
}
