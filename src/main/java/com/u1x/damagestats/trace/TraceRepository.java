package com.u1x.damagestats.trace;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TraceRepository {
    public static final TraceRepository INSTANCE = new TraceRepository();
    public static final int MAX_HISTORY = 100;

    private final Set<UUID> enabledPlayers = new HashSet<>();
    private final Map<UUID, TraceRingBuffer<DamageTrace>> traces = new HashMap<>();
    private final Map<UUID, Long> nextSequences = new HashMap<>();

    private TraceRepository() {
    }

    public boolean isEnabled(UUID playerId) {
        return enabledPlayers.contains(playerId);
    }

    public void setEnabled(UUID playerId, boolean enabled) {
        if (enabled) enabledPlayers.add(playerId);
        else enabledPlayers.remove(playerId);
    }

    public void add(ServerPlayer player, DamageTrace trace) {
        TraceRingBuffer<DamageTrace> history = traces.computeIfAbsent(
                player.getUUID(), ignored -> new TraceRingBuffer<>(MAX_HISTORY));
        long sequence = nextSequences.merge(player.getUUID(), 1L, Long::sum);
        DamageTrace numberedTrace = trace.withSequence(sequence);
        history.add(numberedTrace);
        TraceDelivery.sendSummary(player, numberedTrace);
    }

    public DamageTrace latest(UUID playerId) {
        TraceRingBuffer<DamageTrace> history = traces.get(playerId);
        return history == null ? null : history.latest();
    }

    public List<DamageTrace> history(UUID playerId, int count) {
        TraceRingBuffer<DamageTrace> history = traces.get(playerId);
        return history == null ? List.of() : history.newest(count);
    }

    public DamageTrace find(UUID playerId, long sequence) {
        return history(playerId, MAX_HISTORY).stream()
                .filter(trace -> trace.sequence() == sequence)
                .findFirst()
                .orElse(null);
    }

    public void removePlayer(UUID playerId) {
        enabledPlayers.remove(playerId);
        traces.remove(playerId);
        nextSequences.remove(playerId);
    }
}
