package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.player.Freecam;
import com.instrumentalist.krs.utils.render.GuiEntityRenderGuard;
import com.instrumentalist.krs.utils.render.GraphicsApiCompatibility;
import com.instrumentalist.krs.utils.render.Shader2DRenderer;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.mixin.oringo.IEntityRenderState;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.BufferUtils;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

public class ESP extends Module {

    @Setting
    private static final BooleanValue local = new BooleanValue("Local", true);

    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final FloatArrayBuilder TRIANGLES = new FloatArrayBuilder(4096);
    private static final FloatArrayBuilder ARMOR_TRIANGLES = new FloatArrayBuilder(2048);
    private static final SilhouetteVertexConsumer SILHOUETTE_CONSUMER = new SilhouetteVertexConsumer();
    private static final Map<NativeImage, AlphaMask> IMAGE_ALPHA_MASKS = new WeakHashMap<>();
    private static final Map<TextureAtlasSprite, AlphaMask> SPRITE_ALPHA_MASKS = new WeakHashMap<>();
    private static final Map<Identifier, AlphaMask> TEXTURE_ALPHA_MASKS = new HashMap<>();
    private static final Map<RenderType, ArmorRenderKind> ARMOR_RENDER_TYPES = new IdentityHashMap<>();
    private static final Map<RenderType, Identifier> RENDER_TYPE_TEXTURES = new IdentityHashMap<>();
    private static final Set<Identifier> UNMASKED_TEXTURES = new HashSet<>();
    private static int captureWidth = 0;
    private static int captureHeight = 0;
    private static int captureId = 0;
    private static int renderedCaptureId = -1;
    private static volatile boolean enabled = false;
    private static boolean captureOpen = false;
    private static boolean projectionReady = false;
    private static boolean reversedDepth = false;
    private static Object itemCaptureState = null;
    private static Object equipmentCaptureState = null;
    private static boolean itemCaptureArmor = false;

    public ESP() {
        super("ESP", ModuleCategory.Render, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
        resetCapture();
    }

    @Override
    public void onWorld(WorldEvent event) {
        resetCapture();
    }

    public static void beginCapture() {
        if (!enabled) {
            captureOpen = false;
            projectionReady = false;
            itemCaptureState = null;
            equipmentCaptureState = null;
            itemCaptureArmor = false;
            return;
        }

        TRIANGLES.clear();
        ARMOR_TRIANGLES.clear();
        projectionReady = false;
        captureWidth = 0;
        captureHeight = 0;
        captureId++;
        captureOpen = true;
        reversedDepth = false;

        updateProjection();
    }

    public static <S> void captureModel(ModelFeatureRenderer.Submit<S> modelSubmit, PoseStack poseStack, RenderType renderType) {
        if (!canCapture() || modelSubmit == null || poseStack == null)
            return;

        boolean captureModelState = shouldCaptureState(modelSubmit.state());
        boolean captureEquipmentState = !captureModelState && equipmentCaptureState != null;
        if (!captureModelState && !captureEquipmentState)
            return;

        if (!projectionReady && !updateProjection())
            return;

        Entity entity = getCapturedEntity(captureModelState ? modelSubmit.state() : equipmentCaptureState);
        Model<? super S> model = modelSubmit.model();
        if (captureEquipmentState) {
            VertexConsumer consumer = SILHOUETTE_CONSUMER.reset(
                    VIEW_PROJECTION,
                    captureWidth,
                    captureHeight,
                    alphaMaskFor(renderType),
                    ARMOR_TRIANGLES
            );
            model.renderToBuffer(poseStack, consumer, modelSubmit.lightCoords(), modelSubmit.overlayCoords(), 0xFFFFFFFF);
            return;
        }

        ArmorRenderKind armorRenderKind = armorRenderKind(renderType);
        if (armorRenderKind != ArmorRenderKind.NONE) {
            if (armorRenderKind == ArmorRenderKind.OVERLAY)
                return;

            VertexConsumer consumer = SILHOUETTE_CONSUMER.reset(
                    VIEW_PROJECTION,
                    captureWidth,
                    captureHeight,
                    alphaMaskFor(renderType),
                    ARMOR_TRIANGLES
            );
            model.renderToBuffer(poseStack, consumer, modelSubmit.lightCoords(), modelSubmit.overlayCoords(), 0xFFFFFFFF);
            return;
        }

        if (!(model instanceof PlayerModel))
            return;

        VertexConsumer consumer = SILHOUETTE_CONSUMER.reset(
                VIEW_PROJECTION,
                captureWidth,
                captureHeight,
                alphaMaskFor(model, entity),
                TRIANGLES
        );

        model.renderToBuffer(poseStack, consumer, modelSubmit.lightCoords(), modelSubmit.overlayCoords(), 0xFFFFFFFF);
    }

    public static <S> void captureSubmittedModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords) {
        if (!canCapture() || equipmentCaptureState == null || model == null || state == null || poseStack == null)
            return;
        if (state instanceof EntityRenderState)
            return;

        if (!projectionReady && !updateProjection())
            return;

        VertexConsumer consumer = SILHOUETTE_CONSUMER.reset(
                VIEW_PROJECTION,
                captureWidth,
                captureHeight,
                alphaMaskFor(renderType),
                ARMOR_TRIANGLES
        );
        model.setupAnim(state);
        model.renderToBuffer(poseStack, consumer, lightCoords, overlayCoords, 0xFFFFFFFF);
    }

