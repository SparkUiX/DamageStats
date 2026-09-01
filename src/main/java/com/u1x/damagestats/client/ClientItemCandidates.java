package com.u1x.damagestats.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

public final class ClientItemCandidates {
    private ClientItemCandidates() {
    }

    public static List<ItemStack> collect() {
        var player = Minecraft.getInstance().player;
        if (player == null) return List.of();
        List<ItemStack> result = new ArrayList<>();
        player.getInventory().items.forEach(stack -> add(result, stack));
        player.getInventory().armor.forEach(stack -> add(result, stack));
        player.getInventory().offhand.forEach(stack -> add(result, stack));
        if (ModList.get().isLoaded("curios")) {
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> handler.getCurios().values()
                    .forEach(stacksHandler -> {
                        var stacks = stacksHandler.getStacks();
                        for (int slot = 0; slot < stacks.getSlots(); slot++) add(result, stacks.getStackInSlot(slot));
                    }));
        }
        return List.copyOf(result);
    }

    private static void add(List<ItemStack> result, ItemStack stack) {
        if (!stack.isEmpty()) result.add(stack.copyWithCount(1));
    }
}
