package com.u1x.damagestats.client;

import com.u1x.damagestats.trace.AttributionConfidence;
import com.u1x.damagestats.trace.ContributionSource;
import com.u1x.damagestats.trace.DamageStage;
import com.u1x.damagestats.trace.DamageStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ClientAnnotationStoreTest {
    private static final ContributionSource UNKNOWN =
            new ContributionSource("unknown", "event_priority", "unknown", "未知监听器");

    @Test
    void unknownAnnotationsSeparateDifferentOperationsAndEvidence() {
        DamageStep multiply = new DamageStep(DamageStage.LIVING_HURT_EVENT, 10, 20,
                UNKNOWN, AttributionConfidence.UNKNOWN, "候选类 A");
        DamageStep add = new DamageStep(DamageStage.LIVING_HURT_EVENT, 10, 15,
                UNKNOWN, AttributionConfidence.UNKNOWN, "候选类 A");
        DamageStep otherEvidence = new DamageStep(DamageStage.LIVING_HURT_EVENT, 10, 20,
                UNKNOWN, AttributionConfidence.UNKNOWN, "候选类 B");

        assertNotEquals(ClientAnnotationStore.key(multiply), ClientAnnotationStore.key(add));
        assertNotEquals(ClientAnnotationStore.key(multiply), ClientAnnotationStore.key(otherEvidence));
    }

    @Test
    void exactAnnotationsRemainStableAcrossDamageValues() {
        ContributionSource exact = new ContributionSource("example", "event_listener", "Example#hurt", "Example");
        DamageStep first = new DamageStep(DamageStage.LIVING_HURT_EVENT, 10, 20,
                exact, AttributionConfidence.EXACT, "first");
        DamageStep second = new DamageStep(DamageStage.LIVING_HURT_EVENT, 12, 24,
                exact, AttributionConfidence.EXACT, "second");

        assertEquals(ClientAnnotationStore.key(first), ClientAnnotationStore.key(second));
    }
}
