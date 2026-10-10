package com.flipperx.assist;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;

public final class Chat {
    public static final String NAME = "FlipperX Assist";

    private Chat() {}

    public static MutableComponent line(Component body) {
        return Component.empty()
                .append(Component.literal("[" + NAME + "]").withStyle(ChatFormatting.GOLD))
                .append(" ")
                .append(body);
    }

    public static MutableComponent line(String text) {
        return line(Component.literal(text));
    }

    public static MutableComponent link(String text, String url) {
        return Component.literal(text).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(url))));
    }

    public static MutableComponent button(String text, String command) {
        return Component.literal(text).withStyle(style -> style
                .withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
    }

    public static Component fromServer(JsonObject msg) {
        if (msg.has("component")) {
            Minecraft mc = Minecraft.getInstance();
            ClientPacketListener connection = mc == null ? null : mc.getConnection();
            DynamicOps<JsonElement> ops = connection != null
                    ? connection.registryAccess().createSerializationContext(JsonOps.INSTANCE)
                    : JsonOps.INSTANCE;
            Component parsed = ComponentSerialization.CODEC.parse(ops, msg.get("component")).result().orElse(null);
            if (parsed != null) return parsed;
        }
        if (msg.has("text") && msg.get("text").isJsonPrimitive()) {
            return Component.literal(msg.get("text").getAsString());
        }
        return null;
    }

    public static String shortUrl(String url) {
        return url.replaceFirst("^https?://", "").replaceFirst("/$", "");
    }
}
