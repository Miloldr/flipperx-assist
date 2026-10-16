package com.flipperx.assist.mixin;

import com.flipperx.assist.AssistClient;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class SlotClickMixin {
    @Inject(method = "slotClicked", at = @At("HEAD"))
    private void bzassist$clicked(Slot slot, int index, int button, ContainerInput input, CallbackInfo ci) {
        if (slot != null) AssistClient.slotClicked(slot);
    }
}
