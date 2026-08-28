package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.player.Freecam;
import com.instrumentalist.krs.hacks.features.player.MurdererDetector;
import com.instrumentalist.krs.utils.entity.StreamConverter;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.nanovg.NanoVGTextFormatter;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import com.instrumentalist.krs.utils.render.RenderUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

public class NameTags extends Module {
    private static final int MAX_RENDERED_NAME_TAGS = 256;
    private static final int MAX_VISIBLE_REGIONS = 64;
    private static final Color NAME_COLOR = new Color(235, 240, 244, 230);
    private static final Color TAG_COLOR = new Color(255, 30, 30, 200);
    private static final Comparator<NameTagRenderData> DEPTH_ORDER = Comparator.comparingDouble(NameTagRenderData::depthSquared);
    private static final Comparator<NameTagRenderData> FARTHEST_FIRST = DEPTH_ORDER.reversed();

    @Setting
    private static final BooleanValue onlyPlayers = new BooleanValue("Only Players", true);

    @Setting
    private static final BooleanValue local = new BooleanValue("Local", false);

    private final ArrayList<NameTagRenderData> data = new ArrayList<>(MAX_RENDERED_NAME_TAGS);
    private final ArrayList<NameTagRenderData> dataPool = new ArrayList<>(MAX_RENDERED_NAME_TAGS);
    private final PriorityQueue<NameTagRenderData> nearestData = new PriorityQueue<>(MAX_RENDERED_NAME_TAGS, FARTHEST_FIRST);
    private final ArrayList<NameTagRenderEntry> renderEntryBuffer = new ArrayList<>(MAX_RENDERED_NAME_TAGS);
    private final ArrayList<NameTagRenderEntry> renderEntryPool = new ArrayList<>(MAX_RENDERED_NAME_TAGS);
    private final ArrayList<NameTagRenderEntry> frontToBackBuffer = new ArrayList<>(MAX_RENDERED_NAME_TAGS);
    private final ArrayList<NameTagRenderEntry> visibleEntryBuffer = new ArrayList<>(MAX_RENDERED_NAME_TAGS);
    private final float[] clipScratchA = new float[MAX_VISIBLE_REGIONS * 4];
    private final float[] clipScratchB = new float[MAX_VISIBLE_REGIONS * 4];
    private final float[] projectedPosition = new float[3];
    private final float[] backgroundGeometry = new float[MAX_RENDERED_NAME_TAGS * 8];

