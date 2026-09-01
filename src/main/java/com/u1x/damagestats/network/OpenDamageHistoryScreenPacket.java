package com.u1x.damagestats.network;

import com.u1x.damagestats.client.ClientScreenOpener;
import com.u1x.damagestats.trace.DamageTrace;
import com.u1x.damagestats.trace.TraceRepository;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record OpenDamageHistoryScreenPacket(List<DamageTrace> traces, boolean damageDescending) {
    public OpenDamageHistoryScreenPacket {
        traces = List.copyOf(traces);
    }

    static void encode(OpenDamageHistoryScreenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.damageDescending);
        buffer.writeVarInt(packet.traces.size());
        packet.traces.forEach(trace -> DamageTracePacket.encode(new DamageTracePacket(trace), buffer));
    }

    static OpenDamageHistoryScreenPacket decode(FriendlyByteBuf buffer) {
        boolean damageDescending = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > TraceRepository.MAX_HISTORY) {
            throw new IllegalArgumentException("Invalid history trace count: " + count);
        }
        List<DamageTrace> traces = new ArrayList<>(count);
        for (int i = 0; i < count; i++) traces.add(DamageTracePacket.decode(buffer).trace());
        return new OpenDamageHistoryScreenPacket(traces, damageDescending);
    }

    static void handle(OpenDamageHistoryScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientScreenOpener.openHistory(packet.traces, packet.damageDescending)));
        context.setPacketHandled(true);
    }
}
