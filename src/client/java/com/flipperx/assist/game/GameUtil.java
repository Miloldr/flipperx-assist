package com.flipperx.assist.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class GameUtil {
    private GameUtil() {}

    private static final Pattern COLOR_CODES = Pattern.compile("§.");

    public static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static <T> T call(Supplier<T> supplier) {
        Minecraft mc = mc();
        if (mc.isSameThread()) return supplier.get();
        CompletableFuture<T> fut = new CompletableFuture<>();
        mc.execute(() -> {
            try { fut.complete(supplier.get()); }
            catch (Throwable t) { fut.completeExceptionally(t); }
        });
        try { return fut.get(); } catch (Exception e) { return null; }
    }

    public static String strip(String s) {
        return s == null ? "" : COLOR_CODES.matcher(s).replaceAll("");
    }

    public static Screen currentScreen() {
        return mc().gui == null ? null : mc().gui.screen();
    }

    public static AbstractContainerMenu handler() {
        Screen s = currentScreen();
        return s instanceof AbstractContainerScreen<?> cs ? cs.getMenu() : null;
    }

    public static String screenTitle() {
        Screen s = currentScreen();
        return s == null ? "" : strip(s.getTitle().getString()).trim();
    }

    public static String rawName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name == null) name = stack.getHoverName();
        return name == null ? "" : name.getString();
    }

    public static List<String> lore(ItemStack stack) {
        List<String> out = new ArrayList<>();
        if (stack == null || stack.isEmpty()) return out;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return out;
        for (Component line : lore.lines()) out.add(line.getString());
        return out;
    }

    public static String itemName(ItemStack st) {
        String name = strip(rawName(st)).trim();
        if (name.equals("Enchanted Book")) {
            List<String> l = lore(st);
            if (l.size() > 2) return strip(l.get(2)).trim();
        }
        return name;
    }
}
