package com.u1x.damagestats.trace;

import com.u1x.damagestats.DamageStatsText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.Event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class DamageTraceBuilder {
    private static final float EPSILON = 0.0001F;

    private final UUID traceId = UUID.randomUUID();
    private final Player attacker;
    private final Entity target;
    private final ContributionSource weapon;
    private final long gameTime;
    private final float healthBefore;
    private final AttributeSnapshot attackAttribute;
    private final float cooldown;
    private final float enchantmentBonus;
    private final GenericSourceInference.Context inferenceContext;
    private final List<DamageStep> steps = new ArrayList<>();

    private DamageSource primarySource;
    private float criticalModifier = 1.0F;
    private boolean criticalActive;
    private ApothicAttribution.Attribution criticalAttribution;
    private float livingAttackAmount;
    private float hurtEventAfter = Float.NaN;
    private float hurtCheckpointAmount = Float.NaN;
    private boolean hurtCheckpointCaptured;
    private float mitigationAfter = Float.NaN;
    private float damageEventAfter = Float.NaN;
    private float damageCheckpointAmount = Float.NaN;
    private boolean damageCheckpointCaptured;
    private boolean exactHurtListenerCaptured;
    private boolean exactDamageListenerCaptured;

    DamageTraceBuilder(Player attacker, Entity target) {
        this.attacker = attacker;
        this.target = target;
        this.weapon = AttributeSnapshot.itemSource(attacker.getMainHandItem(), "mainhand");
        this.gameTime = attacker.level().getGameTime();
        this.healthBefore = target instanceof LivingEntity living ? living.getHealth() : 0.0F;
        this.attackAttribute = AttributeSnapshot.attackDamage(attacker);
        this.cooldown = attacker.getAttackStrengthScale(0.5F);
        this.enchantmentBonus = target instanceof LivingEntity living
                ? EnchantmentHelper.getDamageBonus(attacker.getMainHandItem(), living.getMobType())
                : EnchantmentHelper.getDamageBonus(attacker.getMainHandItem(), MobType.UNDEFINED);
        this.inferenceContext = GenericSourceInference.capture(attacker, target);
    }

    Player attacker() {
        return attacker;
    }

    Entity target() {
        return target;
    }

    boolean matches(LivingEntity entity, DamageSource source) {
        return target == entity && sameSource(primarySource, source);
    }

    boolean bindPrimary(LivingEntity entity, DamageSource source, float amount) {
        if (target != entity || primarySource != null) return false;
        primarySource = source;
        livingAttackAmount = amount;
        buildAttackConstruction(amount);
        steps.add(step(DamageStage.LIVING_ATTACK, amount, amount,
                ContributionSource.vanilla("living_attack", tr("source.living_attack")),
                AttributionConfidence.EXACT, damageType(source)));
        return true;
    }

    void recordCritical(float modifier, boolean active, ApothicAttribution.Attribution attribution) {
        this.criticalModifier = modifier;
        this.criticalActive = active;
        this.criticalAttribution = attribution;
    }

    void recordHurt(float before, float after) {
        hurtEventAfter = after;
        if (exactHurtListenerCaptured) return;
        if (hurtCheckpointCaptured) {
            addChanged(DamageStage.LIVING_HURT_EVENT, hurtCheckpointAmount, after,
                    new ContributionSource("unknown", "event_priority", "living_hurt_tail",
                            tr("source.living_hurt_tail")),
                    AttributionConfidence.UNKNOWN,
                    tr("note.possible_lowest_after_sampler"));
            hurtCheckpointAmount = after;
            return;
        }
        ApothicAttribution.Attribution apothic = ApothicAttribution.hurtEvent(attacker, before, after);
        if (apothic != null) {
            addChanged(DamageStage.LIVING_HURT_EVENT, before, after,
                    apothic.source(), apothic.confidence(), apothic.note());
        } else {
            addChanged(DamageStage.LIVING_HURT_EVENT, before, after,
                    new ContributionSource("forge", "event", "living_hurt", tr("source.living_hurt_modification")),
                    AttributionConfidence.UNKNOWN, tr("note.multiple_listeners"));
        }
    }

    void recordArmor(float before, float after) {
        mitigationAfter = after;
        addChanged(DamageStage.ARMOR, before, after,
                new ContributionSource("minecraft", "pipeline_stage", "armor", tr("source.armor")),
                AttributionConfidence.EXACT, tr("note.exact_io_algorithm_may_be_modified"));
    }

    void recordMagic(float before, float after) {
        mitigationAfter = after;
        addChanged(DamageStage.MAGIC, before, after,
                new ContributionSource("minecraft", "pipeline_stage", "magic_mitigation", tr("source.magic")),
                AttributionConfidence.EXACT, tr("note.exact_io_algorithm_may_be_modified"));
    }

    void recordDamageEvent(float before, float after) {
        float beforeAbsorption = !Float.isNaN(mitigationAfter) ? mitigationAfter : hurtEventAfter;
        if (!Float.isNaN(beforeAbsorption) && before < beforeAbsorption - EPSILON) {
            addChanged(DamageStage.ABSORPTION, beforeAbsorption, before,
                    ContributionSource.vanilla("absorption", tr("source.absorption")), AttributionConfidence.INFERRED,
                    tr("note.absorption_inferred"));
        }
        damageEventAfter = after;
        if (exactDamageListenerCaptured) return;
        if (damageCheckpointCaptured) {
            addChanged(DamageStage.LIVING_DAMAGE_EVENT, damageCheckpointAmount, after,
                    new ContributionSource("unknown", "event_priority", "living_damage_tail",
                            tr("source.living_damage_tail")),
                    AttributionConfidence.UNKNOWN,
                    tr("note.possible_lowest_after_sampler"));
            damageCheckpointAmount = after;
            return;
        }
        addChanged(DamageStage.LIVING_DAMAGE_EVENT, before, after,
                new ContributionSource("forge", "event", "living_damage", tr("source.living_damage_modification")),
                AttributionConfidence.UNKNOWN, tr("note.multiple_listeners"));
    }

    DamageTrace finish() {
        float actualLoss = target instanceof LivingEntity living
                ? Math.max(0.0F, healthBefore - living.getHealth())
                : 0.0F;
        float calculated = latestObservedDamage();
        boolean canceled = primarySource == null || (calculated <= EPSILON && actualLoss <= EPSILON);
        if (primarySource != null && Float.isNaN(damageEventAfter)) {
            if (!Float.isNaN(hurtEventAfter) && hurtEventAfter > EPSILON) {
                steps.add(step(DamageStage.PIPELINE_TERMINATED, hurtEventAfter, hurtEventAfter,
                        ContributionSource.unknown(tr("source.pipeline_terminated")),
                        AttributionConfidence.INFERRED,
                        tr("note.custom_health_proxy")));
                canceled = false;
            } else {
                steps.add(step(DamageStage.CANCELED_OR_BLOCKED, livingAttackAmount, 0.0F,
                        ContributionSource.unknown(tr("source.canceled")),
                        AttributionConfidence.INFERRED, tr("note.never_entered_final_damage")));
            }
        }
        steps.add(step(DamageStage.ACTUAL_HEALTH_LOSS, calculated, actualLoss,
                ContributionSource.vanilla("health", tr("source.actual_health_loss")), AttributionConfidence.EXACT,
                actualLoss <= EPSILON && calculated > EPSILON
                        ? tr("note.health_not_exposed")
                        : calculated > actualLoss + EPSILON ? tr("note.overkill_or_prevented") : ""));
        return new DamageTrace(traceId, 0L, attacker.getUUID(), attacker.getGameProfile().getName(),
                target.getId(), target.getDisplayName().getString(), weapon, gameTime,
                calculated, actualLoss, canceled, steps);
    }

    private void buildAttackConstruction(float observedAmount) {
        float value = (float) attackAttribute.baseValue();
        steps.add(step(DamageStage.ATTACK_ATTRIBUTE, 0.0F, value,
                ContributionSource.vanilla("attack_damage_base", tr("source.attack_damage_base")),
                AttributionConfidence.EXACT, ""));

        List<AttributeSnapshot.ModifierSnapshot> modifiers = new ArrayList<>(attackAttribute.modifiers());
        modifiers.sort(Comparator.comparingInt(m -> m.operation().ordinal()));
        double additionBase = value;
        for (AttributeSnapshot.ModifierSnapshot modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADDITION) {
                float next = value + (float) modifier.amount();
                steps.add(step(DamageStage.ATTACK_ATTRIBUTE, value, next, modifier.source(),
                        modifier.source().modId().equals("unknown") ? AttributionConfidence.UNKNOWN : AttributionConfidence.EXACT,
                        DamageStatsText.encodedOperation("add", "note.attribute.addition",
                                signed((float) modifier.amount()), modifier.name(), modifier.id())));
                value = next;
            }
        }
        additionBase = value;
        for (AttributeSnapshot.ModifierSnapshot modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.MULTIPLY_BASE) {
                float next = value + (float) (additionBase * modifier.amount());
                steps.add(step(DamageStage.ATTACK_ATTRIBUTE, value, next, modifier.source(),
                        modifier.source().modId().equals("unknown") ? AttributionConfidence.UNKNOWN : AttributionConfidence.EXACT,
                        DamageStatsText.encodedOperation("add", "note.attribute.multiply_base",
                                signed((float) modifier.amount()), signed(next - value), modifier.name(), modifier.id())));
                value = next;
            }
        }
        for (AttributeSnapshot.ModifierSnapshot modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.MULTIPLY_TOTAL) {
                float next = value * (1.0F + (float) modifier.amount());
                steps.add(step(DamageStage.ATTACK_ATTRIBUTE, value, next, modifier.source(),
                        modifier.source().modId().equals("unknown") ? AttributionConfidence.UNKNOWN : AttributionConfidence.EXACT,
                        DamageStatsText.encodedOperation("multiply", "note.attribute.multiply_total",
                                DamageTraceFormatter.number(1.0F + (float) modifier.amount()),
                                modifier.name(), modifier.id())));
                value = next;
            }
        }

        float cooled = value * (0.2F + cooldown * cooldown * 0.8F);
        addChanged(DamageStage.ATTACK_COOLDOWN, value, cooled,
                ContributionSource.vanilla("attack_cooldown", tr("source.attack_cooldown")), AttributionConfidence.EXACT,
                tr("note.cooldown_strength", DamageTraceFormatter.number(cooldown)));
        value = cooled;

        if (criticalActive && criticalModifier != 1.0F) {
            float critical = value * criticalModifier;
            ApothicAttribution.Attribution attribution = criticalAttribution != null
                    ? criticalAttribution
                    : new ApothicAttribution.Attribution(
                            ContributionSource.vanilla("critical_hit_event", tr("source.critical")),
                            AttributionConfidence.EXACT,
                            "CriticalHitEvent ×" + DamageTraceFormatter.number(criticalModifier));
            addChanged(DamageStage.VANILLA_OR_FORGE_CRITICAL, value, critical,
                    attribution.source(), attribution.confidence(), attribution.note());
            value = critical;
        }

        float enchantment = enchantmentBonus * cooldown;
        if (Math.abs(enchantment) > EPSILON) {
            float next = value + enchantment;
            steps.add(step(DamageStage.ENCHANTMENT_BONUS, value, next,
                    ContributionSource.vanilla("enchantment_damage", tr("source.enchantment_damage")),
                    AttributionConfidence.EXACT, ""));
            value = next;
        }

        if (Math.abs(value - observedAmount) > EPSILON) {
            steps.add(step(DamageStage.ATTACK_CONSTRUCTION_RESIDUAL, value, observedAmount,
                    ContributionSource.unknown(tr("source.attack_residual")),
                    AttributionConfidence.UNKNOWN, tr("note.direct_player_attack_injection")));
        }
    }

    private void addChanged(DamageStage stage, float before, float after, ContributionSource source,
            AttributionConfidence confidence, String note) {
        if (Math.abs(before - after) > EPSILON) {
            steps.add(step(stage, before, after, source, confidence, note));
        }
    }

    private static DamageStep step(DamageStage stage, float before, float after,
            ContributionSource source, AttributionConfidence confidence, String note) {
        return new DamageStep(stage, before, after, source, confidence, note);
    }

    void recordEventListener(Event event, float before, boolean canceledBefore,
            ContributionSource source, AttributionConfidence sourceConfidence,
            String listenerDescription, String attributionNote) {
        float after;
        DamageStage stage;
        if (event instanceof LivingHurtEvent hurt) {
            after = hurt.getAmount();
            stage = DamageStage.LIVING_HURT_EVENT;
        } else if (event instanceof LivingDamageEvent damage) {
            after = damage.getAmount();
            stage = DamageStage.LIVING_DAMAGE_EVENT;
        } else {
            return;
        }
        boolean canceledAfter = event.isCancelable() && event.isCanceled();
        if (Math.abs(before - after) <= EPSILON && canceledBefore == canceledAfter) return;
        float effectiveBefore = canceledBefore ? 0.0F : before;
        float effectiveAfter = canceledAfter ? 0.0F : after;
        AttributionConfidence confidence = sourceConfidence;
        String inferenceNote = "";
        GenericSourceInference.Result inference = GenericSourceInference.infer(source, inferenceContext);
        if (inference != null) {
            source = inference.source();
            confidence = AttributionConfidence.INFERRED;
            inferenceNote = inference.note();
        }
        MutableComponent noteComponent = DamageStatsText.text("note.actual_listener", listenerDescription);
        appendNote(noteComponent, attributionNote);
        appendNote(noteComponent, inferenceNote);
        if (canceledBefore != canceledAfter) {
            appendNote(noteComponent, tr(canceledAfter ? "note.event_canceled" : "note.event_restored"));
        }
        String note = DamageStatsText.encodedComponent(noteComponent);
        steps.add(step(stage, effectiveBefore, effectiveAfter, source, confidence, note));
        if (stage == DamageStage.LIVING_HURT_EVENT) {
            exactHurtListenerCaptured = true;
            hurtCheckpointAmount = event instanceof LivingHurtEvent hurt ? hurt.getAmount() : after;
        } else {
            exactDamageListenerCaptured = true;
            damageCheckpointAmount = event instanceof LivingDamageEvent damage ? damage.getAmount() : after;
        }
    }

    boolean hasDamageEntry() {
        return primarySource != null;
    }

    void beginDamageCheckpoints(float amount) {
        damageCheckpointAmount = amount;
        damageCheckpointCaptured = false;
    }

    void recordDamageCheckpoint(float amount, String interval, String candidates) {
        if (Float.isNaN(damageCheckpointAmount)) return;
        float before = damageCheckpointAmount;
        if (!exactDamageListenerCaptured && Math.abs(before - amount) > EPSILON) {
            steps.add(step(DamageStage.LIVING_DAMAGE_EVENT, before, amount,
                    new ContributionSource("unknown", "event_priority", "living_damage_" + interval,
                            tr("source.living_damage_interval", interval)),
                    AttributionConfidence.UNKNOWN, tr("note.possible_classes", DamageStatsText.component(candidates))));
        }
        damageCheckpointAmount = amount;
        damageCheckpointCaptured = true;
    }

    void beginHurtCheckpoints(float amount) {
        hurtCheckpointAmount = amount;
        hurtCheckpointCaptured = false;
    }

    void recordHurtCheckpoint(float amount, String interval, String candidates) {
        if (Float.isNaN(hurtCheckpointAmount)) return;
        float before = hurtCheckpointAmount;
        if (!exactHurtListenerCaptured && Math.abs(before - amount) > EPSILON) {
            ApothicAttribution.Attribution apothic = ApothicAttribution.hurtEvent(attacker, before, amount);
            if (apothic != null) {
                steps.add(step(DamageStage.LIVING_HURT_EVENT, before, amount,
                        apothic.source(), apothic.confidence(), appendEncoded(apothic.note(),
                                DamageStatsText.text("note.sample_interval", interval))));
            } else {
                steps.add(step(DamageStage.LIVING_HURT_EVENT, before, amount,
                        new ContributionSource("unknown", "event_priority", "living_hurt_" + interval,
                                tr("source.living_hurt_interval", interval)),
                        AttributionConfidence.UNKNOWN, tr("note.possible_classes", DamageStatsText.component(candidates))));
            }
        }
        hurtCheckpointAmount = amount;
        hurtCheckpointCaptured = true;
    }

    private float latestObservedDamage() {
        if (!Float.isNaN(damageEventAfter)) return Math.max(0.0F, damageEventAfter);
        if (!Float.isNaN(mitigationAfter)) return Math.max(0.0F, mitigationAfter);
        if (!Float.isNaN(hurtEventAfter)) return Math.max(0.0F, hurtEventAfter);
        return Math.max(0.0F, livingAttackAmount);
    }

    private static boolean sameSource(DamageSource first, DamageSource second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        return first.typeHolder().equals(second.typeHolder())
                && first.getEntity() == second.getEntity()
                && first.getDirectEntity() == second.getDirectEntity();
    }

    private static String signed(float value) {
        return (value >= 0.0F ? "+" : "") + DamageTraceFormatter.number(value);
    }

    private static String tr(String path, Object... arguments) {
        return DamageStatsText.encoded(path, arguments);
    }

    private static void appendNote(MutableComponent target, String encodedOrLiteral) {
        if (encodedOrLiteral == null || encodedOrLiteral.isBlank()) return;
        target.append(DamageStatsText.text("note.separator"));
        target.append(DamageStatsText.component(encodedOrLiteral));
    }

    private static String appendEncoded(String encodedOrLiteral, MutableComponent suffix) {
        MutableComponent result = DamageStatsText.component(encodedOrLiteral);
        result.append(DamageStatsText.text("note.separator")).append(suffix);
        return DamageStatsText.encodedComponent(result);
    }

    private static String damageType(DamageSource source) {
        return source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("unknown");
    }
}