    public NameTags() {
        super("Name Tags", ModuleCategory.Render, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
        clearFrameData();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onWorld(WorldEvent event) {
        clearFrameData();
    }

    private void clearFrameData() {
        data.clear();
        nearestData.clear();
        renderEntryBuffer.clear();
        frontToBackBuffer.clear();
        visibleEntryBuffer.clear();
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!RenderUtil.shouldRenderWorldHudOverlays()) {
            data.clear();
            return;
        }
        if (data.isEmpty()) return;

        Client.nanoVgManager.load(vg -> {
            ArrayList<NameTagRenderEntry> entries = renderEntryBuffer;
            entries.clear();
            int entryIndex = 0;
            float nameHeight = NVGFonts.INTER.getHeight(16f);
            float tagHeight = NVGFonts.INTER.getHeight(15f);

            for (NameTagRenderData tag : data) {
                String entityName = tag.entityName;
                String entityTag = tag.entityTag;
                float screenX = tag.screenX;
                float screenY = tag.screenY;

                tag.updateTextMeasurements();
                float entityWidth = tag.entityWidth;
                float tagWidth = tag.tagWidth;
                float nameWidth = Math.max(entityWidth, tagWidth);
                float tagExtended = entityTag != null ? tagHeight : 0f;

                while (renderEntryPool.size() <= entryIndex)
                    renderEntryPool.add(new NameTagRenderEntry());
                NameTagRenderEntry entry = renderEntryPool.get(entryIndex++);
                entry.update(
                        entityName,
                        entityTag,
                        screenX,
                        screenY,
                        screenX - nameWidth / 2f - 5f,
                        screenY - nameHeight / 2f - 3f - tagExtended,
                        nameWidth + 10f,
                        nameHeight + 6f + tagExtended,
                        tagExtended,
                        tag.depthSquared
                );
                entries.add(entry);
            }

            List<NameTagRenderEntry> visibleEntries = buildVisibleEntries(entries);
            if (visibleEntries.isEmpty()) return;

            vg.beginEffectBatch();
            for (NameTagRenderEntry entry : visibleEntries) {
                drawNameTagEffects(vg, entry);
            }
            vg.flushEffectBatch();

            boolean allFullyVisible = true;
            for (NameTagRenderEntry entry : visibleEntries) {
                if (!entry.fullyVisible) {
                    allFullyVisible = false;
                    break;
                }
            }

            if (allFullyVisible) {
                int backgroundCount = 0;
                for (NameTagRenderEntry entry : visibleEntries) {
                    int offset = backgroundCount++ * 8;
                    backgroundGeometry[offset] = entry.rectX;
                    backgroundGeometry[offset + 1] = entry.rectY;
                    backgroundGeometry[offset + 2] = entry.width;
                    backgroundGeometry[offset + 3] = entry.height;
                    backgroundGeometry[offset + 4] = 6f;
                    backgroundGeometry[offset + 5] = 6f;
                    backgroundGeometry[offset + 6] = 6f;
                    backgroundGeometry[offset + 7] = 6f;
                }
                vg.roundedRectangles(backgroundGeometry, backgroundCount, NanoVGTheme.COMPACT_BACKGROUND);
                for (NameTagRenderEntry entry : visibleEntries)
                    drawNameTagText(entry);
            } else {
                for (NameTagRenderEntry entry : visibleEntries)
                    renderVisibleRegions(vg, entry);
            }
        });
    }

    private List<NameTagRenderEntry> buildVisibleEntries(List<NameTagRenderEntry> entries) {
        frontToBackBuffer.clear();
        frontToBackBuffer.addAll(entries);
        frontToBackBuffer.sort((a, b) -> Double.compare(a.depthSquared, b.depthSquared));
        visibleEntryBuffer.clear();

        for (int index = 0, size = frontToBackBuffer.size(); index < size; index++) {
            NameTagRenderEntry entry = frontToBackBuffer.get(index);
            if (buildVisibleRegions(entry, index) > 0)
                visibleEntryBuffer.add(entry);
        }

        Collections.reverse(visibleEntryBuffer);
        return visibleEntryBuffer;
    }

    private int buildVisibleRegions(NameTagRenderEntry entry, int entryIndex) {
        float[] current = clipScratchA;
        float[] next = clipScratchB;
        current[0] = entry.rectX;
        current[1] = entry.rectY;
        current[2] = entry.width;
        current[3] = entry.height;
        int currentCount = 1;

        for (int occluderIndex = 0; occluderIndex < entryIndex && currentCount > 0; occluderIndex++) {
            NameTagRenderEntry occluder = frontToBackBuffer.get(occluderIndex);
            if (!intersects(entry.rectX, entry.rectY, entry.width, entry.height, occluder))
                continue;

            int nextCount = 0;
            for (int regionIndex = 0; regionIndex < currentCount; regionIndex++) {
                nextCount = subtractRect(current, regionIndex * 4, occluder, next, nextCount);
            }

            float[] swap = current;
            current = next;
            next = swap;
            currentCount = nextCount;
        }

        entry.setVisibleRects(current, currentCount);
        return currentCount;
    }

    private static boolean intersects(float x, float y, float width, float height, NameTagRenderEntry other) {
        return x < other.rectX + other.width && x + width > other.rectX
                && y < other.rectY + other.height && y + height > other.rectY;
    }

    private static int subtractRect(float[] source, int sourceOffset, NameTagRenderEntry occluder,
                                    float[] output, int outputCount) {
        float sourceX = source[sourceOffset];
        float sourceY = source[sourceOffset + 1];
        float sourceWidth = source[sourceOffset + 2];
        float sourceHeight = source[sourceOffset + 3];
        float sourceRight = sourceX + sourceWidth;
        float sourceBottom = sourceY + sourceHeight;
        float left = Math.max(sourceX, occluder.rectX);
        float top = Math.max(sourceY, occluder.rectY);
        float right = Math.min(sourceRight, occluder.rectX + occluder.width);
        float bottom = Math.min(sourceBottom, occluder.rectY + occluder.height);

        if (left >= right || top >= bottom)
            return addClipRect(output, outputCount, sourceX, sourceY, sourceWidth, sourceHeight);

        outputCount = addClipRect(output, outputCount, sourceX, sourceY, sourceWidth, top - sourceY);
        outputCount = addClipRect(output, outputCount, sourceX, bottom, sourceWidth, sourceBottom - bottom);
        outputCount = addClipRect(output, outputCount, sourceX, top, left - sourceX, bottom - top);
        return addClipRect(output, outputCount, right, top, sourceRight - right, bottom - top);
    }

    private static int addClipRect(float[] output, int outputCount, float x, float y, float width, float height) {
        if (outputCount >= MAX_VISIBLE_REGIONS || width <= 0.5f || height <= 0.5f)
            return outputCount;

        int offset = outputCount * 4;
        output[offset] = x;
        output[offset + 1] = y;
        output[offset + 2] = width;
        output[offset + 3] = height;
        return outputCount + 1;
    }

    private void renderVisibleRegions(NVGU vg, NameTagRenderEntry entry) {
        if (entry.fullyVisible) {
            drawNameTagBody(vg, entry);
            return;
        }

        for (int index = 0; index < entry.visibleRectCount; index++) {
            int offset = index * 4;
            vg.pushScissor(
                    entry.visibleRects[offset],
                    entry.visibleRects[offset + 1],
                    entry.visibleRects[offset + 2],
                    entry.visibleRects[offset + 3]
            );
            try {
                drawNameTagBody(vg, entry);
            } finally {
                vg.popScissor();
            }
        }
    }

    private void drawNameTagEffects(NVGU vg, NameTagRenderEntry entry) {
        NanoVGTheme.renderCompactEffects(vg, entry.rectX, entry.rectY, entry.width, entry.height, 6f, 1f);
    }

    private void drawNameTagBody(NVGU vg, NameTagRenderEntry entry) {
        NanoVGTheme.renderCompact(vg, entry.rectX, entry.rectY, entry.width, entry.height, 6f, 1f);
        drawNameTagText(entry);
    }

    private void drawNameTagText(NameTagRenderEntry entry) {
        NVGFonts.INTER.drawText(entry.entityName, entry.screenX, entry.screenY, 16f, NAME_COLOR, Alignment.CENTER_MIDDLE, false);
        if (entry.entityTag != null)
            NVGFonts.INTER.drawText(entry.entityTag, entry.screenX, entry.screenY - entry.tagExtended, 15f, TAG_COLOR, Alignment.CENTER_MIDDLE, false);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || !RenderUtil.shouldRenderWorldHudOverlays()) {
            data.clear();
            nearestData.clear();
            return;
        }
        var level = mc.level;
        if (level == null) {
            data.clear();
            nearestData.clear();
            return;
        }

        float partialTicks = event.partialTicks;
        Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
        boolean onlyPlayersEnabled = onlyPlayers.get();
        boolean murdererDetectorActive = ModuleManager.getModuleState(MurdererDetector.class);
        boolean renderLocal = local.get() && (!mc.options.getCameraType().isFirstPerson() || ModuleManager.getModuleState(Freecam.class));
        Iterable<? extends Entity> renderSource = onlyPlayersEnabled ? level.players() : level.entitiesForRendering();
        float framebufferToScaledX = NanoVGManager.getScaledScreenWidth() / Math.max(1, mc.getWindow().getWidth());
        float framebufferToScaledY = NanoVGManager.getScaledScreenHeight() / Math.max(1, mc.getWindow().getHeight());

        data.clear();
        nearestData.clear();
        int pooledDataCount = 0;

        for (Entity entity : renderSource) {
            boolean murderer = murdererDetectorActive && MurdererDetector.isMurderer(entity);
            if ((entity instanceof ArmorStand || onlyPlayersEnabled && !(entity instanceof Player)) && !murderer) continue;
            if (!renderLocal && entity instanceof LocalPlayer) continue;
            if (!RenderUtil.INSTANCE.isEntityInRenderedWorldFrustum(entity, partialTicks)) continue;

            Vec3 lerpedPos = RenderUtil.INSTANCE.getLerpedPos(entity, partialTicks);
            double worldX = lerpedPos.x;
            double worldY = lerpedPos.y + entity.getBbHeight() + 0.55f;
            double worldZ = lerpedPos.z;
            double cameraDeltaX = worldX - cameraPos.x;
            double cameraDeltaY = worldY - cameraPos.y;
            double cameraDeltaZ = worldZ - cameraPos.z;
            double depthSquared = cameraDeltaX * cameraDeltaX + cameraDeltaY * cameraDeltaY + cameraDeltaZ * cameraDeltaZ;
            NameTagRenderData farthest = nearestData.peek();
            if (nearestData.size() >= MAX_RENDERED_NAME_TAGS && farthest != null && depthSquared >= farthest.depthSquared)
                continue;

            if (!RenderUtil.INSTANCE.renderedWorldToScreen(worldX, worldY, worldZ, projectedPosition)) continue;

            NameTagRenderData renderData;
            if (nearestData.size() >= MAX_RENDERED_NAME_TAGS) {
                renderData = nearestData.poll();
            } else {
                while (dataPool.size() <= pooledDataCount)
                    dataPool.add(new NameTagRenderData());
                renderData = dataPool.get(pooledDataCount++);
            }

            renderData.update(
                    getEntityName(entity),
                    murderer ? "Murderer" : null,
                    projectedPosition[0] * framebufferToScaledX,
                    projectedPosition[1] * framebufferToScaledY,
                    depthSquared
            );
            nearestData.offer(renderData);
        }

        data.addAll(nearestData);
        nearestData.clear();
        data.sort(DEPTH_ORDER);
    }

