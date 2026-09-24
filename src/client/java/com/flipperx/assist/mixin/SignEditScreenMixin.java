package com.flipperx.assist.mixin;

import com.flipperx.assist.AssistClient;
import com.flipperx.assist.AssistState.Step;
import com.flipperx.assist.hud.AssistHud;
import com.flipperx.assist.hud.GhostText;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignEditScreen.class)
public abstract class SignEditScreenMixin {
    private static final int AMBER = 0xFFE8A33D;
    private static final int WHITE = 0xFFF2F2F2;
    private static final int GREY = 0xFF7A7A7A;
    private static final int GREEN = 0xFF7CCB6B;
    private static final int RED = 0xFFD9534F;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void bzassist$drawAmount(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                     CallbackInfo ci) {
        AssistHud.clearHighlight();
        AssistHud.render(graphics, AssistClient.state());
        if (!AssistClient.state().running() || !AssistClient.state().current()) return;
        Step step = AssistClient.state().step();
        if (!step.isSign() || step.text().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        SignEditScreenAccessor sign = (SignEditScreenAccessor) this;
        String[] lines = sign.bzassist$getMessages();
        String typed = lines != null && lines.length > 0 && lines[0] != null ? lines[0].trim() : "";
        int line = sign.bzassist$getLine();
        String want = step.text();

        int cx = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + 44;

        String amount = "Amount: " + grouped(want);
        drawScaled(graphics, amount, cx, y, AMBER, 1.5f);
        y += 18;

        if (line != 0 && typed.isEmpty()) {
            drawCentered(graphics, "Type it on the first line of the sign", cx, y, RED);
            return;
        }
        GhostText.Render render = GhostText.ofSign(typed, want);
        if (typed.equals(want)) {
            drawCentered(graphics, want + "  done, press Enter", cx, y, GREEN);
            return;
        }
        if (render.hasRed()) {
            drawCentered(graphics, "You typed " + typed + ", it should be " + want
                    + ". Clear the line and type it again.", cx, y, RED);
            return;
        }
        String left = render.ghost();
        int total = mc.font.width(typed) + mc.font.width(left);
        int x = cx - total / 2;
        graphics.text(mc.font, typed, x, y, WHITE, true);
        graphics.text(mc.font, left, x + mc.font.width(typed), y, GREY, true);
    }

    private static void drawCentered(GuiGraphicsExtractor g, String text, int cx, int y, int color) {
        Minecraft mc = Minecraft.getInstance();
        g.text(mc.font, text, cx - mc.font.width(text) / 2, y, color, true);
    }

    private static void drawScaled(GuiGraphicsExtractor g, String text, int cx, int y, int color, float scale) {
        Minecraft mc = Minecraft.getInstance();
        g.pose().pushMatrix();
        g.pose().translate(cx - mc.font.width(text) * scale / 2f, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, color, true);
        g.pose().popMatrix();
    }

    private static String grouped(String digits) {
        if (!digits.chars().allMatch(Character::isDigit)) return digits;
        StringBuilder out = new StringBuilder();
        int n = digits.length();
        for (int i = 0; i < n; i++) {
            if (i > 0 && (n - i) % 3 == 0) out.append(',');
            out.append(digits.charAt(i));
        }
        return out.toString();
    }
}
