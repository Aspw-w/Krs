package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.render.LegacyAtmosphere;
import com.instrumentalist.krs.utils.render.LegacyWater;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Camera;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Random;

public class LegacyRenderer extends Module {

    @Setting
    public static final BooleanValue lightmap = new BooleanValue("Lightmap", true);

    @Setting
    public static final BooleanValue fog = new BooleanValue("Fog", true);

    @Setting
    public static final BooleanValue sky = new BooleanValue("Sky", true);

    @Setting
    public static final BooleanValue clouds = new BooleanValue("Clouds", true);

    @Setting
    public static final BooleanValue biomeBlend = new BooleanValue("Biome Blend", true);

    @Setting
    public static final BooleanValue noChunkFade = new BooleanValue("No Chunk Fade", true);

    @Setting
    public static final BooleanValue shading = new BooleanValue("Shading", true);

    @Setting
    public static final BooleanValue water = new BooleanValue("Water", true);

    @Setting
    public static final BooleanValue weather = new BooleanValue("Weather", true);

    @Setting
    public static final BooleanValue hitParticles = new BooleanValue("Hit Particles", true);

    @Setting
    public static final BooleanValue playerAnimations = new BooleanValue("Player Animations", true);

    @Setting
    public static final BooleanValue camera = new BooleanValue("Camera", true);

    @Setting
    public static final BooleanValue equipAnimation = new BooleanValue("Equip Animation", true);

    @Setting
    public static final BooleanValue firstPerson = new BooleanValue("First Person", true);

    private static final float CLOUD_HEIGHT = 128.0F + 0.33F;
    private static final int SWAMP_WATER_COLOR = 0xE0FFAE;
    private static final SimplexNoise TEMPERATURE_NOISE = new SimplexNoise(new Random(1234L));

    private static float torchFlickerX;
    private static float torchFlickerDX;
    private static float fogColor1;
    private static float fogColor2;
    private static int skylightSubtracted;
    private static float cameraPitch;
    private static float prevCameraPitch;

    public static final int[] lightmapColors = new int[256];
    public static boolean suppressSwing = false;
    public static boolean pendingCooldownReset = false;
    private static int ticksSinceLastSwing;
    private static ItemStack itemStackMainHand = ItemStack.EMPTY;
    public static volatile boolean lightmapPending = false;

    private static Boolean lastBiomeBlendState = null;
    private static Boolean lastShadingState = null;
    private static Boolean lastWaterState = null;

    private static Vector3f legacySkyColor = new Vector3f();
    private static double legacyHorizonDistance;
    private static boolean legacySkyPending;
    private static boolean vanillaDarkDisc;

    public LegacyRenderer() {
        super("Legacy Renderer", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        fogColor1 = fogColor2 = 0.0F;
        torchFlickerX = torchFlickerDX = 0.0F;
        reloadTintsIfNeeded();
    }

    @Override
    public void onDisable() {
        lightmapPending = false;
        reloadTintsIfNeeded();
    }

    @Override
    public void onWorld(WorldEvent event) {
        fogColor1 = fogColor2 = 0.0F;
        cameraPitch = prevCameraPitch = 0.0F;
    }

    @Override
    public void onTick(TickEvent event) {
        reloadTintsIfNeeded();

        ClientLevel level = mc.level;
        Entity entity = mc.getCameraEntity();
        if (level == null || entity == null) return;

        torchFlickerDX = (float) ((double) torchFlickerDX + (Math.random() - Math.random()) * Math.random() * Math.random());
        torchFlickerDX = (float) ((double) torchFlickerDX * 0.9D);
        torchFlickerX += torchFlickerDX - torchFlickerX;

        float angle = celestialAngle(level, 1.0F);
        float f1 = 1.0F - (Mth.cos(angle * ((float) Math.PI * 2F)) * 2.0F + 0.5F);
        f1 = Mth.clamp(f1, 0.0F, 1.0F);
        f1 = 1.0F - f1;
        f1 = (float) ((double) f1 * (1.0D - (double) (level.getRainLevel(1.0F) * 5.0F) / 16.0D));
        f1 = (float) ((double) f1 * (1.0D - (double) (level.getThunderLevel(1.0F) * 5.0F) / 16.0D));
        f1 = 1.0F - f1;
        skylightSubtracted = (int) (f1 * 11.0F);

        LocalPlayer player = mc.player;
        prevCameraPitch = cameraPitch;
        if (player != null) {
            float f5 = (float) (Math.atan(-player.getDeltaMovement().y * 0.20000000298023224D) * 15.0D);
            if (player.onGround() || player.getHealth() <= 0.0F) f5 = 0.0F;
            cameraPitch += (f5 - cameraPitch) * 0.8F;
        }

        fogColor2 = fogColor1;
        float f3 = brightnessTable(level, lightFromNeighbors(level, BlockPos.containing(entity.getX(), entity.getY(), entity.getZ())));
        float f4 = (float) mc.options.getEffectiveRenderDistance() / 32.0F;
        float f2 = f3 * (1.0F - f4) + f4;
        fogColor1 += (f2 - fogColor1) * 0.1F;
    }

    private static int lightFromNeighbors(ClientLevel level, BlockPos pos) {
        if (level.getBlockState(pos).useShapeForLightOcclusion()) {
            int i1 = light(level, pos.above());
            int i = light(level, pos.east());
            int j = light(level, pos.west());
            int k = light(level, pos.south());
            int l = light(level, pos.north());

            if (i > i1) i1 = i;
            if (j > i1) i1 = j;
            if (k > i1) i1 = k;
            if (l > i1) i1 = l;

            return i1;
        }

        return light(level, pos);
    }

    private static int light(ClientLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinY()) return 0;
        if (pos.getY() > level.getMaxY()) pos = new BlockPos(pos.getX(), level.getMaxY(), pos.getZ());

        int i = level.dimensionType().hasSkyLight() ? level.getBrightness(LightLayer.SKY, pos) : 0;
        i -= skylightSubtracted;
        int j = level.getBrightness(LightLayer.BLOCK, pos);
        return Mth.clamp(Math.max(i, j), 0, 15);
    }

