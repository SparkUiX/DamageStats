package com.u1x.damagestats.trace;

import com.u1x.damagestats.DamageStatsText;
import com.google.common.collect.Multimap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record AttributeSnapshot(double baseValue, double totalValue, List<ModifierSnapshot> modifiers) {
    static AttributeSnapshot attackDamage(Player player) {
        AttributeInstance instance = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (instance == null) {
            return new AttributeSnapshot(0.0D, 0.0D, List.of());
        }

        Map<UUID, ContributionSource> equipmentSources = equipmentSources(player);
        List<ModifierSnapshot> modifiers = new ArrayList<>();
        for (AttributeModifier modifier : instance.getModifiers()) {
            ContributionSource source = equipmentSources.getOrDefault(
                    modifier.getId(),
                    ContributionSource.unknown(modifier.getName()));
            modifiers.add(new ModifierSnapshot(
                    modifier.getId(), modifier.getName(), modifier.getAmount(), modifier.getOperation(), source));
        }
        return new AttributeSnapshot(instance.getBaseValue(), instance.getValue(), List.copyOf(modifiers));
    }

    static ContributionSource itemSource(ItemStack stack, String kind) {
        if (stack.isEmpty()) {
            return ContributionSource.vanilla("empty", DamageStatsText.encoded("source.empty_hand"));
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new ContributionSource(id.getNamespace(), kind, id.toString(), stack.getHoverName().getString(), stack);
    }

    private static Map<UUID, ContributionSource> equipmentSources(Player player) {
        Map<UUID, ContributionSource> result = new HashMap<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            ContributionSource source = itemSource(stack, "equipment:" + slot.getName());
            Multimap<net.minecraft.world.entity.ai.attributes.Attribute, AttributeModifier> modifiers =
                    stack.getAttributeModifiers(slot);
            for (AttributeModifier modifier : modifiers.get(Attributes.ATTACK_DAMAGE)) {
                result.put(modifier.getId(), source);
            }
        }
        CuriosAttribution.collectAttackDamageSources(player, result);
        return result;
    }

    record ModifierSnapshot(
            UUID id,
            String name,
            double amount,
            AttributeModifier.Operation operation,
            ContributionSource source) {
    }
}
