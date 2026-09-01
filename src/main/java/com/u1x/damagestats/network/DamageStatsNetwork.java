package com.u1x.damagestats.network;

import com.u1x.damagestats.DamageStatsMod;
import com.u1x.damagestats.trace.DamageTrace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class DamageStatsNetwork {
    private static final String PROTOCOL = "4";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(DamageStatsMod.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private DamageStatsNetwork() {
    }

    public static void initialize() {
        CHANNEL.registerMessage(0, DamageTracePacket.class,
                DamageTracePacket::encode,
                DamageTracePacket::decode,
                DamageTracePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, OpenDamageTraceScreenPacket.class,
                OpenDamageTraceScreenPacket::encode,
                OpenDamageTraceScreenPacket::decode,
                OpenDamageTraceScreenPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, OpenDamageHistoryScreenPacket.class,
                OpenDamageHistoryScreenPacket::encode,
                OpenDamageHistoryScreenPacket::decode,
                OpenDamageHistoryScreenPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, OpenDamageComparisonScreenPacket.class,
                OpenDamageComparisonScreenPacket::encode,
                OpenDamageComparisonScreenPacket::decode,
                OpenDamageComparisonScreenPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void send(ServerPlayer player, DamageTrace trace) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DamageTracePacket(trace));
    }

    public static void openScreen(ServerPlayer player, DamageTrace trace) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenDamageTraceScreenPacket(trace));
    }

    public static void openHistory(ServerPlayer player, java.util.List<DamageTrace> traces,
            boolean damageDescending) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenDamageHistoryScreenPacket(traces, damageDescending));
    }

    public static void openComparison(ServerPlayer player, DamageTrace first, DamageTrace second) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenDamageComparisonScreenPacket(first, second));
    }
}
