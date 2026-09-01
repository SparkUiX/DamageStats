package com.u1x.damagestats.command;

import com.u1x.damagestats.DamageStatsText;
import com.u1x.damagestats.trace.DamageTrace;
import com.u1x.damagestats.trace.TraceRepository;
import com.u1x.damagestats.network.DamageStatsNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class DamageStatsCommands {
    private DamageStatsCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("damagestats")
                .then(Commands.literal("on").executes(context -> setEnabled(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> setEnabled(context.getSource(), false)))
                .then(Commands.literal("last")
                        .executes(context -> showLast(context.getSource()))
                        .then(Commands.argument("sequence", LongArgumentType.longArg(1L))
                                .executes(context -> showSequence(context.getSource(),
                                        LongArgumentType.getLong(context, "sequence")))))
                .then(Commands.literal("history")
                        .executes(context -> showHistory(context.getSource(), 10, false))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, TraceRepository.MAX_HISTORY))
                                .executes(context -> showHistory(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count"), false)))
                        .then(Commands.literal("time")
                                .executes(context -> showHistory(context.getSource(), 10, false))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, TraceRepository.MAX_HISTORY))
                                        .executes(context -> showHistory(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "count"), false))))
                        .then(Commands.literal("damage")
                                .executes(context -> showHistory(context.getSource(), 10, true))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, TraceRepository.MAX_HISTORY))
                                        .executes(context -> showHistory(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "count"), true)))))
                .then(Commands.literal("compare")
                        .executes(context -> compareLatest(context.getSource()))
                        .then(Commands.argument("first", LongArgumentType.longArg(1L))
                                .then(Commands.argument("second", LongArgumentType.longArg(1L))
                                        .executes(context -> compare(context.getSource(),
                                                LongArgumentType.getLong(context, "first"),
                                                LongArgumentType.getLong(context, "second")))))));
    }

    private static int showSequence(CommandSourceStack source, long sequence)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        DamageTrace trace = TraceRepository.INSTANCE.find(player.getUUID(), sequence);
        if (trace == null) {
            source.sendFailure(DamageStatsText.text("command.not_found", sequence));
            return 0;
        }
        DamageStatsNetwork.openScreen(player, trace);
        return 1;
    }

    private static int setEnabled(CommandSourceStack source, boolean enabled) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TraceRepository.INSTANCE.setEnabled(player.getUUID(), enabled);
        source.sendSuccess(() -> DamageStatsText.text(enabled ? "command.enabled" : "command.disabled"), false);
        return 1;
    }

    private static int showLast(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        DamageTrace trace = TraceRepository.INSTANCE.latest(player.getUUID());
        if (trace == null) {
            source.sendFailure(DamageStatsText.text("command.no_history"));
            return 0;
        }
        DamageStatsNetwork.openScreen(player, trace);
        return 1;
    }

    private static int showHistory(CommandSourceStack source, int count, boolean damageDescending)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<DamageTrace> traces = TraceRepository.INSTANCE.history(player.getUUID(), count);
        if (traces.isEmpty()) {
            source.sendFailure(DamageStatsText.text("command.no_history"));
            return 0;
        }
        DamageStatsNetwork.openHistory(player, traces, damageDescending);
        return traces.size();
    }

    private static int compare(CommandSourceStack source, long firstSequence, long secondSequence)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        DamageTrace first = TraceRepository.INSTANCE.find(player.getUUID(), firstSequence);
        DamageTrace second = TraceRepository.INSTANCE.find(player.getUUID(), secondSequence);
        if (first == null || second == null) {
            source.sendFailure(DamageStatsText.text("command.compare_not_found",
                    (first == null ? "#" + firstSequence + " " : "")
                            + (second == null ? "#" + secondSequence : "")));
            return 0;
        }
        DamageStatsNetwork.openComparison(player, first, second);
        return 1;
    }

    private static int compareLatest(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<DamageTrace> traces = TraceRepository.INSTANCE.history(player.getUUID(), 2);
        if (traces.size() < 2) {
            source.sendFailure(DamageStatsText.text("command.compare_needs_two"));
            return 0;
        }
        DamageStatsNetwork.openComparison(player, traces.get(1), traces.get(0));
        return 1;
    }
}