    private static void reloadTintsIfNeeded() {
        boolean biomeBlendState = isBiomeBlendActive();
        boolean shadingState = isShadingActive();
        boolean waterState = isWaterActive();
        boolean changed = false;

        if (lastBiomeBlendState == null || lastBiomeBlendState != biomeBlendState) {
            lastBiomeBlendState = biomeBlendState;
            changed = true;
        }

        if (lastShadingState == null || lastShadingState != shadingState) {
            lastShadingState = shadingState;
            changed = true;

            if (mc.level != null)
                mc.gameRenderer.lighting().updateLevel(cardinalLightingType(mc.level.dimensionType().cardinalLightType()));
        }

        if (lastWaterState == null || lastWaterState != waterState) {
            lastWaterState = waterState;
            changed = true;
            LegacyWater.refresh(waterState);
        }

        if (changed && mc.level != null && mc.levelExtractor != null)
            mc.levelExtractor.allChanged();
    }

    private static boolean active() {
        return ModuleManager.getModuleState(LegacyRenderer.class) && mc.level != null;
    }

    public static boolean isLightmapActive() {
        return active() && lightmap.get() && !FullBright.isBrightening();
    }

    public static boolean isFogActive() {
        return active() && fog.get();
    }

    public static boolean isSkyActive() {
        return active() && sky.get();
    }

    public static boolean isCloudsActive() {
        return active() && clouds.get();
    }

    public static boolean isBiomeBlendActive() {
        return active() && biomeBlend.get();
    }

    public static boolean isNoChunkFadeActive() {
        return active() && noChunkFade.get();
    }

    public static boolean isShadingActive() {
        return active() && shading.get();
    }

    public static boolean isWaterActive() {
        return active() && water.get();
    }

    public static boolean isHitParticlesActive() {
        return active() && hitParticles.get();
    }

    public static boolean isWeatherActive() {
        return active() && weather.get();
    }

    public static boolean isWaterConfigured() {
        return ModuleManager.getModuleState(LegacyRenderer.class) && water.get();
    }

    public static CardinalLighting cardinalLighting(CardinalLighting original) {
        return isShadingActive() ? CardinalLighting.DEFAULT : original;
    }

    public static CardinalLighting.Type cardinalLightingType(CardinalLighting.Type original) {
        return ModuleManager.getModuleState(LegacyRenderer.class) && shading.get() ? CardinalLighting.Type.DEFAULT : original;
    }

    public static int waterColor(Biome biome, int original) {
        if (!isWaterActive()) return original;

        return biome.getSpecialEffects().grassColorModifier() == BiomeSpecialEffects.GrassColorModifier.SWAMP ? SWAMP_WATER_COLOR : 0xFFFFFF;
    }

    public static boolean shouldSkipSky() {
        return isSkyActive() && mc.options.getEffectiveRenderDistance() < 4;
    }

    public static boolean isPlayerAnimationsActive() {
        return active() && playerAnimations.get();
    }

    public static boolean isCameraActive() {
        return active() && camera.get();
    }

    public static boolean isEquipAnimationActive() {
        return active() && equipAnimation.get();
    }

    public static boolean isFirstPersonActive() {
        return active() && firstPerson.get() && !ModuleManager.getModuleState(ViewModel.class) && !OldHitting.shouldBlock();
    }

    public static boolean callWithoutSwing(java.util.function.BooleanSupplier call) {
        if (!isPlayerAnimationsActive() && !isFirstPersonActive()) return call.getAsBoolean();

        suppressSwing = true;
        try {
            return call.getAsBoolean();
        } finally {
            suppressSwing = false;
        }
    }

    public static int armSwingAnimationEnd(LivingEntity entity) {
        int end;
        MobEffectInstance haste = entity.getEffect(MobEffects.HASTE);

        if (haste != null) {
            end = 6 - (1 + haste.getAmplifier());
        } else {
            MobEffectInstance fatigue = entity.getEffect(MobEffects.MINING_FATIGUE);
            end = fatigue != null ? 6 + (1 + fatigue.getAmplifier()) * 2 : 6;
        }

        return Math.max(1, ViewModel.hookSwingSpeed(end, entity));
    }

    public static void updateDistance(LivingEntity entity, boolean swinging) {
        entity.yBodyRot = updateDistance(entity.yBodyRot, entity.getYRot(), entity.getX() - entity.xo, entity.getZ() - entity.zo, swinging);
    }

    public static float updateDistance(float renderYawOffset, float rotationYaw, double d0, double d1, boolean swinging) {
        float f1 = (float) (d0 * d0 + d1 * d1);
        float f2 = renderYawOffset;

        if (f1 > 0.0025000002F)
            f2 = (float) Mth.atan2(d1, d0) * (180F / (float) Math.PI) - 90.0F;

        if (swinging)
            f2 = rotationYaw;

        float f = Mth.wrapDegrees(f2 - renderYawOffset);
        renderYawOffset += f * 0.3F;
        float f3 = Mth.wrapDegrees(rotationYaw - renderYawOffset);

        if (f3 < -75.0F) f3 = -75.0F;
        if (f3 >= 75.0F) f3 = 75.0F;

        renderYawOffset = rotationYaw - f3;

        if (f3 * f3 > 2500.0F)
            renderYawOffset += f3 * 0.2F;

        return renderYawOffset;
    }

    public static void applyCape(AvatarRenderState state, Entity entity, ClientAvatarState avatarState, float partialTicks, float bodyYaw) {
        double d0 = avatarState.getInterpolatedCloakX(partialTicks) - (entity.xo + (entity.getX() - entity.xo) * (double) partialTicks);
        double d1 = avatarState.getInterpolatedCloakY(partialTicks) - (entity.yo + (entity.getY() - entity.yo) * (double) partialTicks);
        double d2 = avatarState.getInterpolatedCloakZ(partialTicks) - (entity.zo + (entity.getZ() - entity.zo) * (double) partialTicks);
        double d3 = Mth.sin(bodyYaw * 0.017453292F);
        double d4 = -Mth.cos(bodyYaw * 0.017453292F);
        float f1 = (float) d1 * 10.0F;
        f1 = Mth.clamp(f1, -6.0F, 32.0F);
        float f2 = (float) (d0 * d3 + d2 * d4) * 100.0F;
        float f3 = (float) (d0 * d4 - d2 * d3) * 100.0F;

        if (f2 < 0.0F) f2 = 0.0F;
        f2 = Mth.clamp(f2, 0.0F, 180.0F);

        float f4 = avatarState.getInterpolatedBob(partialTicks);
        f1 = f1 + Mth.sin(avatarState.getInterpolatedWalkDistance(partialTicks) * 6.0F) * 32.0F * f4;

        if (entity.isShiftKeyDown())
            f1 += 25.0F;

        state.capeFlap = f1;
        state.capeLean = f2;
        state.capeLean2 = f3;
    }

