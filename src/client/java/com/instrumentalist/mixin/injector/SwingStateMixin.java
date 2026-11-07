package com.instrumentalist.mixin.injector;

import com.instrumentalist.mixin.oringo.ITimerScaledTick;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.SwingState.class)
public abstract class SwingStateMixin implements ITimerScaledTick {
    @Shadow
    private LivingEntity.SwingDescription currentSwing;

    @Shadow
    private int ticks;

    @Shadow
    private float oldAnimation;

    @Shadow
    private float animation;

    @Unique
    private float krs$preciseTicks;

    @Unique
    private boolean krs$scaled;

    @Shadow
    private void start(InteractionHand hand, SwingAnimation animation, int durationTicks) {
        throw new AssertionError();
    }

    @Inject(method = "start", at = @At("TAIL"))
    private void krs$resetPreciseTicks(InteractionHand hand, SwingAnimation animation, int durationTicks, CallbackInfo ci) {
        this.krs$preciseTicks = 0f;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void krs$clearScaled(CallbackInfo ci) {
        this.krs$scaled = false;
    }

    @Inject(method = "startIfAble", at = @At("HEAD"), cancellable = true)
    private void krs$scaledStartIfAble(InteractionHand hand, SwingAnimation animation, int durationTicks, CallbackInfoReturnable<Boolean> cir) {
        if (!this.krs$scaled || (int) this.krs$preciseTicks != this.ticks) return;

        if (this.currentSwing != null && this.krs$preciseTicks <= this.currentSwing.durationTicks() / 2 && this.krs$preciseTicks > 0f) {
            cir.setReturnValue(false);
            return;
        }

        this.start(hand, animation, durationTicks);
        cir.setReturnValue(true);
    }

    @Override
    public void krs$tickScaled(float step) {
        this.krs$scaled = true;

        if ((int) this.krs$preciseTicks != this.ticks)
            this.krs$preciseTicks = this.ticks;

        this.oldAnimation = this.animation;

        if (this.currentSwing == null) {
            this.animation = 0f;
            return;
        }

        int duration = this.currentSwing.durationTicks();
        if (duration > 0)
            this.animation = Math.min(this.krs$preciseTicks / duration, 1f);

        boolean finished = this.krs$preciseTicks >= duration + 1;
        this.krs$preciseTicks += step;
        this.ticks = (int) this.krs$preciseTicks;

        if (finished) {
            this.currentSwing = null;
            this.animation = 0f;
        }
    }
}
