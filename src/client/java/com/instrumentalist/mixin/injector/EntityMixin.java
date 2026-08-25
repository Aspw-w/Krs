package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.combat.TargetStrafe;
import com.instrumentalist.krs.hacks.features.movement.EntityControl;
import com.instrumentalist.krs.hacks.features.movement.MovementFix;
import com.instrumentalist.krs.hacks.features.movement.NoPush;
import com.instrumentalist.krs.hacks.features.level.AlwaysRiptide;
import com.instrumentalist.krs.hacks.features.movement.Step;
import com.instrumentalist.krs.hacks.features.player.Phase;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Entity.class)
public abstract class EntityMixin implements IMinecraft {

    @Shadow
    public abstract float getYRot();

    @Shadow
    public abstract void setDeltaMovement(Vec3 velocity);

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Shadow
    protected static Vec3 getInputVector(Vec3 movementInput, float speed, float yaw) {
        return null;
    }

    @Shadow public abstract boolean isSprinting();

    @Shadow public abstract void addDeltaMovement(Vec3 velocity);

    @Shadow public boolean hurtMarked;

    @Shadow public abstract void setDeltaMovement(double x, double y, double z);

    @ModifyReturnValue(method = "isSwimming", at = @At("RETURN"))
    public boolean freeCamHook3(boolean original) {
        if ((Object) this instanceof LocalPlayer) {
            if (ModuleManager.getModuleState(Phase.class))
                return false;
        }

        return original;
    }

    @ModifyReturnValue(method = "getEyeHeight()F", at = @At("RETURN"))
    public float freeCamHook4(float original) {
        if ((Object) this instanceof LocalPlayer) {
            if (!mc.player.isShiftKeyDown() && ModuleManager.getModuleState(Phase.class))
                return 1.62f;
        }

        return original;
    }

    @ModifyReturnValue(method = "getDimensions", at = @At("RETURN"))
    private EntityDimensions riptideDimensionsHook(EntityDimensions original, Pose pose) {
        return AlwaysRiptide.hookDimensions(original, (Entity) (Object) this, pose);
    }

    @Inject(method = "moveRelative", at = @At("HEAD"), cancellable = true)
    public void updateVelocity(float speed, Vec3 movementInput, CallbackInfo ci) {
        ci.cancel();

        float strafe = this.getYRot();

        if ((Object) this instanceof LocalPlayer) {
            if (MovementFix.shouldFixMovement())
                strafe = MovementFix.getMovementYaw();
            else if (TargetStrafe.targetStrafeHook())
                strafe = MovementUtil.getPlayerDirection();
        }

        Vec3 vec3d = getInputVector(movementInput, speed, strafe);
        this.setDeltaMovement(this.getDeltaMovement().add(vec3d));
    }

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 targetStrafeCollisionGuard(Vec3 movement) {
        if ((Object) this instanceof LocalPlayer && TargetStrafe.targetStrafeHook())
            return TargetStrafe.preventHorizontalCollision(movement);

        return movement;
    }

    @ModifyReturnValue(method = "collide", at = @At("RETURN"))
    private Vec3 steppingHook(Vec3 original) {
        if (((Object) this instanceof LocalPlayer)) {
            if (ModuleManager.getModuleState(Step.class) && original.y > 0.6)
                Step.steppingFunctions();
        }

        return original;
    }

    @ModifyReturnValue(method = "getControllingPassenger", at = @At("RETURN"))
    private LivingEntity entityControlPassengerHook(LivingEntity original) {
        return EntityControl.hookControllingPassenger(original, (Entity) (Object) this);
    }

    @ModifyArgs(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;push(DDD)V"))
    private void hookNoPush(Args args, Entity entity) {
        if ((Object) this instanceof LocalPlayer) {
            if (ModuleManager.getModuleState(NoPush.class) || ModuleManager.getModuleState(Phase.class)) {
                args.set(0, 0.0);
                args.set(2, 0.0);
            }
        }
    }
}
