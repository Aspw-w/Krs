package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.AntiBlind;
import com.instrumentalist.krs.hacks.features.render.CameraNoClip;
import com.instrumentalist.krs.hacks.features.movement.LongJump;
import com.instrumentalist.krs.hacks.features.player.Freecam;
import com.instrumentalist.krs.hacks.features.render.Freelook;
import com.instrumentalist.krs.hacks.features.render.WidelyPutin;
import com.instrumentalist.krs.hacks.features.render.Zoom;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class CameraMixin implements IMinecraft {
    @Shadow
    private boolean detached;

    @Shadow
    private Entity entity;

    @Shadow
    public abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void move(float forwards, float up, float right);

    @Shadow
    private float getMaxZoom(float cameraDist) {
        return cameraDist;
    }

    @ModifyVariable(at = @At("HEAD"), method = "getMaxZoom(F)F", argsOnly = true, name = "cameraDist")
    private float cameraDistanceHook(float cameraDist) {
        return ModuleManager.getModuleState(CameraNoClip.class) ? CameraNoClip.distance.get() + cameraDist : cameraDist;
    }

    @Inject(method = "getMaxZoom(F)F", at = @At(value = "HEAD"), cancellable = true)
    public void cameraNoClipHook(float cameraDist, CallbackInfoReturnable<Float> ci) {
        if (ModuleManager.getModuleState(CameraNoClip.class) || ModuleManager.getModuleState(Freecam.class) && Freecam.getCanFly())
            ci.setReturnValue(cameraDist);
    }

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
                    shift = At.Shift.AFTER))
    private void freecamCameraHook(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!ModuleManager.getModuleState(Freecam.class) || !Freecam.getCanFly())
            return;

        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        detached = true;
        setPosition(Freecam.getCamPos(partialTicks));
        setRotation(Freecam.getCamYaw(), Freecam.getCamPitch());
        applyFreecamThirdPersonPull();
    }

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
                    shift = At.Shift.AFTER))
    private void freelookCameraHook(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!ModuleManager.getModuleState(Freelook.class) || !Freelook.isActive())
            return;

        if (entity == null)
            return;

        detached = true;

        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        Vec3 eyePosition = entity.getEyePosition(partialTicks);
        Vec3 direction = Freelook.getCamDirection();
        setPosition(eyePosition.subtract(direction.scale(Freelook.distance.get())));
        setRotation(Freelook.getCamYaw(), Freelook.getCamPitch());
    }

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
                    shift = At.Shift.AFTER))
    private void longJumpSilentCameraHook(DeltaTracker deltaTracker, CallbackInfo ci) {
        LongJump longJump = ModuleManager.getModule(LongJump.class);
        if (!ModuleManager.getModuleState(Freecam.class) && longJump != null && longJump.shouldUseMatrix2SilentCamera())
            setPosition(longJump.getMatrix2SilentCameraPosition());
    }

    @Unique
    private void applyFreecamThirdPersonPull() {
        CameraType cameraType = mc.options.getCameraType();
        if (cameraType.isFirstPerson())
            return;

        if (cameraType.isMirrored())
            setRotation(Freecam.getCamYaw() + 180.0F, -Freecam.getCamPitch());

        float distance = 4.0F;
        float scale = 1.0F;

        if (entity instanceof LivingEntity livingEntity) {
            scale = livingEntity.getScale();
            distance = (float) livingEntity.getAttributeValue(Attributes.CAMERA_DISTANCE);
        }

        float finalDistance = scale * distance;
        if (entity != null && entity.isPassenger() && entity.getVehicle() instanceof LivingEntity vehicle) {
            finalDistance = Math.max(
                    finalDistance,
                    vehicle.getScale() * (float) vehicle.getAttributeValue(Attributes.CAMERA_DISTANCE)
            );
        }

        move(-getMaxZoom(finalDistance), 0.0F, 0.0F);
    }

    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float zoomHook(float original) {
        return Zoom.zoomFovHook(original);
    }

    @ModifyArgs(method = "setupPerspective", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Projection;setupPerspective(FFFFF)V"))
    private void hookWide(Args args) {
        if (ModuleManager.getModuleState(WidelyPutin.class)) {
            args.set(3, (float) args.get(3) / 2);
        }
    }

    @Inject(at = @At("HEAD"), method = "getFluidInCamera()Lnet/minecraft/world/level/material/FogType;", cancellable = true)
    private void antiBlindHook(CallbackInfoReturnable<FogType> ci) {
        if (ModuleManager.getModuleState(AntiBlind.class) && AntiBlind.camera.get())
            ci.setReturnValue(FogType.NONE);
    }
}
