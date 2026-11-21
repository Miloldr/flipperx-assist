package com.flipperx.assist.mixin;

import com.flipperx.assist.CoflPause;
import com.flipperx.assist.game.BazaarMenus;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "CoflCore.handlers.EventRegistry", remap = false)
public abstract class CoflChatMixin {
    @Inject(method = "onChatMessage(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void bzassist$read(String line, CallbackInfo ci) {
        if (CoflPause.active() || BazaarMenus.chatLine(line)) ci.cancel();
    }

    @Inject(method = "shouldBlockChatMessage", at = @At("HEAD"), cancellable = true, require = 0)
    private static void bzassist$block(CallbackInfoReturnable<Boolean> cir) {
        if (CoflPause.active()) cir.setReturnValue(false);
    }
}
