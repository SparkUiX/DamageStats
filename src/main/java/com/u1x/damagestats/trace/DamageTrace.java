package com.u1x.damagestats.trace;

import java.util.List;
import java.util.UUID;

public record DamageTrace(
        UUID traceId,
        long sequence,
        UUID attackerId,
        String attackerName,
        int targetEntityId,
        String targetName,
        ContributionSource weapon,
        long gameTime,
        float calculatedDamage,
        float actualHealthLoss,
        boolean canceledOrBlocked,
        List<DamageStep> steps) {

    public DamageTrace {
        steps = List.copyOf(steps);
    }

    public DamageTrace withSequence(long newSequence) {
        return new DamageTrace(traceId, newSequence, attackerId, attackerName, targetEntityId, targetName,
                weapon, gameTime, calculatedDamage, actualHealthLoss, canceledOrBlocked, steps);
    }
}
