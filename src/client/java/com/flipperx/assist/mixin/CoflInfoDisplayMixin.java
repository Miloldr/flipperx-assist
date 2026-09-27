package com.flipperx.assist.mixin;

import com.flipperx.assist.CoflPause;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.coflnet.gui.hud.InfoDisplayRenderer", remap = false)
public abstract class CoflInfoDisplayMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
    private void bzassist$render(CallbackInfo ci) {
        if (CoflPause.active()) ci.cancel();
    }

    @Inject(method = "styleAt", at = @At("HEAD"), cancellable = true, require = 0)
    private static void bzassist$click(CallbackInfoReturnable<Object> cir) {
        if (CoflPause.active()) cir.setReturnValue(null);
    }
}
