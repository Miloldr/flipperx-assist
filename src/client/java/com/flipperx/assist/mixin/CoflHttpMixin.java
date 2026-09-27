package com.flipperx.assist.mixin;

import com.flipperx.assist.CoflPause;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "CoflCore.network.QueryServerCommands", remap = false)
public abstract class CoflHttpMixin {
    @Inject(method = "PostRequest", at = @At("HEAD"), cancellable = true, require = 0)
    private static void bzassist$post(CallbackInfoReturnable<String> cir) {
        if (CoflPause.active()) cir.setReturnValue(null);
    }
}
