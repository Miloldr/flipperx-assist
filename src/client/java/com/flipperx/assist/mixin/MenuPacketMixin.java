package com.flipperx.assist.mixin;

import com.flipperx.assist.AssistClient;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MenuPacketMixin {
    @Inject(method = "handleOpenScreen", at = @At("RETURN"))
    private void bzassist$opened(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        AssistClient.menuChanged();
    }

    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void bzassist$content(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        AssistClient.menuChanged();
    }

    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void bzassist$slot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        AssistClient.menuChanged();
    }

    @Inject(method = "handleOpenSignEditor", at = @At("RETURN"))
    private void bzassist$sign(ClientboundOpenSignEditorPacket packet, CallbackInfo ci) {
        AssistClient.menuChanged();
    }

    @Inject(method = "handleContainerClose", at = @At("RETURN"))
    private void bzassist$closed(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        AssistClient.menuChanged();
    }
}