    public static void endCapture() {
        captureOpen = false;
        itemCaptureState = null;
        equipmentCaptureState = null;
        itemCaptureArmor = false;
    }

    public static void beginItemCapture(Object state) {
        itemCaptureState = canCapture() && shouldCaptureState(state) ? state : null;
        itemCaptureArmor = false;
    }

    public static void endItemCapture() {
        itemCaptureState = null;
        itemCaptureArmor = false;
    }

    public static void beginEquipmentCapture(Object state) {
        boolean shouldCapture = canCapture() && shouldCaptureState(state);
        itemCaptureState = shouldCapture ? state : null;
        equipmentCaptureState = shouldCapture ? state : null;
        itemCaptureArmor = shouldCapture;
    }

    public static void endEquipmentCapture() {
        itemCaptureState = null;
        equipmentCaptureState = null;
        itemCaptureArmor = false;
    }

    public static void captureItem(PoseStack poseStack, List<BakedQuad> quads) {
        if (!canCapture() || itemCaptureState == null || poseStack == null || quads == null || quads.isEmpty())
            return;

        if (!projectionReady && !updateProjection())
            return;

        SilhouetteVertexConsumer consumer = SILHOUETTE_CONSUMER.reset(
                VIEW_PROJECTION,
                captureWidth,
                captureHeight,
                null,
                itemCaptureArmor ? ARMOR_TRIANGLES : TRIANGLES
        );
        Matrix4fc poseMatrix = poseStack.last().pose();
        for (BakedQuad quad : quads) {
            consumer.addBakedQuad(poseMatrix, quad, alphaMaskFor(quad));
        }
    }

    public static void renderCapturedShadow() {
        if (!enabled || renderedCaptureId == captureId)
            return;
        if (mc.player == null || mc.level == null)
            return;
        if (captureWidth <= 0 || captureHeight <= 0 || TRIANGLES.vertexCount() < 3)
            return;

        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        if (target == null || target.width <= 0 || target.height <= 0)
            return;

        if (GraphicsApiCompatibility.usesCompatibilityRenderer()) {
            renderedCaptureId = captureId;
            GraphicsApiCompatibility.renderOffscreenLayer(
                    GraphicsApiCompatibility.Layer.ESP,
                    () -> drawCapturedShadow(0, 0, 0, 0)
            );
            return;
        }

        MainTargetHandle targetHandle = mainTargetHandle(target);
        if (targetHandle == null)
            return;

        renderedCaptureId = captureId;
        drawCapturedShadow(
                targetHandle.framebuffer(),
                target.width,
                target.height,
                targetHandle.depthTexture()
        );
    }

    private static void drawCapturedShadow(int targetFramebuffer, int targetWidth, int targetHeight, int sourceDepthTexture) {
        Shader2DRenderer.INSTANCE.drawSilhouetteShadow(
                captureWidth,
                captureHeight,
                TRIANGLES.toBuffer(),
                TRIANGLES.vertexCount(),
                ARMOR_TRIANGLES.vertexCount() >= 3 ? ARMOR_TRIANGLES.toBuffer() : null,
                ARMOR_TRIANGLES.vertexCount(),
                80f,
                3f,
                80f / 255f,
                80f / 255f,
                130f / 255f,
                reversedDepth,
                Color.WHITE,
                targetFramebuffer,
                targetWidth,
                targetHeight,
                sourceDepthTexture
        );
    }

