package com.instrumentalist.krs.hacks.features.level;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.render.GraphicsApiCompatibility;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.instrumentalist.mixin.injector.PipelineCacheAccessor;
import com.instrumentalist.mixin.injector.RenderSystemAccessor;
import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;

import java.util.Iterator;
import java.util.Map;
import com.instrumentalist.krs.utils.input.Keys;

public class CaveFinder extends Module {
    private static volatile boolean active;

    public CaveFinder() {
        super("Cave Finder", ModuleCategory.Level, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        active = true;
        reloadVulkanPipelines();
        reloadChunks();
    }

    @Override
    public void onDisable() {
        active = false;
        reloadVulkanPipelines();
        reloadChunks();
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean shouldCullFront(RenderPipeline pipeline) {
        if (pipeline == null || pipeline.getLocation() == null)
            return false;

        return shouldCullFront(pipeline.getLocation().getPath());
    }

    public static boolean shouldCullFront(String location) {
        return active && isTerrainPipeline(location);
    }

    private static boolean isTerrainPipeline(String location) {
        return location != null
                && (location.endsWith("pipeline/solid_terrain")
                || location.endsWith("pipeline/cutout_terrain"));
    }

    private void reloadChunks() {
        if (mc.level != null)
            mc.levelExtractor.allChanged();
    }

    private static void reloadVulkanPipelines() {
        // Vulkan bakes the cull mode into the compiled pipeline, so drop the cached terrain pipelines and let
        // RenderSystem recompile them with the new cull mode on next use. OpenGL applies it on every bind instead.
        if (!GraphicsApiCompatibility.usesCompatibilityRenderer())
            return;

        if (!RenderSystem.isOnRenderThread()) {
            mc.execute(CaveFinder::reloadVulkanPipelines);
            return;
        }

        invalidateTerrainPipelines(RenderSystemAccessor.krs$getCurrentPipelineCache());
        invalidateTerrainPipelines(RenderSystemAccessor.krs$getFallbackPipelineCache());
    }

    private static void invalidateTerrainPipelines(PipelineCache pipelineCache) {
        if (pipelineCache == null)
            return;

        Iterator<Map.Entry<RenderPipeline, CompiledRenderPipeline>> iterator =
                ((PipelineCacheAccessor) pipelineCache).krs$getCache().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<RenderPipeline, CompiledRenderPipeline> entry = iterator.next();
            RenderPipeline pipeline = entry.getKey();
            if (pipeline == null || pipeline.getLocation() == null || !isTerrainPipeline(pipeline.getLocation().getPath()))
                continue;

            iterator.remove();
            entry.getValue().close();
        }
    }
}
