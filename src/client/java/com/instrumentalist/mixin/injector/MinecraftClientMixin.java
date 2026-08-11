package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.level.ItemDropChanger;
import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.HandleInputEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.ViewModel;
import com.instrumentalist.krs.screen.CustomTitleScreen;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.render.GraphicsApiCompatibility;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.platform.Window;
import org.nvgu.NVGU;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin implements IMinecraft {

    @Shadow @Nullable public LocalPlayer player;

    @Shadow protected int missTime;

    @Shadow @Nullable public HitResult hitResult;

    @Shadow @Nullable public ClientLevel level;

    @Shadow @Nullable public MultiPlayerGameMode gameMode;

    @Shadow @Final public ParticleEngine particleEngine;

    @Shadow public Options options;

    @Shadow public abstract Window getWindow();

    @Unique
    private Boolean krs$lastAllowCursorChanges;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;initRenderer(Lcom/mojang/blaze3d/systems/GpuDevice;)V", shift = At.Shift.AFTER))
    private void initNanoVG(CallbackInfo ci) {
        GraphicsApiCompatibility.initialize();
        GraphicsApiCompatibility.runWithOpenGlContext(NVGU.INSTANCE::create);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void injectClient(CallbackInfo callback) {
        Client.inject();
        krs$syncAllowCursorChanges();
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void saveConfigOnClose(CallbackInfo ci) {
        CustomTitleScreen.stopMainMenuMusic();

        if (Client.loaded && Client.configManager != null)
            Client.configManager.saveCurrentIfFilesExist();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void shutdownClientResources(CallbackInfo ci) {
        Client.shutdown();
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;overlay()Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0), method = "tick()V", cancellable = true)
    private void handleInputEvent(CallbackInfo ci) {
        if (this.player == null)
            return;
        if (Client.eventManager == null || !Client.eventManager.hasListeners(HandleInputEvent.class))
            return;

        HandleInputEvent event = new HandleInputEvent();
        Client.eventManager.call(event);
        if (event.isCancelled())
            ci.cancel();
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void handleBlockBreaking(boolean breaking, CallbackInfo ci) {
        if (this.missTime <= 0) {
            if (breaking && this.hitResult != null && this.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                BlockHitResult blockHitResult = (BlockHitResult)this.hitResult;
                BlockPos blockPos = blockHitResult.getBlockPos();
                if (this.level != null && !this.level.getBlockState(blockPos).isAir()) {
                    if (ModuleManager.getModuleState(ViewModel.class) && ViewModel.punchingABlockWhileDoingStuff.get() && this.player.isUsingItem() && mc.player.getUseItem().getUseAnimation() != ItemUseAnimation.NONE) {
                        ci.cancel();
                        this.level.addBreakingBlockEffect(blockPos, blockHitResult.getDirection());
                        PlayerUtil.INSTANCE.swingHandWithoutPacket(InteractionHand.MAIN_HAND);
                    }
                }
            }
        }
    }

    @WrapWithCondition(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private boolean dropItemHook(LocalPlayer instance, InteractionHand hand) {
        return ItemDropChanger.hookDropItemSwing(hand);
    }

    @Inject(method = "setLevel", at = @At("HEAD"), cancellable = true)
    private void worldEvent(ClientLevel world, CallbackInfo ci) {
        if (Client.eventManager == null || !Client.eventManager.hasListeners(WorldEvent.class))
            return;

        WorldEvent event = new WorldEvent(this.level, world);
        Client.eventManager.call(event);
        if (event.isCancelled())
            ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void tickEvent(CallbackInfo ci) {
        krs$syncAllowCursorChanges();

        if (Client.eventManager == null || !Client.eventManager.hasListeners(TickEvent.class))
            return;

        TickEvent event = new TickEvent();
        Client.eventManager.call(event);
        if (event.isCancelled())
            ci.cancel();
    }

    @Unique
    private void krs$syncAllowCursorChanges() {
        if (this.options == null)
            return;

        Window window = this.getWindow();
        if (window == null)
            return;

        Boolean allowCursorChanges = (Boolean) this.options.allowCursorChanges().get();
        if (!Objects.equals(krs$lastAllowCursorChanges, allowCursorChanges)) {
            window.setAllowCursorChanges(Boolean.TRUE.equals(allowCursorChanges));
            krs$lastAllowCursorChanges = allowCursorChanges;
        }
    }

}
