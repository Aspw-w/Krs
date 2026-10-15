package com.instrumentalist.mixin.injector;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.HandAnimation;
import com.instrumentalist.mixin.oringo.IEntityRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin extends HumanoidModel<AvatarRenderState> {

    protected PlayerModelMixin(ModelPart root) { super(root); }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("TAIL"))
    private void krs$applyNotchPose(AvatarRenderState state, CallbackInfo ci) {
        if (!((IEntityRenderState) state).client$shouldApplyLocalAnim()) return;
        if (!ModuleManager.getModuleState(HandAnimation.class)) return;

        float walkCycle = state.walkAnimationPos * 0.6662f;
        float walkStrength = state.walkAnimationSpeed / Math.max(state.speedValue, 1.0E-4f);
        float extra = HandAnimation.walkAnimationMultiplier.get() - 1.0f;
        float spread = Math.min(1.0f, Math.abs(walkStrength)) * HandAnimation.shoulderSpread.get();

        this.rightArm.xRot += Mth.cos(walkCycle + Mth.PI) * walkStrength * extra;
        this.leftArm.xRot  += Mth.cos(walkCycle)          * walkStrength * extra;

        this.rightArm.zRot -= spread;
        this.leftArm.zRot  += spread;

        if (HandAnimation.figureEight.get()) {
            float sweep = HandAnimation.shoulderSweep.get();
            this.rightArm.yRot -= Mth.sin(walkCycle + Mth.PI) * walkStrength * sweep;
            this.leftArm.yRot  += Mth.sin(walkCycle)          * walkStrength * sweep;
        }
    }
}