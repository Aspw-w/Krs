package com.instrumentalist.mixin.injector;



import com.instrumentalist.krs.hacks.features.combat.TargetStrafe;
import com.instrumentalist.krs.hacks.features.level.ItemDropChanger;
import com.instrumentalist.krs.hacks.features.movement.ElytraFly;
import com.instrumentalist.krs.hacks.features.movement.MovementFix;
import com.instrumentalist.krs.hacks.features.level.AlwaysRiptide;
import com.instrumentalist.krs.hacks.features.movement.Sprint;
import com.instrumentalist.krs.hacks.features.movement.Step;
import com.instrumentalist.krs.hacks.features.render.ViewModel;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.render.OldHittingRenderGuard;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends EntityMixin {

    @Shadow protected abstract float getJumpPower();

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
