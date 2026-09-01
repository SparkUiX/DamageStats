package com.u1x.damagestats.mixin;

import com.u1x.damagestats.trace.AttackTraceManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ForgeHooks.class, remap = false)
abstract class ForgeHooksMixin {
    @Redirect(method = {"onLivingHurt", "onLivingDamage"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"),
            remap = false)
    private static boolean damagestats$postWithPerListenerTracing(IEventBus eventBus, Event event) {
        return eventBus.post(event, AttackTraceManager::invokeEventListener);
    }

    @Inject(method = "onLivingAttack", at = @At("HEAD"))
    private static void damagestats$livingAttack(LivingEntity target, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> callback) {
        AttackTraceManager.livingAttack(target, source, amount);
    }

    @Inject(method = "onLivingHurt", at = @At("RETURN"))
    private static void damagestats$livingHurt(LivingEntity target, DamageSource source, float amount,
            CallbackInfoReturnable<Float> callback) {
        AttackTraceManager.livingHurt(target, source, amount, callback.getReturnValueF());
    }

    @Inject(method = "onLivingHurt", at = @At("HEAD"))
    private static void damagestats$livingHurtBegin(LivingEntity target, DamageSource source, float amount,
            CallbackInfoReturnable<Float> callback) {
        AttackTraceManager.livingHurtBegin(target, source, amount);
    }

    @Inject(method = "onLivingDamage", at = @At("RETURN"))
    private static void damagestats$livingDamage(LivingEntity target, DamageSource source, float amount,
            CallbackInfoReturnable<Float> callback) {
        AttackTraceManager.livingDamage(target, source, amount, callback.getReturnValueF());
    }

    @Inject(method = "onLivingDamage", at = @At("HEAD"))
    private static void damagestats$livingDamageBegin(LivingEntity target, DamageSource source, float amount,
            CallbackInfoReturnable<Float> callback) {
        AttackTraceManager.livingDamageBegin(target, source, amount);
    }
}
