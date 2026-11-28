package com.flipperx.assist.mixin;

import com.flipperx.assist.AssistClient;
import com.flipperx.assist.screen.HeldMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class CoveredSignMixin {
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), cancellable = true)
    private void bzassist$drawMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                   CallbackInfo ci) {
        if (!(AssistClient.covering((Screen) (Object) this) instanceof AbstractContainerScreen<?> menu)) return;
        if (!HeldMenu.drawOver(menu, graphics, delta, AssistClient.coverSince())) {
            AssistClient.uncover();
            return;
        }
        graphics.extractDeferredElements(mouseX, mouseY, delta);
        ci.cancel();
    }
}
