package com.flipperx.assist.mixin;

import com.flipperx.assist.CoflPause;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.coflnet.CoflModClient", remap = false)
public abstract class CoflMessageMixin {
    @Inject(method = "displayModMessage", at = @At("HEAD"), cancellable = true, require = 0)
    private static void bzassist$message(CallbackInfo ci) {
        if (CoflPause.active()) ci.cancel();
    }
}
