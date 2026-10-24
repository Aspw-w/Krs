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
        final boolean[] flat;
        final Map<Integer, List<SkinBuffers>> skinBuffers = new HashMap<>();

        LoadedModel(GlbModel model, AutoRig rig, ModelProfile profile) {
            this.model = model;
            this.rig = rig;
            this.profile = profile;
            flat = new boolean[model.primitives.size()];
            for (int p = 0; p < flat.length; p++)
                flat[p] = profile.isFlat(model.primitives.get(p).meshName);
        }

        SkinBuffers acquireSkinBuffers(int entityId) {
            List<SkinBuffers> sets = skinBuffers.computeIfAbsent(entityId, k -> new ArrayList<>(2));
            for (SkinBuffers set : sets) {
                if (set.pending <= 0)
                    return set;
            }
            SkinBuffers set = new SkinBuffers(model.primitives);
            if (sets.size() < 4)
                sets.add(set);
            return set;
        }
    }

    private static final class SkinBuffers {
        final float[][] positions;
        final float[][] normals;
        int pending;

        SkinBuffers(List<GlbModel.Primitive> primitives) {
            positions = new float[primitives.size()][];
            normals = new float[primitives.size()][];
            for (int p = 0; p < positions.length; p++) {
                int length = primitives.get(p).vertexCount() * 3;
                positions[p] = new float[length];
                normals[p] = new float[length];
            }
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

        Matrix4f[] bones = BONES;
        for (Matrix4f bone : bones)
            bone.identity();
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
        SkinBuffers buffers = loaded.acquireSkinBuffers(state.id);
        packBones(bones);
        Map<Identifier, List<Skinned>> opaque = new HashMap<>();
        Map<Identifier, List<Skinned>> blended = new HashMap<>();
        for (int p = 0; p < primitives.size(); p++) {
            GlbModel.Primitive prim = primitives.get(p);
            Identifier texture = prim.texture >= 0 && prim.texture < loaded.textureIds.length && loaded.textureIds[prim.texture] != null
                    ? loaded.textureIds[prim.texture] : loaded.whiteTexture;
            int[] withoutProp = rig.indicesWithoutProp.get(p);
            int[] triangles = withoutProp != null && holdingItem ? withoutProp : prim.indices;
            boolean flat = loaded.flat[p];
            float[] normals = buffers.normals[p];
            skin(prim, rig.boneIndices.get(p), rig.boneWeights.get(p), buffers.positions[p], flat ? null : normals);
            if (flat) {
                Vector3f forward = bones[AutoRig.HEAD].transformDirection(new Vector3f(0.0f, 0.0f, 1.0f)).normalize();
                for (int i = 0; i < normals.length; i += 3) {
                    normals[i] = forward.x;
                    normals[i + 1] = forward.y;
                    normals[i + 2] = forward.z;
                }
            }
            Skinned skinned = new Skinned(buffers.positions[p], normals, prim.uvs, triangles, prim.color);
            (translucent || prim.translucent ? blended : opaque).computeIfAbsent(texture, k -> new ArrayList<>()).add(skinned);
            ESP.captureCustomModel(state, poseStack.last(), texture, skinned.positions, skinned.uvs, skinned.indices);
        }

        buffers.pending = opaque.size() + blended.size();
        submitMeshes(opaque, false, poseStack, collector, tint, light, overlay, buffers);
        submitMeshes(blended, true, poseStack, collector, tint, light, overlay, buffers);
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

        submitMeshes(opaque, false, poseStack, collector, 0xFFFFFFFF, light, OverlayTexture.NO_OVERLAY, null);
        submitMeshes(blended, true, poseStack, collector, 0xFFFFFFFF, light, OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();
        return true;
    }

    private static void submitMeshes(Map<Identifier, List<Skinned>> byTexture, boolean blend, PoseStack poseStack, SubmitNodeCollector collector,
                                     int tint, int light, int overlay, SkinBuffers owner) {
        for (Map.Entry<Identifier, List<Skinned>> entry : byTexture.entrySet()) {
            RenderType type = blend ? shadedTranslucent(entry.getKey()) : shadedCutout(entry.getKey());
            List<Skinned> meshes = entry.getValue();
            collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
                for (Skinned mesh : meshes)
                    mesh.emit(pose, buffer, tint, light, overlay);
                if (owner != null)
                    owner.pending--;
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
            for (LoadedModel loaded : LOADED.values())
                loaded.skinBuffers.clear();
            return;
        }
        SMOOTHING.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (LoadedModel loaded : LOADED.values())
            loaded.skinBuffers.keySet().removeIf(id -> mc.level.getEntity(id) == null);
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
            float[] out = EmitScratch.positions(count);
            float[] outN = EmitScratch.normals(count);
            int[] shaded = EmitScratch.shaded(count);
            int[] stamp = EmitScratch.stamp(count);
            int id = EmitScratch.nextStamp();
            Matrix4f m = pose.pose();
            Vector3f n = EmitScratch.NORMAL;
            for (int i = 0; i + 2 < indices.length; i += 3) {
                for (int c = 0; c < 3; c++) {
                    int v = indices[i + c];
                    if (stamp[v] == id)
                        continue;
                    stamp[v] = id;
                    int o = v * 3;
                    float x = positions[o], y = positions[o + 1], z = positions[o + 2];
                    out[o] = m.m00() * x + m.m10() * y + m.m20() * z + m.m30();
                    out[o + 1] = m.m01() * x + m.m11() * y + m.m21() * z + m.m31();
                    out[o + 2] = m.m02() * x + m.m12() * y + m.m22() * z + m.m32();
                    pose.transformNormal(normals[o], normals[o + 1], normals[o + 2], n);
                    outN[o] = n.x;
                    outN[o + 1] = n.y;
                    outN[o + 2] = n.z;
                    shaded[v] = ARGB.multiply(color, ARGB.gray(shade(n)));
                }
                int a = indices[i], b = indices[i + 1], c = indices[i + 2];
                vertex(buffer, a, out, outN, shaded, light, overlay);
                vertex(buffer, b, out, outN, shaded, light, overlay);
                vertex(buffer, c, out, outN, shaded, light, overlay);
                vertex(buffer, c, out, outN, shaded, light, overlay);
            }
        }

        private void vertex(com.mojang.blaze3d.vertex.VertexConsumer buffer, int v, float[] out, float[] outN, int[] shaded, int light, int overlay) {
            int o = v * 3;
            buffer.addVertex(out[o], out[o + 1], out[o + 2], shaded[v], uvs[v * 2], uvs[v * 2 + 1], overlay, light,
                    outN[o], outN[o + 1], outN[o + 2]);
        }
    }

    private static final class EmitScratch {
        static final Vector3f NORMAL = new Vector3f();
        private static float[] positions = new float[0];
        private static float[] normals = new float[0];
        private static int[] shaded = new int[0];
        private static int[] stamp = new int[0];
        private static int stampId;

        static float[] positions(int count) {
            if (positions.length < count * 3)
                positions = new float[count * 3];
            return positions;
        }

        static float[] normals(int count) {
            if (normals.length < count * 3)
                normals = new float[count * 3];
            return normals;
        }

        static int[] shaded(int count) {
            if (shaded.length < count)
                shaded = new int[count];
            return shaded;
        }

        static int[] stamp(int count) {
            if (stamp.length < count) {
                stamp = new int[count];
                stampId = 0;
            }
            return stamp;
        }

        static int nextStamp() {
            if (++stampId == 0) {
                Arrays.fill(stamp, 0);
                stampId = 1;
            }
            return stampId;
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

    private static final Matrix4f[] BONES = new Matrix4f[AutoRig.BONE_COUNT];
    private static final float[] PACKED_BONES = new float[AutoRig.BONE_COUNT * 12];

    static {
        for (int i = 0; i < BONES.length; i++)
            BONES[i] = new Matrix4f();
    }

    private static void packBones(Matrix4f[] bones) {
        for (int b = 0; b < bones.length; b++) {
            Matrix4f m = bones[b];
            int o = b * 12;
            PACKED_BONES[o] = m.m00();
            PACKED_BONES[o + 1] = m.m01();
            PACKED_BONES[o + 2] = m.m02();
            PACKED_BONES[o + 3] = m.m10();
            PACKED_BONES[o + 4] = m.m11();
            PACKED_BONES[o + 5] = m.m12();
            PACKED_BONES[o + 6] = m.m20();
            PACKED_BONES[o + 7] = m.m21();
            PACKED_BONES[o + 8] = m.m22();
            PACKED_BONES[o + 9] = m.m30();
            PACKED_BONES[o + 10] = m.m31();
            PACKED_BONES[o + 11] = m.m32();
        }
    }

    private static void skin(GlbModel.Primitive prim, int[] boneIndex, float[] boneWeight, float[] pos, float[] nrm) {
        int count = prim.vertexCount();
        float[] src = prim.positions;
        float[] srcN = prim.normals;
        float[] mats = PACKED_BONES;
        boolean normals = nrm != null;

        for (int v = 0; v < count; v++) {
            int o = v * 3;
            float x = src[o], y = src[o + 1], z = src[o + 2];
            float nx = srcN[o], ny = srcN[o + 1], nz = srcN[o + 2];
            float px = 0.0f, py = 0.0f, pz = 0.0f;
            float qx = 0.0f, qy = 0.0f, qz = 0.0f;
            int base = v * AutoRig.INFLUENCES;
            for (int k = 0; k < AutoRig.INFLUENCES; k++) {
                float w = boneWeight[base + k];
                if (w <= 0.0f)
                    continue;
                int m = boneIndex[base + k] * 12;
                float m00 = mats[m], m01 = mats[m + 1], m02 = mats[m + 2];
                float m10 = mats[m + 3], m11 = mats[m + 4], m12 = mats[m + 5];
                float m20 = mats[m + 6], m21 = mats[m + 7], m22 = mats[m + 8];
                px += w * (m00 * x + m10 * y + m20 * z + mats[m + 9]);
                py += w * (m01 * x + m11 * y + m21 * z + mats[m + 10]);
                pz += w * (m02 * x + m12 * y + m22 * z + mats[m + 11]);
                if (normals) {
                    qx += w * (m00 * nx + m10 * ny + m20 * nz);
                    qy += w * (m01 * nx + m11 * ny + m21 * nz);
                    qz += w * (m02 * nx + m12 * ny + m22 * nz);
                }
            }
            pos[o] = px;
            pos[o + 1] = py;
            pos[o + 2] = pz;
            if (normals) {
                float lengthSquared = qx * qx + qy * qy + qz * qz;
                if (lengthSquared > 1.0e-12f) {
                    float inv = 1.0f / (float) Math.sqrt(lengthSquared);
                    qx *= inv;
                    qy *= inv;
                    qz *= inv;
                }
                nrm[o] = qx;
                nrm[o + 1] = qy;
                nrm[o + 2] = qz;
            }
        }
    }
}
