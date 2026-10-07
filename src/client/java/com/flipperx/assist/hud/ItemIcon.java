package com.flipperx.assist.hud;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ItemIcon {
    private ItemIcon() {}

    public record Look(String type, String model, String skin, boolean glint) {
        public static Look from(JsonElement e) {
            if (e == null || !e.isJsonObject()) return null;
            JsonObject o = e.getAsJsonObject();
            String type = str(o, "type");
            if (type == null) return null;
            JsonElement glint = o.get("glint");
            return new Look(type, str(o, "model"), str(o, "skin"),
                    glint != null && !glint.isJsonNull() && glint.getAsBoolean());
        }

        private static String str(JsonObject o, String key) {
            JsonElement e = o.get(key);
            return e == null || e.isJsonNull() ? null : e.getAsString();
        }
    }

    private static final Pattern SKIN = Pattern.compile("[0-9a-f]{20,80}");
    private static final Map<String, ItemStack> STACKS = new ConcurrentHashMap<>();

    public static boolean draw(GuiGraphicsExtractor g, Look look, String itemId, String url,
                               int x, int y, int tint) {
        ItemStack stack = stack(look);
        if (stack != null) {
            g.item(stack, x, y);
            return true;
        }
        Identifier icon = IconCache.get(itemId, url);
        if (icon == null) return false;
        g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0f, 0f, 16, 16, 16, 16, tint);
        return true;
    }

    private static ItemStack stack(Look look) {
        if (look == null) return null;
        Identifier model = loaded(look.model());
        String key = look.type() + "|" + model + "|" + look.skin() + "|" + look.glint();
        ItemStack stack = STACKS.get(key);
        if (stack != null) return stack;
        stack = build(look, model);
        if (stack != null) STACKS.put(key, stack);
        return stack;
    }

    private static Identifier loaded(String model) {
        Identifier id = model == null ? null : Identifier.tryParse(model);
        if (id == null) return null;
        return Minecraft.getInstance().getModelManager().getItemModel(id) instanceof MissingItemModel ? null : id;
    }

    private static ItemStack build(Look look, Identifier model) {
        Identifier type = Identifier.tryParse(look.type());
        if (type == null) return null;
        Item item = BuiltInRegistries.ITEM.getValue(type);
        if (item == null || item == Items.AIR) return null;
        ItemStack stack = new ItemStack(item);
        if (model != null) stack.set(DataComponents.ITEM_MODEL, model);
        if (look.skin() != null && SKIN.matcher(look.skin()).matches()) {
            stack.set(DataComponents.PROFILE, profile(look.skin()));
        }
        if (look.glint()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    private static ResolvableProfile profile(String skin) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + skin + "\"}}}";
        Property textures = new Property("textures",
                Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
        UUID id = UUID.nameUUIDFromBytes(skin.getBytes(StandardCharsets.UTF_8));
        return ResolvableProfile.createResolved(
                new GameProfile(id, "", new PropertyMap(ImmutableMultimap.of("textures", textures))));
    }
}
