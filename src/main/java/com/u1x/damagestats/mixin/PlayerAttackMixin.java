package com.u1x.damagestats.mixin;

import com.u1x.damagestats.trace.AttackTraceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class PlayerAttackMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void damagestats$beginAttack(Entity target, CallbackInfo callback) {
        AttackTraceManager.begin((Player) (Object) this, target);
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void damagestats$finishAttack(Entity target, CallbackInfo callback) {
        AttackTraceManager.finish((Player) (Object) this, target);
    }
}
