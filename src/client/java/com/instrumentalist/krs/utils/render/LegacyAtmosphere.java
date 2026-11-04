package com.instrumentalist.krs.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

import java.util.IdentityHashMap;
import java.util.Map;

public final class LegacyAtmosphere {

    private static final int SKY_PLANE_QUADS = 13 * 13;
    private static final int HORIZON_BOX_QUADS = 5;

    private static final RenderPipeline SKY = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("krs", "pipeline/legacy_sky"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("krs", "core/legacy_sky"))
                    .withFragmentShader("core/sky")
                    .withVertexBinding(0, DefaultVertexFormat.POSITION)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .withCull(false)
                    .build());

    private static final RenderPipeline CLOUDS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.CLOUDS_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("krs", "pipeline/legacy_clouds"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("krs", "core/legacy_clouds"))
                    .build());

    private static final RenderPipeline FLAT_CLOUDS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.CLOUDS_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("krs", "pipeline/legacy_flat_clouds"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("krs", "core/legacy_clouds"))
                    .withCull(false)
                    .build());

    private static final OitPipelineSet OIT_CLOUDS = RenderPipelines.register(
            OitPipelineSet.builder("krs_legacy_clouds", RenderPipeline.builder(RenderPipelines.OIT_CLOUDS_SNIPPET)
                            .withFragmentShader(Identifier.fromNamespaceAndPath("krs", "core/legacy_clouds")))
                    .withDepthBoundsModifier(depthBounds -> depthBounds.withDepthStencilState(DepthStencilState.DEFAULT))
                    .build());

    private static final OitPipelineSet OIT_FLAT_CLOUDS = RenderPipelines.register(
            OitPipelineSet.builder("krs_legacy_flat_clouds", RenderPipeline.builder(RenderPipelines.OIT_CLOUDS_SNIPPET)
                            .withFragmentShader(Identifier.fromNamespaceAndPath("krs", "core/legacy_clouds"))
                            .withCull(false))
                    .build());

    private static final Map<RenderPipeline, RenderPipeline> CLOUD_PIPELINES = new IdentityHashMap<>();
    private static final Map<RenderPipeline, Boolean> AVAILABILITY = new IdentityHashMap<>();

    static {
        CLOUD_PIPELINES.put(RenderPipelines.CLOUDS, CLOUDS);
        CLOUD_PIPELINES.put(RenderPipelines.FLAT_CLOUDS, FLAT_CLOUDS);

        for (OitStage stage : new OitStage[]{OitStage.DEPTH_BOUNDS, OitStage.TRANSMITTANCE, OitStage.ACCUMULATE}) {
            CLOUD_PIPELINES.put(RenderPipelines.OIT_CLOUDS.getPipeline(stage), OIT_CLOUDS.getPipeline(stage));
            CLOUD_PIPELINES.put(RenderPipelines.OIT_FLAT_CLOUDS.getPipeline(stage), OIT_FLAT_CLOUDS.getPipeline(stage));
        }

        try {
            var copy = Class.forName("net.irisshaders.iris.pipeline.IrisPipelines")
                    .getMethod("copyPipeline", RenderPipeline.class, RenderPipeline.class);
            copy.invoke(null, RenderPipelines.SKY, SKY);
            copy.invoke(null, RenderPipelines.CLOUDS, CLOUDS);
            copy.invoke(null, RenderPipelines.FLAT_CLOUDS, FLAT_CLOUDS);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static GpuBuffer upperSkyPlane;
    private static GpuBuffer lowerSkyPlane;
    private static GpuBuffer horizonBox;

    private LegacyAtmosphere() {
    }

    private static boolean available(RenderPipeline pipeline) {
        return AVAILABILITY.computeIfAbsent(pipeline, key -> {
            try {
                CompiledRenderPipeline compiled = RenderSystem.getCompiledPipelineNullable(key);
                return compiled != null;
            } catch (RuntimeException exception) {
                return false;
            }
        });
    }

    public static RenderPipeline cloudPipeline(RenderPipeline original) {
        RenderPipeline legacy = CLOUD_PIPELINES.get(original);
        if (legacy == null || !available(legacy))
            return original;

        return legacy;
    }

    public static boolean renderUpperSky(RenderPass renderPass, Vector3fc skyColor) {
        if (!available(SKY)) return false;

        if (upperSkyPlane == null)
            upperSkyPlane = buildSkyPlane("Legacy upper sky", 16.0F, false);

        renderPass.pushDebugGroup(() -> "Legacy sky");
        draw(renderPass, upperSkyPlane, SKY_PLANE_QUADS, RenderSystem.getModelViewMatrixCopy(), new Vector4f(skyColor, 1.0F));
        renderPass.popDebugGroup();
        return true;
    }

    public static boolean renderLowerSky(RenderPass renderPass, Vector3fc skyColor, double horizonDistance) {
        if (!available(SKY)) return false;

        if (lowerSkyPlane == null)
            lowerSkyPlane = buildSkyPlane("Legacy lower sky", -16.0F, true);
        if (horizonBox == null)
            horizonBox = buildHorizonBox();

        Vector4f black = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
        renderPass.pushDebugGroup(() -> "Legacy lower sky");

        if (horizonDistance < 0.0D) {
            draw(renderPass, lowerSkyPlane, SKY_PLANE_QUADS, RenderSystem.getModelViewMatrixCopy().translate(0.0F, 12.0F, 0.0F), black);

            float depth = (float) (horizonDistance + 64.0D);
            if (depth > 0.0F)
                draw(renderPass, horizonBox, HORIZON_BOX_QUADS, RenderSystem.getModelViewMatrixCopy().translate(0.0F, -1.0F, 0.0F).scale(1.0F, depth, 1.0F), black);
        } else if (horizonDistance > 0.0D) {
            Vector4f color = new Vector4f(skyColor.x() * 0.2F + 0.04F, skyColor.y() * 0.2F + 0.04F, skyColor.z() * 0.6F + 0.1F, 1.0F);
            draw(renderPass, lowerSkyPlane, SKY_PLANE_QUADS, RenderSystem.getModelViewMatrixCopy().translate(0.0F, -(float) (horizonDistance - 16.0D), 0.0F), color);
        }

        renderPass.popDebugGroup();
        return true;
    }

    private static void draw(RenderPass renderPass, GpuBuffer vertices, int quads, Matrix4f modelView, Vector4f color) {
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        int indexCount = quads * 6;
        GpuBuffer indexBuffer = indices.getBuffer(indexCount);

        renderPass.setPipeline(RenderSystem.getCompiledPipeline(SKY));
        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(modelView, color));
        renderPass.setVertexBuffer(0, vertices.slice());
        renderPass.setIndexBuffer(indexBuffer, indices.type());
        renderPass.drawIndexed(indexCount, 1, 0, 0, 0);
    }

    private static GpuBuffer buildSkyPlane(String name, float y, boolean reverseX) {
        try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(SKY_PLANE_QUADS * 4 * DefaultVertexFormat.POSITION.getVertexSize())) {
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);

            for (int k = -384; k <= 384; k += 64) {
                for (int l = -384; l <= 384; l += 64) {
                    float f = (float) k;
                    float f1 = (float) (k + 64);

                    if (reverseX) {
                        f1 = (float) k;
                        f = (float) (k + 64);
                    }

                    bufferBuilder.addVertex(f, y, (float) l);
                    bufferBuilder.addVertex(f1, y, (float) l);
                    bufferBuilder.addVertex(f1, y, (float) (l + 64));
                    bufferBuilder.addVertex(f, y, (float) (l + 64));
                }
            }

            try (MeshData mesh = bufferBuilder.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> name, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
        }
    }

    private static GpuBuffer buildHorizonBox() {
        try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(HORIZON_BOX_QUADS * 4 * DefaultVertexFormat.POSITION.getVertexSize())) {
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);

            bufferBuilder.addVertex(-1.0F, -1.0F, 1.0F);
            bufferBuilder.addVertex(1.0F, -1.0F, 1.0F);
            bufferBuilder.addVertex(1.0F, 0.0F, 1.0F);
            bufferBuilder.addVertex(-1.0F, 0.0F, 1.0F);

            bufferBuilder.addVertex(-1.0F, 0.0F, -1.0F);
            bufferBuilder.addVertex(1.0F, 0.0F, -1.0F);
            bufferBuilder.addVertex(1.0F, -1.0F, -1.0F);
            bufferBuilder.addVertex(-1.0F, -1.0F, -1.0F);

            bufferBuilder.addVertex(1.0F, 0.0F, -1.0F);
            bufferBuilder.addVertex(1.0F, 0.0F, 1.0F);
            bufferBuilder.addVertex(1.0F, -1.0F, 1.0F);
            bufferBuilder.addVertex(1.0F, -1.0F, -1.0F);

            bufferBuilder.addVertex(-1.0F, -1.0F, -1.0F);
            bufferBuilder.addVertex(-1.0F, -1.0F, 1.0F);
            bufferBuilder.addVertex(-1.0F, 0.0F, 1.0F);
            bufferBuilder.addVertex(-1.0F, 0.0F, -1.0F);

            bufferBuilder.addVertex(-1.0F, -1.0F, -1.0F);
            bufferBuilder.addVertex(1.0F, -1.0F, -1.0F);
            bufferBuilder.addVertex(1.0F, -1.0F, 1.0F);
            bufferBuilder.addVertex(-1.0F, -1.0F, 1.0F);

            try (MeshData mesh = bufferBuilder.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> "Legacy horizon box", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
        }
    }
}
