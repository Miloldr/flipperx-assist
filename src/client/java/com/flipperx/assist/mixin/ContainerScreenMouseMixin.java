package com.flipperx.assist.mixin;

import com.flipperx.assist.hud.AssistHud;
import com.flipperx.assist.hud.ProfitPops;
import net.minecraft.world.inventory.Slot;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMouseMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void bzassist$startDrag(MouseButtonEvent event, boolean doubled,
                                    CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (event.button() == 0 && AssistHud.mouseClicked(event.x(), event.y(), self.width)) {
            cir.setReturnValue(true);
            return;
        }
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) self;
        Slot slot = acc.bzassist$getHoveredSlot();
        if (slot != null) {
            ProfitPops.noteClick(self, acc.bzassist$getLeftPos() + slot.x, acc.bzassist$getTopPos() + slot.y);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void bzassist$drag(MouseButtonEvent event, double dx, double dy,
                               CallbackInfoReturnable<Boolean> cir) {
        if (AssistHud.mouseDragged(event.x(), event.y())) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void bzassist$endDrag(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (AssistHud.mouseReleased()) cir.setReturnValue(true);
    }
}
