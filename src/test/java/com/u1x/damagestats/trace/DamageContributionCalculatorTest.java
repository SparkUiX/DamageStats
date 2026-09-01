package com.u1x.damagestats.trace;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageContributionCalculatorTest {
    private static final ContributionSource SOURCE =
            new ContributionSource("test", "item", "test:item", "item");

    @Test
    void removalLossReplaysDownstreamMultipliersInsteadOfUsingImmediateDelta() {
        List<DamageStep> steps = List.of(
                step(DamageStage.ATTACK_ATTRIBUTE, 0.0F, 100.0F, ""),
                step(DamageStage.ATTACK_ATTRIBUTE, 100.0F, 135.0F, "总乘算 ×1.35"),
                step(DamageStage.ATTACK_ATTRIBUTE, 135.0F, 168.75F, "总乘算 ×1.25"));
        DamageTrace trace = trace(steps, 168.75F);

        assertEquals(43.75F, DamageContributionCalculator.removalLoss(trace, 1), 0.001F);
        assertEquals(33.75F, DamageContributionCalculator.removalLoss(trace, 2), 0.001F);
    }

    @Test
    void removalLossAmplifiesEarlyAdditionThroughLaterMultiplier() {
        List<DamageStep> steps = List.of(
                step(DamageStage.ATTACK_ATTRIBUTE, 0.0F, 10.0F, ""),
                step(DamageStage.ATTACK_ATTRIBUTE, 10.0F, 12.0F, "加法 +2"),
                step(DamageStage.ATTACK_ATTRIBUTE, 12.0F, 24.0F, "总乘算 ×2"));
        assertEquals(4.0F, DamageContributionCalculator.removalLoss(trace(steps, 24.0F), 1), 0.001F);
    }

    private static DamageStep step(DamageStage stage, float before, float after, String note) {
        return new DamageStep(stage, before, after, SOURCE, AttributionConfidence.EXACT, note);
    }

    private static DamageTrace trace(List<DamageStep> steps, float damage) {
        return new DamageTrace(UUID.randomUUID(), 1L, UUID.randomUUID(), "player", 1,
                "target", SOURCE, 0L, damage, damage, false, steps);
    }
}