    public static void updateProjection(Matrix4fc projectionMatrix, Matrix4fc viewRotationMatrix) {
        if (!canCapture() || projectionMatrix == null || viewRotationMatrix == null)
            return;

        captureWidth = mc.getWindow().getWidth();
        captureHeight = mc.getWindow().getHeight();
        if (captureWidth <= 0 || captureHeight <= 0)
            return;

        VIEW_PROJECTION.set(projectionMatrix).mul(viewRotationMatrix);
        reversedDepth = isReversedDepthProjection(projectionMatrix);
        projectionReady = true;
    }

    private static boolean shouldCaptureState(Object state) {
        if (!(state instanceof EntityRenderState entityRenderState))
            return false;

        Entity entity = ((IEntityRenderState) entityRenderState).client$getEntity();
        if (!(entity instanceof Player))
            return false;

        if (mc.player == null || mc.level == null)
            return false;

        if (entity instanceof LocalPlayer)
            return local.get() && (!mc.options.getCameraType().isFirstPerson() || ModuleManager.getModuleState(Freecam.class));

        return true;
    }

    private static boolean canCapture() {
        return captureOpen && !GuiEntityRenderGuard.isActive();
    }

    private static boolean updateProjection() {
        if (mc.player == null || mc.level == null)
            return false;

        captureWidth = mc.getWindow().getWidth();
        captureHeight = mc.getWindow().getHeight();
        if (captureWidth <= 0 || captureHeight <= 0)
            return false;

        mc.gameRenderer.mainCamera().getViewRotationProjectionMatrix(VIEW_PROJECTION.identity());
        projectionReady = true;
        return true;
    }

    private static void resetCapture() {
        TRIANGLES.clear();
        ARMOR_TRIANGLES.clear();
        IMAGE_ALPHA_MASKS.clear();
        SPRITE_ALPHA_MASKS.clear();
        TEXTURE_ALPHA_MASKS.clear();
        ARMOR_RENDER_TYPES.clear();
        RENDER_TYPE_TEXTURES.clear();
        UNMASKED_TEXTURES.clear();
        captureOpen = false;
        projectionReady = false;
        reversedDepth = false;
        itemCaptureState = null;
        equipmentCaptureState = null;
        itemCaptureArmor = false;
        captureWidth = 0;
        captureHeight = 0;
        captureId++;
        renderedCaptureId = captureId;
    }

    private static AlphaMask alphaMaskFor(Model<?> model, Entity entity) {
        if (!(model instanceof PlayerModel) || !(entity instanceof AbstractClientPlayer player))
            return null;

        AbstractTexture texture = mc.getTextureManager().getTexture(player.getSkin().body().texturePath());
        if (!(texture instanceof DynamicTexture dynamicTexture))
            return null;

        NativeImage image = dynamicTexture.getPixels();
        if (image.isClosed() || image.getWidth() <= 0 || image.getHeight() <= 0)
            return null;

        return IMAGE_ALPHA_MASKS.computeIfAbsent(image, NativeImageAlphaMask::new);
    }

    private static ArmorRenderKind armorRenderKind(RenderType renderType) {
        if (renderType == null)
            return ArmorRenderKind.NONE;

        return ARMOR_RENDER_TYPES.computeIfAbsent(renderType, ESP::detectArmorRenderKind);
    }

    private static ArmorRenderKind detectArmorRenderKind(RenderType renderType) {
        String name = renderType.toString().toLowerCase(Locale.ROOT);
        if (name.contains("armor_entity_glint")
                || name.contains("armor_trims")
                || name.contains("textures/atlas/armor_trims")) {
            return ArmorRenderKind.OVERLAY;
        }

        if (name.contains("armor_cutout_no_cull")
                || name.contains("armor_decal_cutout_no_cull")
                || name.contains("armor_translucent")
                || name.contains("textures/models/armor")) {
            return ArmorRenderKind.ARMOR;
        }

        return ArmorRenderKind.NONE;
    }

    private static AlphaMask alphaMaskFor(BakedQuad quad) {
        if (quad == null)
            return null;

        return alphaMaskFor(quad.materialInfo().sprite());
    }

