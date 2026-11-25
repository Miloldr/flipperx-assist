package com.flipperx.assist.mixin;

import com.flipperx.assist.AssistClient;
import com.flipperx.assist.AssistState;
import com.flipperx.assist.game.GameUtil;
import com.flipperx.assist.game.Location;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class OrdersTotalMixin {
    private static final int TOOLTIP_DROP = 20;

    @Shadow protected int titleLabelX;
    @Shadow protected int titleLabelY;
    @Shadow protected int imageWidth;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    @Inject(method = "extractLabels", at = @At("TAIL"))
    private void bzassist$ordersTotal(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (!(self instanceof ContainerScreen) || !Location.skyBlock()) return;
        AssistState.OrdersTotal total = AssistClient.state().ordersTotal(
                GameUtil.strip(self.getTitle().getString()).trim());
        if (total == null) return;
        Font font = Minecraft.getInstance().font;
        int width = font.width(total.text());
        int x = imageWidth - titleLabelX - width;
        if (x < titleLabelX + font.width(self.getTitle()) + 4) return;
        graphics.text(font, total.text(), x, titleLabelY, total.color(), false);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (!total.lines().isEmpty() && self.getMenu().getCarried().isEmpty()
                && mx >= x - 2 && mx < x + width + 2 && my >= titleLabelY - 2 && my < titleLabelY + font.lineHeight + 1) {
            graphics.setComponentTooltipForNextFrame(font, total.lines(), mouseX,
                    topPos + titleLabelY + font.lineHeight + TOOLTIP_DROP);
        }
    }
}
