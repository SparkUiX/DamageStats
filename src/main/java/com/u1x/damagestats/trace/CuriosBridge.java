package com.u1x.damagestats.trace;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.Map;
import java.util.List;
import java.util.UUID;

final class CuriosBridge {
    private CuriosBridge() {
    }

    static void collectAttackDamageSources(Player player, Map<UUID, ContributionSource> result) {
        CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                handler.getCurios().forEach((identifier, stacksHandler) -> {
                    var stacks = stacksHandler.getStacks();
                    for (int index = 0; index < stacks.getSlots(); index++) {
                        ItemStack stack = stacks.getStackInSlot(index);
                        if (stack.isEmpty()) continue;
                        SlotContext slotContext = new SlotContext(
                                identifier, player, index, false, stacksHandler.isVisible());
                        UUID slotUuid = CuriosApi.getSlotUuid(slotContext);
                        for (AttributeModifier modifier : CuriosApi
                                .getAttributeModifiers(slotContext, slotUuid, stack)
                                .get(Attributes.ATTACK_DAMAGE)) {
                            result.put(modifier.getId(), new ContributionSource(
                                    AttributeSnapshot.itemSource(stack, "curios").modId(),
                                    "curios:" + identifier + "[" + index + "]",
                                    AttributeSnapshot.itemSource(stack, "curios").id(),
                                    stack.getHoverName().getString(), stack));
                        }
                    }
                }));
    }

    static void collectItems(LivingEntity entity, List<ItemStack> result) {
        CuriosApi.getCuriosInventory(entity).ifPresent(handler ->
                handler.getCurios().values().forEach(stacksHandler -> {
                    var stacks = stacksHandler.getStacks();
                    for (int index = 0; index < stacks.getSlots(); index++) {
                        ItemStack stack = stacks.getStackInSlot(index);
                        if (!stack.isEmpty() && result.stream()
                                .noneMatch(existing -> ItemStack.isSameItemSameTags(existing, stack))) {
                            result.add(stack.copy());
                        }
                    }
                }));
    }
}
