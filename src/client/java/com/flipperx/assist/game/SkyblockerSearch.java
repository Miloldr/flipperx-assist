package com.flipperx.assist.game;

import com.flipperx.assist.AssistClient;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class SkyblockerSearch {
    private SkyblockerSearch() {}

    private static final Method GET = find();
    private static Field uiAndVisuals;
    private static Field searchOverlay;
    private static Field enableBazaar;
    private static Object held;
    private static boolean was;

    private static Method find() {
        if (!FabricLoader.getInstance().isModLoaded("skyblocker")) return null;
        try {
            return Class.forName("de.hysky.skyblocker.config.SkyblockerConfigManager").getMethod("get");
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object overlay() throws ReflectiveOperationException {
        Object config = GET.invoke(null);
        if (config == null) return null;
        if (uiAndVisuals == null) uiAndVisuals = config.getClass().getField("uiAndVisuals");
        Object ui = uiAndVisuals.get(config);
        if (ui == null) return null;
        if (searchOverlay == null) searchOverlay = ui.getClass().getField("searchOverlay");
        Object overlay = searchOverlay.get(ui);
        if (overlay != null && enableBazaar == null) enableBazaar = overlay.getClass().getField("enableBazaar");
        return overlay;
    }

    public static void suspend() {
        if (GET == null || held != null || !AssistClient.state().running()) return;
        try {
            Object overlay = overlay();
            if (overlay == null || !enableBazaar.getBoolean(overlay)) return;
            was = true;
            enableBazaar.setBoolean(overlay, false);
            held = overlay;
        } catch (Throwable t) {
            held = null;
        }
    }

    public static void resume() {
        if (held == null) return;
        try {
            enableBazaar.setBoolean(held, was);
        } catch (Throwable ignored) {
        }
        held = null;
    }
}
