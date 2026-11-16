package com.instrumentalist.mixin.injector;

import com.instrumentalist.mixin.oringo.ITimerWalkAnimation;
import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WalkAnimationState.class)
public abstract class WalkAnimationStateMixin implements ITimerWalkAnimation {
    @Shadow
    private float speedOld;

    @Shadow
    private float speed;

    @Shadow
    public float position;

    @Shadow
    private float positionScale;

    @Unique
    private float krs$positionStep = 1f;

    @Inject(method = "update", at = @At("HEAD"))
    private void krs$resetPositionStep(float targetSpeed, float factor, float positionScale, CallbackInfo ci) {
        this.krs$positionStep = 1f;
    }

    @Inject(method = "position(F)F", at = @At("HEAD"), cancellable = true)
    private void krs$scaledPosition(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (this.krs$positionStep == 1f) return;

        cir.setReturnValue((this.position - this.speed * this.krs$positionStep * (1f - partialTicks)) * this.positionScale);
    }

    @Override
    public void krs$updateScaled(float targetSpeed, float factor, float positionScale, float step) {
        this.krs$positionStep = step;
        this.speedOld = this.speed;
        this.speed = this.speed + (targetSpeed - this.speed) * factor;
        this.position = this.position + this.speed * step;
        this.positionScale = positionScale;
    }
}