    public static void applyCapeModel(AvatarRenderState state, ModelPart body, ModelPart cape) {
        body.resetPose();

        if (state.isCrouching)
            cape.y += 2.0F;
    }

    public static int serverResponseTicks(LocalPlayer player) {
        PlayerInfo info = player.connection.getPlayerInfo(player.getUUID());
        int latency = info != null ? Math.max(info.getLatency(), 0) : 0;
        return 1 + Mth.ceil((float) latency / 50.0F);
    }

    public static float tickCooledAttackStrength(LocalPlayer player) {
        if (pendingCooldownReset) {
            ticksSinceLastSwing = 0;
            pendingCooldownReset = false;
        }

        float f = Mth.clamp(((float) ticksSinceLastSwing + 1.0F) / player.getCurrentItemAttackStrengthDelay(), 0.0F, 1.0F);

        ++ticksSinceLastSwing;
        ItemStack itemstack = player.getMainHandItem();

        if (!ItemStack.matches(itemStackMainHand, itemstack)) {
            if (!ItemStack.isSameItem(itemStackMainHand, itemstack))
                ticksSinceLastSwing = 0;

            itemStackMainHand = itemstack.copy();
        }

        return f;
    }

    public static float cameraPitch(float partialTicks) {
        return prevCameraPitch + (cameraPitch - prevCameraPitch) * partialTicks;
    }

    public static float eyeHeight(Entity entity) {
        if (!(entity instanceof LocalPlayer player)) return Float.NaN;

        float f = player.getEyeHeight(Pose.STANDING);

        if (player.isSleeping()) {
            return 0.2F * player.getScale();
        } else if (player.isShiftKeyDown()) {
            f -= 0.08F * player.getScale();
        } else if (player.isFallFlying() || player.isAutoSpinAttack()) {
            return Float.NaN;
        }

        BlockPos pos = BlockPos.containing(player.getX(), player.getY() + f + 0.1D, player.getZ());
        if (player.level().getBlockState(pos).isSuffocating(player.level(), pos))
            return player.getEyeHeight();

        return f;
    }

    private static HumanoidModel.ArmPose legacyArmPose(HumanoidRenderState state, HumanoidArm arm) {
        HumanoidModel.ArmPose pose = arm == HumanoidArm.RIGHT ? state.rightArmPose : state.leftArmPose;

        return switch (pose) {
            case EMPTY, BLOCK -> pose;
            case BOW_AND_ARROW -> arm == state.mainArm ? pose : HumanoidModel.ArmPose.ITEM;
            default -> HumanoidModel.ArmPose.ITEM;
        };
    }

