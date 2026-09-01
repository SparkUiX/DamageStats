package com.u1x.damagestats.trace;

import com.u1x.damagestats.DamageStatsText;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.ASMEventHandler;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventListener;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

final class ForgeEventCandidates {
    private static final int MAX_CANDIDATES = 12;
    private static final int FORGE_BUS_ID = findForgeBusId();

    private ForgeEventCandidates() {
    }

    static String describe(Event event, EventPriority... priorities) {
        if (FORGE_BUS_ID < 0) return DamageStatsText.encoded("note.candidates.unavailable");
        Set<EventPriority> accepted = Set.copyOf(Arrays.asList(priorities));
        LinkedHashSet<String> names = new LinkedHashSet<>();
        int total = 0;
        for (IEventListener listener : event.getListenerList().getListeners(FORGE_BUS_ID)) {
            if (!(listener instanceof ASMEventHandler handler) || !accepted.contains(handler.getPriority())) continue;
            String name = handler.toString();
            if (name.contains("com.u1x.damagestats")) continue;
            total++;
            if (names.size() < MAX_CANDIDATES) names.add(name);
        }
        if (names.isEmpty()) return DamageStatsText.encoded("note.candidates.none");
        String result = String.join("; ", names);
        return total > names.size()
                ? DamageStatsText.encoded("note.candidates.truncated", result, total) : result;
    }

    private static int findForgeBusId() {
        try {
            Field field = MinecraftForge.EVENT_BUS.getClass().getDeclaredField("busID");
            field.setAccessible(true);
            return field.getInt(MinecraftForge.EVENT_BUS);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1;
        }
    }
}
