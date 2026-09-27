package com.flipperx.assist.mixin;

import com.flipperx.assist.CoflPause;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "CoflCore.network.WSClient", remap = false)
public abstract class CoflSocketMixin {
    @Inject(method = "Send", at = @At("HEAD"), cancellable = true, require = 0)
    private void bzassist$send(CallbackInfo ci) {
        if (CoflPause.active()) ci.cancel();
    }

    @Inject(method = "HandleCommand", at = @At("HEAD"), cancellable = true, require = 0)
    private static void bzassist$receive(CallbackInfo ci) {
        if (CoflPause.active()) ci.cancel();
    }
}