    public static void applyPlayerModel(HumanoidRenderState state, ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg, boolean oldHittingBlock) {
        head.resetPose();
        body.resetPose();
        rightArm.resetPose();
        leftArm.resetPose();
        rightLeg.resetPose();
        leftLeg.resetPose();

        head.yRot = state.yRot * 0.017453292F;

        if (state.isFallFlying)
            head.xRot = -((float) Math.PI / 4F);
        else
            head.xRot = state.xRot * 0.017453292F;

        body.yRot = 0.0F;
        rightArm.z = 0.0F;
        rightArm.x = -5.0F;
        leftArm.z = 0.0F;
        leftArm.x = 5.0F;
        float f = state.speedValue;

        if (f < 1.0F) f = 1.0F;

        float limbSwing = state.walkAnimationPos;
        float limbSwingAmount = state.walkAnimationSpeed;
        rightArm.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 2.0F * limbSwingAmount * 0.5F / f;
        leftArm.xRot = Mth.cos(limbSwing * 0.6662F) * 2.0F * limbSwingAmount * 0.5F / f;
        rightArm.zRot = 0.0F;
        leftArm.zRot = 0.0F;
        rightLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount / f;
        leftLeg.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.4F * limbSwingAmount / f;
        rightLeg.yRot = 0.0F;
        leftLeg.yRot = 0.0F;
        rightLeg.zRot = 0.0F;
        leftLeg.zRot = 0.0F;

        if (state.isPassenger) {
            rightArm.xRot += -((float) Math.PI / 5F);
            leftArm.xRot += -((float) Math.PI / 5F);
            rightLeg.xRot = -1.4137167F;
            rightLeg.yRot = ((float) Math.PI / 10F);
            rightLeg.zRot = 0.07853982F;
            leftLeg.xRot = -1.4137167F;
            leftLeg.yRot = -((float) Math.PI / 10F);
            leftLeg.zRot = -0.07853982F;
        }

        rightArm.yRot = 0.0F;
        rightArm.zRot = 0.0F;

        HumanoidModel.ArmPose leftArmPose = legacyArmPose(state, HumanoidArm.LEFT);
        HumanoidModel.ArmPose rightArmPose = legacyArmPose(state, HumanoidArm.RIGHT);

        if (oldHittingBlock && state.mainArm == HumanoidArm.LEFT) {
            OldHitting.thirdPersonAnimation(leftArm, HumanoidArm.LEFT);
        } else {
            switch (leftArmPose) {
                case EMPTY -> leftArm.yRot = 0.0F;
                case BLOCK -> {
                    leftArm.xRot = leftArm.xRot * 0.5F - 0.9424779F;
                    leftArm.yRot = 0.5235988F;
                }
                case ITEM -> {
                    leftArm.xRot = leftArm.xRot * 0.5F - ((float) Math.PI / 10F);
                    leftArm.yRot = 0.0F;
                }
                default -> {
                }
            }
        }

        if (oldHittingBlock && state.mainArm == HumanoidArm.RIGHT) {
            OldHitting.thirdPersonAnimation(rightArm, HumanoidArm.RIGHT);
        } else {
            switch (rightArmPose) {
                case EMPTY -> rightArm.yRot = 0.0F;
                case BLOCK -> {
                    rightArm.xRot = rightArm.xRot * 0.5F - 0.9424779F;
                    rightArm.yRot = -0.5235988F;
                }
                case ITEM -> {
                    rightArm.xRot = rightArm.xRot * 0.5F - ((float) Math.PI / 10F);
                    rightArm.yRot = 0.0F;
                }
                default -> {
                }
            }
        }

        float swingProgress = state.swingAnimation;
        if (swingProgress > 0.0F) {
            HumanoidArm side = state.currentSwing != null ? state.currentSwing.hand().asArm(state.mainArm) : state.mainArm;
            ModelPart modelPart = side == HumanoidArm.LEFT ? leftArm : rightArm;
            float f1 = swingProgress;
            body.yRot = Mth.sin(Mth.sqrt(f1) * ((float) Math.PI * 2F)) * 0.2F;

            if (side == HumanoidArm.LEFT)
                body.yRot *= -1.0F;

            rightArm.z = Mth.sin(body.yRot) * 5.0F;
            rightArm.x = -Mth.cos(body.yRot) * 5.0F;
            leftArm.z = -Mth.sin(body.yRot) * 5.0F;
            leftArm.x = Mth.cos(body.yRot) * 5.0F;
            rightArm.yRot += body.yRot;
            leftArm.yRot += body.yRot;
            leftArm.xRot += body.yRot;
            f1 = 1.0F - swingProgress;
            f1 = f1 * f1;
            f1 = f1 * f1;
            f1 = 1.0F - f1;
            float f2 = Mth.sin(f1 * (float) Math.PI);
            float f3 = Mth.sin(swingProgress * (float) Math.PI) * -(head.xRot - 0.7F) * 0.75F;
            modelPart.xRot = (float) ((double) modelPart.xRot - ((double) f2 * 1.2D + (double) f3));
            modelPart.yRot += body.yRot * 2.0F;
            modelPart.zRot += Mth.sin(swingProgress * (float) Math.PI) * -0.4F;
        }

        if (state.isCrouching) {
            body.xRot = 0.5F;
            rightArm.xRot += 0.4F;
            leftArm.xRot += 0.4F;
            rightLeg.z = 4.0F;
            leftLeg.z = 4.0F;
            rightLeg.y = 9.0F;
            leftLeg.y = 9.0F;
            head.y = 1.0F;
        } else {
            body.xRot = 0.0F;
            rightLeg.z = 0.1F;
            leftLeg.z = 0.1F;
            rightLeg.y = 12.0F;
            leftLeg.y = 12.0F;
            head.y = 0.0F;
        }

        rightArm.zRot += Mth.cos(state.ageInTicks * 0.09F) * 0.05F + 0.05F;
        leftArm.zRot -= Mth.cos(state.ageInTicks * 0.09F) * 0.05F + 0.05F;
        rightArm.xRot += Mth.sin(state.ageInTicks * 0.067F) * 0.05F;
        leftArm.xRot -= Mth.sin(state.ageInTicks * 0.067F) * 0.05F;

        if (rightArmPose == HumanoidModel.ArmPose.BOW_AND_ARROW) {
            rightArm.yRot = -0.1F + head.yRot;
            leftArm.yRot = 0.1F + head.yRot + 0.4F;
            rightArm.xRot = -((float) Math.PI / 2F) + head.xRot;
            leftArm.xRot = -((float) Math.PI / 2F) + head.xRot;
        } else if (leftArmPose == HumanoidModel.ArmPose.BOW_AND_ARROW) {
            rightArm.yRot = -0.1F + head.yRot - 0.4F;
            leftArm.yRot = 0.1F + head.yRot;
            rightArm.xRot = -((float) Math.PI / 2F) + head.xRot;
            leftArm.xRot = -((float) Math.PI / 2F) + head.xRot;
        }

        if (state.isCrouching) {
            head.y += 3.2F;
            body.y += 3.2F;
            rightArm.y += 3.2F;
            leftArm.y += 3.2F;
            rightLeg.y += 3.2F;
            leftLeg.y += 3.2F;
        }
    }

    public static Object biomeBlendRadius(OptionInstance<?> option, Object original) {
        if (option == mc.options.biomeBlendRadius() && isBiomeBlendActive())
            return 1;

        return original;
    }

    public static Object chunkSectionFadeInTime(OptionInstance<?> option, Object original) {
        if (option == mc.options.chunkSectionFadeInTime() && isNoChunkFadeActive())
            return 0.0D;

        return original;
    }

    public static float celestialAngle(ClientLevel level, float partialTicks) {
        if (level.dimension() == Level.NETHER) return 0.5F;
        if (level.dimension() == Level.END || level.dimensionType().defaultClock().isEmpty()) return 0.0F;

        int i = (int) (level.getDefaultClockTime() % 24000L);
        float f = ((float) i + partialTicks) / 24000.0F - 0.25F;

        if (f < 0.0F) ++f;
        if (f > 1.0F) --f;

        float f1 = 1.0F - (float) ((Math.cos((double) f * Math.PI) + 1.0D) / 2.0D);
        f = f + (f1 - f) / 3.0F;
        return f;
    }

    private static float brightnessTable(ClientLevel level, int index) {
        float f1 = 1.0F - (float) index / 15.0F;
        float curved = (1.0F - f1) / (f1 * 3.0F + 1.0F);

        if (level.dimension() == Level.NETHER) return curved * 0.9F + 0.1F;
        if (level.dimension() == Level.OVERWORLD || level.dimension() == Level.END) return curved * 1.0F + 0.0F;

        float ambient = level.dimensionType().ambientLight();
        return curved * (1.0F - ambient) + ambient;
    }

