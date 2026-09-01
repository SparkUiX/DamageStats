package com.u1x.damagestats.trace;

public record DamageStep(
        DamageStage stage,
        float before,
        float after,
        ContributionSource source,
        AttributionConfidence confidence,
        String note) {
}
