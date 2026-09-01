package com.u1x.damagestats.trace;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

final class ApothicAttribution {
    private static final float EPSILON = 0.001F;

    private ApothicAttribution() {
    }

    static Attribution criticalEvent(CriticalHitEvent event) {
        if (!loaded() || Math.abs(event.getDamageModifier() - event.getOldDamageModifier()) <= EPSILON) {
            return new Attribution(
                    ContributionSource.vanilla("critical_hit_event", "原版/Forge 暴击"),
                    AttributionConfidence.EXACT,
                    "CriticalHitEvent ×" + DamageTraceFormatter.number(event.getDamageModifier()));
        }
        return new Attribution(
                new ContributionSource("attributeslib", "attribute", "attributeslib:crit_damage", "Apothic Attributes 暴击伤害"),
                AttributionConfidence.INFERRED,
                "暴击修正由事件值变化推断；最终事件值 ×" + DamageTraceFormatter.number(event.getDamageModifier()));
    }

    static Attribution hurtEvent(Player attacker, float before, float after) {
        if (!loaded() || before <= EPSILON || after <= before + EPSILON) return null;
        Attribute chanceAttribute = ForgeRegistries.ATTRIBUTES.getValue(
                ResourceLocation.fromNamespaceAndPath("attributeslib", "crit_chance"));
        Attribute damageAttribute = ForgeRegistries.ATTRIBUTES.getValue(
                ResourceLocation.fromNamespaceAndPath("attributeslib", "crit_damage"));
        if (chanceAttribute == null || damageAttribute == null || attacker.getAttribute(chanceAttribute) == null
                || attacker.getAttribute(damageAttribute) == null) return null;

        double chance = attacker.getAttributeValue(chanceAttribute);
        float critDamage = (float) attacker.getAttributeValue(damageAttribute);
        float observed = after / before;
        int rolls = matchingCriticalRolls(chance, critDamage, observed);
        if (rolls > 0) {
            return new Attribution(
                    new ContributionSource("attributeslib", "attribute", "attributeslib:crit_chance", "Apothic Attributes 额外暴击"),
                    AttributionConfidence.INFERRED,
                    "观测倍率与 AttributesLib 暴击算法匹配；暴击层数 " + rolls);
        }
        return null;
    }

    static int matchingCriticalRolls(double chance, float critDamage, float observed) {
        float candidate = 1.0F;
        int maxRolls = Math.max(1, (int) Math.ceil(chance) + 1);
        for (int roll = 1; roll <= maxRolls && critDamage > 1.0F; roll++) {
            candidate *= critDamage;
            if (Math.abs(candidate - observed) <= EPSILON * Math.max(1.0F, observed)) {
                return roll;
            }
            critDamage *= 0.85F;
        }
        return 0;
    }

    private static boolean loaded() {
        return ModList.get().isLoaded("attributeslib");
    }

    record Attribution(ContributionSource source, AttributionConfidence confidence, String note) {
    }
}
