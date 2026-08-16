package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.FreeLook;
import com.instrumentalist.krs.hacks.features.render.OldHitting;
import com.instrumentalist.krs.hacks.features.render.ViewModel;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;
import org.joml.Quaternionf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin implements IMinecraft {

    @Shadow public abstract void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext renderMode, PoseStack matrices, SubmitNodeCollector submitter, int light);

    @Shadow private float mainHandHeight;

    @Shadow private ItemStack mainHandItem;

    @Shadow private float oMainHandHeight;

    @Shadow @Final private Minecraft minecraft;

    @Shadow @Final private EntityRenderDispatcher entityRenderDispatcher;

    @Shadow private float offHandHeight;

    @Shadow private ItemStack offHandItem;

    @Shadow protected abstract boolean shouldInstantlyReplaceVisibleItem(ItemStack from, ItemStack to);

    @Shadow private float oOffHandHeight;

    @Unique
    private void applyFirstPersonItem(PoseStack matrices, HumanoidArm arm, float equipProgress, float swingProgress) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        float realEquipProgress = equipProgress;
        float sizeModifier = getItemModelScale();

        if (ModuleManager.getModuleState(ViewModel.class) && ViewModel.noEquipAnimation.get())
            realEquipProgress = 0f;

        matrices.translate(direction * 0.56F, -0.52F, -0.71999997F);
        matrices.translate(0.0F, realEquipProgress * -0.6F, 0.0F);
        this.applyCustomItemPosition(matrices, arm);
        if (sizeModifier != 1.0F)
            matrices.scale(sizeModifier, sizeModifier, sizeModifier);
        matrices.translate(direction * -0.1F, 0.08F, 0.0F);
        matrices.mulPose(Axis.YP.rotationDegrees(direction * 45.0F));
        float swingYaw = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        float swing = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
        matrices.mulPose(Axis.YP.rotationDegrees(direction * swingYaw * -20.0F));
        matrices.mulPose(Axis.ZP.rotationDegrees(direction * swing * -20.0F));
        matrices.mulPose(Axis.XP.rotationDegrees(swing * -80.0F));
        matrices.scale(0.4F, 0.4F, 0.4F);
    }

    @Unique
    private void applyVanillaBlockTransformation(PoseStack matrices, HumanoidArm arm) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        matrices.translate(direction * -0.15F, 0.16F, 0.15F);
        matrices.mulPose(Axis.YP.rotationDegrees(direction * -18.0F));
        matrices.mulPose(Axis.ZP.rotationDegrees(direction * 82.0F));
        matrices.mulPose(Axis.YP.rotationDegrees(direction * 112.0F));
    }

    @Unique
    private void applyBlockTransformation(PoseStack matrices, HumanoidArm arm) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        matrices.translate(direction * -0.5F, 0.2F, 0.0F);
        matrices.mulPose(Axis.YP.rotationDegrees(direction * 30.0F));
        matrices.mulPose(Axis.XP.rotationDegrees(-80.0F));
        matrices.mulPose(Axis.YP.rotationDegrees(direction * 60.0F));
    }

    @Unique
    private void applyCrazyBlockTransformation(PoseStack matrices, HumanoidArm arm) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        matrices.translate(0.0F, -0.25F, -0.04F);

        this.applyBlockTransformation(matrices, arm);

        matrices.translate(direction * -0.55F, 0.2F, 0.1F);
        matrices.scale(0.85F, 0.85F, 0.85F);
        this.applyMirroredAxisRotation(matrices, arm, 1.0F, 0.0F, 0.0F, -1.0F);
        this.applyMirroredAxisRotation(matrices, arm, 1.0F, 0.25F, 0.0F, 0.0F);
        this.applyMirroredAxisRotation(matrices, arm, 2.0F, 0.0F, 2.0F, 0.0F);
    }

    @Unique
    private void applyMirroredAxisRotation(PoseStack matrices, HumanoidArm arm, float degrees, float x, float y, float z) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        this.applyAxisRotation(matrices, degrees, x, direction * y, direction * z);
    }

    @Unique
    private void applyAxisRotation(PoseStack matrices, float degrees, float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length == 0.0F)
            return;

        matrices.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(degrees), x / length, y / length, z / length));
    }

    @Unique
    private void applyFirstPersonDisplayTransform(PoseStack matrices, HumanoidArm arm) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        matrices.translate(0.0F, 4.0F / 16.0F, 2.0F / 16.0F);
        matrices.mulPose(Axis.YP.rotationDegrees(direction * -135.0F));
        matrices.mulPose(Axis.ZP.rotationDegrees(direction * 25.0F));
        matrices.scale(2.2F, 2.2F, 2.2F);
        matrices.mulPose(new Quaternionf().rotationXYZ(
                0.0F,
                (float) Math.toRadians(direction * -90.0F),
                (float) Math.toRadians(direction * 25.0F)
        ).invert());
        matrices.translate(direction * -1.13F / 16.0F, -3.2F / 16.0F, -1.13F / 16.0F);
    }

    @Unique
    private void applySwingOffset(PoseStack matrices, HumanoidArm arm, float swingProgress, CallbackInfo ci) {
        if (ci != null)
            ci.cancel();

        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        float f = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        matrices.mulPose(Axis.YP.rotationDegrees((float) i * (45.0F + f * -20.0F)));
        float g = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
        matrices.mulPose(Axis.ZP.rotationDegrees((float) i * g * -20.0F));
        matrices.mulPose(Axis.XP.rotationDegrees(g * -80.0F));
        matrices.mulPose(Axis.YP.rotationDegrees((float) i * -45.0F));
    }

    @Unique
    private void applySwordAnimation(PoseStack matrices, HumanoidArm arm, float equipProgress, float swingProgress) {
        float swing = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);

        switch (OldHitting.mode.get().toLowerCase(Locale.ROOT)) {
            case "dash" -> {
                this.applyFirstPersonItem(matrices, arm, equipProgress / 1.7F, 0.0F);

                this.applyMirroredAxisRotation(matrices, arm, -swing * 22.0F, swing / 2.0F, 0.0F, 9.0F);
                this.applyMirroredAxisRotation(matrices, arm, -swing * 50.0F, 0.8F, swing / 2.0F, 0.0F);

                this.applyBlockTransformation(matrices, arm);
            }

            case "push" -> {
                this.applyFirstPersonItem(matrices, arm, equipProgress / 2.0F, 0.0F);

                this.applyMirroredAxisRotation(matrices, arm, -swing * 40.0F / 2.0F, swing / 2.0F, 1.0F, 4.0F);
                this.applyMirroredAxisRotation(matrices, arm, -swing * 30.0F, 1.0F, swing / 3.0F, 0.0F);

                this.applyBlockTransformation(matrices, arm);
            }

            case "swang" -> {
                this.applyFirstPersonItem(matrices, arm, equipProgress / 1.7F, 0.0F);

                this.applyMirroredAxisRotation(matrices, arm, -swing * 74.0F / 2.0F, swing / 2.0F, 1.0F, 4.0F);
                this.applyMirroredAxisRotation(matrices, arm, -swing * 52.0F, 1.0F, swing / 3.0F, 0.0F);

                this.applyBlockTransformation(matrices, arm);
            }

            case "swonk" -> {
                this.applyFirstPersonItem(matrices, arm, equipProgress / 1.6F, 0.0F);

                this.applyMirroredAxisRotation(matrices, arm, -swing * -30.0F / 2.0F, swing / 2.0F, 1.0F, 4.0F);
                this.applyMirroredAxisRotation(matrices, arm, -swing * 7.5F, 1.0F, swing / 3.0F, 0.0F);

                this.applyBlockTransformation(matrices, arm);
            }
        }
    }

    @Inject(method = "submitArmWithItem", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V", ordinal = 0, shift = At.Shift.AFTER),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V", ordinal = 1, shift = At.Shift.AFTER),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V", ordinal = 2, shift = At.Shift.AFTER)
    })
    private void applyOldEatSwingAfterArmTransform(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector submitter, int light, CallbackInfo ci) {
        this.customApplySwingOffset(player, hand, item, swingProgress, matrices);
    }

    @Inject(method = "applyItemArmTransform", at = @At("HEAD"), cancellable = true)
    private void itemModifierHook(PoseStack matrices, HumanoidArm arm, float equipProgress, CallbackInfo ci) {
        if (this.shouldUseCustomEquipOffset())
            this.customApplyEquipOffset(matrices, arm, equipProgress, ci);
    }

    @Inject(method = "renderPlayerArm", at = @At("HEAD"), cancellable = true)
    private void armModifierHook(PoseStack matrices, SubmitNodeCollector submitter, int light, float equipProgress, float swingProgress, HumanoidArm arm, CallbackInfo ci) {
        if (FreeLook.shouldMoveCamera()) {
            ci.cancel();
            return;
        }

        if (this.shouldUseCustomEquipOffset())
            this.customRenderArmHoldingItem(matrices, submitter, light, equipProgress, swingProgress, arm, ci);
    }

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void fluxSwingHook(float swingProgress, PoseStack matrices, int armX, HumanoidArm arm, CallbackInfo ci) {
        if (ModuleManager.getModuleState(ViewModel.class) && ViewModel.fluxSwing.get())
            this.customFluxSwingArm(swingProgress, matrices, arm, ci);
    }

    @Unique
    private void customFluxSwingArm(float swingProgress, PoseStack matrices, HumanoidArm arm, CallbackInfo ci) {
        if (ci != null)
            ci.cancel();

        this.applySwingOffset(matrices, arm, swingProgress, null);
    }

    @Unique
    private void customSwingArm(float swingProgress, float equipProgress, PoseStack matrices, int armX, HumanoidArm arm, CallbackInfo ci) {
        if (ci != null)
            ci.cancel();

        if (!(ci != null && ModuleManager.getModuleState(ViewModel.class) && ViewModel.fluxSwing.get())) {
            float f = -0.4F * Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
            float g = 0.2F * Mth.sin(Mth.sqrt(swingProgress) * ((float) Math.PI * 2F));
            float h = -0.2F * Mth.sin(swingProgress * (float) Math.PI);
            matrices.translate((float) armX * f, g, h);
        }
        this.customApplyEquipOffset(matrices, arm, equipProgress, null);
        this.applySwingOffset(matrices, arm, swingProgress, null);
    }

    @Unique
    private void customApplyEquipOffset(PoseStack matrices, HumanoidArm arm, float equipProgress, CallbackInfo ci) {
        if (ci != null)
            ci.cancel();

        float realEquipProgress = equipProgress;
        float sizeModifier = getItemModelScale();

        if (ModuleManager.getModuleState(ViewModel.class) && ViewModel.noEquipAnimation.get())
            realEquipProgress = 0f;

        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        matrices.translate((float)i * 0.56F, -0.52F + realEquipProgress * -0.6F, -0.72F);
        this.applyCustomItemPosition(matrices, arm);
        if (sizeModifier != 1.0F)
            matrices.scale(sizeModifier, sizeModifier, sizeModifier);
    }

    @Unique
    private void applyCustomItemPosition(PoseStack matrices, HumanoidArm arm) {
        if (!ModuleManager.getModuleState(ViewModel.class))
            return;

        final int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        final float positionX = ViewModel.itemPositionX.get();
        final float positionY = ViewModel.itemPositionY.get();
        final float positionZ = ViewModel.itemPositionZ.get();

        if (positionX == 0.0F && positionY == 0.0F && positionZ == 0.0F)
            return;

        matrices.translate(direction * positionX, positionY, positionZ);
    }

    @Unique
    private void customRenderArmHoldingItem(PoseStack matrices, SubmitNodeCollector submitter, int light, float equipProgress, float swingProgress, HumanoidArm arm, CallbackInfo ci) {
        if (ci != null)
            ci.cancel();

        float realEquipProgress = equipProgress;

        if (ModuleManager.getModuleState(ViewModel.class) && ViewModel.noEquipAnimation.get())
            realEquipProgress = 0f;

        boolean bl = arm != HumanoidArm.LEFT;
        float f = bl ? 1.0F : -1.0F;
        float g = Mth.sqrt(swingProgress);
        float h = -0.3F * Mth.sin(g * (float)Math.PI);
        float i = 0.4F * Mth.sin(g * ((float)Math.PI * 2F));
        float j = -0.4F * Mth.sin(swingProgress * (float)Math.PI);
        matrices.translate(f * (h + 0.64000005F), i + -0.6F + realEquipProgress * -0.6F, j + -0.71999997F);
        matrices.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        float k = Mth.sin(swingProgress * swingProgress * (float)Math.PI);
        float l = Mth.sin(g * (float)Math.PI);
        matrices.mulPose(Axis.YP.rotationDegrees(f * l * 70.0F));
        matrices.mulPose(Axis.ZP.rotationDegrees(f * k * -20.0F));
        AbstractClientPlayer abstractClientPlayerEntity = this.minecraft.player;
        matrices.translate(f * -1.0F, 3.6F, 3.5F);
        matrices.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        matrices.mulPose(Axis.XP.rotationDegrees(200.0F));
        matrices.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        matrices.translate(f * 5.6F, 0.0F, 0.0F);
        AvatarRenderer playerEntityRenderer = (AvatarRenderer)this.entityRenderDispatcher.getRenderer(abstractClientPlayerEntity);
        Identifier identifier = abstractClientPlayerEntity.getSkin().body().texturePath();
        if (bl) {
            playerEntityRenderer.renderRightHand(matrices, submitter, light, identifier, abstractClientPlayerEntity.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
        } else {
            playerEntityRenderer.renderLeftHand(matrices, submitter, light, identifier, abstractClientPlayerEntity.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
        }

    }

    @Unique
    private void customApplySwingOffset(AbstractClientPlayer player, InteractionHand hand, ItemStack stack, float swingProgress, PoseStack matrices) {
        if (shouldApplyOldEatSwing(player, hand, stack)) {
            final HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
            applySwingOffset(matrices, arm, swingProgress, null);
        }
    }

    @Unique
    private boolean shouldApplyOldEatSwing(AbstractClientPlayer player, InteractionHand hand, ItemStack stack) {
        if (!ModuleManager.getModuleState(ViewModel.class) || !ViewModel.oldEatSwing.get())
            return false;
        if (!player.isUsingItem() || player.getUsedItemHand() != hand)
            return false;

        return isOldEatSwingUseAnimation(stack.getUseAnimation());
    }

    @Unique
    private boolean shouldKeepOldEatSwingItemVisible(LocalPlayer player) {
        return player != null
                && ModuleManager.getModuleState(ViewModel.class)
                && ViewModel.oldEatSwing.get()
                && player.isUsingItem()
                && isOldEatSwingUseAnimation(player.getUseItem().getUseAnimation());
    }

    @Unique
    private boolean isOldEatSwingUseAnimation(ItemUseAnimation animation) {
        return animation != ItemUseAnimation.NONE;
    }

    @Unique
    private void slightlyTiltItemPosition(AbstractClientPlayer player, InteractionHand hand, ItemStack stack, PoseStack matrices, boolean moduleCheck) {
        if ((!moduleCheck || ModuleManager.getModuleState(ViewModel.class) && ViewModel.oldItemPosition.get()) && !(stack.getItem() instanceof BlockItem)) {
            final HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
            final int direction = arm == HumanoidArm.RIGHT ? 1 : -1;

            final float scale = 0.7585F / 0.86F;
            matrices.scale(scale, scale, scale);
            matrices.translate(direction * -0.084F, 0.059F, 0.08F);
            matrices.mulPose(Axis.YP.rotationDegrees(direction * 5.0F));
        }
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V", ordinal = 1))
    private void hookSlightlyTiltItemPosition(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack matrices, SubmitNodeCollector submitter, int light, CallbackInfo ci) {
        this.slightlyTiltItemPosition(player, hand, stack, matrices, true);
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void itemRendererHook(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector submitter, int light, CallbackInfo ci) {
        if (FreeLook.shouldMoveCamera()) {
            ci.cancel();
            return;
        }

        boolean bl = hand == InteractionHand.MAIN_HAND;
        boolean animationBlocking = OldHitting.shouldBlock();
        boolean substituteMainHand = animationBlocking && shouldSubstituteBlockingMainHand(player, hand);
        HumanoidArm arm = bl || substituteMainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        final int direction = arm == HumanoidArm.RIGHT ? 1 : -1;

        if (ModuleManager.getModuleState(ViewModel.class)) {
            float itemFovScale = bl || substituteMainHand ? Mth.clamp(1f + ViewModel.itemFov.get(), 0.1F, 3.0F) : 1f;
            matrices.scale(1f, 1f, itemFovScale);
        }

        if (animationBlocking) {
            ci.cancel();
            if (bl || substituteMainHand) {
                PlayerUtil.INSTANCE.fullResetSpoofState();

                matrices.pushPose();
                try {
                    ItemStack renderedItem = substituteMainHand ? player.getMainHandItem() : item;
                    float renderedEquipProgress = substituteMainHand
                            ? 1.0F - Mth.lerp(tickDelta, this.oMainHandHeight, this.mainHandHeight)
                            : equipProgress;
                    float renderedSwingProgress = substituteMainHand ? 0.0F : swingProgress;

                    if (OldHitting.mode.get().equalsIgnoreCase("vanilla")) {
                        this.customApplyEquipOffset(matrices, arm, renderedEquipProgress, null);
                        this.applySwingOffset(matrices, arm, renderedSwingProgress, null);

                        this.slightlyTiltItemPosition(player, InteractionHand.MAIN_HAND, renderedItem, matrices, false);
                        this.applyVanillaBlockTransformation(matrices, arm);
                    } else {
                        matrices.translate(0.0F, 0.0F, -0.02F);
                        matrices.mulPose(Axis.ZP.rotationDegrees(direction * -1.0F));
                        this.applySwordAnimation(matrices, arm, renderedEquipProgress, renderedSwingProgress);
                        this.applyFirstPersonDisplayTransform(matrices, arm);
                    }

                    this.renderItem(player, renderedItem, arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, matrices, submitter, light);
                } finally {
                    matrices.popPose();
                }
            }
        } else if (PlayerUtil.INSTANCE.getSpoofSlot() != null && mc.player != null) {
            if (hand == InteractionHand.MAIN_HAND) {
                ItemStack spoofedStack = this.getFirstPersonSpoofStack(mc.player);
                if (!this.shouldRenderFirstPersonSpoof(mc.player, spoofedStack))
                    return;

                ci.cancel();

                matrices.pushPose();

                if (spoofedStack != null && !spoofedStack.isEmpty()) {
                    this.customSwingArm(swingProgress, equipProgress, matrices, direction, arm, ci);

                    this.slightlyTiltItemPosition(player, hand, item, matrices, true);

                    this.renderItem(player, spoofedStack, arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, matrices, submitter, light);
                } else if (!player.isInvisible()) {
                    this.customRenderArmHoldingItem(matrices, submitter, light, equipProgress, swingProgress, arm, null);
                }

                matrices.popPose();
            }
        }
    }

    @Unique
    private boolean shouldSubstituteBlockingMainHand(AbstractClientPlayer player, InteractionHand renderedHand) {
        if (renderedHand != InteractionHand.OFF_HAND || player == null || !player.isUsingItem()
                || player.getUsedItemHand() != InteractionHand.OFF_HAND)
            return false;

        ItemUseAnimation useAnimation = player.getUseItem().getUseAnimation();
        return useAnimation == ItemUseAnimation.BOW || useAnimation == ItemUseAnimation.CROSSBOW;
    }

    @Unique
    private ItemStack getFirstPersonSpoofStack(LocalPlayer player) {
        Integer spoofSlot = PlayerUtil.INSTANCE.getSpoofSlot();
        if (player == null || spoofSlot == null || spoofSlot < 0 || spoofSlot > 8)
            return null;

        return player.getInventory().getItem(spoofSlot);
    }

    @Unique
    private boolean shouldRenderFirstPersonSpoof(LocalPlayer player, ItemStack spoofedStack) {
        if (player == null) {
            PlayerUtil.INSTANCE.fullResetSpoofState();
            return false;
        }
        if (spoofedStack == null)
            return false;

        if (PlayerUtil.INSTANCE.getSpoofing())
            return true;

        PlayerUtil.INSTANCE.finishSpoofReleaseIfReady();
        return PlayerUtil.INSTANCE.getSpoofSlot() != null;
    }

    @Unique
    private boolean shouldUseCustomEquipOffset() {
        return ModuleManager.getModuleState(ViewModel.class) && (ViewModel.noEquipAnimation.get() || getItemModelScale() != 1.0F || hasCustomItemPosition());
    }

    @Unique
    private float getItemModelScale() {
        if (!ModuleManager.getModuleState(ViewModel.class))
            return 1.0F;

        return Mth.clamp(1.0F + ViewModel.itemSize.get(), 0.1F, 3.0F);
    }

    @Unique
    private boolean hasCustomItemPosition() {
        if (!ModuleManager.getModuleState(ViewModel.class))
            return false;

        return ViewModel.itemPositionX.get() != 0.0F
                || ViewModel.itemPositionY.get() != 0.0F
                || ViewModel.itemPositionZ.get() != 0.0F;
    }

    @Unique
    private void updateOffhandHeldItems() {
        this.oOffHandHeight = this.offHandHeight;
        LocalPlayer clientPlayerEntity = this.minecraft.player;
        ItemStack itemStack2 = clientPlayerEntity.getOffhandItem();

        if (this.shouldInstantlyReplaceVisibleItem(this.offHandItem, itemStack2)) {
            this.offHandItem = itemStack2;
        }

        float h = this.offHandItem != itemStack2 ? 0.0F : 1.0F;
        this.offHandHeight += Mth.clamp(h - this.offHandHeight, -0.4F, 0.4F);

        if (this.offHandHeight < 0.1F) {
            this.offHandItem = itemStack2;
        }
    }

    @Unique
    private void updateSpoofedHeldItems(LocalPlayer player, ItemStack mainHandTarget, boolean forceNoCooldown) {
        updateOffhandHeldItems();

        this.oMainHandHeight = this.mainHandHeight;
        if (player == null || mainHandTarget == null)
            return;

        if (forceNoCooldown) {
            this.mainHandItem = mainHandTarget;
            this.oMainHandHeight = 1.0F;
            this.mainHandHeight = 1.0F;
            return;
        }

        if (this.shouldInstantlyReplaceVisibleItem(this.mainHandItem, mainHandTarget))
            this.mainHandItem = mainHandTarget;

        if (player.isHandsBusy()) {
            this.mainHandHeight = Mth.clamp(this.mainHandHeight - 0.4F, 0.0F, 1.0F);
        } else {
            float targetHeight = this.mainHandItem != mainHandTarget ? 0.0F : 1.0F;
            this.mainHandHeight += Mth.clamp(targetHeight - this.mainHandHeight, -0.4F, 0.4F);
        }

        if (this.mainHandHeight < 0.1F)
            this.mainHandItem = mainHandTarget;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void firstPersonItemSpoofHook(CallbackInfo ci) {
        PlayerUtil.INSTANCE.tickSpoofRelease();

        LocalPlayer currentPlayer = mc.player;

        if (OldHitting.shouldBlock() || shouldKeepOldEatSwingItemVisible(currentPlayer)) {
            ci.cancel();

            updateOffhandHeldItems();

            this.oMainHandHeight = this.mainHandHeight;
            LocalPlayer clientPlayerEntity = this.minecraft.player;
            if (clientPlayerEntity == null) return;
            ItemStack itemStack = clientPlayerEntity.getMainHandItem();

            if (ItemStack.matches(this.mainHandItem, itemStack))
                this.mainHandItem = itemStack;

            this.mainHandHeight += Mth.clamp((this.mainHandItem == itemStack ? 1f : 0.0F) - this.mainHandHeight, -0.4F, 0.4F);

            if (this.mainHandHeight < 0.1F)
                this.mainHandItem = itemStack;
        } else if (PlayerUtil.INSTANCE.getSpoofSlot() != null && mc.player != null) {
            ci.cancel();

            LocalPlayer clientPlayerEntity = this.minecraft.player;
            if (clientPlayerEntity == null) return;
            ItemStack spoofedStack = getFirstPersonSpoofStack(clientPlayerEntity);
            ItemStack itemStack = PlayerUtil.INSTANCE.getSpoofing() && spoofedStack != null ? spoofedStack : clientPlayerEntity.getMainHandItem();
            boolean spoofStateActive = PlayerUtil.INSTANCE.getSpoofSlot() != null;
            updateSpoofedHeldItems(clientPlayerEntity, itemStack, spoofStateActive);
        }
    }
}
