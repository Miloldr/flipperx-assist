package com.flipperx.assist.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.flipperx.assist.mixin.PlayerListHudAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;

public final class ScreenReader {
    private ScreenReader() {}

    public static JsonObject tick() {
        return GameUtil.call(ScreenReader::tickUnlocked);
    }

    private static JsonObject tickUnlocked() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "tick");

        Screen screen = GameUtil.currentScreen();
        o.addProperty("view_id", screen == null ? 0 : System.identityHashCode(screen));
        if (screen instanceof AbstractSignEditScreen) {
            o.addProperty("kind", "sign");
            o.addProperty("title", "");
        } else if (screen instanceof ChatScreen) {
            o.addProperty("kind", "none");
            o.addProperty("title", "");
            o.add("slots", new JsonArray());
        } else {
            o.addProperty("title", screen == null ? "" : screen.getTitle().getString());
            o.add("slots", slots(false));
            o.add("player_slots", slots(true));
        }

        o.add("inventory", inventory());
        o.add("sidebar", toArray(sidebarLines()));
        JsonObject cookie = cookie();
        if (cookie != null) o.add("cookie", cookie);
        return o;
    }

    private static JsonArray slots(boolean playerHalf) {
        JsonArray arr = new JsonArray();
        AbstractContainerMenu h = GameUtil.handler();
        if (h == null) return arr;
        for (Slot slot : h.slots) {
            if ((slot.container instanceof Inventory) != playerHalf) continue;
            ItemStack st = slot.getItem();
            if (st == null || st.isEmpty()) continue;
            JsonObject s = new JsonObject();
            s.addProperty("i", slot.index);
            s.addProperty("name", GameUtil.rawName(st));
            s.addProperty("count", st.getCount());
            s.add("lore", toArray(GameUtil.lore(st)));
            arr.add(s);
        }
        return arr;
    }

    private static JsonArray inventory() {
        JsonArray arr = new JsonArray();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return arr;
        for (int i = 0; i < 36; i++) {
            ItemStack st = mc.player.getInventory().getItem(i);
            if (st == null || st.isEmpty()) continue;
            String name = GameUtil.itemName(st);
            if (name.isEmpty()) continue;
            JsonObject o = new JsonObject();
            o.addProperty("slot", i);
            o.addProperty("name", name);
            o.addProperty("count", st.getCount());
            arr.add(o);
        }
        return arr;
    }

    private static List<String> sidebarLines() {
        List<String> lines = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return lines;
        Scoreboard sb = mc.level.getScoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) return lines;
        sb.listPlayerScores(obj).forEach(entry -> {
            PlayerTeam team = sb.getPlayersTeam(entry.owner());
            lines.add(PlayerTeam.formatNameForTeam(team, Component.literal(entry.owner())).getString());
        });
        return lines;
    }

    private static JsonObject cookie() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null || mc.gui.hud == null) return null;
        PlayerListHudAccessor acc = (PlayerListHudAccessor) (Object) mc.gui.hud.getTabList();
        if (acc == null) return null;
        Component header = acc.bzassist$getHeader();
        Component footer = acc.bzassist$getFooter();
        StringBuilder sb = new StringBuilder();
        if (header != null) sb.append(header.getString()).append('\n');
        if (footer != null) sb.append(footer.getString());
        String[] lines = GameUtil.strip(sb.toString()).split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.startsWith("Cookie Buff")) continue;
            String value = line.length() > "Cookie Buff".length()
                    ? line.substring("Cookie Buff".length()).replace(":", "").trim() : "";
            if (value.isEmpty() && i + 1 < lines.length) value = lines[i + 1].trim();
            if (value.isEmpty()) return null;
            JsonObject o = new JsonObject();
            boolean inactive = value.toLowerCase().contains("not active");
            o.addProperty("active", !inactive);
            o.addProperty("time_left", inactive ? "" : value);
            return o;
        }
        return null;
    }

    private static JsonArray toArray(List<String> lines) {
        JsonArray arr = new JsonArray();
        for (String l : lines) arr.add(l);
        return arr;
    }
}
