package com.u1x.damagestats.mixin;

import com.u1x.damagestats.trace.AttackTraceManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Shadow
    protected abstract float getDamageAfterArmorAbsorb(DamageSource source, float amount);

    @Shadow
    protected abstract float getDamageAfterMagicAbsorb(DamageSource source, float amount);

    @Inject(method = "hurt", at = @At("HEAD"))
    private void damagestats$beginDamagePipeline(DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> callback) {
        AttackTraceManager.beginDamagePipeline((LivingEntity) (Object) this, source, amount);
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void damagestats$finishDamagePipeline(DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> callback) {
        AttackTraceManager.finishDamagePipeline();
    }

    @Redirect(method = "actuallyHurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float damagestats$captureArmorAtUse(LivingEntity instance, DamageSource source, float amount) {
        float result = getDamageAfterArmorAbsorb(source, amount);
        AttackTraceManager.armor((LivingEntity) (Object) this, source, amount, result);
        return result;
    }

    @Redirect(method = "actuallyHurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float damagestats$captureMagicAtUse(LivingEntity instance, DamageSource source, float amount) {
        float result = getDamageAfterMagicAbsorb(source, amount);
        AttackTraceManager.magic((LivingEntity) (Object) this, source, amount, result);
        return result;
    }
}
