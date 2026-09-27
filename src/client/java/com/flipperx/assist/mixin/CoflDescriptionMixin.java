package com.flipperx.assist.mixin;

import com.flipperx.assist.CoflPause;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Pseudo
@Mixin(targets = "CoflCore.handlers.DescriptionHandler", remap = false)
public abstract class CoflDescriptionMixin {
    @Inject(method = "loadDescriptionForInventory([Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;LCoflCore/classes/Position;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void bzassist$upload(CallbackInfo ci) {
        if (CoflPause.active()) ci.cancel();
    }

    @Inject(method = "getTooltipData", at = @At("HEAD"), cancellable = true, require = 0)
    private static void bzassist$tooltip(CallbackInfoReturnable<Object[]> cir) {
        if (CoflPause.active()) cir.setReturnValue(null);
    }

    @Inject(method = "getInfoDisplay", at = @At("RETURN"), cancellable = true, require = 0)
    private static void bzassist$panel(CallbackInfoReturnable<Object[]> cir) {
        Object[] shown = cir.getReturnValue();
        if (CoflPause.active() && shown != null) cir.setReturnValue(Arrays.copyOf(shown, 0));
    }
}
