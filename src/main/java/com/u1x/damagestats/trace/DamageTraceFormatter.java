package com.u1x.damagestats.trace;

import com.u1x.damagestats.DamageStatsText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class DamageTraceFormatter {
    private DamageTraceFormatter() {
    }

    public static Component summary(DamageTrace trace) {
        MutableComponent result = DamageStatsText.text("message.summary.prefix", trace.sequence()).copy()
                .withStyle(ChatFormatting.GOLD)
                .append(DamageStatsText.component(trace.weapon().displayName()).withStyle(ChatFormatting.AQUA))
                .append(DamageStatsText.text("message.summary.target", trace.targetName()))
                .append(Component.literal(number(trace.calculatedDamage())).withStyle(ChatFormatting.RED));
        if (Math.abs(trace.calculatedDamage() - trace.actualHealthLoss()) > 0.0001F) {
            result.append(DamageStatsText.text("message.summary.actual", number(trace.actualHealthLoss()))
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (trace.canceledOrBlocked()) {
            result.append(DamageStatsText.text("message.summary.blocked").withStyle(ChatFormatting.GRAY));
        }
        return result;
    }

    public static List<Component> details(DamageTrace trace) {
        List<Component> lines = new ArrayList<>();
        lines.add(summary(trace));
        int index = 1;
        for (DamageStep step : trace.steps()) {
            ChatFormatting color = step.confidence() == AttributionConfidence.UNKNOWN
                    ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
            String operation = operation(step.before(), step.after());
            Component source = DamageStatsText.component(step.source().displayName());
            String confidence = DamageStatsText.confidence(step.confidence()).getString();
            MutableComponent line = DamageStatsText.text("message.detail.prefix", index++,
                            DamageStatsText.stage(step.stage())).copy()
                    .append(Component.literal(number(step.before()) + " " + operation + " → " + number(step.after())))
                    .append(DamageStatsText.text("message.detail.source", source, confidence).withStyle(color));
            if (step.note() != null && !step.note().isBlank()) {
                line.append(Component.literal("  ").append(DamageStatsText.component(step.note()))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            lines.add(line);
        }
        return lines;
    }

    static String number(float value) {
        if (!Float.isFinite(value)) return Float.toString(value);
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    static String operation(float before, float after) {
        if (Math.abs(before) > 0.0001F) {
            float ratio = after / before;
            if (Float.isFinite(ratio) && Math.abs(ratio - 1.0F) > 0.0001F) {
                return "×" + number(ratio);
            }
        }
        float delta = after - before;
        return (delta >= 0 ? "+" : "") + number(delta);
    }

}
