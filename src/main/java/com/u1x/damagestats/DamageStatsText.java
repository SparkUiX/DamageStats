package com.u1x.damagestats;

import com.u1x.damagestats.trace.AttributionConfidence;
import com.u1x.damagestats.trace.DamageStage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class DamageStatsText {
    private static final String PREFIX = "damagestats.";

    private DamageStatsText() {
    }

    public static MutableComponent text(String path, Object... arguments) {
        return Component.translatable(PREFIX + path, arguments);
    }

    public static String string(String path, Object... arguments) {
        return text(path, arguments).getString();
    }

    public static Component stage(DamageStage stage) {
        return text("stage." + stage.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static Component confidence(AttributionConfidence confidence) {
        return text("confidence." + confidence.name().toLowerCase(java.util.Locale.ROOT));
    }
}
