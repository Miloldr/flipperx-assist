package com.flipperx.assist.mixin;

import com.flipperx.assist.AssistClient;
import com.flipperx.assist.AssistState;
import com.flipperx.assist.game.GameUtil;
import com.flipperx.assist.game.Location;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractContainerScreen.class)
public abstract class OrderMarkMixin {
    @Shadow protected Slot hoveredSlot;

    private static AssistState.Mark bzassist$markOf(Slot slot) {
        if (slot == null || slot.container instanceof Inventory || !Location.skyBlock()) return null;
        ItemStack stack = slot.getItem();
        if (stack == null || stack.isEmpty()) return null;
        return AssistClient.state().mark(slot.index, GameUtil.rawName(stack));
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void bzassist$markSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY,
                                   CallbackInfo ci) {
        AssistState.Mark mark = bzassist$markOf(slot);
        if (mark != null) graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, mark.background());
    }

    @Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), cancellable = true)
    private void bzassist$markLore(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        AssistState.Mark mark = bzassist$markOf(hoveredSlot);
        if (mark == null || mark.lore().isEmpty() || hoveredSlot.getItem() != stack) return;
        List<Component> lines = new ArrayList<>(cir.getReturnValue());
        for (String line : mark.lore().split("\n", -1)) {
            lines.add(Component.literal(line).withColor(mark.loreColor()));
        }
        cir.setReturnValue(lines);
    }
}
