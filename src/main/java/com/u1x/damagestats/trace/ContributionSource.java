package com.u1x.damagestats.trace;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public record ContributionSource(String modId, String kind, String id, String displayName,
        ItemStack itemStack, ItemStack secondaryItemStack, EffectPreview effectPreview) {
    public ContributionSource(String modId, String kind, String id, String displayName) {
        this(modId, kind, id, displayName, null, null, null);
    }

    public ContributionSource(String modId, String kind, String id, String displayName, ItemStack itemStack) {
        this(modId, kind, id, displayName, itemStack, null, null);
    }

    public ContributionSource {
        modId = safe(modId, "unknown");
        kind = safe(kind, "unknown");
        id = safe(id, "unknown");
        displayName = safe(displayName, id);
        itemStack = itemStack == null ? null : itemStack.copy();
        secondaryItemStack = secondaryItemStack == null ? null : secondaryItemStack.copy();
    }

    @Override
    public ItemStack itemStack() {
        return itemStack == null ? ItemStack.EMPTY : itemStack.copy();
    }

    @Override
    public ItemStack secondaryItemStack() {
        return secondaryItemStack == null ? ItemStack.EMPTY : secondaryItemStack.copy();
    }

    public static ContributionSource vanilla(String id, String displayName) {
        return new ContributionSource("minecraft", "mechanic", id, displayName);
    }

    public static ContributionSource unknown(String displayName) {
        return new ContributionSource("unknown", "unknown", "unknown", displayName);
    }

    private static String safe(String value, String fallback) {
        return Objects.requireNonNullElse(value, fallback);
    }
}
