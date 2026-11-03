package com.instrumentalist.mixin.injector;



import com.instrumentalist.krs.hacks.features.combat.TargetStrafe;
import com.instrumentalist.krs.hacks.features.level.ItemDropChanger;
import com.instrumentalist.krs.hacks.features.movement.ElytraFly;
import com.instrumentalist.krs.hacks.features.movement.MovementFix;
import com.instrumentalist.krs.hacks.features.level.NoBlockEffects;
import com.instrumentalist.krs.hacks.features.level.AlwaysRiptide;
import com.instrumentalist.krs.hacks.features.movement.Sprint;
import com.instrumentalist.krs.hacks.features.movement.Step;
import com.instrumentalist.krs.hacks.features.render.ViewModel;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.render.OldHittingRenderGuard;
import com.instrumentalist.krs.hacks.features.dev.LegacyRenderer;
import com.instrumentalist.mixin.oringo.ILegacySwing;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends EntityMixin implements ILegacySwing {

    @Unique
    private int krs$swingProgressInt;

    @Unique
    private boolean krs$isSwingInProgress;

    @Unique
    private float krs$swingProgress;

    @Unique
    private float krs$prevSwingProgress;

    @Unique
    private boolean krs$pendingSwing;

    @Override
    public float krs$getLegacySwing(float partialTicks) {
        float f = this.krs$swingProgress - this.krs$prevSwingProgress;

        if (f < 0.0F) ++f;

        return this.krs$prevSwingProgress + f * partialTicks;
    }

    @Override
    public boolean krs$isLegacySwinging() {
        return this.krs$swingProgress > 0.0F;
    }

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("HEAD"))
    private void legacyRendererSwingHook(InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide() || LegacyRenderer.suppressSwing) return;

        if (self instanceof LocalPlayer) {
            this.krs$pendingSwing = true;
            return;
        }

        krs$startLegacySwing(self);
    }

    @Unique
    private void krs$startLegacySwing(LivingEntity self) {
        if (!this.krs$isSwingInProgress || this.krs$swingProgressInt >= LegacyRenderer.armSwingAnimationEnd(self) / 2 || this.krs$swingProgressInt < 0) {
            this.krs$swingProgressInt = -1;
            this.krs$isSwingInProgress = true;
        }
    }

    @Inject(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity$SwingState;tick()V", shift = At.Shift.AFTER))
    private void legacyRendererSwingTickHook(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide()) return;

        this.krs$prevSwingProgress = this.krs$swingProgress;
        int i = LegacyRenderer.armSwingAnimationEnd(self);

        if (this.krs$isSwingInProgress) {
            ++this.krs$swingProgressInt;

            if (this.krs$swingProgressInt >= i) {
                this.krs$swingProgressInt = 0;
                this.krs$isSwingInProgress = false;
            }
        } else {
            this.krs$swingProgressInt = 0;
        }

        this.krs$swingProgress = (float) this.krs$swingProgressInt / (float) i;

        if (this.krs$pendingSwing) {
            this.krs$pendingSwing = false;
            krs$startLegacySwing(self);
        }
    }

    @Inject(method = "tickHeadTurn", at = @At("HEAD"), cancellable = true)
    private void legacyRendererHeadTurnHook(float yBodyRotT, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player) || !self.level().isClientSide() || !LegacyRenderer.isPlayerAnimationsActive()) return;

        ci.cancel();
        LegacyRenderer.updateDistance(self, this.krs$isLegacySwinging());
    }

    @Shadow protected abstract float getJumpPower();

    @ModifyExpressionValue(method = "travelInAir", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float noBlockEffectsFrictionHook(float original) {
        return NoBlockEffects.shouldIgnore(this) ? NoBlockEffects.normalFriction() : original;
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    public void jumpEvent(CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer) {
            ci.cancel();

            float f = this.getJumpPower();
            if (!(f <= 1.0E-5F)) {
                Vec3 vec3d = this.getDeltaMovement();
                this.setDeltaMovement(vec3d.x, Math.max((double) f, vec3d.y), vec3d.z);
                if (this.isSprinting()) {
                    float directionYaw = this.getYRot();

                    if (MovementFix.shouldFixMovement())
                        directionYaw = MovementFix.getJumpMovementYaw();
                    else if (TargetStrafe.targetStrafeHook())
                        directionYaw = MovementUtil.getPlayerDirection();

                    directionYaw = Sprint.getOmnidirectionalJumpYaw(directionYaw);

                    float g = directionYaw * ((float) Math.PI / 180F);
                    this.addDeltaMovement(new Vec3((double) (-Mth.sin(g)) * 0.2, (double) 0.0F, (double) Mth.cos(g) * 0.2));
                }

                this.needsSync = true;
            }
        }
    }

    @ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 movementFixFallFlyingLookVector(Vec3 original) {
        if ((Object) this instanceof LocalPlayer && MovementFix.shouldFixMovement())
            return MovementFix.getMovementLookVector();

        return original;
    }

    @ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
    private float movementFixFallFlyingPitch(float original) {
        if ((Object) this instanceof LocalPlayer && MovementFix.shouldFixMovement())
            return MovementFix.getMovementPitch();

        return original;
    }

    @ModifyReturnValue(method = "updateFallFlyingMovement", at = @At("RETURN"))
    private Vec3 elytraFlyMovementHook(Vec3 original) {
        return ElytraFly.hookFallFlyingMovement(original, (LivingEntity) (Object) this);
    }

    // Since 26.3 the swing duration is computed once when a swing starts, so this also drives the swing ticking.
    @ModifyReturnValue(method = "getModifiedSwingDuration", at = @At("RETURN"))
    private int swingSpeedModifier1(int original) {
        return ViewModel.hookSwingSpeed(original, (LivingEntity) (Object) this);
    }

    @ModifyReturnValue(method = "isAutoSpinAttack", at = @At("RETURN"))
    private boolean riptideSpinAttackHook(boolean original) {
        return original || AlwaysRiptide.shouldForceSpinAttack((LivingEntity) (Object) this);
    }

    @ModifyReturnValue(method = "isUsingItem", at = @At("RETURN"))
    private boolean krs$oldHittingRenderUseState(boolean original) {
        return original || OldHittingRenderGuard.shouldSpoofUseState((LivingEntity) (Object) this);
    }

    @ModifyReturnValue(method = "isBlocking", at = @At("RETURN"))
    private boolean krs$oldHittingRenderBlockState(boolean original) {
        return original || OldHittingRenderGuard.shouldSpoofUseState((LivingEntity) (Object) this);
    }

    @ModifyReturnValue(method = "getUsedItemHand", at = @At("RETURN"))
    private InteractionHand krs$oldHittingRenderUseHand(InteractionHand original) {
        if (OldHittingRenderGuard.shouldSpoofUseState((LivingEntity) (Object) this))
            return InteractionHand.MAIN_HAND;

        return original;
    }

    @ModifyReturnValue(method = "getUseItem", at = @At("RETURN"))
    private ItemStack krs$oldHittingRenderUseItem(ItemStack original) {
        if (OldHittingRenderGuard.shouldSpoofUseState((LivingEntity) (Object) this))
            return ((LivingEntity) (Object) this).getMainHandItem();

        return original;
    }

    @WrapOperation(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z"))
    private boolean dropItemHook(LivingEntity instance, InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity, Operation<Boolean> original) {
        if (!(instance instanceof LocalPlayer) || ItemDropChanger.hookDropItemSwing(hand))
            return original.call(instance, hand, animation, sendToSwingingEntity);

        return false;
    }

    @ModifyReturnValue(method = "maxUpStep", at = @At("RETURN"))
    private float stepHook(float original) {
        return Step.hookStepHeight(original, (LivingEntity) (Object) this);
    }
}
