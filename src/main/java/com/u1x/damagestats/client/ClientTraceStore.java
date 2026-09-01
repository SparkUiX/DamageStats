package com.u1x.damagestats.client;

import com.u1x.damagestats.trace.DamageTrace;
import com.u1x.damagestats.trace.DamageTraceFormatter;
import com.u1x.damagestats.trace.TraceRepository;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class ClientTraceStore {
    private static final Deque<DamageTrace> TRACES = new ArrayDeque<>();

    private ClientTraceStore() {
    }

    public static void accept(DamageTrace trace) {
        TRACES.addFirst(trace);
        while (TRACES.size() > TraceRepository.MAX_HISTORY) TRACES.removeLast();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(DamageTraceFormatter.summary(trace), false);
        }
    }

    public static List<DamageTrace> history() {
        return List.copyOf(TRACES);
    }

    public static void clear() {
        TRACES.clear();
    }
}
