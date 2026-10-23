package com.instrumentalist.krs.utils.render.custommodel;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.CustomModel;
import com.instrumentalist.krs.hacks.features.render.ESP;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.mixin.oringo.IEntityRenderState;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CustomModelRenderer {
    private static final float AVATAR_SCALE = 0.9375f;
    private static final float PLAYER_MODEL_HEIGHT = 1.8f / AVATAR_SCALE;
    private static final float SEAT_HEIGHT = (float) Avatar.DEFAULT_VEHICLE_ATTACHMENT.y;

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Krs Custom Model Loader");
        thread.setDaemon(true);
        return thread;
    });

    private static final float MODEL_SCALE = 1.2f;
    private static final float SWING_AMOUNT = 1.0f;
    private static final boolean IDLE_MOTION = true;
    private static final boolean SHOW_ITEMS = true;

    private static final Map<String, LoadedModel> LOADED = new HashMap<>();
    private static final Map<String, CompletableFuture<LoadedModel>> PENDING = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static int generation;
    private static final Map<Integer, ModelAnimator.Smoothing> SMOOTHING = new HashMap<>();
    private static final ModelAnimator ANIMATOR = new ModelAnimator();
    private static long lastPrune;

    private CustomModelRenderer() {
    }

    private static final class LoadedModel {
        final GlbModel model;
        final AutoRig rig;
        final ModelProfile profile;
        Identifier[] textureIds;
        Identifier whiteTexture;
        final int[][][] armTriangles = new int[4][][];
        BodyPartFit partFit;

        LoadedModel(GlbModel model, AutoRig rig, ModelProfile profile) {
            this.model = model;
            this.rig = rig;
            this.profile = profile;
        }
    }

    private static LoadedModel model(ModelProfile profile) {
        collectFinishedLoads();

        String key = profile.file;
        LoadedModel loaded = LOADED.get(key);
        if (loaded == null) {
            if (ModelDownloader.isMissing(profile) && FAILED.add(key))
                ChatUtil.showLog("[Custom Model] " + profile.label + " is not available");
            if (ModelDownloader.isReady(profile) && !PENDING.containsKey(key) && !FAILED.contains(key))
                PENDING.put(key, CompletableFuture.supplyAsync(() -> load(profile), LOADER));
            return null;
        }
        if (loaded.textureIds == null)
            upload(loaded);
        return loaded;
    }

    private static LoadedModel load(ModelProfile profile) {
        if (profile.pmx) {
            try {
                byte[] data = ModelDownloader.readPmxFile(profile, profile.file + ".pmx");
                if (data == null)
                    throw new IllegalStateException("Missing file");
                PmxModel pmx = PmxModel.load(data, path -> ModelDownloader.readPmxFile(profile, path));
                AutoRig rig = AutoRig.fromPmx(pmx, profile);
                if (pmx.mesh.primitives.isEmpty())
                    throw new IllegalStateException("Model has no triangles");
                return new LoadedModel(pmx.mesh, rig, profile);
            } catch (IOException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
        try {
            GlbModel glb = GlbModel.load(Files.readAllBytes(ModelDownloader.glbPath(profile)));
            AutoRig rig = AutoRig.build(glb, profile);
            if (glb.primitives.isEmpty())
                throw new IllegalStateException("Model has no triangles");
            return new LoadedModel(glb, rig, profile);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static void collectFinishedLoads() {
        var it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!entry.getValue().isDone())
                continue;
            it.remove();
            try {
                LOADED.put(entry.getKey(), entry.getValue().join());
            } catch (Exception e) {
                FAILED.add(entry.getKey());
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                ChatUtil.showLog("[Custom Model] Failed to load " + entry.getKey() + ": " + cause.getMessage());
            }
        }
        ModelProfile self = CustomModel.profileFor(true);
        ModelProfile others = CustomModel.profileFor(false);
        LOADED.entrySet().removeIf(e -> {
            boolean used = (self != null && self.file.equals(e.getKey())) || (others != null && others.file.equals(e.getKey()));
            if (!used)
                releaseTextures(e.getValue());
            return !used;
        });
    }
    private static void upload(LoadedModel loaded) {
        int id = ++generation;
        List<GlbModel.Texture> textures = loaded.model.textures;
        loaded.textureIds = new Identifier[textures.size()];
        for (int i = 0; i < textures.size(); i++) {
            GlbModel.Texture texture = textures.get(i);
            NativeImage image = new NativeImage(texture.width, texture.height, false);
            for (int y = 0; y < texture.height; y++) {
                for (int x = 0; x < texture.width; x++)
                    image.setPixel(x, y, texture.argb[y * texture.width + x]);
            }
            loaded.textureIds[i] = register("custom_model/" + id + "_" + i, image);
        }

        NativeImage white = new NativeImage(1, 1, false);
        white.setPixel(0, 0, 0xFFFFFFFF);
        loaded.whiteTexture = register("custom_model/" + id + "_white", white);
    }

    private static Identifier register(String path, NativeImage image) {
        Identifier identifier = Identifier.fromNamespaceAndPath("krs", path);
        DynamicTexture texture = new DynamicTexture(() -> "krs " + path, image) {
            {
                sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
            }
        };
        Minecraft.getInstance().getTextureManager().register(identifier, texture);
        return identifier;
    }

    private static void releaseTextures(LoadedModel loaded) {
        if (loaded.textureIds == null)
            return;
        var textureManager = Minecraft.getInstance().getTextureManager();
        for (Identifier id : loaded.textureIds) {
            textureManager.release(id);
            SHADED_TYPES.remove(id);
            SHADED_TRANSLUCENT_TYPES.remove(id);
        }
        textureManager.release(loaded.whiteTexture);
        loaded.textureIds = null;
    }

    public static void reset() {
        for (LoadedModel loaded : LOADED.values())
            releaseTextures(loaded);
        LOADED.clear();
        FAILED.clear();
        SMOOTHING.clear();
    }

    public static boolean isActive(LivingEntityRenderState state) {
        return modelFor(state) != null;
    }

    private static LoadedModel modelFor(LivingEntityRenderState state) {
        if (!(state instanceof AvatarRenderState avatar) || !ModuleManager.getModuleState(CustomModel.class) || avatar.isSpectator)
            return null;
        ModelProfile profile = CustomModel.profileFor(isSelf(avatar));
        return profile == null ? null : model(profile);
    }

    private static boolean isSelf(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return false;
        Entity entity = ((IEntityRenderState) state).client$getEntity();
        return entity != null ? entity == mc.player : state.id == mc.player.getId();
    }
    public static boolean trySubmit(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                    int light, int overlay, int tint) {
        LoadedModel loaded = modelFor(state);
        if (loaded == null)
            return false;

        AutoRig rig = loaded.rig;
        Entity entity = ((IEntityRenderState) state).client$getEntity();

        ModelAnimator.Smoothing smooth = SMOOTHING.computeIfAbsent(state.id, k -> new ModelAnimator.Smoothing());
        pruneSmoothing();

        Matrix4f[] bones = new Matrix4f[AutoRig.BONE_COUNT];
        for (int i = 0; i < bones.length; i++)
            bones[i] = new Matrix4f();
        float scale = PLAYER_MODEL_HEIGHT * MODEL_SCALE / rig.height;
        AnimInput input = animInput(state, entity);
        input.seatHipY = SEAT_HEIGHT / (AVATAR_SCALE * scale) + loaded.profile.landmarks.legRadius * rig.height;
        ANIMATOR.animate(rig, loaded.profile.style, input, smooth,
                SWING_AMOUNT, IDLE_MOTION, bones);
        if (loaded.partFit == null)
            loaded.partFit = new BodyPartFit(loaded.model, rig);
        loaded.partFit.pose(bones, scale, MODEL_ROOT_Y, PART_POSES.computeIfAbsent(state, k -> new BodyPartFit.Poses()));

        boolean translucent = ARGB.alpha(tint) < 255;

        poseStack.pushPose();
        poseStack.translate(0.0f, MODEL_ROOT_Y, 0.0f);
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.rotateDegrees(Axis.YP, 180.0f);

        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        List<GlbModel.Primitive> primitives = loaded.model.primitives;
        boolean holdingItem = !state.leftHandItemState.isEmpty() || !state.rightHandItemState.isEmpty();
        Map<Identifier, List<Skinned>> opaque = new HashMap<>();
        Map<Identifier, List<Skinned>> blended = new HashMap<>();
        for (int p = 0; p < primitives.size(); p++) {
            GlbModel.Primitive prim = primitives.get(p);
            Identifier texture = prim.texture >= 0 && prim.texture < loaded.textureIds.length && loaded.textureIds[prim.texture] != null
                    ? loaded.textureIds[prim.texture] : loaded.whiteTexture;
            int[] withoutProp = rig.indicesWithoutProp.get(p);
            int[] triangles = withoutProp != null && holdingItem ? withoutProp : prim.indices;
            Skinned skinned = skin(prim, triangles, rig.boneIndices.get(p), rig.boneWeights.get(p), bones);
            if (loaded.profile.isFlat(prim.meshName)) {
                Vector3f forward = bones[AutoRig.HEAD].transformDirection(new Vector3f(0.0f, 0.0f, 1.0f)).normalize();
                for (int i = 0; i < skinned.normals.length; i += 3) {
                    skinned.normals[i] = forward.x;
                    skinned.normals[i + 1] = forward.y;
                    skinned.normals[i + 2] = forward.z;
                }
            }
            (translucent || prim.translucent ? blended : opaque).computeIfAbsent(texture, k -> new ArrayList<>()).add(skinned);
            ESP.captureCustomModel(state, poseStack.last(), texture, skinned.positions, skinned.uvs, skinned.indices);
        }

        submitMeshes(opaque, false, poseStack, collector, tint, light, overlay);
        submitMeshes(blended, true, poseStack, collector, tint, light, overlay);
        poseStack.popPose();

        if (SHOW_ITEMS) {
            ESP.beginItemCapture(state);
            submitHeldItem(state, rig, bones, scale, HumanoidArm.LEFT, poseStack, collector, light);
            submitHeldItem(state, rig, bones, scale, HumanoidArm.RIGHT, poseStack, collector, light);
            ESP.endItemCapture();
        }
        poseStack.popPose();
        return true;
    }

    private static final float MODEL_ROOT_Y = 1.501f;
    private static final Map<HumanoidRenderState, BodyPartFit.Poses> PART_POSES = new WeakHashMap<>();

    public static void fitBodyParts(HumanoidRenderState state, ModelPart head, ModelPart body, ModelPart rightArm,
                                    ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {
        BodyPartFit.Poses poses = PART_POSES.get(state);
        if (poses == null || !ModuleManager.getModuleState(CustomModel.class))
            return;
        poses.apply(BodyPartFit.HEAD, head);
        poses.apply(BodyPartFit.BODY, body);
        poses.apply(BodyPartFit.RIGHT_ARM, rightArm);
        poses.apply(BodyPartFit.LEFT_ARM, leftArm);
        poses.apply(BodyPartFit.RIGHT_LEG, rightLeg);
        poses.apply(BodyPartFit.LEFT_LEG, leftLeg);
    }

    public static boolean pushBodyFrame(HumanoidRenderState state, PoseStack poseStack) {
        BodyPartFit.Poses poses = PART_POSES.get(state);
        if (poses == null || !ModuleManager.getModuleState(CustomModel.class))
            return false;
        ModelPart frame = BODY_FRAME;
        poses.apply(BodyPartFit.BODY, frame);
        poseStack.pushPose();
        frame.translateAndRotate(poseStack);
        return true;
    }

    private static final ModelPart BODY_FRAME = new ModelPart(List.of(), Map.of());

    private static final float VANILLA_HAND_Y = 10.0f / 16.0f;

    public static boolean trySubmitFirstPersonHand(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                                   int light, HumanoidArm arm) {
        LoadedModel loaded = modelFor(state);
        if (loaded == null)
            return false;

        AutoRig rig = loaded.rig;
        boolean left = arm == HumanoidArm.LEFT;
        float scale = PLAYER_MODEL_HEIGHT * MODEL_SCALE / rig.height;
        Vector3f shoulder = rig.pivots[left ? AutoRig.UPPER_ARM_L : AutoRig.UPPER_ARM_R];
        Vector3f fistEnd = left ? rig.fistEndL : rig.fistEndR;
        Quaternionf alignArm = new Quaternionf().rotationTo(new Vector3f(fistEnd).sub(shoulder).normalize(), new Vector3f(0.0f, -1.0f, 0.0f));

        poseStack.pushPose();
        poseStack.translate((left ? 5.0f : -5.0f) / 16.0f, 2.0f / 16.0f, 0.0f);
        poseStack.rotate(Axis.ZP.rotation(left ? -0.1f : 0.1f));
        poseStack.translate((left ? 1.0f : -1.0f) / 16.0f, VANILLA_HAND_Y, 0.0f);
        poseStack.rotateDegrees(Axis.XP, 180.0f);
        poseStack.rotate(alignArm);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-fistEnd.x, -fistEnd.y, -fistEnd.z);

        boolean holdingItem = !state.leftHandItemState.isEmpty() || !state.rightHandItemState.isEmpty();
        int[][] triangles = armTriangles(loaded, left, holdingItem);
        List<GlbModel.Primitive> primitives = loaded.model.primitives;
        Map<Identifier, List<Skinned>> opaque = new HashMap<>();
        Map<Identifier, List<Skinned>> blended = new HashMap<>();
        for (int p = 0; p < primitives.size(); p++) {
            if (triangles[p].length == 0)
                continue;
            GlbModel.Primitive prim = primitives.get(p);
            Identifier texture = prim.texture >= 0 && prim.texture < loaded.textureIds.length && loaded.textureIds[prim.texture] != null
                    ? loaded.textureIds[prim.texture] : loaded.whiteTexture;
            (prim.translucent ? blended : opaque).computeIfAbsent(texture, k -> new ArrayList<>())
                    .add(new Skinned(prim.positions, prim.normals, prim.uvs, triangles[p], prim.color));
        }

        submitMeshes(opaque, false, poseStack, collector, 0xFFFFFFFF, light, OverlayTexture.NO_OVERLAY);
        submitMeshes(blended, true, poseStack, collector, 0xFFFFFFFF, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        return true;
    }

    private static void submitMeshes(Map<Identifier, List<Skinned>> byTexture, boolean blend, PoseStack poseStack, SubmitNodeCollector collector,
                                     int tint, int light, int overlay) {
        for (Map.Entry<Identifier, List<Skinned>> entry : byTexture.entrySet()) {
            RenderType type = blend ? shadedTranslucent(entry.getKey()) : shadedCutout(entry.getKey());
            List<Skinned> meshes = entry.getValue();
            collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
                for (Skinned mesh : meshes)
                    mesh.emit(pose, buffer, tint, light, overlay);
            });
        }
    }

    private static int[][] armTriangles(LoadedModel loaded, boolean left, boolean holdingItem) {
        int slot = (left ? 0 : 2) + (holdingItem ? 1 : 0);
        if (loaded.armTriangles[slot] != null)
            return loaded.armTriangles[slot];

        boolean[] armBone = new boolean[AutoRig.BONE_COUNT];
        if (left) {
            armBone[AutoRig.UPPER_ARM_L] = armBone[AutoRig.FORE_ARM_L] = armBone[AutoRig.SLEEVE_L] = armBone[AutoRig.SLEEVE_BACK_L] = true;
        } else {
            armBone[AutoRig.UPPER_ARM_R] = armBone[AutoRig.FORE_ARM_R] = armBone[AutoRig.SLEEVE_R] = armBone[AutoRig.SLEEVE_BACK_R] = true;
        }

        AutoRig rig = loaded.rig;
        List<GlbModel.Primitive> primitives = loaded.model.primitives;
        int[][] result = new int[primitives.size()][];
        for (int p = 0; p < primitives.size(); p++) {
            int[] withoutProp = rig.indicesWithoutProp.get(p);
            int[] source = withoutProp != null && holdingItem ? withoutProp : primitives.get(p).indices;
            int[] boneIndex = rig.boneIndices.get(p);
            float[] boneWeight = rig.boneWeights.get(p);
            int[] kept = new int[source.length];
            int count = 0;
            for (int i = 0; i + 2 < source.length; i += 3) {
                float weight = 0.0f;
                for (int c = 0; c < 3; c++) {
                    int v = source[i + c];
                    for (int k = 0; k < AutoRig.INFLUENCES; k++) {
                        if (armBone[boneIndex[v * AutoRig.INFLUENCES + k]])
                            weight += boneWeight[v * AutoRig.INFLUENCES + k];
                    }
                }
                if (weight < 1.5f)
                    continue;
                kept[count++] = source[i];
                kept[count++] = source[i + 1];
                kept[count++] = source[i + 2];
            }
            result[p] = Arrays.copyOf(kept, count);
        }
        loaded.armTriangles[slot] = result;
        return result;
    }

    private static void submitHeldItem(AvatarRenderState state, AutoRig rig, Matrix4f[] bones, float scale, HumanoidArm arm,
                                       PoseStack poseStack, SubmitNodeCollector collector, int light) {
        ItemStackRenderState item = arm == HumanoidArm.LEFT ? state.leftHandItemState : state.rightHandItemState;
        if (item.isEmpty())
            return;

        boolean left = arm == HumanoidArm.LEFT;
        int fore = left ? AutoRig.FORE_ARM_L : AutoRig.FORE_ARM_R;

        Vector3f fistEnd = bones[fore].transformPosition(left ? rig.fistEndL : rig.fistEndR, new Vector3f()).mul(scale);
        float itemScale = itemScale(rig, scale);
        float fistHalfDepth = rig.fistHalfDepth * scale / itemScale;

        int side = left ? 0 : 1;
        Quaternionf orientation = bones[AutoRig.CHEST].getUnnormalizedRotation(new Quaternionf()).normalize()
                .rotateY(ANIMATOR.armYawOut[side])
                .rotateX(-ANIMATOR.armForwardOut[side]);

        poseStack.pushPose();
        poseStack.translate(fistEnd.x, fistEnd.y, fistEnd.z);
        poseStack.rotate(orientation);
        poseStack.rotateDegrees(Axis.XP, 180.0f);
        poseStack.scale(itemScale, itemScale, itemScale);
        float lateral = (left ? -1.0f : 1.0f) / 16.0f;
        poseStack.translate(lateral, -10.0f / 16.0f, 2.0f / 16.0f - fistHalfDepth);
        poseStack.rotateDegrees(Axis.XP, -90.0f);
        poseStack.rotateDegrees(Axis.YP, 180.0f);
        poseStack.translate(lateral, 2.0f / 16.0f, -10.0f / 16.0f);
        item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }

    private static final float VANILLA_ARM = 12.0f / 16.0f;

    private static float itemScale(AutoRig rig, float scale) {
        float arm = (rig.pivots[AutoRig.UPPER_ARM_L].distance(rig.pivots[AutoRig.FORE_ARM_L])
                + rig.pivots[AutoRig.FORE_ARM_L].distance(rig.handL)) * scale;
        float ratio = Math.max(0.5f, Math.min(1.3f, arm / VANILLA_ARM));
        return ratio * rig.itemScale;
    }

    private static AnimInput animInput(AvatarRenderState state, Entity entity) {
        AnimInput in = new AnimInput();
        in.ageInTicks = state.ageInTicks;
        in.walkPos = state.walkAnimationPos;
        in.walkSpeed = state.walkAnimationSpeed;
        in.headYaw = state.yRot;
        in.headPitch = state.xRot;
        in.bodyYaw = state.bodyRot;
        in.crouching = state.isCrouching;
        in.riding = state.isPassenger;
        in.swimming = state.isVisuallySwimming;
        in.gliding = state.isFallFlying;
        in.onGround = entity == null || entity.onGround() || state.isPassenger || state.isInWater;
        in.sprinting = entity != null ? entity.isSprinting() : state.walkAnimationSpeed > 0.9f;
        in.verticalSpeed = entity != null ? (float) entity.getDeltaMovement().y : 0.0f;
        in.swing = state.swingAnimation;
        in.swingArmLeft = (state.currentSwing != null ? state.currentSwing.hand().asArm(state.mainArm) : state.mainArm) == HumanoidArm.LEFT;
        in.leftPose = armPose(state.leftArmPose);
        in.rightPose = armPose(state.rightArmPose);
        in.eatingLeft = isEating(state, HumanoidArm.LEFT);
        in.eatingRight = isEating(state, HumanoidArm.RIGHT);
        in.useTicks = state.isUsingItem ? state.ticksUsingItem : 0.0f;
        in.chargeDuration = state.maxCrossbowChargeDuration;
        return in;
    }

    private static AnimInput.ArmPose armPose(HumanoidModel.ArmPose pose) {
        return switch (pose) {
            case ITEM, SPEAR -> AnimInput.ArmPose.ITEM;
            case BLOCK -> AnimInput.ArmPose.BLOCK;
            case BOW_AND_ARROW -> AnimInput.ArmPose.AIM;
            case CROSSBOW_HOLD -> AnimInput.ArmPose.CROSSBOW_HOLD;
            case CROSSBOW_CHARGE -> AnimInput.ArmPose.CROSSBOW_CHARGE;
            case SPYGLASS, TOOT_HORN -> AnimInput.ArmPose.SPYGLASS;
            case THROW_TRIDENT -> AnimInput.ArmPose.THROW;
            case BRUSH -> AnimInput.ArmPose.BRUSH;
            default -> AnimInput.ArmPose.EMPTY;
        };
    }

    private static boolean isEating(AvatarRenderState state, HumanoidArm arm) {
        if (state.ticksUsingItem(arm) <= 0.0f)
            return false;
        ItemStack stack = state.getUseItemStackForArm(arm);
        if (stack.isEmpty())
            return false;
        ItemUseAnimation anim = stack.getUseAnimation();
        return anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK;
    }

    private static void pruneSmoothing() {
        long now = System.currentTimeMillis();
        if (now - lastPrune < 10_000L)
            return;
        lastPrune = now;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            SMOOTHING.clear();
            return;
        }
        SMOOTHING.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    private static final class Skinned {
        final float[] positions;
        final float[] normals;
        final float[] uvs;
        final int[] indices;
        final int color;

        Skinned(float[] positions, float[] normals, float[] uvs, int[] indices, int color) {
            this.positions = positions;
            this.normals = normals;
            this.uvs = uvs;
            this.indices = indices;
            this.color = color;
        }

        void emit(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, int tint, int light, int overlay) {
            int color = ARGB.multiply(tint, this.color);
            int count = positions.length / 3;
            int[] shaded = new int[count];
            Vector3f n = new Vector3f();
            for (int v = 0; v < count; v++) {
                pose.transformNormal(normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2], n);
                shaded[v] = ARGB.multiply(color, ARGB.gray(shade(n)));
            }
            for (int i = 0; i + 2 < indices.length; i += 3) {
                vertex(pose, buffer, indices[i], shaded, light, overlay);
                vertex(pose, buffer, indices[i + 1], shaded, light, overlay);
                vertex(pose, buffer, indices[i + 2], shaded, light, overlay);
                vertex(pose, buffer, indices[i + 2], shaded, light, overlay);
            }
        }

        private void vertex(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, int v, int[] shaded, int light, int overlay) {
            buffer.addVertex(pose, positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2])
                    .setColor(shaded[v])
                    .setUv(uvs[v * 2], uvs[v * 2 + 1])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2]);
        }
    }

    private static final Vector3f KEY_LIGHT = new Vector3f(0.0f, 1.0f, 0.0f);
    private static final float SHADOW_LEVEL = 0.84f;

    private static float shade(Vector3f normal) {
        if (normal.lengthSquared() < 1.0e-8f)
            return 1.0f;
        float half = normal.normalize().dot(KEY_LIGHT) * 0.5f + 0.5f;
        float t = Math.max(0.0f, Math.min(1.0f, (half - 0.3f) / 0.3f));
        float lit = t * t * (3.0f - 2.0f * t);
        return SHADOW_LEVEL + (1.0f - SHADOW_LEVEL) * lit;
    }

    private static final RenderPipeline SHADED_CUTOUT = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("krs", "pipeline/custom_model_cutout"))
                    .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                    .withShaderDefine("NO_CARDINAL_LIGHTING")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                    .withCull(false)
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .build());
    private static final RenderPipeline SHADED_TRANSLUCENT = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("krs", "pipeline/custom_model_translucent"))
                    .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                    .withShaderDefine("NO_CARDINAL_LIGHTING")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                    .withCull(false)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .build());

    static {
        try {
            var copy = Class.forName("net.irisshaders.iris.pipeline.IrisPipelines")
                    .getMethod("copyPipeline", RenderPipeline.class, RenderPipeline.class);
            copy.invoke(null, RenderPipelines.ENTITY_CUTOUT, SHADED_CUTOUT);
            copy.invoke(null, RenderPipelines.ENTITY_TRANSLUCENT, SHADED_TRANSLUCENT);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static final Map<Identifier, RenderType> SHADED_TYPES = new HashMap<>();
    private static final Map<Identifier, RenderType> SHADED_TRANSLUCENT_TYPES = new HashMap<>();

    private static RenderType shadedTranslucent(Identifier texture) {
        return SHADED_TRANSLUCENT_TYPES.computeIfAbsent(texture, tex -> RenderType.create("krs_custom_model_translucent",
                RenderSetup.builder(SHADED_TRANSLUCENT)
                        .withTexture("Sampler0", tex)
                        .useLightmap()
                        .useOverlay()
                        .affectsCrumbling()
                        .sortOnUpload()
                        .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                        .createRenderSetup()));
    }

    private static RenderType shadedCutout(Identifier texture) {
        return SHADED_TYPES.computeIfAbsent(texture, tex -> RenderType.create("krs_custom_model",
                RenderSetup.builder(SHADED_CUTOUT)
                        .withTexture("Sampler0", tex)
                        .useLightmap()
                        .useOverlay()
                        .affectsCrumbling()
                        .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                        .createRenderSetup()));
    }

    private static Skinned skin(GlbModel.Primitive prim, int[] triangles, int[] boneIndex, float[] boneWeight, Matrix4f[] bones) {
        int count = prim.vertexCount();
        float[] src = prim.positions;
        float[] srcN = prim.normals;
        float[] pos = new float[count * 3];
        float[] nrm = new float[count * 3];
        Vector3f p = new Vector3f();
        Vector3f n = new Vector3f();
        Vector3f accP = new Vector3f();
        Vector3f accN = new Vector3f();

        for (int v = 0; v < count; v++) {
            accP.zero();
            accN.zero();
            for (int k = 0; k < AutoRig.INFLUENCES; k++) {
                float w = boneWeight[v * AutoRig.INFLUENCES + k];
                if (w <= 0.0f)
                    continue;
                Matrix4f m = bones[boneIndex[v * AutoRig.INFLUENCES + k]];
                m.transformPosition(p.set(src[v * 3], src[v * 3 + 1], src[v * 3 + 2]));
                m.transformDirection(n.set(srcN[v * 3], srcN[v * 3 + 1], srcN[v * 3 + 2]));
                accP.fma(w, p);
                accN.fma(w, n);
            }
            if (accN.lengthSquared() > 1.0e-12f)
                accN.normalize();
            pos[v * 3] = accP.x;
            pos[v * 3 + 1] = accP.y;
            pos[v * 3 + 2] = accP.z;
            nrm[v * 3] = accN.x;
            nrm[v * 3 + 1] = accN.y;
            nrm[v * 3 + 2] = accN.z;
        }
        return new Skinned(pos, nrm, prim.uvs, triangles, prim.color);
    }
}