    private static float sunBrightness(ClientLevel level, float partialTicks) {
        float f = celestialAngle(level, partialTicks);
        float f1 = 1.0F - (Mth.cos(f * ((float) Math.PI * 2F)) * 2.0F + 0.2F);
        f1 = Mth.clamp(f1, 0.0F, 1.0F);
        f1 = 1.0F - f1;
        f1 = (float) ((double) f1 * (1.0D - (double) (level.getRainLevel(partialTicks) * 5.0F) / 16.0D));
        f1 = (float) ((double) f1 * (1.0D - (double) (level.getThunderLevel(partialTicks) * 5.0F) / 16.0D));
        return f1 * 0.8F + 0.2F;
    }

    private static float starBrightness(ClientLevel level, float partialTicks) {
        float f = celestialAngle(level, partialTicks);
        float f1 = 1.0F - (Mth.cos(f * ((float) Math.PI * 2F)) * 2.0F + 0.25F);
        f1 = Mth.clamp(f1, 0.0F, 1.0F);
        return f1 * f1 * 0.5F;
    }

    private static float[] sunriseSunsetColors(ClientLevel level, float celestialAngle) {
        if (level.dimension() == Level.END) return null;

        float f1 = Mth.cos(celestialAngle * ((float) Math.PI * 2F)) - 0.0F;
        if (f1 >= -0.4F && f1 <= 0.4F) {
            float f3 = (f1 - -0.0F) / 0.4F * 0.5F + 0.5F;
            float f4 = 1.0F - (1.0F - Mth.sin(f3 * (float) Math.PI)) * 0.99F;
            f4 = f4 * f4;
            return new float[]{f3 * 0.3F + 0.7F, f3 * f3 * 0.7F + 0.2F, f3 * f3 * 0.0F + 0.2F, f4};
        }
        return null;
    }

    private static float biomeTemperature(float temperature, BlockPos pos) {
        if (pos.getY() > 64) {
            float f = (float) (TEMPERATURE_NOISE.getValue((double) ((float) pos.getX() / 8.0F), (double) ((float) pos.getZ() / 8.0F)) * 4.0D);
            return temperature - (f + (float) pos.getY() - 64.0F) * 0.05F / 30.0F;
        }
        return temperature;
    }

    private static int skyColorByTemp(float temperature) {
        temperature = temperature / 3.0F;
        temperature = Mth.clamp(temperature, -1.0F, 1.0F);
        return hsvToRGB(0.62222224F - temperature * 0.05F, 0.5F + temperature * 0.1F, 1.0F);
    }

    private static int hsvToRGB(float hue, float saturation, float value) {
        int i = (int) (hue * 6.0F) % 6;
        float f = hue * 6.0F - (float) i;
        float f1 = value * (1.0F - saturation);
        float f2 = value * (1.0F - f * saturation);
        float f3 = value * (1.0F - (1.0F - f) * saturation);
        float f4;
        float f5;
        float f6;

        switch (i) {
            case 0 -> {
                f4 = value;
                f5 = f3;
                f6 = f1;
            }
            case 1 -> {
                f4 = f2;
                f5 = value;
                f6 = f1;
            }
            case 2 -> {
                f4 = f1;
                f5 = value;
                f6 = f3;
            }
            case 3 -> {
                f4 = f1;
                f5 = f2;
                f6 = value;
            }
            case 4 -> {
                f4 = f3;
                f5 = f1;
                f6 = value;
            }
            default -> {
                f4 = value;
                f5 = f1;
                f6 = f2;
            }
        }

        int j = Mth.clamp((int) (f4 * 255.0F), 0, 255);
        int k = Mth.clamp((int) (f5 * 255.0F), 0, 255);
        int l = Mth.clamp((int) (f6 * 255.0F), 0, 255);
        return j << 16 | k << 8 | l;
    }

    private static Vector3f skyColor(ClientLevel level, Entity entity, float partialTicks) {
        float f = celestialAngle(level, partialTicks);
        float f1 = Mth.cos(f * ((float) Math.PI * 2F)) * 2.0F + 0.5F;
        f1 = Mth.clamp(f1, 0.0F, 1.0F);

        BlockPos pos = new BlockPos(Mth.floor(entity.getX()), Mth.floor(entity.getY()), Mth.floor(entity.getZ()));
        float f2 = biomeTemperature(level.getBiome(pos).value().getBaseTemperature(), pos);
        int l = skyColorByTemp(f2);

        float f3 = (float) (l >> 16 & 255) / 255.0F;
        float f4 = (float) (l >> 8 & 255) / 255.0F;
        float f5 = (float) (l & 255) / 255.0F;
        f3 = f3 * f1;
        f4 = f4 * f1;
        f5 = f5 * f1;

        float f6 = level.getRainLevel(partialTicks);
        if (f6 > 0.0F) {
            float f7 = (f3 * 0.3F + f4 * 0.59F + f5 * 0.11F) * 0.6F;
            float f8 = 1.0F - f6 * 0.75F;
            f3 = f3 * f8 + f7 * (1.0F - f8);
            f4 = f4 * f8 + f7 * (1.0F - f8);
            f5 = f5 * f8 + f7 * (1.0F - f8);
        }

        float f10 = level.getThunderLevel(partialTicks);
        if (f10 > 0.0F) {
            float f11 = (f3 * 0.3F + f4 * 0.59F + f5 * 0.11F) * 0.2F;
            float f9 = 1.0F - f10 * 0.75F;
            f3 = f3 * f9 + f11 * (1.0F - f9);
            f4 = f4 * f9 + f11 * (1.0F - f9);
            f5 = f5 * f9 + f11 * (1.0F - f9);
        }

        if (level.skyFlashTime > 0) {
            float f12 = (float) level.skyFlashTime - partialTicks;
            if (f12 > 1.0F) f12 = 1.0F;
            f12 = f12 * 0.45F;
            f3 = f3 * (1.0F - f12) + 0.8F * f12;
            f4 = f4 * (1.0F - f12) + 0.8F * f12;
            f5 = f5 * (1.0F - f12) + 1.0F * f12;
        }

        return new Vector3f(f3, f4, f5);
    }

