package com.u1x.damagestats.client;

import com.u1x.damagestats.client.screen.DamageTraceScreen;
import com.u1x.damagestats.client.screen.DamageHistoryScreen;
import com.u1x.damagestats.client.screen.DamageComparisonScreen;
import com.u1x.damagestats.trace.DamageTrace;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class ClientScreenOpener {
    private ClientScreenOpener() {
    }

    public static void open(DamageTrace trace) {
        Minecraft.getInstance().setScreen(new DamageTraceScreen(trace));
    }

    public static void openHistory(List<DamageTrace> traces, boolean damageDescending) {
        Minecraft.getInstance().setScreen(new DamageHistoryScreen(traces, damageDescending));
    }

    public static void openComparison(DamageTrace first, DamageTrace second) {
        Minecraft.getInstance().setScreen(new DamageComparisonScreen(first, second));
    }
}
