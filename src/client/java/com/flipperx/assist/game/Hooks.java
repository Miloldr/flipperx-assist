package com.flipperx.assist.game;

import com.google.gson.JsonArray;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

public final class Hooks {
    private static final String MOD_ID = "bzassist";
    private static final String OWN = "com.flipperx.assist.";
    private static final String OWN_MIXINS = OWN + "mixin.";
    private static final int MAX = 20;

    private static volatile JsonArray found;

    private Hooks() {}

    public static JsonArray get() {
        JsonArray f = found;
        if (f == null) {
            f = scan();
            found = f;
        }
        return f;
    }

    private static JsonArray scan() {
        TreeSet<String> out = new TreeSet<>();
        try {
            ModContainer self = FabricLoader.getInstance().getModContainer(MOD_ID).orElse(null);
            if (self != null) {
                ClassLoader loader = Hooks.class.getClassLoader();
                for (String name : ownClasses(self)) merged(name, loader, out);
            }
        } catch (Throwable ignored) {
        }
        JsonArray a = new JsonArray();
        out.stream().limit(MAX).forEach(a::add);
        return a;
    }

    private static void merged(String name, ClassLoader loader, TreeSet<String> out) {
        Class<?> type;
        try {
            type = Class.forName(name, false, loader);
        } catch (Throwable e) {
            return;
        }
        List<MixinMerged> marks = new ArrayList<>();
        try {
            for (Method m : type.getDeclaredMethods()) marks.add(m.getAnnotation(MixinMerged.class));
            for (Field f : type.getDeclaredFields()) marks.add(f.getAnnotation(MixinMerged.class));
        } catch (Throwable e) {
            return;
        }
        for (MixinMerged m : marks) {
            if (m == null || m.mixin().startsWith(OWN_MIXINS)) continue;
            out.add(modOf(m.mixin()) + ": " + m.mixin() + " > " + name);
        }
    }

    private static String modOf(String mixin) {
        String path = mixin.replace('.', '/') + ".class";
        try {
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                for (Path root : mod.getRootPaths()) {
                    if (Files.exists(root.resolve(path))) return mod.getMetadata().getId();
                }
            }
        } catch (Throwable ignored) {
        }
        return "?";
    }

    private static List<String> ownClasses(ModContainer self) throws Exception {
        List<String> names = new ArrayList<>();
        for (Path root : self.getRootPaths()) {
            Path base = root.resolve("com/flipperx/assist");
            if (!Files.isDirectory(base)) continue;
            try (Stream<Path> walk = Files.walk(base)) {
                walk.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                    String rel = root.relativize(p).toString().replace('\\', '/');
                    String name = rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
                    if (!name.startsWith(OWN_MIXINS)) names.add(name);
                });
            }
        }
        return names;
    }
}
