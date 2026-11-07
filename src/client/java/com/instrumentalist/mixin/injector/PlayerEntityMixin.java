package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.hacks.features.render.LegacyRenderer;
import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.BlockEdgeEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.features.combat.Reach;
import com.instrumentalist.krs.hacks.features.exploit.ChestTP;
import com.instrumentalist.krs.hacks.features.movement.MovementFix;
import com.instrumentalist.krs.hacks.features.level.AlwaysRiptide;
import com.instrumentalist.krs.hacks.features.movement.Sprint;
import com.instrumentalist.krs.hacks.features.player.FastBreak;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.mixin.oringo.ITimerScaledTick;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

@Mixin(Player.class)
public abstract class PlayerEntityMixin implements IMinecraft {

    @Unique
    private float krs$preciseAttackStrengthTicks;

    @Unique
    private float krs$preciseItemSwapTicks;

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getMainHandItem()Lnet/minecraft/world/item/ItemStack;", ordinal = 0))
    private void krs$timerIndependentAttackStrength(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!(self instanceof LocalPlayer)) return;

        float step = TimerUtil.realTickStep();
        LivingEntityAccessor accessor = (LivingEntityAccessor) self;

        this.krs$preciseAttackStrengthTicks = krs$advance(this.krs$preciseAttackStrengthTicks, self.attackStrengthTicker - 1, step);
        self.attackStrengthTicker = (int) this.krs$preciseAttackStrengthTicks;

        this.krs$preciseItemSwapTicks = krs$advance(this.krs$preciseItemSwapTicks, accessor.krs$getItemSwapTicker() - 1, step);
        accessor.krs$setItemSwapTicker((int) this.krs$preciseItemSwapTicks);
    }

    @Unique
    private static float krs$advance(float precise, int previous, float step) {
        if ((int) precise != previous)
            precise = previous;

        return precise + step;
    }

    @Inject(method = "resetAttackStrengthTicker", at = @At("TAIL"))
    private void krs$resetPreciseTickers(CallbackInfo ci) {
        this.krs$preciseAttackStrengthTicks = 0f;
        this.krs$preciseItemSwapTicks = 0f;
    }

    @Inject(method = "resetOnlyAttackStrengthTicker", at = @At("TAIL"))
    private void krs$resetPreciseAttackTicker(CallbackInfo ci) {
        this.krs$preciseAttackStrengthTicks = 0f;
    }

    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void krs$timerIndependentAttackStrengthScale(float a, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        if (!(self instanceof LocalPlayer) || (int) this.krs$preciseAttackStrengthTicks != self.attackStrengthTicker) return;

        cir.setReturnValue(Mth.clamp((this.krs$preciseAttackStrengthTicks + a * TimerUtil.realTickStep()) / self.getCurrentItemAttackStrengthDelay(), 0.0F, 1.0F));
    }

    @Inject(method = "getItemSwapScale", at = @At("HEAD"), cancellable = true)
    private void krs$timerIndependentItemSwapScale(float a, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        if (!(self instanceof LocalPlayer) || (int) this.krs$preciseItemSwapTicks != ((LivingEntityAccessor) self).krs$getItemSwapTicker()) return;

        cir.setReturnValue(Mth.clamp((this.krs$preciseItemSwapTicks + a * TimerUtil.realTickStep()) / self.getCurrentItemAttackStrengthDelay(), 0.0F, 1.0F));
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemCooldowns;tick()V"))
    private void krs$timerIndependentItemCooldowns(ItemCooldowns instance, Operation<Void> original) {
        if ((Object) this instanceof LocalPlayer)
            ((ITimerScaledTick) instance).krs$tickScaled(TimerUtil.realTickStep());
        else original.call(instance);
    }

    @Inject(method = {"resetAttackStrengthTicker", "resetOnlyAttackStrengthTicker"}, at = @At("HEAD"))
    private void legacyRendererCooldownResetHook(CallbackInfo ci) {
        if ((Object) this == mc.player)
            LegacyRenderer.pendingCooldownReset = true;
    }

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void updateEvent(CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer) {
            if (mc.player != null) {
                if (mc.player.onGround())
                    MovementUtil.fallTicks = 0;
                else MovementUtil.fallTicks++;
            }

            if (Client.eventManager == null || !Client.eventManager.hasListeners(UpdateEvent.class))
                return;

            UpdateEvent event = new UpdateEvent();
            Client.eventManager.call(event);
            if (event.isCancelled())
                ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 movementFixSwimmingLookVector(Vec3 original) {
        if ((Object) this instanceof LocalPlayer && MovementFix.shouldFixMovement() && ((Player) (Object) this).isSwimming())
            return MovementFix.getMovementLookVector();

        return original;
    }

    @ModifyReturnValue(method = "isStayingOnGroundSurface", at = @At("RETURN"))
    private boolean safeWalkHook(boolean original) {
        if ((Object) this instanceof LocalPlayer) {
            if (Client.eventManager == null || !Client.eventManager.hasListeners(BlockEdgeEvent.class))
                return original;

            BlockEdgeEvent event = new BlockEdgeEvent();
            Client.eventManager.call(event);
            return original || event.isCancelled();
        }

        return original;
    }

    @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", ordinal = 0))
    private boolean keepSprintHook1(Player instance, Vec3 vec3d) {
        return Sprint.keepSprintHook(instance);
    }

    @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V", ordinal = 0))
    private boolean keepSprintHook2(Player instance, boolean b) {
        return Sprint.keepSprintHook(instance);
    }

    @ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
    public float fastBreakHook(float original) {
        return (Object) this instanceof LocalPlayer ? FastBreak.hookFastBreak(original) : original;
    }

    @ModifyReturnValue(method = "getDesiredPose", at = @At("RETURN"))
    private Pose riptidePoseHook(Pose original) {
        return AlwaysRiptide.hookDesiredPose(original, (Player) (Object) this);
    }

    @ModifyReturnValue(method = "blockInteractionRange", at = @At("RETURN"))
    public double blockReachHook(double original) {
        if ((Object) this instanceof LocalPlayer) {
            original = Reach.hookBlockReach(original);
            return ChestTP.hookBlockReach(original);
        }

        return original;
    }

    @ModifyReturnValue(method = "entityInteractionRange", at = @At("RETURN"))
    public double entityReachHook(double original) {
        if ((Object) this instanceof LocalPlayer)
            return Reach.hookEntityReach(original);

        return original;
    }
}
