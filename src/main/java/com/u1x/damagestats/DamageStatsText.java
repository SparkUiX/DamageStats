package com.u1x.damagestats;

import com.u1x.damagestats.trace.AttributionConfidence;
import com.u1x.damagestats.trace.DamageStage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class DamageStatsText {
    private static final String PREFIX = "damagestats.";
    private static final String ENCODED_PREFIX = "\u0000damagestats:";
    private static final String OPERATION_PREFIX = "\u0000damagestats-op:";

    private DamageStatsText() {
    }

    public static MutableComponent text(String path, Object... arguments) {
        return Component.translatable(PREFIX + path, arguments);
    }

    public static String string(String path, Object... arguments) {
        return text(path, arguments).getString();
    }

    /**
     * Stores a translatable component inside the existing string-based trace protocol.
     * The client resolves it using its own selected language; ordinary mod-provided
     * strings remain untouched and are rendered literally.
     */
    public static String encoded(String path, Object... arguments) {
        return ENCODED_PREFIX + Component.Serializer.toJson(text(path, arguments));
    }

    public static String encodedComponent(Component component) {
        return ENCODED_PREFIX + Component.Serializer.toJson(component);
    }

    public static String encodedOperation(String operation, String path, Object... arguments) {
        return OPERATION_PREFIX + operation + ":" + Component.Serializer.toJson(text(path, arguments));
    }

    public static String operationTag(String encodedOrLiteral) {
        if (encodedOrLiteral == null || !encodedOrLiteral.startsWith(OPERATION_PREFIX)) return "";
        int end = encodedOrLiteral.indexOf(':', OPERATION_PREFIX.length());
        return end < 0 ? "" : encodedOrLiteral.substring(OPERATION_PREFIX.length(), end);
    }

    public static MutableComponent component(String encodedOrLiteral) {
        if (encodedOrLiteral == null || encodedOrLiteral.isEmpty()) return Component.empty();
        String json;
        if (encodedOrLiteral.startsWith(ENCODED_PREFIX)) {
            json = encodedOrLiteral.substring(ENCODED_PREFIX.length());
        } else if (encodedOrLiteral.startsWith(OPERATION_PREFIX)) {
            int end = encodedOrLiteral.indexOf(':', OPERATION_PREFIX.length());
            if (end < 0) return Component.literal(encodedOrLiteral);
            json = encodedOrLiteral.substring(end + 1);
        } else {
            return Component.literal(encodedOrLiteral);
        }
        try {
            MutableComponent component = Component.Serializer.fromJson(json);
            return component == null ? Component.empty() : component;
        } catch (RuntimeException ignored) {
            return Component.literal(json);
        }
    }

    public static String resolve(String encodedOrLiteral) {
        return component(encodedOrLiteral).getString();
    }

    public static Component stage(DamageStage stage) {
        return text("stage." + stage.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static Component confidence(AttributionConfidence confidence) {
        return text("confidence." + confidence.name().toLowerCase(java.util.Locale.ROOT));
    }
}