    private static String getEntityName(Entity entity) {
        if (entity instanceof Player || entity.hasCustomName())
            return NanoVGTextFormatter.formatColors(entity.getDisplayName());

        return StreamConverter.formatNameByPath(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath());
    }

    public static boolean shouldRender(Entity entity) {
        return RenderUtil.shouldRenderWorldHudOverlays()
                && (
                        !(entity instanceof ArmorStand) && (!onlyPlayers.get() || entity instanceof Player)
                                || ModuleManager.getModuleState(MurdererDetector.class) && MurdererDetector.isMurderer(entity)
                )
                && (local.get() && (!mc.options.getCameraType().isFirstPerson() || ModuleManager.getModuleState(Freecam.class)) || !(entity instanceof LocalPlayer));
    }

    private static final class NameTagRenderData {
        private String entityName;
        private String entityTag;
        private float screenX;
        private float screenY;
        private double depthSquared;
        private String measuredEntityName;
        private String measuredEntityTag;
        private float entityWidth;
        private float tagWidth;

        private void update(String entityName, String entityTag, float screenX, float screenY, double depthSquared) {
            this.entityName = entityName;
            this.entityTag = entityTag;
            this.screenX = screenX;
            this.screenY = screenY;
            this.depthSquared = depthSquared;
        }

