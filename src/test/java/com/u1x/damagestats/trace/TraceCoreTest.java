package com.u1x.damagestats.trace;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TraceCoreTest {
    @Test
    void formatsMultiplicationAndAdditionWithoutInventingAPureMultiplierFromZero() {
        assertEquals("×4", DamageTraceFormatter.operation(10.0F, 40.0F));
        assertEquals("×0.5", DamageTraceFormatter.operation(40.0F, 20.0F));
        assertEquals("+10", DamageTraceFormatter.operation(0.0F, 10.0F));
    }

    @Test
    void ringBufferKeepsNewestHundredInOrder() {
        TraceRingBuffer<Integer> buffer = new TraceRingBuffer<>(100);
        for (int i = 0; i < 105; i++) buffer.add(i);
        assertEquals(104, buffer.latest());
        assertEquals(100, buffer.newest(100).size());
        assertEquals(5, buffer.newest(100).get(99));
    }

    @Test
    void traceDefensivelyCopiesSteps() {
        List<DamageStep> steps = new ArrayList<>();
        DamageTrace trace = new DamageTrace(UUID.randomUUID(), 0L, UUID.randomUUID(), "attacker", 1,
                "target", ContributionSource.vanilla("air", "空手"), 0L, 1.0F, 1.0F,
                false, steps);
        steps.add(new DamageStep(DamageStage.ACTUAL_HEALTH_LOSS, 1, 1,
                ContributionSource.vanilla("health", "生命"), AttributionConfidence.EXACT, ""));
        assertEquals(0, trace.steps().size());
        assertThrows(UnsupportedOperationException.class, () -> trace.steps().add(null));
    }

    @Test
    void matchesApothicSingleAndOvercritMultipliers() {
        assertEquals(1, ApothicAttribution.matchingCriticalRolls(1.0D, 2.0F, 2.0F));
        assertEquals(2, ApothicAttribution.matchingCriticalRolls(2.0D, 2.0F, 3.4F));
        assertEquals(0, ApothicAttribution.matchingCriticalRolls(1.0D, 2.0F, 2.5F));
    }
}