    private static Vector3f worldFogColor(ClientLevel level, float celestialAngle) {
        if (level.dimension() == Level.NETHER)
            return new Vector3f((float) 0.20000000298023224D, (float) 0.029999999329447746D, (float) 0.029999999329447746D);

        float f = Mth.cos(celestialAngle * ((float) Math.PI * 2F)) * 2.0F + 0.5F;
        f = Mth.clamp(f, 0.0F, 1.0F);

        if (level.dimension() == Level.END) {
            float f1 = 0.627451F * (f * 0.0F + 0.15F);
            float f2 = 0.5019608F * (f * 0.0F + 0.15F);
            float f3 = 0.627451F * (f * 0.0F + 0.15F);
            return new Vector3f(f1, f2, f3);
        }

        float f1 = 0.7529412F * (f * 0.94F + 0.06F);
        float f2 = 0.84705883F * (f * 0.94F + 0.06F);
        float f3 = 1.0F * (f * 0.91F + 0.09F);
        return new Vector3f(f1, f2, f3);
    }

    private static float nightVisionBrightness(LivingEntity entity, float partialTicks) {
        return GameRenderer.nightVisionScale(entity, partialTicks);
    }

    public static void computeLightmap(float partialTicks) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        float f = sunBrightness(level, 1.0F);
        float f1 = f * 0.95F + 0.05F;
        boolean end = level.dimension() == Level.END;
        float f11 = mc.gameRenderer.bossOverlayWorldDarkening(partialTicks);
        boolean nightVision = mc.player.hasEffect(MobEffects.NIGHT_VISION);
        float f15 = nightVision ? nightVisionBrightness(mc.player, partialTicks) : 0.0F;
        float f16 = mc.options.gamma().get().floatValue();

        for (int i = 0; i < 256; ++i) {
            float f2 = brightnessTable(level, i / 16) * f1;
            float f3 = brightnessTable(level, i % 16) * (torchFlickerX * 0.1F + 1.5F);

            if (level.skyFlashTime > 0)
                f2 = brightnessTable(level, i / 16);

            float f4 = f2 * (f * 0.65F + 0.35F);
            float f5 = f2 * (f * 0.65F + 0.35F);
            float f6 = f3 * ((f3 * 0.6F + 0.4F) * 0.6F + 0.4F);
            float f7 = f3 * (f3 * f3 * 0.6F + 0.4F);
            float f8 = f4 + f3;
            float f9 = f5 + f6;
            float f10 = f2 + f7;
            f8 = f8 * 0.96F + 0.03F;
            f9 = f9 * 0.96F + 0.03F;
            f10 = f10 * 0.96F + 0.03F;

            if (f11 > 0.0F) {
                f8 = f8 * (1.0F - f11) + f8 * 0.7F * f11;
                f9 = f9 * (1.0F - f11) + f9 * 0.6F * f11;
                f10 = f10 * (1.0F - f11) + f10 * 0.6F * f11;
            }

            if (end) {
                f8 = 0.22F + f3 * 0.75F;
                f9 = 0.28F + f6 * 0.75F;
                f10 = 0.25F + f7 * 0.75F;
            }

            if (nightVision) {
                float f12 = 1.0F / f8;
                if (f12 > 1.0F / f9) f12 = 1.0F / f9;
                if (f12 > 1.0F / f10) f12 = 1.0F / f10;
                f8 = f8 * (1.0F - f15) + f8 * f12 * f15;
                f9 = f9 * (1.0F - f15) + f9 * f12 * f15;
                f10 = f10 * (1.0F - f15) + f10 * f12 * f15;
            }

            if (f8 > 1.0F) f8 = 1.0F;
            if (f9 > 1.0F) f9 = 1.0F;
            if (f10 > 1.0F) f10 = 1.0F;

            float f17 = 1.0F - f8;
            float f13 = 1.0F - f9;
            float f14 = 1.0F - f10;
            f17 = 1.0F - f17 * f17 * f17 * f17;
            f13 = 1.0F - f13 * f13 * f13 * f13;
            f14 = 1.0F - f14 * f14 * f14 * f14;
            f8 = f8 * (1.0F - f16) + f17 * f16;
            f9 = f9 * (1.0F - f16) + f13 * f16;
            f10 = f10 * (1.0F - f16) + f14 * f16;
            f8 = f8 * 0.96F + 0.03F;
            f9 = f9 * 0.96F + 0.03F;
            f10 = f10 * 0.96F + 0.03F;

            if (f8 > 1.0F) f8 = 1.0F;
            if (f9 > 1.0F) f9 = 1.0F;
            if (f10 > 1.0F) f10 = 1.0F;
            if (f8 < 0.0F) f8 = 0.0F;
            if (f9 < 0.0F) f9 = 0.0F;
            if (f10 < 0.0F) f10 = 0.0F;

            int k = (int) (f8 * 255.0F);
            int l = (int) (f9 * 255.0F);
            int i1 = (int) (f10 * 255.0F);
            lightmapColors[i] = -16777216 | k << 16 | l << 8 | i1;
        }

