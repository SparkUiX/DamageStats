package com.u1x.damagestats.network;

import com.u1x.damagestats.client.ClientScreenOpener;
import com.u1x.damagestats.trace.DamageTrace;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OpenDamageComparisonScreenPacket(DamageTrace first, DamageTrace second) {
    static void encode(OpenDamageComparisonScreenPacket packet, FriendlyByteBuf buffer) {
        DamageTracePacket.encode(new DamageTracePacket(packet.first), buffer);
        DamageTracePacket.encode(new DamageTracePacket(packet.second), buffer);
    }

    static OpenDamageComparisonScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenDamageComparisonScreenPacket(
                DamageTracePacket.decode(buffer).trace(), DamageTracePacket.decode(buffer).trace());
    }

    static void handle(OpenDamageComparisonScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientScreenOpener.openComparison(packet.first, packet.second)));
        context.setPacketHandled(true);
    }
}
