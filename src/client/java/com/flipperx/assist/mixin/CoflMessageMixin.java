package com.flipperx.assist.mixin;

import com.flipperx.assist.CoflPause;
import com.flipperx.assist.game.BazaarMenus;

import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.coflnet.CoflModClient", remap = false)
public abstract class CoflMessageMixin {
    @Inject(method = "displayModMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void bzassist$message(Component message, CallbackInfo ci) {
        if (CoflPause.active() || (message != null && BazaarMenus.about(message.getString()))) ci.cancel();
    }
}