        lightmapPending = true;
    }

    public static void applyFog(FogData data, Camera camera, ClientLevel level, int renderDistanceChunks, float partialTicks) {
        FogType fogType = camera.getFluidInCamera();
        if (fogType == FogType.POWDER_SNOW) return;

        Entity entity = camera.entity();
        float farPlaneDistance = (float) (renderDistanceChunks * 16);
        float celestialAngle = celestialAngle(level, partialTicks);

        float f = 0.25F + 0.75F * (float) renderDistanceChunks / 32.0F;
        f = 1.0F - (float) Math.pow((double) f, 0.25D);
        Vector3f sky = skyColor(level, entity, partialTicks);
        Vector3f fogColor = worldFogColor(level, celestialAngle);
        float red = fogColor.x;
        float green = fogColor.y;
        float blue = fogColor.z;

        if (renderDistanceChunks >= 4) {
            double d0 = Mth.sin(celestialAngle * ((float) Math.PI * 2F)) > 0.0F ? -1.0D : 1.0D;
            Vec3 look = entity.getViewVector(partialTicks);
            float f5 = (float) look.dot(new Vec3(d0, 0.0D, 0.0D));
            if (f5 < 0.0F) f5 = 0.0F;

            if (f5 > 0.0F) {
                float[] colors = sunriseSunsetColors(level, celestialAngle);
                if (colors != null) {
                    f5 = f5 * colors[3];
                    red = red * (1.0F - f5) + colors[0] * f5;
                    green = green * (1.0F - f5) + colors[1] * f5;
                    blue = blue * (1.0F - f5) + colors[2] * f5;
                }
            }
        }

        red += (sky.x - red) * f;
        green += (sky.y - green) * f;
        blue += (sky.z - blue) * f;

        float f8 = level.getRainLevel(partialTicks);
        if (f8 > 0.0F) {
            float f4 = 1.0F - f8 * 0.5F;
            float f10 = 1.0F - f8 * 0.4F;
            red *= f4;
            green *= f4;
            blue *= f10;
        }

        float f9 = level.getThunderLevel(partialTicks);
        if (f9 > 0.0F) {
            float f11 = 1.0F - f9 * 0.5F;
            red *= f11;
            green *= f11;
            blue *= f11;
        }

        LivingEntity living = entity instanceof LivingEntity livingEntity ? livingEntity : null;

        if (fogType == FogType.WATER) {
            float f12 = 0.0F;
            if (living != null) {
                f12 = (float) living.getAttributeValue(Attributes.OXYGEN_BONUS) * 0.2F;
                if (living.hasEffect(MobEffects.WATER_BREATHING))
                    f12 = f12 * 0.3F + 0.6F;
            }
            red = 0.02F + f12;
            green = 0.02F + f12;
            blue = 0.2F + f12;
        } else if (fogType == FogType.LAVA) {
            red = 0.6F;
            green = 0.1F;
            blue = 0.0F;
        }

        float f13 = fogColor2 + (fogColor1 - fogColor2) * partialTicks;
        red *= f13;
        green *= f13;
        blue *= f13;

        double d1 = (entity.yo + (entity.getY() - entity.yo) * (double) partialTicks - level.getMinY()) / level.getLevelData().voidDarknessOnsetRange();
        MobEffectInstance blindness = living != null ? living.getEffect(MobEffects.BLINDNESS) : null;

        if (blindness != null) {
            int i = blindness.getDuration();
            if (i < 20 && !blindness.isInfiniteDuration())
                d1 *= (double) (1.0F - (float) i / 20.0F);
            else
                d1 = 0.0D;
        }

        if (d1 < 1.0D) {
            if (d1 < 0.0D) d1 = 0.0D;
            d1 = d1 * d1;
            red = (float) ((double) red * d1);
            green = (float) ((double) green * d1);
            blue = (float) ((double) blue * d1);
        }

        float f14 = mc.gameRenderer.bossOverlayWorldDarkening(partialTicks);
        if (f14 > 0.0F) {
            red = red * (1.0F - f14) + red * 0.7F * f14;
            green = green * (1.0F - f14) + green * 0.6F * f14;
            blue = blue * (1.0F - f14) + blue * 0.6F * f14;
        }

        if (living != null && living.hasEffect(MobEffects.NIGHT_VISION)) {
            float f15 = nightVisionBrightness(living, partialTicks);
            float f6 = 1.0F / red;
            if (f6 > 1.0F / green) f6 = 1.0F / green;
            if (f6 > 1.0F / blue) f6 = 1.0F / blue;
            red = red * (1.0F - f15) + red * f6 * f15;
            green = green * (1.0F - f15) + green * f6 * f15;
            blue = blue * (1.0F - f15) + blue * f6 * f15;
        }

        data.color.set(red, green, blue, 1.0F);
        data.renderDistanceStart = Float.MAX_VALUE;
        data.renderDistanceEnd = Float.MAX_VALUE;

        if (blindness != null) {
            float f1 = 5.0F;
            int i = blindness.getDuration();
            if (i < 20 && !blindness.isInfiniteDuration())
                f1 = 5.0F + (farPlaneDistance - 5.0F) * (1.0F - (float) i / 20.0F);

            data.environmentalStart = f1 * 0.25F;
            data.environmentalEnd = f1;
            data.skyEnd = f1 * 0.8F;
            data.cloudEnd = f1;
        } else if (fogType == FogType.WATER) {
            float density;
            if (living != null && living.hasEffect(MobEffects.WATER_BREATHING))
                density = 0.01F;
            else if (living != null)
                density = 0.1F - (float) living.getAttributeValue(Attributes.OXYGEN_BONUS) * 0.03F;
            else
                density = 0.1F;

            setExpFog(data, density);
        } else if (fogType == FogType.LAVA) {
            setExpFog(data, 2.0F);
        } else {
            data.environmentalStart = farPlaneDistance * 0.75F;
            data.environmentalEnd = farPlaneDistance;
            data.skyEnd = farPlaneDistance;

            if (level.dimension() == Level.NETHER || mc.gui.hud.getBossOverlay().shouldCreateWorldFog()) {
                data.environmentalStart = farPlaneDistance * 0.05F;
                data.environmentalEnd = Math.min(farPlaneDistance, 192.0F) * 0.5F;
                data.skyEnd = data.environmentalEnd;
            }
        }
    }

    private static void setExpFog(FogData data, float density) {
        data.environmentalStart = 0.0F;
        data.environmentalEnd = 2.0F / Math.max(density, 1.0E-4F);
        data.skyEnd = data.environmentalEnd;
        data.cloudEnd = data.environmentalEnd;
    }

    public static void applySky(SkyRenderState state, ClientLevel level, Camera camera, float partialTicks) {
        if (state.skybox != DimensionType.Skybox.OVERWORLD) return;

        float celestialAngle = celestialAngle(level, partialTicks);
        float angle = celestialAngle * ((float) Math.PI * 2F);
        float f16 = 1.0F - level.getRainLevel(partialTicks);

        state.skyColor = skyColor(level, camera.entity(), partialTicks);
        state.sunAngle = angle;
        state.moonAngle = angle + (float) Math.PI;
        state.starAngle = angle;
        state.rainBrightness = f16;
        state.starBrightness = starBrightness(level, partialTicks) * f16;

        float[] colors = sunriseSunsetColors(level, celestialAngle);
        state.sunriseAndSunsetColor = colors != null ? new Vector4f(colors[0], colors[1], colors[2], colors[3]) : new Vector4f(0.0F);

        legacySkyColor = new Vector3f(state.skyColor);
        legacyHorizonDistance = mc.player != null ? mc.player.getEyePosition(partialTicks).y - level.getLevelData().getHorizonHeight(level) : 0.0D;
        legacySkyPending = true;
        vanillaDarkDisc = state.shouldRenderDarkDisc;
        state.shouldRenderDarkDisc = true;
    }

    public static boolean renderUpperSky(RenderPass renderPass) {
        return legacySkyPending && LegacyAtmosphere.renderUpperSky(renderPass, legacySkyColor);
    }

    public static boolean renderLowerSky(RenderPass renderPass) {
        if (!legacySkyPending) return false;

        legacySkyPending = false;
        return LegacyAtmosphere.renderLowerSky(renderPass, legacySkyColor, legacyHorizonDistance);
    }

    public static boolean vanillaDarkDisc() {
        return vanillaDarkDisc;
    }

    public static int cloudRange(int original, int renderDistance) {
        return Math.min(original, Math.min(24, renderDistance * 4));
    }

    public static float cloudHeight(ClientLevel level, float original) {
        return level.dimension() == Level.OVERWORLD ? CLOUD_HEIGHT : original;
    }

    public static int cloudColor(ClientLevel level, float partialTicks) {
        float f = celestialAngle(level, partialTicks);
        float f1 = Mth.cos(f * ((float) Math.PI * 2F)) * 2.0F + 0.5F;
        f1 = Mth.clamp(f1, 0.0F, 1.0F);
        float f2 = 1.0F;
        float f3 = 1.0F;
        float f4 = 1.0F;

        float f5 = level.getRainLevel(partialTicks);
        if (f5 > 0.0F) {
            float f6 = (f2 * 0.3F + f3 * 0.59F + f4 * 0.11F) * 0.6F;
            float f7 = 1.0F - f5 * 0.95F;
            f2 = f2 * f7 + f6 * (1.0F - f7);
            f3 = f3 * f7 + f6 * (1.0F - f7);
            f4 = f4 * f7 + f6 * (1.0F - f7);
        }

        f2 = f2 * (f1 * 0.9F + 0.1F);
        f3 = f3 * (f1 * 0.9F + 0.1F);
        f4 = f4 * (f1 * 0.85F + 0.15F);

        float f9 = level.getThunderLevel(partialTicks);
        if (f9 > 0.0F) {
            float f10 = (f2 * 0.3F + f3 * 0.59F + f4 * 0.11F) * 0.2F;
            float f8 = 1.0F - f9 * 0.95F;
            f2 = f2 * f8 + f10 * (1.0F - f8);
            f3 = f3 * f8 + f10 * (1.0F - f8);
            f4 = f4 * f8 + f10 * (1.0F - f8);
        }

        return ARGB.colorFromFloat(0.8F, f2, f3, f4);
    }

    private static final class SimplexNoise {
        private static final int[][] GRAD3 = new int[][]{{1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1}, {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}};
        private static final double SQRT_3 = Math.sqrt(3.0D);
        private final int[] p = new int[512];

        private SimplexNoise(Random random) {
            random.nextDouble();
            random.nextDouble();
            random.nextDouble();

            for (int i = 0; i < 256; i++)
                this.p[i] = i;

            for (int l = 0; l < 256; ++l) {
                int j = random.nextInt(256 - l) + l;
                int k = this.p[l];
                this.p[l] = this.p[j];
                this.p[j] = k;
                this.p[l + 256] = this.p[l];
            }
        }

        private static int fastFloor(double value) {
            return value > 0.0D ? (int) value : (int) value - 1;
        }

        private static double dot(int[] grad, double x, double y) {
            return (double) grad[0] * x + (double) grad[1] * y;
        }

        private double getValue(double x, double y) {
            double d3 = 0.5D * (SQRT_3 - 1.0D);
            double d4 = (x + y) * d3;
            int i = fastFloor(x + d4);
            int j = fastFloor(y + d4);
            double d5 = (3.0D - SQRT_3) / 6.0D;
            double d6 = (double) (i + j) * d5;
            double d7 = (double) i - d6;
            double d8 = (double) j - d6;
            double d9 = x - d7;
            double d10 = y - d8;
            int k;
            int l;

            if (d9 > d10) {
                k = 1;
                l = 0;
            } else {
                k = 0;
                l = 1;
            }

            double d11 = d9 - (double) k + d5;
            double d12 = d10 - (double) l + d5;
            double d13 = d9 - 1.0D + 2.0D * d5;
            double d14 = d10 - 1.0D + 2.0D * d5;
            int i1 = i & 255;
            int j1 = j & 255;
            int k1 = this.p[i1 + this.p[j1]] % 12;
            int l1 = this.p[i1 + k + this.p[j1 + l]] % 12;
            int i2 = this.p[i1 + 1 + this.p[j1 + 1]] % 12;
            double d15 = 0.5D - d9 * d9 - d10 * d10;
            double d0;

            if (d15 < 0.0D) {
                d0 = 0.0D;
            } else {
                d15 = d15 * d15;
                d0 = d15 * d15 * dot(GRAD3[k1], d9, d10);
            }

            double d16 = 0.5D - d11 * d11 - d12 * d12;
            double d1;

            if (d16 < 0.0D) {
                d1 = 0.0D;
            } else {
                d16 = d16 * d16;
                d1 = d16 * d16 * dot(GRAD3[l1], d11, d12);
            }

            double d17 = 0.5D - d13 * d13 - d14 * d14;
            double d2;

            if (d17 < 0.0D) {
                d2 = 0.0D;
            } else {
                d17 = d17 * d17;
                d2 = d17 * d17 * dot(GRAD3[i2], d13, d14);
            }

            return 70.0D * (d0 + d1 + d2);
        }
    }
}
