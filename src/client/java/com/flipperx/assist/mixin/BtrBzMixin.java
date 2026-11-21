package com.flipperx.assist.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.lutzluca.btrbz.core.runtime.Activation", remap = false)
public abstract class BtrBzMixin {
    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true, require = 0)
    private void bzassist$enabled(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
