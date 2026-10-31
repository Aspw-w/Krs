package com.instrumentalist.krs.utils.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.EventListener;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector4f;
import org.nvgu.NVGU;

import java.awt.Color;
import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.nanovg.NanoVG.*;

public final class BlockHighlight implements EventListener {
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_FACE_VERTICES = 6;
    private static final int OPACITY_LEVELS = 16;
    private static final float NEAR_W = 0.05f;
    private static final float DEFAULT_FADE_IN_SECONDS = 0.12f;
    private static final float DEFAULT_FADE_OUT_SECONDS = 0.2f;
    private static final float POP_SCALE = 0.14f;
    private static final double EXPAND = 0.002;
    private static final float SIDE_FILL_ALPHA = 0.12f;
    private static final float TOP_FILL_ALPHA = 0.22f;
    private static final float GLOW_ALPHA = 0.2f;
    private static final float EDGE_ALPHA = 0.95f;
    private static final float GLOW_WIDTH = 4.5f;
    private static final float EDGE_WIDTH = 1.35f;
    private static final int TOP_FACE = 3;

    private static final int[][] FACE_CORNERS = {
            {0, 2, 6, 4}, {1, 3, 7, 5},
            {0, 1, 5, 4}, {2, 3, 7, 6},
            {0, 1, 3, 2}, {4, 5, 7, 6}
    };
    private static final int[][] FACE_OFFSETS = {
            {-1, 0, 0}, {1, 0, 0},
            {0, -1, 0}, {0, 1, 0},
            {0, 0, -1}, {0, 0, 1}
    };
    private static final int[] EDGE_FROM = new int[12];
    private static final int[] EDGE_TO = new int[12];
    private static final int[] EDGE_FACE_A = new int[12];
    private static final int[] EDGE_FACE_B = new int[12];

