package com.u1x.damagestats.trace;

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
                ContributionSource.vanilla("living_attack", "LivingAttackEvent 入口"),
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
                            "LivingHurtEvent LOWEST 后的剩余修改"),
                    AttributionConfidence.UNKNOWN,
                    "可能类: LOWEST 优先级中位于 Damage Stats 采样器之后的监听器");
            hurtCheckpointAmount = after;
            return;
        }
        ApothicAttribution.Attribution apothic = ApothicAttribution.hurtEvent(attacker, before, after);
        if (apothic != null) {
            addChanged(DamageStage.LIVING_HURT_EVENT, before, after,
                    apothic.source(), apothic.confidence(), apothic.note());
        } else {
            addChanged(DamageStage.LIVING_HURT_EVENT, before, after,
                    new ContributionSource("forge", "event", "living_hurt", "LivingHurtEvent 中的 mod 修改"),
                    AttributionConfidence.UNKNOWN, "该阶段可能包含多个监听器");
        }
    }

    void recordArmor(float before, float after) {
        mitigationAfter = after;
        addChanged(DamageStage.ARMOR, before, after,
                new ContributionSource("minecraft", "pipeline_stage", "armor", "护甲与韧性阶段"),
                AttributionConfidence.EXACT, "输入输出精确；实际算法可能已被其他 mod 的 Mixin 或属性库替换");
    }

    void recordMagic(float before, float after) {
        mitigationAfter = after;
        addChanged(DamageStage.MAGIC, before, after,
                new ContributionSource("minecraft", "pipeline_stage", "magic_mitigation", "抗性与保护阶段"),
                AttributionConfidence.EXACT, "输入输出精确；实际算法可能已被其他 mod 的 Mixin 或属性库替换");
    }

    void recordDamageEvent(float before, float after) {
        float beforeAbsorption = !Float.isNaN(mitigationAfter) ? mitigationAfter : hurtEventAfter;
        if (!Float.isNaN(beforeAbsorption) && before < beforeAbsorption - EPSILON) {
            addChanged(DamageStage.ABSORPTION, beforeAbsorption, before,
                    ContributionSource.vanilla("absorption", "吸收生命"), AttributionConfidence.INFERRED,
                    "由最终减伤结果与 LivingDamageEvent 入口差额计算");
        }
        damageEventAfter = after;
        if (exactDamageListenerCaptured) return;
        if (damageCheckpointCaptured) {
            addChanged(DamageStage.LIVING_DAMAGE_EVENT, damageCheckpointAmount, after,
                    new ContributionSource("unknown", "event_priority", "living_damage_tail",
                            "LivingDamageEvent LOWEST 后的剩余修改"),
                    AttributionConfidence.UNKNOWN,
                    "可能类: LOWEST 优先级中位于 Damage Stats 采样器之后的监听器");
            damageCheckpointAmount = after;
            return;
        }
        addChanged(DamageStage.LIVING_DAMAGE_EVENT, before, after,
                new ContributionSource("forge", "event", "living_damage", "LivingDamageEvent 中的 mod 修改"),
                AttributionConfidence.UNKNOWN, "该阶段可能包含多个监听器");
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
                        ContributionSource.unknown("伤害管线在 LivingHurtEvent 后提前结束"),
                        AttributionConfidence.INFERRED,
                        "目标可能是训练假人或使用自定义生命/伤害代理；不视为格挡"));
                canceled = false;
            } else {
                steps.add(step(DamageStage.CANCELED_OR_BLOCKED, livingAttackAmount, 0.0F,
                        ContributionSource.unknown("伤害被格挡、免疫或在中途取消"),
                        AttributionConfidence.INFERRED, "未进入 LivingHurtEvent/最终伤害阶段"));
            }
        }
        steps.add(step(DamageStage.ACTUAL_HEALTH_LOSS, calculated, actualLoss,
                ContributionSource.vanilla("health", "实际生命损失"), AttributionConfidence.EXACT,
                actualLoss <= EPSILON && calculated > EPSILON
                        ? "目标未通过原版 health 字段暴露扣血；可能使用假人/自定义生命"
                        : calculated > actualLoss + EPSILON ? "包含过量伤害或后续阻止" : ""));
        return new DamageTrace(traceId, 0L, attacker.getUUID(), attacker.getGameProfile().getName(),
                target.getId(), target.getDisplayName().getString(), weapon, gameTime,
                calculated, actualLoss, canceled, steps);
    }

    private void buildAttackConstruction(float observedAmount) {
        float value = (float) attackAttribute.baseValue();
        steps.add(step(DamageStage.ATTACK_ATTRIBUTE, 0.0F, value,
                ContributionSource.vanilla("attack_damage_base", "攻击伤害基础值"),
                AttributionConfidence.EXACT, ""));

        List<AttributeSnapshot.ModifierSnapshot> modifiers = new ArrayList<>(attackAttribute.modifiers());
        modifiers.sort(Comparator.comparingInt(m -> m.operation().ordinal()));
        double additionBase = value;
        for (AttributeSnapshot.ModifierSnapshot modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADDITION) {
                float next = value + (float) modifier.amount();
                steps.add(step(DamageStage.ATTACK_ATTRIBUTE, value, next, modifier.source(),
                        modifier.source().modId().equals("unknown") ? AttributionConfidence.UNKNOWN : AttributionConfidence.EXACT,
                        "加法 " + signed((float) modifier.amount()) + "；" + modifier.name()
                                + " [" + modifier.id() + "]"));
                value = next;
            }
        }
        additionBase = value;
        for (AttributeSnapshot.ModifierSnapshot modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.MULTIPLY_BASE) {
                float next = value + (float) (additionBase * modifier.amount());
                steps.add(step(DamageStage.ATTACK_ATTRIBUTE, value, next, modifier.source(),
                        modifier.source().modId().equals("unknown") ? AttributionConfidence.UNKNOWN : AttributionConfidence.EXACT,
                        "基础乘算 " + signed((float) modifier.amount()) + "（本次 "
                                + signed(next - value) + "）；" + modifier.name() + " [" + modifier.id() + "]"));
                value = next;
            }
        }
        for (AttributeSnapshot.ModifierSnapshot modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.MULTIPLY_TOTAL) {
                float next = value * (1.0F + (float) modifier.amount());
                steps.add(step(DamageStage.ATTACK_ATTRIBUTE, value, next, modifier.source(),
                        modifier.source().modId().equals("unknown") ? AttributionConfidence.UNKNOWN : AttributionConfidence.EXACT,
                        "总乘算 ×" + DamageTraceFormatter.number(1.0F + (float) modifier.amount())
                                + "；" + modifier.name() + " [" + modifier.id() + "]"));
                value = next;
            }
        }

        float cooled = value * (0.2F + cooldown * cooldown * 0.8F);
        addChanged(DamageStage.ATTACK_COOLDOWN, value, cooled,
                ContributionSource.vanilla("attack_cooldown", "攻击冷却"), AttributionConfidence.EXACT,
                "冷却强度 " + DamageTraceFormatter.number(cooldown));
        value = cooled;

        if (criticalActive && criticalModifier != 1.0F) {
            float critical = value * criticalModifier;
            ApothicAttribution.Attribution attribution = criticalAttribution != null
                    ? criticalAttribution
                    : new ApothicAttribution.Attribution(
                            ContributionSource.vanilla("critical_hit_event", "原版/Forge 暴击"),
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
                    ContributionSource.vanilla("enchantment_damage", "武器附魔伤害"),
                    AttributionConfidence.EXACT, ""));
            value = next;
        }

        if (Math.abs(value - observedAmount) > EPSILON) {
            steps.add(step(DamageStage.ATTACK_CONSTRUCTION_RESIDUAL, value, observedAmount,
                    ContributionSource.unknown("攻击构造阶段的未归因变化"),
                    AttributionConfidence.UNKNOWN, "可能来自直接注入 Player.attack 的 mod"));
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
            inferenceNote = "；" + inference.note();
        }
        String note = "实际事件监听器: " + listenerDescription
                + (attributionNote == null || attributionNote.isBlank() ? "" : "；" + attributionNote)
                + inferenceNote
                + (canceledBefore != canceledAfter ? (canceledAfter ? "；取消了事件" : "；恢复了事件") : "");
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
                            "LivingDamageEvent " + interval + " 区间修改"),
                    AttributionConfidence.UNKNOWN, "可能类: " + candidates));
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
                        apothic.source(), apothic.confidence(), apothic.note() + "；采样区间 " + interval));
            } else {
                steps.add(step(DamageStage.LIVING_HURT_EVENT, before, amount,
                        new ContributionSource("unknown", "event_priority", "living_hurt_" + interval,
                                "LivingHurtEvent " + interval + " 区间修改"),
                        AttributionConfidence.UNKNOWN, "可能类: " + candidates));
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

    private static String damageType(DamageSource source) {
        return source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("unknown");
    }
}
