package com.flipperx.assist.game;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class Compat {
    private Compat() {}

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

    private static final MethodHandle HUD = find(() -> LOOKUP.findGetter(Gui.class, "hud",
            Class.forName("net.minecraft.client.gui.Hud")));
    private static final MethodHandle GET_CHAT = HUD == null
            ? find(() -> LOOKUP.findVirtual(Gui.class, "getChat", MethodType.methodType(ChatComponent.class)))
            : find(() -> LOOKUP.findVirtual(HUD.type().returnType(), "getChat",
                    MethodType.methodType(ChatComponent.class)));
    private static final MethodHandle GET_TAB_LIST = HUD == null
            ? find(() -> LOOKUP.findVirtual(Gui.class, "getTabList", MethodType.methodType(PlayerTabOverlay.class)))
            : find(() -> LOOKUP.findVirtual(HUD.type().returnType(), "getTabList",
                    MethodType.methodType(PlayerTabOverlay.class)));
    private static final MethodHandle IS_HIDDEN = HUD == null ? null
            : find(() -> LOOKUP.findVirtual(HUD.type().returnType(), "isHidden", MethodType.methodType(boolean.class)));
    private static final MethodHandle HIDE_GUI = IS_HIDDEN != null ? null
            : find(() -> LOOKUP.findGetter(Options.class, "hideGui", boolean.class));

    private static final MethodHandle GUI_SCREEN = find(() -> LOOKUP.findVirtual(Gui.class, "screen",
            MethodType.methodType(Screen.class)));
    private static final MethodHandle GUI_SET_SCREEN = find(() -> LOOKUP.findVirtual(Gui.class, "setScreen",
            MethodType.methodType(void.class, Screen.class)));
    private static final MethodHandle MC_SCREEN = GUI_SCREEN != null ? null
            : find(() -> LOOKUP.findGetter(Minecraft.class, "screen", Screen.class));
    private static final MethodHandle MC_SET_SCREEN = GUI_SET_SCREEN != null ? null
            : find(() -> LOOKUP.findVirtual(Minecraft.class, "setScreen",
                    MethodType.methodType(void.class, Screen.class)));

    private static final Object TEXTURED = staticField(RenderPipelines.class, "GUI_TEXTURED");
    private static final MethodHandle BLIT = TEXTURED == null ? null : find(() -> {
        for (Method m : GuiGraphicsExtractor.class.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getName().equals("blit") && p.length == 11 && p[0].isInstance(TEXTURED)
                    && p[1] == Identifier.class && p[4] == float.class) {
                return LOOKUP.unreflect(m);
            }
        }
        return null;
    });

    private static final InputConstants.Type KEYBOARD = findKeyboard();

    public static final int KEY_LBRACKET = key("KEY_LBRACKET");
    public static final int KEY_RBRACKET = key("KEY_RBRACKET");
    public static final int KEY_B = key("KEY_B");

    public static InputConstants.Type keyboard() {
        return KEYBOARD;
    }

    private static InputConstants.Type findKeyboard() {
        for (InputConstants.Type t : InputConstants.Type.values()) {
            if (t.name().equals("KEYSYM") || t.name().equals("KEYBOARD")) return t;
        }
        return InputConstants.Type.values()[0];
    }

    public static Screen screen() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (GUI_SCREEN != null) return mc.gui == null ? null : (Screen) GUI_SCREEN.invoke(mc.gui);
            return MC_SCREEN == null ? null : (Screen) MC_SCREEN.invoke(mc);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void setScreen(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (GUI_SET_SCREEN != null) GUI_SET_SCREEN.invoke(mc.gui, screen);
            else if (MC_SET_SCREEN != null) MC_SET_SCREEN.invoke(mc, screen);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    public static ChatComponent chat() {
        Object owner = hud();
        if (owner == null || GET_CHAT == null) return null;
        try {
            return (ChatComponent) GET_CHAT.invoke(owner);
        } catch (Throwable t) {
            return null;
        }
    }

    public static PlayerTabOverlay tabList() {
        Object owner = hud();
        if (owner == null || GET_TAB_LIST == null) return null;
        try {
            return (PlayerTabOverlay) GET_TAB_LIST.invoke(owner);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean hudHidden() {
        try {
            if (IS_HIDDEN != null) {
                Object owner = hud();
                return owner == null || (boolean) IS_HIDDEN.invoke(owner);
            }
            Options options = Minecraft.getInstance().options;
            return HIDE_GUI != null && options != null && (boolean) HIDE_GUI.invoke(options);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean blitIcon(GuiGraphicsExtractor g, Identifier icon, int x, int y, int tint) {
        if (BLIT == null) return false;
        try {
            BLIT.invoke(g, TEXTURED, icon, x, y, 0f, 0f, 16, 16, 16, 16, tint);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Item item(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(path));
    }

    private static Object hud() {
        Gui gui = Minecraft.getInstance().gui;
        if (gui == null) return null;
        if (HUD == null) return gui;
        try {
            return HUD.invoke(gui);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int key(String name) {
        try {
            return InputConstants.class.getField(name).getInt(null);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    private static Object staticField(Class<?> owner, String name) {
        try {
            Field f = owner.getField(name);
            return f.get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private interface Finder {
        MethodHandle find() throws Throwable;
    }

    private static MethodHandle find(Finder finder) {
        try {
            return finder.find();
        } catch (Throwable t) {
            return null;
        }
    }
}