    static {
        int edge = 0;
        for (int corner = 0; corner < 8; corner++) {
            for (int axis = 0; axis < 3; axis++) {
                if ((corner >> axis & 1) != 0)
                    continue;

                int first = (axis + 1) % 3;
                int second = (axis + 2) % 3;
                EDGE_FROM[edge] = corner;
                EDGE_TO[edge] = corner | 1 << axis;
                EDGE_FACE_A[edge] = first * 2 + (corner >> first & 1);
                EDGE_FACE_B[edge] = second * 2 + (corner >> second & 1);
                edge++;
            }
        }
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final Entry[] entries = new Entry[MAX_ENTRIES];
    private final Long2ObjectOpenHashMap<Entry> blockEntries = new Long2ObjectOpenHashMap<>(MAX_ENTRIES);
    private final Vector4f clip = new Vector4f();
    private final float[] clipCorners = new float[24];
    private final float[] polygonIn = new float[MAX_FACE_VERTICES * 3];
    private final float[] polygonOut = new float[MAX_FACE_VERTICES * 3];
    private final boolean[] frontFaces = new boolean[6];
    private final boolean[] neighbourFaces = new boolean[6];
    private final Consumer<NVGU> drawer = this::draw;
    private final Color color;
    private final float fadeInSeconds;
    private final float fadeOutSeconds;
    private int entryCount;
    private boolean registered;
    private int usedLevels;
    private long lastFrameNanos;

    public BlockHighlight() {
        this(DEFAULT_FADE_OUT_SECONDS);
    }

    public BlockHighlight(Color color) {
        this(DEFAULT_FADE_OUT_SECONDS, color);
    }

    public BlockHighlight(float fadeOutSeconds) {
        this(fadeOutSeconds, RenderColors.DEFAULT);
    }

    public BlockHighlight(float fadeOutSeconds, Color color) {
        this.color = color;
        this.fadeInSeconds = DEFAULT_FADE_IN_SECONDS;
        this.fadeOutSeconds = fadeOutSeconds;
        for (int i = 0; i < MAX_ENTRIES; i++)
            entries[i] = new Entry();
    }

    public void setTarget(BlockPos pos) {
        setTarget(pos, null);
    }

    public void setTarget(BlockPos pos, BlockPos mergedPos) {
        releaseTargets();
        if (pos != null)
            acquire(pos, mergedPos, 0L);
    }

    public void setTargets(List<BlockPos> positions) {
        releaseTargets();
        for (int i = 0, n = Math.min(positions.size(), MAX_ENTRIES); i < n; i++)
            acquire(positions.get(i), null, 0L);
    }

    public void flash(BlockPos pos, long holdMillis) {
        acquire(pos, null, System.nanoTime() + holdMillis * 1_000_000L);
    }

    public void release() {
        releaseTargets();
    }

    public void clear() {
        entryCount = 0;
        usedLevels = 0;
        lastFrameNanos = 0L;
        blockEntries.clear();
        setRegistered(false);
    }

    private void setRegistered(boolean state) {
        if (registered == state || Client.eventManager == null)
            return;

        registered = state;
        if (state)
            Client.eventManager.register(this);
        else
            Client.eventManager.unregister(this);
    }

    @Override
    public void onWorld(WorldEvent event) {
        clear();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        long now = System.nanoTime();
        float deltaSeconds = lastFrameNanos == 0L ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        usedLevels = 0;

        for (int i = entryCount - 1; i >= 0; i--) {
            Entry entry = entries[i];
            if (entry.holdUntilNanos != 0L && now - entry.holdUntilNanos >= 0L) {
                entry.target = false;
                entry.holdUntilNanos = 0L;
            }

            entry.alpha = approach(entry.alpha, entry.target ? 1f : 0f, deltaSeconds);
            if (!entry.target && entry.alpha <= 0f)
                removeAt(i);
        }

        if (entryCount == 0) {
            clear();
            return;
        }

        blockEntries.clear();
        for (int i = 0; i < entryCount; i++) {
            Entry entry = entries[i];
            if (entry.fullBlock)
                blockEntries.put(entry.key, entry);
        }
        for (int i = 0; i < entryCount; i++)
            updateExposure(entries[i], deltaSeconds);

        if (entryCount == 0 || mc.player == null || !RenderUtil.shouldRenderWorldHudOverlays())
            return;

        Vec3 camera = mc.gameRenderer.mainCamera().position();
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        for (int i = 0; i < entryCount; i++)
            project(entries[i], camera, screenWidth, screenHeight);
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        if (usedLevels != 0 && entryCount > 0 && RenderUtil.shouldRenderWorldHudOverlays())
            Client.nanoVgManager.loadWorldAnchored(drawer);
    }

    private void releaseTargets() {
        for (int i = 0; i < entryCount; i++) {
            if (entries[i].holdUntilNanos == 0L)
                entries[i].target = false;
        }
    }

    private void acquire(BlockPos pos, BlockPos mergedPos, long holdUntilNanos) {
        long key = pos.asLong();
        for (int i = 0; i < entryCount; i++) {
            Entry entry = entries[i];
            if (entry.key == key) {
                entry.target = true;
                if (holdUntilNanos != 0L)
                    entry.holdUntilNanos = holdUntilNanos;
                return;
            }
        }

        ClientLevel level = mc.level;
        if (level == null)
            return;

        Entry entry;
        if (entryCount < MAX_ENTRIES) {
            entry = entries[entryCount++];
        } else {
            entry = null;
            for (int i = 0; i < entryCount; i++) {
                Entry candidate = entries[i];
                if (!candidate.target && (entry == null || candidate.alpha < entry.alpha))
                    entry = candidate;
            }
            if (entry == null)
                return;
        }

        setRegistered(true);
        entry.key = key;
        entry.x = pos.getX();
        entry.y = pos.getY();
        entry.z = pos.getZ();
        entry.target = true;
        entry.fresh = true;
        entry.holdUntilNanos = holdUntilNanos;
        entry.alpha = 0f;
        entry.resetBounds();
        entry.include(level, pos);
        if (mergedPos != null)
            entry.include(level, mergedPos);
        entry.fullBlock = entry.minX == entry.x && entry.minY == entry.y && entry.minZ == entry.z
                && entry.maxX == entry.x + 1.0 && entry.maxY == entry.y + 1.0 && entry.maxZ == entry.z + 1.0;
    }

    private void removeAt(int index) {
        int last = --entryCount;
        Entry removed = entries[index];
        entries[index] = entries[last];
        entries[last] = removed;
    }

    private float approach(float value, float target, float deltaSeconds) {
        if (value < target)
            return Math.min(target, value + deltaSeconds / fadeInSeconds);
        return Math.max(target, value - deltaSeconds / fadeOutSeconds);
    }

    private float approachExposure(float value, float target, float deltaSeconds) {
        if (value < target)
            return Math.min(target, value + deltaSeconds / fadeOutSeconds);
        return Math.max(target, value - deltaSeconds / fadeInSeconds);
    }

    private boolean occupied(Entry entry, int[] first, int[] second) {
        int dx = first[0] + (second == null ? 0 : second[0]);
        int dy = first[1] + (second == null ? 0 : second[1]);
        int dz = first[2] + (second == null ? 0 : second[2]);
        Entry neighbour = blockEntries.get(BlockPos.asLong(entry.x + dx, entry.y + dy, entry.z + dz));
        return neighbour != null && (neighbour.target || !entry.target);
    }

    private void updateExposure(Entry entry, float deltaSeconds) {
        boolean isolated = true;
        for (int face = 0; face < 6; face++) {
            neighbourFaces[face] = entry.fullBlock && occupied(entry, FACE_OFFSETS[face], null);
            isolated &= !neighbourFaces[face];
        }
        entry.isolated = isolated;

        for (int face = 0; face < 6; face++) {
            float target = neighbourFaces[face] ? 0f : 1f;
            entry.faceExposure[face] = entry.fresh ? target : approachExposure(entry.faceExposure[face], target, deltaSeconds);
        }

        for (int edge = 0; edge < 12; edge++) {
            int faceA = EDGE_FACE_A[edge];
            int faceB = EDGE_FACE_B[edge];
            boolean neighbourA = neighbourFaces[faceA];
            boolean neighbourB = neighbourFaces[faceB];
            boolean exposed = !neighbourA && !neighbourB
                    || neighbourA && neighbourB && !occupied(entry, FACE_OFFSETS[faceA], FACE_OFFSETS[faceB]);
            float target = exposed ? 1f : 0f;
            entry.edgeExposure[edge] = entry.fresh ? target : approachExposure(entry.edgeExposure[edge], target, deltaSeconds);
        }
        entry.fresh = false;
    }

    private int opacityLevel(float opacity) {
        int level = Math.round(opacity * OPACITY_LEVELS);
        if (level <= 0)
            return 0;
        usedLevels |= 1 << level;
        return level;
    }

    private void project(Entry entry, Vec3 camera, float screenWidth, float screenHeight) {
        entry.faceCount = 0;
        entry.edgeCount = 0;

        double scale = 1.0;
        if (entry.isolated) {
            float eased = 1f - (1f - entry.alpha) * (1f - entry.alpha) * (1f - entry.alpha);
            scale -= POP_SCALE * (1.0 - eased);
        }
        double centerX = (entry.minX + entry.maxX) * 0.5;
        double centerY = (entry.minY + entry.maxY) * 0.5;
        double centerZ = (entry.minZ + entry.maxZ) * 0.5;
        double halfX = (entry.maxX - entry.minX) * 0.5 * scale + EXPAND;
        double halfY = (entry.maxY - entry.minY) * 0.5 * scale + EXPAND;
        double halfZ = (entry.maxZ - entry.minZ) * 0.5 * scale + EXPAND;
        double minX = centerX - halfX, minY = centerY - halfY, minZ = centerZ - halfZ;
        double maxX = centerX + halfX, maxY = centerY + halfY, maxZ = centerZ + halfZ;

        if (!RenderUtil.INSTANCE.isAabInRenderedWorldFrustum(minX, minY, minZ, maxX, maxY, maxZ))
            return;

        frontFaces[0] = camera.x < minX;
        frontFaces[1] = camera.x > maxX;
        frontFaces[2] = camera.y < minY;
        frontFaces[3] = camera.y > maxY;
        frontFaces[4] = camera.z < minZ;
        frontFaces[5] = camera.z > maxZ;
        if (!frontFaces[0] && !frontFaces[1] && !frontFaces[2] && !frontFaces[3] && !frontFaces[4] && !frontFaces[5])
            return;

        for (int corner = 0; corner < 8; corner++) {
            if (!RenderUtil.INSTANCE.renderedWorldToClip(
                    (corner & 1) != 0 ? maxX : minX,
                    (corner & 2) != 0 ? maxY : minY,
                    (corner & 4) != 0 ? maxZ : minZ,
                    clip
            ))
                return;

            int offset = corner * 3;
            clipCorners[offset] = clip.x;
            clipCorners[offset + 1] = clip.y;
            clipCorners[offset + 2] = clip.w;
        }

        for (int face = 0; face < 6; face++) {
            if (!frontFaces[face])
                continue;
            int level = opacityLevel(entry.alpha * entry.faceExposure[face]);
            if (level > 0)
                projectFace(entry, face, level, screenWidth, screenHeight);
        }

        for (int edge = 0; edge < 12; edge++) {
            if (!frontFaces[EDGE_FACE_A[edge]] && !frontFaces[EDGE_FACE_B[edge]])
                continue;
            int level = opacityLevel(entry.alpha * entry.edgeExposure[edge]);
            if (level > 0)
                projectEdge(entry, EDGE_FROM[edge] * 3, EDGE_TO[edge] * 3, level, screenWidth, screenHeight);
        }
    }

    private void projectFace(Entry entry, int face, int level, float screenWidth, float screenHeight) {
        int[] corners = FACE_CORNERS[face];
        for (int i = 0; i < 4; i++)
            System.arraycopy(clipCorners, corners[i] * 3, polygonIn, i * 3, 3);

        int count = 0;
        for (int i = 0; i < 4; i++) {
            int current = i * 3;
            int next = (i + 1) % 4 * 3;
            float currentW = polygonIn[current + 2];
            float nextW = polygonIn[next + 2];
            boolean currentInside = currentW >= NEAR_W;
            if (currentInside) {
                System.arraycopy(polygonIn, current, polygonOut, count * 3, 3);
                count++;
            }
            if (currentInside != nextW >= NEAR_W) {
                float t = (NEAR_W - currentW) / (nextW - currentW);
                int out = count * 3;
                polygonOut[out] = polygonIn[current] + (polygonIn[next] - polygonIn[current]) * t;
                polygonOut[out + 1] = polygonIn[current + 1] + (polygonIn[next + 1] - polygonIn[current + 1]) * t;
                polygonOut[out + 2] = NEAR_W;
                count++;
            }
        }
        if (count < 3)
            return;

        int slot = entry.faceCount++;
        entry.faceTop[slot] = face == TOP_FACE;
        entry.faceLevels[slot] = level;
        entry.faceVertexCounts[slot] = count;
        int base = slot * MAX_FACE_VERTICES * 2;
        for (int i = 0; i < count; i++) {
            int in = i * 3;
            float inverseW = 1f / polygonOut[in + 2];
            entry.faceVertices[base + i * 2] = (polygonOut[in] * inverseW * 0.5f + 0.5f) * screenWidth;
            entry.faceVertices[base + i * 2 + 1] = (0.5f - polygonOut[in + 1] * inverseW * 0.5f) * screenHeight;
        }
    }

    private void projectEdge(Entry entry, int from, int to, int level, float screenWidth, float screenHeight) {
        float fromX = clipCorners[from], fromY = clipCorners[from + 1], fromW = clipCorners[from + 2];
        float toX = clipCorners[to], toY = clipCorners[to + 1], toW = clipCorners[to + 2];
        if (fromW < NEAR_W && toW < NEAR_W)
            return;

        if (fromW < NEAR_W) {
            float t = (NEAR_W - fromW) / (toW - fromW);
            fromX += (toX - fromX) * t;
            fromY += (toY - fromY) * t;
            fromW = NEAR_W;
        } else if (toW < NEAR_W) {
            float t = (NEAR_W - toW) / (fromW - toW);
            toX += (fromX - toX) * t;
            toY += (fromY - toY) * t;
            toW = NEAR_W;
        }

        int slot = entry.edgeCount++;
        entry.edgeLevels[slot] = level;
        int offset = slot * 4;
        entry.edgeVertices[offset] = (fromX / fromW * 0.5f + 0.5f) * screenWidth;
        entry.edgeVertices[offset + 1] = (0.5f - fromY / fromW * 0.5f) * screenHeight;
        entry.edgeVertices[offset + 2] = (toX / toW * 0.5f + 0.5f) * screenWidth;
        entry.edgeVertices[offset + 3] = (0.5f - toY / toW * 0.5f) * screenHeight;
    }

    private void draw(NVGU vg) {
        if (entryCount == 0 || usedLevels == 0)
            return;

        long handle = vg.getHandle();
        nvgSave(handle);
        nvgLineJoin(handle, NVG_ROUND);
        nvgLineCap(handle, NVG_ROUND);

        float colorAlpha = color.getAlpha() / 255f;
        for (int level = 1; level <= OPACITY_LEVELS; level++) {
            if ((usedLevels & 1 << level) != 0)
                drawLevel(vg, handle, level, colorAlpha * level / OPACITY_LEVELS);
        }

        nvgRestore(handle);
    }

    private void drawLevel(NVGU vg, long handle, int level, float opacity) {
        nvgBeginPath(handle);
        boolean anySide = false;
        for (int i = 0; i < entryCount; i++)
            anySide |= emitFaces(handle, entries[i], false, level);
        if (anySide) {
            nvgFillColor(handle, vg.createAndStoreColour(color).a(SIDE_FILL_ALPHA * opacity));
            nvgFill(handle);
        }

        nvgBeginPath(handle);
        boolean anyTop = false;
        for (int i = 0; i < entryCount; i++)
            anyTop |= emitFaces(handle, entries[i], true, level);
        if (anyTop) {
            nvgFillColor(handle, vg.createAndStoreColour(color).a(TOP_FILL_ALPHA * opacity));
            nvgFill(handle);
        }

        nvgBeginPath(handle);
        boolean anyEdge = false;
        for (int i = 0; i < entryCount; i++) {
            Entry entry = entries[i];
            float[] edges = entry.edgeVertices;
            for (int edge = 0; edge < entry.edgeCount; edge++) {
                if (entry.edgeLevels[edge] != level)
                    continue;
                int offset = edge * 4;
                nvgMoveTo(handle, edges[offset], edges[offset + 1]);
                nvgLineTo(handle, edges[offset + 2], edges[offset + 3]);
                anyEdge = true;
            }
        }
        if (!anyEdge)
            return;
        nvgStrokeWidth(handle, GLOW_WIDTH);
        nvgStrokeColor(handle, vg.createAndStoreColour(color).a(GLOW_ALPHA * opacity));
        nvgStroke(handle);
        nvgStrokeWidth(handle, EDGE_WIDTH);
        nvgStrokeColor(handle, vg.createAndStoreColour(color).a(EDGE_ALPHA * opacity));
        nvgStroke(handle);
    }

    private static boolean emitFaces(long handle, Entry entry, boolean top, int level) {
        boolean emitted = false;
        for (int face = 0; face < entry.faceCount; face++) {
            if (entry.faceTop[face] != top || entry.faceLevels[face] != level)
                continue;

            int base = face * MAX_FACE_VERTICES * 2;
            float[] vertices = entry.faceVertices;
            nvgMoveTo(handle, vertices[base], vertices[base + 1]);
            for (int i = 1, n = entry.faceVertexCounts[face]; i < n; i++)
                nvgLineTo(handle, vertices[base + i * 2], vertices[base + i * 2 + 1]);
            nvgClosePath(handle);
            emitted = true;
        }
        return emitted;
    }

    private static final class Entry {
        private long key;
        private int x, y, z;
        private boolean fullBlock;
        private boolean target;
        private boolean fresh;
        private boolean isolated;
        private long holdUntilNanos;
        private float alpha;
        private double minX, minY, minZ, maxX, maxY, maxZ;
        private final float[] faceExposure = new float[6];
        private final float[] edgeExposure = new float[12];
        private int faceCount;
        private final boolean[] faceTop = new boolean[3];
        private final int[] faceLevels = new int[3];
        private final int[] faceVertexCounts = new int[3];
        private final float[] faceVertices = new float[3 * MAX_FACE_VERTICES * 2];
        private int edgeCount;
        private final int[] edgeLevels = new int[12];
        private final float[] edgeVertices = new float[12 * 4];

        private void resetBounds() {
            minX = minY = minZ = Double.POSITIVE_INFINITY;
            maxX = maxY = maxZ = Double.NEGATIVE_INFINITY;
        }

        private void include(ClientLevel level, BlockPos pos) {
            VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
            double x = pos.getX(), y = pos.getY(), z = pos.getZ();
            if (shape.isEmpty()) {
                expand(x, y, z, x + 1.0, y + 1.0, z + 1.0);
            } else {
                AABB bounds = shape.bounds();
                expand(x + bounds.minX, y + bounds.minY, z + bounds.minZ, x + bounds.maxX, y + bounds.maxY, z + bounds.maxZ);
            }
        }

        private void expand(double x1, double y1, double z1, double x2, double y2, double z2) {
            minX = Math.min(minX, x1);
            minY = Math.min(minY, y1);
            minZ = Math.min(minZ, z1);
            maxX = Math.max(maxX, x2);
            maxY = Math.max(maxY, y2);
            maxZ = Math.max(maxZ, z2);
        }
    }
}
