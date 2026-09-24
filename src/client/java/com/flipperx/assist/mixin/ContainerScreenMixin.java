package com.flipperx.assist.mixin;

import com.flipperx.assist.AssistClient;
import com.flipperx.assist.AssistState;
import com.flipperx.assist.AssistState.Step;
import com.flipperx.assist.hud.AssistHud;
import com.flipperx.assist.hud.ProfitPops;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void bzassist$drawOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                      CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        AssistState state = AssistClient.state();
        Step step = state.step();

        boolean drawn = false;
        if (state.running() && state.current() && step.isClick() && step.slot() >= 0) {
            for (Slot slot : self.getMenu().slots) {
                if (slot.index != step.slot()) continue;
                int x = ((AbstractContainerScreenAccessor) self).bzassist$getLeftPos() + slot.x;
                int y = ((AbstractContainerScreenAccessor) self).bzassist$getTopPos() + slot.y;
                AssistHud.renderHighlight(graphics, self, x, y);
                drawn = true;
                break;
            }
        }
        if (!drawn) AssistHud.clearHighlight();
        AssistHud.render(graphics, state);
        ProfitPops.render(graphics);
    }
}
