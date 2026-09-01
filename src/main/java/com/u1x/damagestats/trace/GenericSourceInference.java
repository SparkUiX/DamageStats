package com.u1x.damagestats.trace;

import com.u1x.damagestats.DamageStatsText;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class GenericSourceInference {
    private GenericSourceInference() {
    }

    static Context capture(Player attacker, Entity target) {
        return new Context(List.copyOf(collectItems(attacker, target)), List.copyOf(collectEffects(attacker, target)));
    }

    static Result infer(ContributionSource original, Context context) {
        if (!"event_listener".equals(original.kind())) return null;
        String haystack = normalize(original.id() + " " + original.displayName());

        Result enchantment = inferEnchantment(original, haystack, context.items());
        if (enchantment != null) return enchantment;

        Result effect = inferEffect(original, haystack, context.effects());
        if (effect != null) return effect;

        ItemStack item = bestItem(original, haystack, context.items());
        if (!item.isEmpty()) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
            ContributionSource source = new ContributionSource(original.modId(), original.kind(), original.id(),
                    DamageStatsText.encoded("source.possible_item", item.getHoverName()),
                    item, ItemStack.EMPTY, null);
            return new Result(source, DamageStatsText.encoded("note.inferred_item", itemId));
        }
        return null;
    }

    private static Result inferEnchantment(ContributionSource original, String haystack, List<ItemStack> items) {
        for (ItemStack stack : items) {
            for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
                ResourceLocation id = BuiltInRegistries.ENCHANTMENT.getKey(entry.getKey());
                if (!sameOwner(original, id) || !matches(haystack, id.getPath(), entry.getKey().getClass().getSimpleName())) {
                    continue;
                }
                ItemStack book = EnchantedBookItem.createForEnchantment(
                        new EnchantmentInstance(entry.getKey(), entry.getValue()));
                String enchantmentName = entry.getKey().getFullname(entry.getValue()).getString();
                ContributionSource source = new ContributionSource(original.modId(), original.kind(), original.id(),
                        DamageStatsText.encoded("source.possible_enchantment", enchantmentName), stack, book, null);
                return new Result(source, DamageStatsText.encoded("note.inferred_enchantment",
                        id, stack.getHoverName()));
            }
        }
        return null;
    }

    private static Result inferEffect(ContributionSource original, String haystack, List<EffectOwner> effects) {
        for (EffectOwner owner : effects) {
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(owner.effect().getEffect());
            if (!sameOwner(original, id)
                    || !matches(haystack, id.getPath(), owner.effect().getEffect().getClass().getSimpleName())) continue;
            EffectPreview preview = new EffectPreview(id.toString(), owner.effect().getAmplifier(),
                    owner.effect().getDuration());
            ContributionSource source = new ContributionSource(original.modId(), original.kind(), original.id(),
                    DamageStatsText.encoded("source.possible_effect",
                            owner.effect().getEffect().getDisplayName()),
                    ItemStack.EMPTY, ItemStack.EMPTY, preview);
            return new Result(source, DamageStatsText.encoded("note.inferred_effect",
                    DamageStatsText.text(owner.owner()), id));
        }
        return null;
    }

    private static ItemStack bestItem(ContributionSource original, String haystack, List<ItemStack> items) {
        for (ItemStack stack : items) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (sameOwner(original, id) && matches(haystack, id.getPath(), stack.getItem().getClass().getSimpleName())) {
                return stack.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    private static List<ItemStack> collectItems(Player player, Entity target) {
        List<ItemStack> result = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) add(result, player.getItemBySlot(slot));
        for (ItemStack stack : player.getInventory().items) add(result, stack);
        for (ItemStack stack : player.getInventory().offhand) add(result, stack);
        if (target instanceof LivingEntity living) {
            for (EquipmentSlot slot : EquipmentSlot.values()) add(result, living.getItemBySlot(slot));
        }
        if (ModList.get().isLoaded("curios")) {
            CuriosBridge.collectItems(player, result);
            if (target instanceof LivingEntity living && living != player) CuriosBridge.collectItems(living, result);
        }
        return result;
    }

    private static List<EffectOwner> collectEffects(Player attacker, Entity target) {
        List<EffectOwner> effects = new ArrayList<>();
        for (MobEffectInstance effect : attacker.getActiveEffects()) effects.add(new EffectOwner(effect, "owner.attacker"));
        if (target instanceof LivingEntity living) {
            for (MobEffectInstance effect : living.getActiveEffects()) effects.add(new EffectOwner(effect, "owner.target"));
        }
        return effects;
    }

    private static void add(List<ItemStack> result, ItemStack stack) {
        if (!stack.isEmpty() && result.stream().noneMatch(existing -> ItemStack.isSameItemSameTags(existing, stack))) {
            result.add(stack.copy());
        }
    }

    private static boolean sameOwner(ContributionSource source, ResourceLocation id) {
        return "unknown".equals(source.modId()) || source.modId().equals(id.getNamespace());
    }

    private static boolean matches(String haystack, String registryPath, String className) {
        String path = normalize(registryPath);
        String simpleClass = normalize(className.replaceAll("(?i)(item|effect|enchantment)$", ""));
        return path.length() >= 4 && haystack.contains(path)
                || simpleClass.length() >= 5 && haystack.contains(simpleClass);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    record Result(ContributionSource source, String note) {
    }

    record Context(List<ItemStack> items, List<EffectOwner> effects) {
    }

    record EffectOwner(MobEffectInstance effect, String owner) {
    }
}
