package com.flipperx.assist.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

import java.util.Locale;

public final class Location {
    private Location() {}

    private static final long UNSURE_MS = 15_000;

    private static boolean skyBlock;
    private static long sureAt;

    public static boolean skyBlock() {
        return skyBlock;
    }

    public static void update(Minecraft mc) {
        Boolean hypixel = hypixel(mc);
        if (Boolean.FALSE.equals(hypixel)) {
            skyBlock = false;
            return;
        }
        long now = System.currentTimeMillis();
        String title = hypixel == null ? null : sidebarTitle(mc);
        if (title != null) {
            skyBlock = title.contains("SKYBLOCK");
            sureAt = now;
        } else if (now - sureAt > UNSURE_MS) {
            skyBlock = false;
        }
    }

    private static Boolean hypixel(Minecraft mc) {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return null;
        ServerData server = mc.getCurrentServer();
        if (server != null && server.ip != null) {
            String host = server.ip.toLowerCase(Locale.ROOT).split(":")[0];
            if (host.equals("hypixel.net") || host.endsWith(".hypixel.net")) return true;
        }
        String brand = connection.serverBrand();
        if (brand == null) return null;
        return brand.toLowerCase(Locale.ROOT).contains("hypixel");
    }

    private static String sidebarTitle(Minecraft mc) {
        if (mc.level == null) return null;
        Objective obj = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) return null;
        return GameUtil.strip(obj.getDisplayName().getString()).toUpperCase(Locale.ROOT);
    }
}
