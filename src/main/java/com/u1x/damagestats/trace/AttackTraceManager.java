package com.u1x.damagestats.trace;

import com.mojang.logging.LogUtils;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventListener;

import java.util.ArrayDeque;
import java.util.Deque;
import org.slf4j.Logger;

public final class AttackTraceManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Deque<DamageTraceBuilder>> ACTIVE =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<DamageScope>> DAMAGE_SCOPES =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<AttackScope>> ATTACK_SCOPES =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<EventListenerScope>> EVENT_LISTENER_SCOPES =
            ThreadLocal.withInitial(ArrayDeque::new);

    private AttackTraceManager() {
    }

    public static void begin(Player attacker, Entity target) {
        if (attacker.level().isClientSide || !(target instanceof LivingEntity)
                || !TraceRepository.INSTANCE.isEnabled(attacker.getUUID())) return;
        pruneCompletedAttackScopes();
        DamageTraceBuilder builder = new DamageTraceBuilder(attacker, target);
        ACTIVE.get().push(builder);
        ATTACK_SCOPES.get().push(new AttackScope(attacker, target, builder));
        LOGGER.info("[DamageStats/Audit] player_attack_begin attacker={}({}) target={}({}) depth={}",
                attacker.getGameProfile().getName(), attacker.getUUID(), target.getDisplayName().getString(),
                target.getId(), ACTIVE.get().size());
    }

    public static void finish(Player attacker, Entity target) {
        if (attacker.level().isClientSide || !TraceRepository.INSTANCE.isEnabled(attacker.getUUID())) return;
        Deque<AttackScope> attacks = ATTACK_SCOPES.get();
        if (attacks.isEmpty()) return;
        AttackScope scope = attacks.peek();
        if (scope.attacker() != attacker || scope.target() != target) return;
        attacks.pop();
        if (attacks.isEmpty()) ATTACK_SCOPES.remove();
        finishBuilder(scope.builder());
    }

    public static void beginDamagePipeline(LivingEntity target, DamageSource source, float amount) {
        DamageTraceBuilder owned = null;
        Player attacker = source.getEntity() instanceof Player player ? player : null;
        if (!target.level().isClientSide && attacker instanceof net.minecraft.server.level.ServerPlayer
                && TraceRepository.INSTANCE.isEnabled(attacker.getUUID())) {
            DamageTraceBuilder current = current();
            boolean canJoinAttack = current != null && current.attacker() == attacker && current.target() == target
                    && !isInDamagePipeline(current)
                    && (!current.hasDamageEntry() || current.matches(target, source));
            if (!canJoinAttack) {
                DamageTraceBuilder builder = new DamageTraceBuilder(attacker, target);
                builder.bindPrimary(target, source, amount);
                ACTIVE.get().push(builder);
                owned = builder;
                LOGGER.info("[DamageStats/Audit] hurt_fallback_begin attacker={} target={} source={} amount={} depth={}",
                        attacker.getUUID(), target.getId(), damageType(source), amount, ACTIVE.get().size());
            } else {
                owned = current;
                LOGGER.info("[DamageStats/Audit] hurt_join_existing attacker={} target={} source={} amount={}",
                        attacker.getUUID(), target.getId(), damageType(source), amount);
            }
        }
        DAMAGE_SCOPES.get().push(new DamageScope(owned));
    }

    public static void finishDamagePipeline() {
        Deque<DamageScope> scopes = DAMAGE_SCOPES.get();
        if (scopes.isEmpty()) return;
        DamageScope scope = scopes.pop();
        if (scopes.isEmpty()) DAMAGE_SCOPES.remove();
        if (scope.builder() != null) finishBuilder(scope.builder());
    }

    public static void beginEventListener(Event event, IEventListener listener) {
        DamageTraceBuilder builder = current();
        EventListenerScope scope = EventListenerScope.capture(builder, event, listener);
        EVENT_LISTENER_SCOPES.get().push(scope);
    }

    public static void invokeEventListener(IEventListener listener, Event event) {
        beginEventListener(event, listener);
        try {
            listener.invoke(event);
        } finally {
            finishEventListener(event, listener);
        }
    }

    public static void finishEventListener(Event event, IEventListener listener) {
        Deque<EventListenerScope> scopes = EVENT_LISTENER_SCOPES.get();
        if (scopes.isEmpty()) return;
        EventListenerScope scope = scopes.pop();
        if (scopes.isEmpty()) EVENT_LISTENER_SCOPES.remove();
        if (scope.builder() == null || scope.event() != event || scope.listener() != listener) return;
        if (!ACTIVE.get().contains(scope.builder())) return;
        try {
            float after = event instanceof LivingHurtEvent hurt ? hurt.getAmount()
                    : event instanceof LivingDamageEvent damage ? damage.getAmount() : Float.NaN;
            boolean canceledAfter = event.isCancelable() && event.isCanceled();
            EventListenerAttribution.Resolution resolution = EventListenerAttribution.resolve(listener, event);
            ContributionSource source = resolution.source();
            String description = resolution.description();
            scope.builder().recordEventListener(event, scope.before(), scope.canceledBefore(), source,
                    resolution.confidence(), description, resolution.note());
            if ((!Float.isNaN(after) && Math.abs(after - scope.before()) > 0.0001F)
                    || canceledAfter != scope.canceledBefore()) {
                LOGGER.info("[DamageStats/Audit] event_listener_changed event={} listener={} before={} after={} canceled={} mod={} confidence={}",
                        event.getClass().getSimpleName(), description, scope.before(), after, canceledAfter,
                        source.modId(), resolution.confidence());
            }
        } catch (RuntimeException | LinkageError error) {
            LOGGER.warn("[DamageStats/Audit] event_listener_attribution_failed event={} listener={}",
                    event.getClass().getName(), listener.getClass().getName(), error);
        }
    }

    public static void recordCritical(CriticalHitEvent event) {
        DamageTraceBuilder builder = current();
        if (builder == null || builder.attacker() != event.getEntity() || builder.target() != event.getTarget()) return;
        boolean active = event.getResult() == Event.Result.ALLOW
                || (event.isVanillaCritical() && event.getResult() == Event.Result.DEFAULT);
        builder.recordCritical(event.getDamageModifier(), active, ApothicAttribution.criticalEvent(event));
    }

    public static void livingAttack(LivingEntity target, DamageSource source, float amount) {
        DamageTraceBuilder builder = current();
        if (builder != null) builder.bindPrimary(target, source, amount);
    }

    public static void livingHurt(LivingEntity target, DamageSource source, float before, float after) {
        DamageTraceBuilder builder = current();
        if (builder != null && builder.matches(target, source)) builder.recordHurt(before, after);
    }

    public static void livingHurtBegin(LivingEntity target, DamageSource source, float amount) {
        DamageTraceBuilder builder = current();
        if (builder != null && builder.matches(target, source)) builder.beginHurtCheckpoints(amount);
    }

    public static void livingHurtCheckpoint(LivingHurtEvent event, String interval,
            EventPriority... candidatePriorities) {
        DamageTraceBuilder builder = current();
        if (builder == null || !builder.matches(event.getEntity(), event.getSource())) return;
        builder.recordHurtCheckpoint(event.getAmount(), interval,
                ForgeEventCandidates.describe(event, candidatePriorities));
    }

    public static void armor(LivingEntity target, DamageSource source, float before, float after) {
        DamageTraceBuilder builder = current();
        if (builder != null && builder.matches(target, source)) {
            builder.recordArmor(before, after);
            if (Math.abs(before - after) > 0.0001F) {
                LOGGER.info("[DamageStats/Audit] mitigation_stage stage=armor target={} before={} after={}",
                        target.getId(), before, after);
            }
        }
    }

    public static void magic(LivingEntity target, DamageSource source, float before, float after) {
        DamageTraceBuilder builder = current();
        if (builder != null && builder.matches(target, source)) {
            builder.recordMagic(before, after);
            if (Math.abs(before - after) > 0.0001F) {
                LOGGER.info("[DamageStats/Audit] mitigation_stage stage=magic_protection target={} before={} after={}",
                        target.getId(), before, after);
            }
        }
    }

    public static void livingDamage(LivingEntity target, DamageSource source, float before, float after) {
        DamageTraceBuilder builder = current();
        if (builder != null && builder.matches(target, source)) builder.recordDamageEvent(before, after);
    }

    public static void livingDamageBegin(LivingEntity target, DamageSource source, float amount) {
        DamageTraceBuilder builder = current();
        if (builder != null && builder.matches(target, source)) builder.beginDamageCheckpoints(amount);
    }

    public static void livingDamageCheckpoint(LivingDamageEvent event, String interval,
            EventPriority... candidatePriorities) {
        DamageTraceBuilder builder = current();
        if (builder == null || !builder.matches(event.getEntity(), event.getSource())) return;
        builder.recordDamageCheckpoint(event.getAmount(), interval,
                ForgeEventCandidates.describe(event, candidatePriorities));
    }

    private static DamageTraceBuilder current() {
        Deque<DamageTraceBuilder> stack = ACTIVE.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    private static void finishBuilder(DamageTraceBuilder builder) {
        Deque<DamageTraceBuilder> stack = ACTIVE.get();
        if (stack.isEmpty() || !stack.contains(builder)) return;
        if (stack.peek() != builder) {
            LOGGER.warn("[DamageStats/Audit] trace_finish_not_top attacker={} target={} depth={}",
                    builder.attacker().getUUID(), builder.target().getId(), stack.size());
            return;
        }
        stack.pop();
        if (stack.isEmpty()) ACTIVE.remove();
        if (!builder.hasDamageEntry()) {
            LOGGER.info("[DamageStats/Audit] trace_discard_no_damage_entry attacker={} target={}",
                    builder.attacker().getUUID(), builder.target().getId());
            return;
        }
        if (builder.attacker() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            DamageTrace trace = builder.finish();
            LOGGER.info("[DamageStats/Audit] trace_complete attacker={} target={} calculated={} actual={} canceled={} steps={}",
                    serverPlayer.getUUID(), builder.target().getId(), trace.calculatedDamage(),
                    trace.actualHealthLoss(), trace.canceledOrBlocked(), trace.steps().size());
            TraceRepository.INSTANCE.add(serverPlayer, trace);
        }
    }

    private static String damageType(DamageSource source) {
        return source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("unknown");
    }

    private static boolean isInDamagePipeline(DamageTraceBuilder builder) {
        for (DamageScope scope : DAMAGE_SCOPES.get()) {
            if (scope.builder() == builder) return true;
        }
        return false;
    }

    private static void pruneCompletedAttackScopes() {
        Deque<AttackScope> attacks = ATTACK_SCOPES.get();
        Deque<DamageTraceBuilder> active = ACTIVE.get();
        while (!attacks.isEmpty() && !active.contains(attacks.peek().builder())) attacks.pop();
        if (attacks.isEmpty()) ATTACK_SCOPES.remove();
    }

    private record DamageScope(DamageTraceBuilder builder) {
    }

    private record AttackScope(Player attacker, Entity target, DamageTraceBuilder builder) {
    }

    private record EventListenerScope(DamageTraceBuilder builder, Event event, IEventListener listener,
            float before, boolean canceledBefore) {
        private static EventListenerScope capture(DamageTraceBuilder builder, Event event, IEventListener listener) {
            float amount = event instanceof LivingHurtEvent hurt ? hurt.getAmount()
                    : event instanceof LivingDamageEvent damage ? damage.getAmount() : Float.NaN;
            boolean canceled = event.isCancelable() && event.isCanceled();
            boolean matches = builder != null && (event instanceof LivingHurtEvent hurt
                    ? builder.matches(hurt.getEntity(), hurt.getSource())
                    : event instanceof LivingDamageEvent damage
                            && builder.matches(damage.getEntity(), damage.getSource()));
            if (!matches || Float.isNaN(amount)) builder = null;
            return new EventListenerScope(builder, event, listener, amount, canceled);
        }
    }
}
