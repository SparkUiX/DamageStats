package com.u1x.damagestats.trace;

import com.u1x.damagestats.network.DamageStatsNetwork;
import net.minecraft.server.level.ServerPlayer;

final class TraceDelivery {
    private TraceDelivery() {
    }

    static void sendSummary(ServerPlayer player, DamageTrace trace) {
        DamageStatsNetwork.send(player, trace);
    }
}
