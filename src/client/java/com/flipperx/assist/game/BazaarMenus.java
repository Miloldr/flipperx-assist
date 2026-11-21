package com.flipperx.assist.game;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;

public final class BazaarMenus {
    private BazaarMenus() {}

    private static final String[] TITLES = {
            "how many do you want?", "how many are you selling?",
            "how much do you want to pay?", "at what price are you selling?", "order options"
    };
    private static final String[] BUTTONS = {
            "Create Buy Order", "Create Sell Offer", "Cancel Order", "Flip Order", "Manage Orders"
    };

    private static final long KEEP_MS = 250;
    private static volatile Screen seen;
    private static volatile long seenAt;
    private static volatile boolean seenOpen;

    public static boolean open() {
        Screen screen = GameUtil.currentScreen();
        long now = System.currentTimeMillis();
        if (screen == seen && now - seenAt < KEEP_MS) return seenOpen;
        boolean open = read(screen);
        seen = screen;
        seenAt = now;
        seenOpen = open;
        return open;
    }

    private static boolean read(Screen screen) {
        try {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return false;
            String t = GameUtil.strip(cs.getTitle().getString()).trim().toLowerCase(Locale.ROOT);
            if (t.contains("bazaar")) return true;
            for (String title : TITLES) if (t.equals(title)) return true;
            if (t.startsWith("confirm") && (t.contains("buy order") || t.contains("sell offer"))) return true;
            for (Slot slot : cs.getMenu().slots) {
                if (!slot.hasItem()) continue;
                String name = GameUtil.strip(GameUtil.rawName(slot.getItem()));
                for (String button : BUTTONS) if (name.contains(button)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean chatLine(String line) {
        if (line == null) return false;
        String s = GameUtil.strip(line).trim();
        return s.startsWith("[Bazaar]") || s.startsWith("Bazaar!");
    }

    public static boolean about(String text) {
        return text != null && GameUtil.strip(text).toLowerCase(Locale.ROOT).contains("bazaar");
    }
}
