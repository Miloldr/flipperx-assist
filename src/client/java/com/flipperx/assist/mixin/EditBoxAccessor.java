package com.flipperx.assist.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditBox.class)
public interface EditBoxAccessor {
    @Accessor("textX")
    int bzassist$getTextX();

    @Accessor("textY")
    int bzassist$getTextY();

    @Accessor("displayPos")
    int bzassist$getDisplayPos();
}
