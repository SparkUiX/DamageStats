package com.u1x.damagestats.network;

import com.u1x.damagestats.client.ClientTraceStore;
import com.u1x.damagestats.trace.AttributionConfidence;
import com.u1x.damagestats.trace.ContributionSource;
import com.u1x.damagestats.trace.DamageStage;
import com.u1x.damagestats.trace.DamageStep;
import com.u1x.damagestats.trace.DamageTrace;
import com.u1x.damagestats.trace.EffectPreview;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record DamageTracePacket(DamageTrace trace) {
    private static final int MAX_STEPS = 256;

    static void encode(DamageTracePacket packet, FriendlyByteBuf buffer) {
        DamageTrace trace = packet.trace;
        buffer.writeUUID(trace.traceId());
        buffer.writeVarLong(trace.sequence());
        buffer.writeUUID(trace.attackerId());
        buffer.writeUtf(trace.attackerName());
        buffer.writeVarInt(trace.targetEntityId());
        buffer.writeUtf(trace.targetName());
        writeSource(buffer, trace.weapon());
        buffer.writeLong(trace.gameTime());
        buffer.writeFloat(trace.calculatedDamage());
        buffer.writeFloat(trace.actualHealthLoss());
        buffer.writeBoolean(trace.canceledOrBlocked());
        buffer.writeVarInt(trace.steps().size());
        for (DamageStep step : trace.steps()) {
            buffer.writeEnum(step.stage());
            buffer.writeFloat(step.before());
            buffer.writeFloat(step.after());
            writeSource(buffer, step.source());
            buffer.writeEnum(step.confidence());
            buffer.writeUtf(step.note() == null ? "" : step.note());
        }
    }

    static DamageTracePacket decode(FriendlyByteBuf buffer) {
        var traceId = buffer.readUUID();
        long sequence = buffer.readVarLong();
        var attackerId = buffer.readUUID();
        String attackerName = buffer.readUtf();
        int targetId = buffer.readVarInt();
        String targetName = buffer.readUtf();
        ContributionSource weapon = readSource(buffer);
        long gameTime = buffer.readLong();
        float calculated = buffer.readFloat();
        float actual = buffer.readFloat();
        boolean canceled = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_STEPS) {
            throw new IllegalArgumentException("Invalid damage trace step count: " + count);
        }
        List<DamageStep> steps = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            steps.add(new DamageStep(
                    buffer.readEnum(DamageStage.class),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    readSource(buffer),
                    buffer.readEnum(AttributionConfidence.class),
                    buffer.readUtf()));
        }
        return new DamageTracePacket(new DamageTrace(traceId, sequence, attackerId, attackerName, targetId,
                targetName, weapon, gameTime, calculated, actual, canceled, steps));
    }

    static void handle(DamageTracePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientTraceStore.accept(packet.trace)));
        context.setPacketHandled(true);
    }

    private static void writeSource(FriendlyByteBuf buffer, ContributionSource source) {
        buffer.writeUtf(source.modId());
        buffer.writeUtf(source.kind());
        buffer.writeUtf(source.id());
        buffer.writeUtf(source.displayName());
        buffer.writeItem(source.itemStack());
        buffer.writeItem(source.secondaryItemStack());
        EffectPreview effect = source.effectPreview();
        buffer.writeBoolean(effect != null);
        if (effect != null) {
            buffer.writeUtf(effect.effectId());
            buffer.writeVarInt(effect.amplifier());
            buffer.writeVarInt(effect.duration());
        }
    }

    private static ContributionSource readSource(FriendlyByteBuf buffer) {
        String modId = buffer.readUtf();
        String kind = buffer.readUtf();
        String id = buffer.readUtf();
        String displayName = buffer.readUtf();
        var item = buffer.readItem();
        var secondaryItem = buffer.readItem();
        EffectPreview effect = buffer.readBoolean()
                ? new EffectPreview(buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt()) : null;
        return new ContributionSource(modId, kind, id, displayName, item, secondaryItem, effect);
    }
}
