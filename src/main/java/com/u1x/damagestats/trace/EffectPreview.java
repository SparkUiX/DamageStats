package com.u1x.damagestats.trace;

import java.util.Objects;

public record EffectPreview(String effectId, int amplifier, int duration) {
    public EffectPreview {
        effectId = Objects.requireNonNullElse(effectId, "minecraft:empty");
        amplifier = Math.max(0, amplifier);
        duration = Math.max(0, duration);
    }
}