    private static AlphaMask alphaMaskFor(TextureAtlasSprite sprite) {
        if (sprite == null || !sprite.contents().transparency().hasTransparent())
            return null;

        return SPRITE_ALPHA_MASKS.computeIfAbsent(sprite, SpriteAlphaMask::new);
    }

    private static AlphaMask alphaMaskFor(RenderType renderType) {
        Identifier texture = textureIdentifierFor(renderType);
        if (texture == null || UNMASKED_TEXTURES.contains(texture))
            return null;

        AlphaMask cached = TEXTURE_ALPHA_MASKS.get(texture);
        if (cached != null)
            return cached;

        AlphaMask loaded = loadTextureAlphaMask(texture);
        if (loaded == null) {
            UNMASKED_TEXTURES.add(texture);
            return null;
        }

        TEXTURE_ALPHA_MASKS.put(texture, loaded);
        return loaded;
    }

    private static Identifier textureIdentifierFor(RenderType renderType) {
        if (renderType == null)
            return null;

        if (RENDER_TYPE_TEXTURES.containsKey(renderType))
            return RENDER_TYPE_TEXTURES.get(renderType);

        String name = renderType.toString().toLowerCase(Locale.ROOT);
        int start = 0;
        while (start < name.length()) {
            int extension = name.indexOf(".png", start);
            if (extension < 0)
                break;

            int candidateStart = findTextureIdentifierStart(name, extension);
            if (candidateStart >= 0) {
                Identifier texture = Identifier.tryParse(name.substring(candidateStart, extension + 4));
                if (texture != null) {
                    RENDER_TYPE_TEXTURES.put(renderType, texture);
                    return texture;
                }
            }

            start = extension + 4;
        }

        RENDER_TYPE_TEXTURES.put(renderType, null);
        return null;
    }

    private static int findTextureIdentifierStart(String text, int extensionStart) {
        int namespaceSeparator = -1;
        int index = extensionStart - 1;

        while (index >= 0 && isTexturePathCharacter(text.charAt(index))) {
            if (text.charAt(index) == ':')
                namespaceSeparator = index;
            index--;
        }

        if (namespaceSeparator <= index + 1 || namespaceSeparator >= extensionStart)
            return -1;

        for (int i = index + 1; i < namespaceSeparator; i++) {
            if (!isTextureNamespaceCharacter(text.charAt(i)))
                return -1;
        }

        return index + 1;
    }

    private static boolean isTextureNamespaceCharacter(char c) {
        return c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-';
    }

    private static boolean isTexturePathCharacter(char c) {
        return isTextureNamespaceCharacter(c) || c == '/' || c == ':';
    }

