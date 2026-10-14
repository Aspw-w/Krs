package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.OldHitting;
import com.instrumentalist.krs.utils.render.GuiEntityRenderGuard;
import com.instrumentalist.mixin.oringo.IEntityRenderState;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class BipedEntityModelMixin<T extends HumanoidRenderState> extends EntityModel<T> {

    @Shadow @Final public ModelPart head;

    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;

    @Shadow
    private void poseRightArm(T state) {
        throw new AssertionError();
    }

    @Shadow
    private void poseLeftArm(T state) {
        throw new AssertionError();
    }

    @Shadow protected abstract void setupAttackAnimation(T state);

    @Shadow
    private float quadraticArmUpdate(float f) {
        throw new AssertionError();
    }

    @Shadow @Final public ModelPart body;

    protected BipedEntityModelMixin(ModelPart root) {
        super(root);
    }

    @Unique
    private void customArmState(T bipedEntityRenderState, float g, float h) {
        if (OldHitting.shouldBlock()) {
            if (bipedEntityRenderState.mainArm == HumanoidArm.RIGHT) {
                OldHitting.thirdPersonAnimation(this.rightArm, bipedEntityRenderState.mainArm);
                this.leftArm.xRot = Mth.cos(g * 0.6662F) * 2.0F * h * 0.5F / bipedEntityRenderState.speedValue;
            } else {
                OldHitting.thirdPersonAnimation(this.leftArm, bipedEntityRenderState.mainArm);
                this.rightArm.xRot = Mth.cos(g * 0.6662F + 3.1415927F) * 2.0F * h * 0.5F / bipedEntityRenderState.speedValue;
            }
        } else {
            this.rightArm.xRot = Mth.cos(g * 0.6662F + 3.1415927F) * 2.0F * h * 0.5F / bipedEntityRenderState.speedValue;
            this.leftArm.xRot = Mth.cos(g * 0.6662F) * 2.0F * h * 0.5F / bipedEntityRenderState.speedValue;
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/model/geom/ModelPart;zRot:F", ordinal = 1, shift = At.Shift.AFTER), cancellable = true)
    private void setAngles(T bipedEntityRenderState, CallbackInfo ci) {
        if (GuiEntityRenderGuard.isActive())
            return;

        if (((IEntityRenderState) bipedEntityRenderState).client$getEntity() instanceof LocalPlayer) {
            ci.cancel();

            HumanoidModel.ArmPose armPose = bipedEntityRenderState.leftArmPose;
            HumanoidModel.ArmPose armPose2 = bipedEntityRenderState.rightArmPose;
            float f = bipedEntityRenderState.swimAmount;
            boolean bl = bipedEntityRenderState.isFallFlying;

            this.head.xRot = bipedEntityRenderState.xRot * 0.017453292F;
            this.head.yRot = bipedEntityRenderState.yRot * 0.017453292F;

            if (bl) {
                this.head.xRot = -0.7853982F;
            } else if (f > 0.0F) {
                this.head.xRot = Mth.rotLerpRad(f, this.head.xRot, -0.7853982F);
            }

            float g = bipedEntityRenderState.walkAnimationPos;
            float h = bipedEntityRenderState.walkAnimationSpeed;

            customArmState(bipedEntityRenderState, g, h);

            this.rightLeg.xRot = Mth.cos(g * 0.6662F) * 1.4F * h / bipedEntityRenderState.speedValue;
            this.leftLeg.xRot = Mth.cos(g * 0.6662F + 3.1415927F) * 1.4F * h / bipedEntityRenderState.speedValue;
            this.rightLeg.yRot = 0.005F;
            this.leftLeg.yRot = -0.005F;
            this.rightLeg.zRot = 0.005F;
            this.leftLeg.zRot = -0.005F;
            ModelPart var10000;
            if (bipedEntityRenderState.isPassenger) {
                var10000 = this.rightArm;
                var10000.xRot += -0.62831855F;
                var10000 = this.leftArm;
                var10000.xRot += -0.62831855F;
                this.rightLeg.xRot = -1.4137167F;
                this.rightLeg.yRot = 0.31415927F;
                this.rightLeg.zRot = 0.07853982F;
                this.leftLeg.xRot = -1.4137167F;
                this.leftLeg.yRot = -0.31415927F;
                this.leftLeg.zRot = -0.07853982F;
            }

            if (!OldHitting.shouldBlock()) {
                boolean bl2 = bipedEntityRenderState.mainArm == HumanoidArm.RIGHT;
                boolean bl3;
                if (bipedEntityRenderState.isUsingItem) {
                    bl3 = bipedEntityRenderState.useItemHand == InteractionHand.MAIN_HAND;
                    if (bl3 == bl2) {
                        this.poseRightArm(bipedEntityRenderState);
                    } else {
                        this.poseLeftArm(bipedEntityRenderState);
                    }
                } else {
                    bl3 = bl2 ? armPose.isTwoHanded() : armPose2.isTwoHanded();
                    if (bl2 != bl3) {
                        this.poseLeftArm(bipedEntityRenderState);
                        this.poseRightArm(bipedEntityRenderState);
                    } else {
                        this.poseRightArm(bipedEntityRenderState);
                        this.poseLeftArm(bipedEntityRenderState);
                    }
                }
            }

            this.setupAttackAnimation(bipedEntityRenderState);
            if (bipedEntityRenderState.isCrouching) {
                this.body.xRot = 0.5F;
                var10000 = this.rightArm;
                var10000.xRot += 0.4F;
                var10000 = this.leftArm;
                var10000.xRot += 0.4F;
                var10000 = this.rightLeg;
                var10000.z += 4.0F;
                var10000 = this.leftLeg;
                var10000.z += 4.0F;
                var10000 = this.head;
                var10000.y += 4.2F;
                var10000 = this.body;
                var10000.y += 3.2F;
                var10000 = this.leftArm;
                var10000.y += 3.2F;
                var10000 = this.rightArm;
                var10000.y += 3.2F;
            }

            if (armPose2 != HumanoidModel.ArmPose.SPYGLASS) {
                AnimationUtils.bobModelPart(this.rightArm, bipedEntityRenderState.ageInTicks, 1.0F);
            }

            if (armPose != HumanoidModel.ArmPose.SPYGLASS) {
                AnimationUtils.bobModelPart(this.leftArm, bipedEntityRenderState.ageInTicks, -1.0F);
            }

            if (f > 0.0F) {
                float i = g % 26.0F;
                HumanoidArm arm = bipedEntityRenderState.currentSwing != null
                        ? bipedEntityRenderState.currentSwing.hand().asArm(bipedEntityRenderState.mainArm)
                        : null;
                float j = arm == HumanoidArm.RIGHT && bipedEntityRenderState.swingAnimation > 0.0F ? 0.0F : f;
                float k = arm == HumanoidArm.LEFT && bipedEntityRenderState.swingAnimation > 0.0F ? 0.0F : f;
                float l;
                if (!bipedEntityRenderState.isUsingItem) {
                    if (i < 14.0F) {
                        this.leftArm.xRot = Mth.rotLerpRad(k, this.leftArm.xRot, 0.0F);
                        this.rightArm.xRot = Mth.lerp(j, this.rightArm.xRot, 0.0F);
                        this.leftArm.yRot = Mth.rotLerpRad(k, this.leftArm.yRot, 3.1415927F);
                        this.rightArm.yRot = Mth.lerp(j, this.rightArm.yRot, 3.1415927F);
                        this.leftArm.zRot = Mth.rotLerpRad(k, this.leftArm.zRot, 3.1415927F + 1.8707964F * this.quadraticArmUpdate(i) / this.quadraticArmUpdate(14.0F));
                        this.rightArm.zRot = Mth.lerp(j, this.rightArm.zRot, 3.1415927F - 1.8707964F * this.quadraticArmUpdate(i) / this.quadraticArmUpdate(14.0F));
                    } else if (i >= 14.0F && i < 22.0F) {
                        l = (i - 14.0F) / 8.0F;
                        this.leftArm.xRot = Mth.rotLerpRad(k, this.leftArm.xRot, 1.5707964F * l);
                        this.rightArm.xRot = Mth.lerp(j, this.rightArm.xRot, 1.5707964F * l);
                        this.leftArm.yRot = Mth.rotLerpRad(k, this.leftArm.yRot, 3.1415927F);
                        this.rightArm.yRot = Mth.lerp(j, this.rightArm.yRot, 3.1415927F);
                        this.leftArm.zRot = Mth.rotLerpRad(k, this.leftArm.zRot, 5.012389F - 1.8707964F * l);
                        this.rightArm.zRot = Mth.lerp(j, this.rightArm.zRot, 1.2707963F + 1.8707964F * l);
                    } else if (i >= 22.0F && i < 26.0F) {
                        l = (i - 22.0F) / 4.0F;
                        this.leftArm.xRot = Mth.rotLerpRad(k, this.leftArm.xRot, 1.5707964F - 1.5707964F * l);
                        this.rightArm.xRot = Mth.lerp(j, this.rightArm.xRot, 1.5707964F - 1.5707964F * l);
                        this.leftArm.yRot = Mth.rotLerpRad(k, this.leftArm.yRot, 3.1415927F);
                        this.rightArm.yRot = Mth.lerp(j, this.rightArm.yRot, 3.1415927F);
                        this.leftArm.zRot = Mth.rotLerpRad(k, this.leftArm.zRot, 3.1415927F);
                        this.rightArm.zRot = Mth.lerp(j, this.rightArm.zRot, 3.1415927F);
                    }
                }

                l = 0.3F;
                float m = 0.33333334F;
                this.leftLeg.xRot = Mth.lerp(f, this.leftLeg.xRot, 0.3F * Mth.cos(g * 0.33333334F + 3.1415927F));
                this.rightLeg.xRot = Mth.lerp(f, this.rightLeg.xRot, 0.3F * Mth.cos(g * 0.33333334F));
            }
        }
    }
}
