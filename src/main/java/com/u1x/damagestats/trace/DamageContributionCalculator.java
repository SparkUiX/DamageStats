package com.u1x.damagestats.trace;

import com.u1x.damagestats.DamageStatsText;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public final class DamageContributionCalculator {
    private static final float EPSILON = 0.0001F;

    private DamageContributionCalculator() {
    }

    public static float instantMagnitude(DamageStep step) {
        return Math.abs(step.after() - step.before());
    }

    public static float removalLoss(DamageTrace trace, int removedIndex) {
        return removalLoss(trace, List.of(removedIndex));
    }

    public static float removalLoss(DamageTrace trace, List<Integer> removedIndices) {
        float baseline = replay(trace.steps(), Set.of());
        float without = replay(trace.steps(), new HashSet<>(removedIndices));
        return baseline - without;
    }

    public static String operationSignature(DamageStep step) {
        Operation operation = operation(step);
        return switch (operation) {
            case SET -> "set:" + rounded(step.after());
            case ADD -> "add:" + rounded(step.after() - step.before());
            case MULTIPLY -> "multiply:" + rounded(step.before() == 0.0F ? 1.0F : step.after() / step.before());
            case OBSERVE -> "observe";
        };
    }

    private static float replay(List<DamageStep> steps, Set<Integer> removedIndices) {
        float value = 0.0F;
        for (int i = 0; i < steps.size(); i++) {
            DamageStep step = steps.get(i);
            Operation operation = operation(step);
            if (operation == Operation.OBSERVE || removedIndices.contains(i)) continue;
            value = switch (operation) {
                case SET -> step.after();
                case ADD -> value + (step.after() - step.before());
                case MULTIPLY -> Math.abs(step.before()) <= EPSILON
                        ? value : value * (step.after() / step.before());
                case OBSERVE -> value;
            };
        }
        return value;
    }

    private static Operation operation(DamageStep step) {
        if (step.stage() == DamageStage.LIVING_ATTACK
                || step.stage() == DamageStage.ACTUAL_HEALTH_LOSS
                || step.stage() == DamageStage.PIPELINE_TERMINATED
                || step.stage() == DamageStage.CANCELED_OR_BLOCKED
                || Math.abs(step.after() - step.before()) <= EPSILON) {
            return Operation.OBSERVE;
        }
        if (Math.abs(step.before()) <= EPSILON) return Operation.SET;
        String note = step.note() == null ? "" : step.note();
        if (step.stage() == DamageStage.ENCHANTMENT_BONUS
                || (step.stage() == DamageStage.ATTACK_ATTRIBUTE
                && ("add".equals(DamageStatsText.operationTag(note))
                || note.startsWith("加法 ") || note.startsWith("基础乘算 ")))) {
            return Operation.ADD;
        }
        return Operation.MULTIPLY;
    }

    private static float rounded(float value) {
        return Math.round(value * 10000.0F) / 10000.0F;
    }

    private enum Operation {
        SET,
        ADD,
        MULTIPLY,
        OBSERVE
    }
}
