package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.math.TimerUtil;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.tutorial.Tutorial;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.TickRateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(Minecraft.class)
public abstract class MinecraftTimerMixin {
    @Shadow
    private int rightClickDelay;

    @Shadow
    public int missTime;

    @Shadow
    private long clientTickCount;

    @Unique
    private int krs$savedRightClickDelay;

    @Unique
    private int krs$savedMissTime;

    @Unique
    private long krs$savedClientTickCount;

    @Unique
    private void krs$saveTimerFields() {
        this.krs$savedRightClickDelay = this.rightClickDelay;
        this.krs$savedMissTime = this.missTime;
        this.krs$savedClientTickCount = this.clientTickCount;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void krs$restoreTimerFields(CallbackInfo ci) {
        if (!TimerUtil.selfTick) {
            this.rightClickDelay = this.krs$savedRightClickDelay;
            this.missTime = this.krs$savedMissTime;
        }

        if (!TimerUtil.worldTick)
            this.clientTickCount = this.krs$savedClientTickCount;

        TimerUtil.endClientTick();
    }

    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;clientTickCount:J", ordinal = 0))
    private void krs$captureTimerFields(CallbackInfo ci) {
        this.krs$saveTimerFields();
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/TickRateManager;tick()V"))
    private boolean krs$worldTickRate(TickRateManager instance) {
        return TimerUtil.worldTick;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;tick()V"))
    private boolean krs$selfGameMode(MultiPlayerGameMode instance) {
        return TimerUtil.selfTick;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;pick(F)V"))
    private boolean krs$selfPick(Minecraft instance, float partialTicks) {
        return TimerUtil.selfTick;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;tick()V"))
    private boolean krs$worldGui(Gui instance) {
        return TimerUtil.worldTick;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;handleKeybinds()V"))
    private boolean krs$selfKeybinds(Minecraft instance) {
        return TimerUtil.selfTick;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;tick()V"))
    private boolean krs$selfGameRenderer(GameRenderer instance) {
        return TimerUtil.selfTick;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;tickBlockEntities()V"))
    private boolean krs$worldBlockEntities(ClientLevel instance) {
        return TimerUtil.worldTick;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;sendChanges()V"))
    private boolean krs$selfSendChanges(LocalPlayer instance) {
        return TimerUtil.selfTick;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/MusicManager;tick()V"))
    private boolean krs$worldMusic(MusicManager instance) {
        return TimerUtil.worldTick;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;tick(Z)V"))
    private boolean krs$worldSound(SoundManager instance, boolean paused) {
        return TimerUtil.worldTick;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/tutorial/Tutorial;tick()V"))
    private boolean krs$worldTutorial(Tutorial instance) {
        return TimerUtil.worldTick;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
    private boolean krs$worldLevel(ClientLevel instance, BooleanSupplier haveTime) {
        return TimerUtil.worldTick;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;animateTick(III)V"))
    private boolean krs$worldAnimateTick(ClientLevel instance, int x, int y, int z) {
        return TimerUtil.worldTick;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;tick()V"))
    private boolean krs$worldParticles(ParticleEngine instance) {
        return TimerUtil.worldTick;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private boolean krs$selfTickEndPacket(ClientPacketListener instance, Packet<?> packet) {
        return TimerUtil.selfTick;
    }

    @ModifyVariable(method = "pick(F)V", at = @At("HEAD"), argsOnly = true)
    private float krs$selfPickPartialTick(float partialTicks) {
        if (TimerUtil.inClientTick)
            return partialTicks;

        return TimerUtil.selfPartialTick(partialTicks);
    }
}
