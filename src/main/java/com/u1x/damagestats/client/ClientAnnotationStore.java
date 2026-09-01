package com.u1x.damagestats.client;

import com.u1x.damagestats.trace.DamageStep;
import com.u1x.damagestats.trace.AttributionConfidence;
import com.u1x.damagestats.trace.DamageContributionCalculator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public final class ClientAnnotationStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, ItemStack> ANNOTATIONS = new HashMap<>();
    private static boolean loaded;

    private ClientAnnotationStore() {
    }

    public static ItemStack icon(DamageStep step) {
        ensureLoaded();
        ItemStack stack = ANNOTATIONS.get(key(step));
        if (stack == null && step.confidence() != AttributionConfidence.UNKNOWN) {
            stack = ANNOTATIONS.get(legacyKey(step));
        }
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public static boolean has(DamageStep step) {
        ensureLoaded();
        return ANNOTATIONS.containsKey(key(step))
                || step.confidence() != AttributionConfidence.UNKNOWN && ANNOTATIONS.containsKey(legacyKey(step));
    }

    public static void set(DamageStep step, ItemStack stack) {
        ensureLoaded();
        if (stack.isEmpty()) ANNOTATIONS.remove(key(step));
        else ANNOTATIONS.put(key(step), stack.copyWithCount(1));
        save();
    }

    public static void clear(DamageStep step) {
        ensureLoaded();
        boolean changed = ANNOTATIONS.remove(key(step)) != null;
        if (step.confidence() != AttributionConfidence.UNKNOWN) changed |= ANNOTATIONS.remove(legacyKey(step)) != null;
        if (changed) save();
    }

    public static String key(DamageStep step) {
        String base = "v2|" + part(step.stage().name()) + "|" + part(step.source().modId()) + "|"
                + part(step.source().kind()) + "|" + part(step.source().id()) + "|"
                + part(step.source().displayName()) + "|" + part(step.confidence().name());
        if (step.confidence() != AttributionConfidence.UNKNOWN) return base;
        return base + "|" + part(DamageContributionCalculator.operationSignature(step)) + "|"
                + part(step.note());
    }

    public static String classification(DamageStep step) {
        String result = step.stage().name() + " · " + step.source().modId() + " · "
                + step.source().kind() + " · " + step.source().id();
        if (step.confidence() == AttributionConfidence.UNKNOWN) {
            result += " · " + DamageContributionCalculator.operationSignature(step);
            if (step.note() != null && !step.note().isBlank()) result += " · " + step.note();
        }
        return result;
    }

    private static String legacyKey(DamageStep step) {
        return step.stage().name() + "|" + step.source().modId() + "|" + step.source().kind()
                + "|" + step.source().id();
    }

    private static String part(String value) {
        return value == null ? "" : value.replace("%", "%25").replace("|", "%7C");
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path path = path();
        if (!Files.isRegularFile(path)) return;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
                try {
                    CompoundTag tag = TagParser.parseTag(entry.getValue().getAsString());
                    ItemStack stack = ItemStack.of(tag);
                    if (!stack.isEmpty()) ANNOTATIONS.put(entry.getKey(), stack);
                } catch (CommandSyntaxException | RuntimeException ignored) {
                    // Skip a damaged single annotation without losing the remaining file.
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // A malformed optional client file must never prevent joining a server.
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        ANNOTATIONS.forEach((key, stack) -> root.addProperty(key, stack.save(new CompoundTag()).toString()));
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Annotation persistence is best-effort and must not affect combat or UI.
        }
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("damagestats").resolve("annotations.json");
    }
}
