package com.flipperx.assist.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int bzassist$getLeftPos();

    @Accessor("topPos")
    int bzassist$getTopPos();

    @Accessor("imageWidth")
    int bzassist$getImageWidth();

    @Accessor("imageHeight")
    int bzassist$getImageHeight();

    @Accessor("hoveredSlot")
    Slot bzassist$getHoveredSlot();
}