    private static AlphaMask loadTextureAlphaMask(Identifier texture) {
        Optional<Resource> resource = mc.getResourceManager().getResource(texture);
        if (resource.isEmpty())
            return null;

        try (InputStream inputStream = resource.get().open(); NativeImage image = NativeImage.read(inputStream)) {
            if (image.isClosed() || image.getWidth() <= 0 || image.getHeight() <= 0 || !image.computeTransparency().hasTransparent())
                return null;

            return new NativeImageAlphaMask(image);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static Entity getCapturedEntity(Object state) {
        if (!(state instanceof EntityRenderState entityRenderState))
            return null;

        return ((IEntityRenderState) entityRenderState).client$getEntity();
    }

    private static MainTargetHandle mainTargetHandle(RenderTarget target) {
        if (target == null || target.width <= 0 || target.height <= 0)
            return null;

        GpuDeviceBackend backend = RenderSystem.getDevice().backend;
        if (!(backend instanceof GlDevice glDevice)
                || !(target.getColorTextureView() instanceof GlTextureView colorView)
                || !(target.getDepthTextureView() instanceof GlTextureView depthView))
            return null;

        int framebuffer = glDevice.frameBufferCache().getFbo(glDevice.directStateAccess(), List.of(colorView), depthView);
        int depthTexture = depthView.glId();
        return framebuffer > 0 && depthTexture > 0
                ? new MainTargetHandle(framebuffer, depthTexture)
                : null;
    }

    private record MainTargetHandle(int framebuffer, int depthTexture) {
    }

    private static boolean isReversedDepthProjection(Matrix4fc projectionMatrix) {
        DepthDirectionSample negativeZ = depthDirectionSample(projectionMatrix, -1f, -512f);
        DepthDirectionSample positiveZ = depthDirectionSample(projectionMatrix, 1f, 512f);
        DepthDirectionSample selected = positiveZ.score() > negativeZ.score() ? positiveZ : negativeZ;
        return selected.valid() && selected.nearDepth > selected.farDepth;
    }

    private static DepthDirectionSample depthDirectionSample(Matrix4fc projectionMatrix, float nearZ, float farZ) {
        return new DepthDirectionSample(projectedDepth(projectionMatrix, nearZ), projectedDepth(projectionMatrix, farZ));
    }

    private static float projectedDepth(Matrix4fc projectionMatrix, float viewZ) {
        Vector4f projected = new Vector4f(0f, 0f, viewZ, 1f).mul(projectionMatrix);
        if (Math.abs(projected.w) < 1.0e-6f)
            return Float.NaN;

        return projected.z / projected.w;
    }

    private static void addScreenTriangle(FloatArrayBuilder target, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz) {
        float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (Math.abs(area) < 0.01f)
            return;

        target.add(ax, ay, az);
        target.add(bx, by, bz);
        target.add(cx, cy, cz);
    }

    private static final class DepthDirectionSample {
        private final float nearDepth;
        private final float farDepth;

        private DepthDirectionSample(float nearDepth, float farDepth) {
            this.nearDepth = nearDepth;
            this.farDepth = farDepth;
        }

        private boolean valid() {
            return Float.isFinite(nearDepth) && Float.isFinite(farDepth);
        }

        private float score() {
            if (!valid())
                return -1f;

            float score = 0f;
            if (nearDepth >= -1.1f && nearDepth <= 1.1f)
                score += 2f;
            if (farDepth >= -1.1f && farDepth <= 1.1f)
                score += 2f;

            return score + Math.min(Math.abs(nearDepth - farDepth), 1f);
        }
    }

    private static final class SilhouetteVertexConsumer implements VertexConsumer {
        private Matrix4fc viewProjection;
        private int width;
        private int height;
        private AlphaMask fallbackAlphaMask;
        private FloatArrayBuilder target;
        private final Vector4f vector = new Vector4f();
        private final Vector3f itemVector = new Vector3f();
        private final float[] quadX = new float[4];
        private final float[] quadY = new float[4];
        private final float[] quadZ = new float[4];
        private final float[] quadU = new float[4];
        private final float[] quadV = new float[4];
        private final boolean[] valid = new boolean[4];
        private AlphaMask activeAlphaMask;
        private int quadIndex = 0;

        private SilhouetteVertexConsumer() {
        }

        private SilhouetteVertexConsumer reset(Matrix4fc viewProjection, int width, int height,
                                               AlphaMask alphaMask, FloatArrayBuilder target) {
            this.viewProjection = viewProjection;
            this.width = width;
            this.height = height;
            this.fallbackAlphaMask = alphaMask;
            this.activeAlphaMask = alphaMask;
            this.target = target;
            this.quadIndex = 0;
            return this;
        }

        @Override
        public @NonNull VertexConsumer addVertex(float x, float y, float z) {
            addVertex(x, y, z, 0f, 0f);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
            addVertex(x, y, z, u, v);
        }

        private void addVertex(float x, float y, float z, float u, float v) {
            int index = quadIndex++;
            quadU[index] = u;
            quadV[index] = v;
            projectVertex(index, x, y, z);

            if (quadIndex == 4) {
                emitQuad();
                quadIndex = 0;
            }
        }

        private void addBakedQuad(Matrix4fc poseMatrix, BakedQuad quad, AlphaMask alphaMask) {
            activeAlphaMask = alphaMask;
            for (int i = 0; i < 4; i++) {
                Vector3fc position = quad.position(i);
                long packedUv = quad.packedUV(i);
                poseMatrix.transformPosition(position.x(), position.y(), position.z(), itemVector);
                addVertex(itemVector.x, itemVector.y, itemVector.z, UVPair.unpackU(packedUv), UVPair.unpackV(packedUv));
            }
            activeAlphaMask = fallbackAlphaMask;
        }

        private void projectVertex(int index, float x, float y, float z) {
            Vector4f projected = vector.set(x, y, z, 1f).mul(viewProjection);
            if (Math.abs(projected.w) < 1e-6f || Float.isNaN(projected.w) || Float.isInfinite(projected.w)) {
                valid[index] = false;
                return;
            }

            float ndcX = projected.x / projected.w;
            float ndcY = projected.y / projected.w;
            float ndcZ = projected.z / projected.w;
            if (Float.isNaN(ndcX) || Float.isNaN(ndcY) || Float.isNaN(ndcZ)
                    || Float.isInfinite(ndcX) || Float.isInfinite(ndcY) || Float.isInfinite(ndcZ)
                    || ndcZ < -1f || ndcZ > 1f) {
                valid[index] = false;
                return;
            }

            quadX[index] = (ndcX * 0.5f + 0.5f) * width;
            quadY[index] = (1f - (ndcY * 0.5f + 0.5f)) * height;
            quadZ[index] = ndcZ;
            valid[index] = true;
        }

        private void emitQuad() {
            if (!valid[0] || !valid[1] || !valid[2] || !valid[3])
                return;

            float minX = Math.min(Math.min(quadX[0], quadX[1]), Math.min(quadX[2], quadX[3]));
            float maxX = Math.max(Math.max(quadX[0], quadX[1]), Math.max(quadX[2], quadX[3]));
            float minY = Math.min(Math.min(quadY[0], quadY[1]), Math.min(quadY[2], quadY[3]));
            float maxY = Math.max(Math.max(quadY[0], quadY[1]), Math.max(quadY[2], quadY[3]));
            if (maxX < -64f || minX > width + 64f || maxY < -64f || minY > height + 64f)
                return;

            AlphaMask alphaMask = activeAlphaMask;
            if (alphaMask != null) {
                emitAlphaAwareQuad();
                return;
            }

            addTriangle(0, 1, 2);
            addTriangle(0, 2, 3);
        }

        private void emitAlphaAwareQuad() {
            AlphaMask alphaMask = activeAlphaMask;
            if (alphaMask == null)
                return;

            float minU = Math.min(Math.min(quadU[0], quadU[1]), Math.min(quadU[2], quadU[3]));
            float maxU = Math.max(Math.max(quadU[0], quadU[1]), Math.max(quadU[2], quadU[3]));
            float minV = Math.min(Math.min(quadV[0], quadV[1]), Math.min(quadV[2], quadV[3]));
            float maxV = Math.max(Math.max(quadV[0], quadV[1]), Math.max(quadV[2], quadV[3]));
            if (alphaMask.isRegionTransparent(minU, minV, maxU, maxV))
                return;
            if (alphaMask.isRegionOpaque(minU, minV, maxU, maxV)) {
                addTriangle(0, 1, 2);
                addTriangle(0, 2, 3);
                return;
            }

            int stepsU = clampSteps((int) Math.ceil(Math.abs(alphaMask.localU(maxU) - alphaMask.localU(minU)) * alphaMask.width()));
            int stepsV = clampSteps((int) Math.ceil(Math.abs(alphaMask.localV(maxV) - alphaMask.localV(minV)) * alphaMask.height()));

            for (int y = 0; y < stepsV; y++) {
                float t0 = y / (float) stepsV;
                float t1 = (y + 1) / (float) stepsV;
                for (int x = 0; x < stepsU; x++) {
                    float s0 = x / (float) stepsU;
                    float s1 = (x + 1) / (float) stepsU;
                    float sampleU = interpolateQuad(quadU, (s0 + s1) * 0.5f, (t0 + t1) * 0.5f);
                    float sampleV = interpolateQuad(quadV, (s0 + s1) * 0.5f, (t0 + t1) * 0.5f);
                    if (!alphaMask.isOpaque(sampleU, sampleV))
                        continue;

                    emitSubQuad(s0, t0, s1, t1);
                }
            }
        }

        private int clampSteps(int steps) {
            return Math.clamp(steps, 1, 16);
        }

        private void emitSubQuad(float s0, float t0, float s1, float t1) {
            float x0 = interpolateQuad(quadX, s0, t0);
            float y0 = interpolateQuad(quadY, s0, t0);
            float z0 = interpolateQuad(quadZ, s0, t0);
            float x1 = interpolateQuad(quadX, s1, t0);
            float y1 = interpolateQuad(quadY, s1, t0);
            float z1 = interpolateQuad(quadZ, s1, t0);
            float x2 = interpolateQuad(quadX, s1, t1);
            float y2 = interpolateQuad(quadY, s1, t1);
            float z2 = interpolateQuad(quadZ, s1, t1);
            float x3 = interpolateQuad(quadX, s0, t1);
            float y3 = interpolateQuad(quadY, s0, t1);
            float z3 = interpolateQuad(quadZ, s0, t1);

            addTriangle(x0, y0, z0, x1, y1, z1, x2, y2, z2);
            addTriangle(x0, y0, z0, x2, y2, z2, x3, y3, z3);
        }

        private float interpolateQuad(float[] values, float s, float t) {
            float top = values[0] + (values[1] - values[0]) * s;
            float bottom = values[3] + (values[2] - values[3]) * s;
            return top + (bottom - top) * t;
        }

        private void addTriangle(int a, int b, int c) {
            addTriangle(quadX[a], quadY[a], quadZ[a], quadX[b], quadY[b], quadZ[b], quadX[c], quadY[c], quadZ[c]);
        }

        private void addTriangle(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz) {
            addScreenTriangle(target, ax, ay, az, bx, by, bz, cx, cy, cz);
        }

        @Override
        public @NonNull VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public @NonNull VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public @NonNull VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public @NonNull VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public @NonNull VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public @NonNull VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public @NonNull VertexConsumer setLineWidth(float width) {
            return this;
        }
    }

    private interface AlphaMask {
        boolean isOpaque(float u, float v);

        default float localU(float u) {
            return u;
        }

        default float localV(float v) {
            return v;
        }

        int width();

        int height();

        boolean isRegionOpaque(float minU, float minV, float maxU, float maxV);

        boolean isRegionTransparent(float minU, float minV, float maxU, float maxV);
    }

    private static final class NativeImageAlphaMask implements AlphaMask {
        private final int width;
        private final int height;
        private final byte[] opaque;
        private final int[] opaquePrefix;

        private NativeImageAlphaMask(NativeImage image) {
            width = image.getWidth();
            height = image.getHeight();
            opaque = new byte[width * height];
            opaquePrefix = new int[(width + 1) * (height + 1)];
            for (int y = 0; y < height; y++) {
                int rowOpaque = 0;
                for (int x = 0; x < width; x++) {
                    int opaqueValue = (((image.getPixel(x, y) >>> 24) & 0xFF) > 16) ? 1 : 0;
                    opaque[y * width + x] = (byte) opaqueValue;
                    rowOpaque += opaqueValue;
                    opaquePrefix[(y + 1) * (width + 1) + x + 1] = opaquePrefix[y * (width + 1) + x + 1] + rowOpaque;
                }
            }
        }

        @Override
        public boolean isOpaque(float u, float v) {
            int x = Math.clamp((int) Math.floor(u * width), 0, width - 1);
            int y = Math.clamp((int) Math.floor(v * height), 0, height - 1);
            return opaque[y * width + x] != 0;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public boolean isRegionOpaque(float minU, float minV, float maxU, float maxV) {
            RegionCoverage coverage = regionCoverage(this, minU, minV, maxU, maxV);
            int area = coverage.area();
            return area > 0 && opaqueCount(coverage) == area;
        }

        @Override
        public boolean isRegionTransparent(float minU, float minV, float maxU, float maxV) {
            RegionCoverage coverage = regionCoverage(this, minU, minV, maxU, maxV);
            return coverage.area() > 0 && opaqueCount(coverage) == 0;
        }

        private int opaqueCount(RegionCoverage coverage) {
            return regionOpaqueCount(opaquePrefix, width, coverage);
        }
    }

    private static final class SpriteAlphaMask implements AlphaMask {
        private final float u0;
        private final float v0;
        private final float uScale;
        private final float vScale;
        private final int width;
        private final int height;
        private final byte[] opaque;
        private final int[] opaquePrefix;

        private SpriteAlphaMask(TextureAtlasSprite sprite) {
            SpriteContents contents = sprite.contents();
            u0 = sprite.getU0();
            v0 = sprite.getV0();
            uScale = 1f / Math.max(1.0e-6f, sprite.getU1() - sprite.getU0());
            vScale = 1f / Math.max(1.0e-6f, sprite.getV1() - sprite.getV0());
            width = Math.max(1, contents.width());
            height = Math.max(1, contents.height());
            opaque = new byte[width * height];
            opaquePrefix = new int[(width + 1) * (height + 1)];
            for (int y = 0; y < height; y++) {
                int rowOpaque = 0;
                for (int x = 0; x < width; x++) {
                    int opaqueValue = contents.isTransparent(0, x, y) ? 0 : 1;
                    opaque[y * width + x] = (byte) opaqueValue;
                    rowOpaque += opaqueValue;
                    opaquePrefix[(y + 1) * (width + 1) + x + 1] = opaquePrefix[y * (width + 1) + x + 1] + rowOpaque;
                }
            }
        }

        @Override
        public boolean isOpaque(float u, float v) {
            int x = Math.clamp((int) Math.floor(localU(u) * width), 0, width - 1);
            int y = Math.clamp((int) Math.floor(localV(v) * height), 0, height - 1);
            return opaque[y * width + x] != 0;
        }

        @Override
        public float localU(float u) {
            return (u - u0) * uScale;
        }

        @Override
        public float localV(float v) {
            return (v - v0) * vScale;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public boolean isRegionOpaque(float minU, float minV, float maxU, float maxV) {
            RegionCoverage coverage = regionCoverage(this, minU, minV, maxU, maxV);
            int area = coverage.area();
            return area > 0 && opaqueCount(coverage) == area;
        }

        @Override
        public boolean isRegionTransparent(float minU, float minV, float maxU, float maxV) {
            RegionCoverage coverage = regionCoverage(this, minU, minV, maxU, maxV);
            return coverage.area() > 0 && opaqueCount(coverage) == 0;
        }

        private int opaqueCount(RegionCoverage coverage) {
            return regionOpaqueCount(opaquePrefix, width, coverage);
        }
    }

    private enum ArmorRenderKind {
        NONE,
        ARMOR,
        OVERLAY
    }

    private record RegionCoverage(int x0, int y0, int x1, int y1) {
        private int area() {
            return Math.max(0, x1 - x0) * Math.max(0, y1 - y0);
        }
    }

    private static RegionCoverage regionCoverage(AlphaMask alphaMask, float minU, float minV, float maxU, float maxV) {
        float localMinU = Math.min(alphaMask.localU(minU), alphaMask.localU(maxU));
        float localMaxU = Math.max(alphaMask.localU(minU), alphaMask.localU(maxU));
        float localMinV = Math.min(alphaMask.localV(minV), alphaMask.localV(maxV));
        float localMaxV = Math.max(alphaMask.localV(minV), alphaMask.localV(maxV));
        int x0 = regionStart(localMinU, alphaMask.width());
        int x1 = regionEnd(localMaxU, alphaMask.width(), x0);
        int y0 = regionStart(localMinV, alphaMask.height());
        int y1 = regionEnd(localMaxV, alphaMask.height(), y0);
        return new RegionCoverage(x0, y0, x1, y1);
    }

    private static int regionStart(float local, int size) {
        return Math.clamp((int) Math.floor(local * size), 0, Math.max(0, size - 1));
    }

    private static int regionEnd(float local, int size, int start) {
        return Math.clamp(Math.max(start + 1, (int) Math.ceil(local * size)), start + 1, size);
    }

    private static int regionOpaqueCount(int[] prefix, int width, RegionCoverage coverage) {
        int stride = width + 1;
        return prefix[coverage.y1 * stride + coverage.x1]
                - prefix[coverage.y0 * stride + coverage.x1]
                - prefix[coverage.y1 * stride + coverage.x0]
                + prefix[coverage.y0 * stride + coverage.x0];
    }

    private static final class FloatArrayBuilder {
        private float[] values;
        private int size;
        private FloatBuffer uploadBuffer;

        private FloatArrayBuilder(int initialCapacity) {
            values = new float[Math.max(2, initialCapacity)];
        }

        private void add(float x, float y, float z) {
            ensureCapacity(size + 3);
            values[size++] = x;
            values[size++] = y;
            values[size++] = z;
        }

        private int vertexCount() {
            return size / 3;
        }

        private FloatBuffer toBuffer() {
            if (uploadBuffer == null || uploadBuffer.capacity() < size)
                uploadBuffer = BufferUtils.createFloatBuffer(nextCapacity(size));

            uploadBuffer.clear();
            uploadBuffer.put(values, 0, size);
            uploadBuffer.flip();
            return uploadBuffer;
        }

        private void clear() {
            size = 0;
        }

        private void ensureCapacity(int required) {
            if (required <= values.length)
                return;

            values = Arrays.copyOf(values, nextCapacity(required));
        }

        private int nextCapacity(int required) {
            int nextCapacity = values.length;
            while (nextCapacity < required) {
                nextCapacity *= 2;
            }
            return nextCapacity;
        }
    }
}
