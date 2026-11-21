package com.flipperx.assist.mixin;

import com.flipperx.assist.game.BazaarMenus;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.coflnet.gui.bazaar.BazaarCharts", remap = false)
public abstract class CoflChartMixin {
    @Inject(method = "chart", at = @At("HEAD"), cancellable = true, require = 0)
    private static void bzassist$chart(CallbackInfoReturnable<Object> cir) {
        cir.setReturnValue(null);
    }

    @Inject(method = "handleKey", at = @At("HEAD"), cancellable = true, require = 0)
    private static void bzassist$key(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "handleMouse", at = @At("HEAD"), cancellable = true, require = 0)
    private static void bzassist$mouse(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
