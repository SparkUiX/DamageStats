package com.u1x.damagestats;

import com.u1x.damagestats.command.DamageStatsCommands;
import com.u1x.damagestats.trace.AttackTraceManager;
import com.u1x.damagestats.trace.TraceRepository;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class DamageStatsEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCriticalHit(CriticalHitEvent event) {
        AttackTraceManager.recordCritical(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurtHighest(LivingHurtEvent event) {
        AttackTraceManager.livingHurtCheckpoint(event, "HEAD→HIGHEST", EventPriority.HIGHEST);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingHurtHigh(LivingHurtEvent event) {
        AttackTraceManager.livingHurtCheckpoint(event, "HIGHEST→HIGH",
                EventPriority.HIGHEST, EventPriority.HIGH);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingHurtNormal(LivingHurtEvent event) {
        AttackTraceManager.livingHurtCheckpoint(event, "HIGH→NORMAL",
                EventPriority.HIGH, EventPriority.NORMAL);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingHurtLow(LivingHurtEvent event) {
        AttackTraceManager.livingHurtCheckpoint(event, "NORMAL→LOW",
                EventPriority.NORMAL, EventPriority.LOW);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingHurtLowest(LivingHurtEvent event) {
        AttackTraceManager.livingHurtCheckpoint(event, "LOW→LOWEST",
                EventPriority.LOW, EventPriority.LOWEST);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDamageHighest(LivingDamageEvent event) {
        AttackTraceManager.livingDamageCheckpoint(event, "HEAD→HIGHEST", EventPriority.HIGHEST);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDamageHigh(LivingDamageEvent event) {
        AttackTraceManager.livingDamageCheckpoint(event, "HIGHEST→HIGH",
                EventPriority.HIGHEST, EventPriority.HIGH);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDamageNormal(LivingDamageEvent event) {
        AttackTraceManager.livingDamageCheckpoint(event, "HIGH→NORMAL",
                EventPriority.HIGH, EventPriority.NORMAL);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingDamageLow(LivingDamageEvent event) {
        AttackTraceManager.livingDamageCheckpoint(event, "NORMAL→LOW",
                EventPriority.NORMAL, EventPriority.LOW);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDamageLowest(LivingDamageEvent event) {
        AttackTraceManager.livingDamageCheckpoint(event, "LOW→LOWEST",
                EventPriority.LOW, EventPriority.LOWEST);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        DamageStatsCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TraceRepository.INSTANCE.removePlayer(event.getEntity().getUUID());
    }
}
