package com.u1x.damagestats.client.screen;

import com.u1x.damagestats.trace.AttributionConfidence;
import com.u1x.damagestats.trace.ContributionSource;
import com.u1x.damagestats.trace.DamageStage;
import com.u1x.damagestats.trace.DamageStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageComparisonScreenTest {
    @Test
    void keepsSameMultiplierWhenTheInputBaseChanged() {
        DamageStep first = step("same", 10.0F, 13.5F);
        DamageStep second = step("same", 100.0F, 135.0F);

        List<DamageComparisonScreen.ComparisonRow> rows = DamageComparisonScreen.compare(
                List.of(first), List.of(second));

        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).first().size());
        assertEquals(1, rows.get(0).second().size());
    }

    @Test
    void groupsRepeatedApplicationsAndKeepsCountChanges() {
        DamageStep first = step("repeat", 10.0F, 13.5F);
        DamageStep secondA = step("repeat", 10.0F, 13.5F);
        DamageStep secondB = step("repeat", 13.5F, 18.225F);

        List<DamageComparisonScreen.ComparisonRow> rows = DamageComparisonScreen.compare(
                List.of(first), List.of(secondA, secondB));

        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).first().size());
        assertEquals(2, rows.get(0).second().size());
    }

    @Test
    void missingAndAddedSourcesSortBeforeChangedSources() {
        DamageStep missing = step("missing", 10.0F, 20.0F);
        DamageStep changedFirst = step("changed", 10.0F, 13.5F);
        DamageStep added = step("added", 5.0F, 500.0F);
        DamageStep changedSecond = step("changed", 10.0F, 17.0F);

        List<DamageComparisonScreen.ComparisonRow> rows = DamageComparisonScreen.compare(
                List.of(missing, changedFirst), List.of(added, changedSecond));

        assertEquals(3, rows.size());
        assertEquals("added", rows.get(0).second().get(0).source().id());
        assertEquals("missing", rows.get(1).first().get(0).source().id());
        assertEquals("changed", rows.get(2).first().get(0).source().id());
    }

    private static DamageStep step(String id, float before, float after) {
        ContributionSource source = new ContributionSource("test", "event", id, id);
        return new DamageStep(DamageStage.LIVING_HURT_EVENT, before, after,
                source, AttributionConfidence.EXACT, "");
    }
}