        private void updateTextMeasurements() {
            if (!Objects.equals(measuredEntityName, entityName)) {
                measuredEntityName = entityName;
                entityWidth = NVGFonts.INTER.getWidth(entityName, 16f);
            }
            if (!Objects.equals(measuredEntityTag, entityTag)) {
                measuredEntityTag = entityTag;
                tagWidth = entityTag != null ? NVGFonts.INTER.getWidth(entityTag, 16f) : 0f;
            }
        }

        private double depthSquared() {
            return depthSquared;
        }
    }

    private static final class NameTagRenderEntry {
        private String entityName;
        private String entityTag;
        private float screenX;
        private float screenY;
        private float rectX;
        private float rectY;
        private float width;
        private float height;
        private float tagExtended;
        private double depthSquared;
        private final float[] visibleRects = new float[MAX_VISIBLE_REGIONS * 4];
        private int visibleRectCount;
        private boolean fullyVisible;

        private void update(String entityName, String entityTag, float screenX, float screenY,
                            float rectX, float rectY, float width, float height,
                            float tagExtended, double depthSquared) {
            this.entityName = entityName;
            this.entityTag = entityTag;
            this.screenX = screenX;
            this.screenY = screenY;
            this.rectX = rectX;
            this.rectY = rectY;
            this.width = width;
            this.height = height;
            this.tagExtended = tagExtended;
            this.depthSquared = depthSquared;
            this.visibleRectCount = 0;
            this.fullyVisible = false;
        }

        private void setVisibleRects(float[] source, int count) {
            visibleRectCount = count;
            if (count > 0) {
                System.arraycopy(source, 0, visibleRects, 0, count * 4);
                fullyVisible = count == 1
                        && Math.abs(source[0] - rectX) < 0.01f
                        && Math.abs(source[1] - rectY) < 0.01f
                        && Math.abs(source[2] - width) < 0.01f
                        && Math.abs(source[3] - height) < 0.01f;
            } else {
                fullyVisible = false;
            }
        }
    }
}
