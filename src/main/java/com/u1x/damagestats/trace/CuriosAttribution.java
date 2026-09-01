package com.u1x.damagestats.trace;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.util.Map;
import java.util.UUID;

/** Optional integration entrypoint. The reflection-only fallback keeps Curios non-mandatory. */
final class CuriosAttribution {
    private CuriosAttribution() {
    }

    static void collectAttackDamageSources(Player player, Map<UUID, ContributionSource> result) {
        if (ModList.get().isLoaded("curios")) {
            CuriosBridge.collectAttackDamageSources(player, result);
        }
    }
}
