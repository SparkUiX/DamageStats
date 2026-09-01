package com.u1x.damagestats.network;

import com.u1x.damagestats.client.ClientScreenOpener;
import com.u1x.damagestats.trace.DamageTrace;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OpenDamageTraceScreenPacket(DamageTrace trace) {
    static void encode(OpenDamageTraceScreenPacket packet, FriendlyByteBuf buffer) {
        DamageTracePacket.encode(new DamageTracePacket(packet.trace), buffer);
    }

    static OpenDamageTraceScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenDamageTraceScreenPacket(DamageTracePacket.decode(buffer).trace());
    }

    static void handle(OpenDamageTraceScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientScreenOpener.open(packet.trace)));
        context.setPacketHandled(true);
    }
}
