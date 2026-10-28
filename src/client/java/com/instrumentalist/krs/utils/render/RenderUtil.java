package com.instrumentalist.krs.utils.render;

import com.instrumentalist.krs.utils.GuiInputBlocker;
import com.instrumentalist.krs.utils.math.Tuple;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.util.ArrayDeque;
import java.util.ArrayList;

public final class RenderUtil {
    public static final RenderUtil INSTANCE = new RenderUtil();

    public final Minecraft mc = Minecraft.getInstance();
    private static final ArrayDeque<Tuple<Integer, Integer>> pending = new ArrayDeque<>();
    private final Matrix4f screenMatrix = new Matrix4f();
    private final Matrix4f renderedWorldScreenMatrix = new Matrix4f();
    private final Vector4f screenVector = new Vector4f();
    private final FrustumIntersection screenFrustum = new FrustumIntersection();
    private final FrustumIntersection renderedWorldFrustum = new FrustumIntersection();
    private final float[] compatibilityProjectionOutput = new float[3];
    private double projectionCameraX;
    private double projectionCameraY;
    private double projectionCameraZ;
    private int projectionScreenWidth;
    private int projectionScreenHeight;
    private boolean projectionReady;
    private boolean renderedWorldProjectionReady;

    private RenderUtil() {
    }

    public static ArrayDeque<Tuple<Integer, Integer>> getPending() {
        return pending;
    }

    public static boolean shouldRenderWorldHudOverlays() {
        Minecraft client = Minecraft.getInstance();
        return client.gui.screen() == null || client.gui.screen() instanceof ChatScreen || GuiInputBlocker.shouldAllowWorldHudOverlays();
    }

    public void reloadWorldWithoutFlickers() {
        var player = mc.player;
        if (player == null) return;

        pending.clear();

        int rd = Math.max(1, Math.min(32, mc.options.renderDistance().get()));
        int pcx = Math.floorDiv(player.getBlockX(), 16);
        int pcz = Math.floorDiv(player.getBlockZ(), 16);
        int diameter = rd * 2 + 1;
        ArrayList<Tuple<Integer, Integer>> temp = new ArrayList<>(diameter * diameter);

        for (int dx = -rd; dx <= rd; dx++) {
            for (int dz = -rd; dz <= rd; dz++) {
                int cx = (pcx + dx) * 16;
                int cz = (pcz + dz) * 16;
                temp.add(Tuple.of(cx, cz));
            }
        }

        temp.sort((a, b) -> {
            double ax = player.getBlockX() - (a.getFirst() + 8);
            double az = player.getBlockZ() - (a.getSecond() + 8);
            double bx = player.getBlockX() - (b.getFirst() + 8);
            double bz = player.getBlockZ() - (b.getSecond() + 8);
            return Double.compare(ax * ax + az * az, bx * bx + bz * bz);
        });
        pending.addAll(temp);
    }

    public void beginWorldProjection() {
        projectionReady = false;
        if (mc.player == null)
            return;

        projectionScreenWidth = mc.getWindow().getWidth();
        projectionScreenHeight = mc.getWindow().getHeight();
        if (projectionScreenWidth <= 0 || projectionScreenHeight <= 0)
            return;

        var camera = mc.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        projectionCameraX = cameraPos.x;
        projectionCameraY = cameraPos.y;
        projectionCameraZ = cameraPos.z;
        camera.getViewRotationProjectionMatrix(screenMatrix.identity());
        screenFrustum.set(screenMatrix);
        projectionReady = true;
    }

    public void beginWorldProjectionFrame() {
        projectionReady = false;
        renderedWorldProjectionReady = false;
    }

    public void updateRenderedWorldProjection(Matrix4fc projectionMatrix, Matrix4fc viewRotationMatrix) {
        if (projectionMatrix == null || viewRotationMatrix == null) {
            renderedWorldProjectionReady = false;
            return;
        }

        renderedWorldScreenMatrix.set(projectionMatrix).mul(viewRotationMatrix);
        renderedWorldFrustum.set(renderedWorldScreenMatrix);
        renderedWorldProjectionReady = true;
    }

    public boolean isEntityInCameraFrustum(Entity entity, float partialTicks) {
        return isEntityInFrustum(entity, partialTicks, screenFrustum);
    }

    public boolean isEntityInRenderedWorldFrustum(Entity entity, float partialTicks) {
        if (!projectionReady)
            beginWorldProjection();
        return isEntityInFrustum(
                entity,
                partialTicks,
                renderedWorldProjectionReady ? renderedWorldFrustum : screenFrustum
        );
    }

    private boolean isEntityInFrustum(Entity entity, float partialTicks, FrustumIntersection frustum) {
        if (entity == null || entity.isRemoved())
            return false;
        if (!projectionReady)
            beginWorldProjection();
        if (!projectionReady)
            return false;

        Vec3 lerpedPos = getLerpedPos(entity, partialTicks);
        AABB box = entity.getBoundingBox();
        double offsetX = lerpedPos.x - entity.getX() - projectionCameraX;
        double offsetY = lerpedPos.y - entity.getY() - projectionCameraY;
        double offsetZ = lerpedPos.z - entity.getZ() - projectionCameraZ;

        return frustum.testAab(
                (float) (box.minX + offsetX),
                (float) (box.minY + offsetY),
                (float) (box.minZ + offsetZ),
                (float) (box.maxX + offsetX),
                (float) (box.maxY + offsetY),
                (float) (box.maxZ + offsetZ)
        );
    }

    public boolean worldToScreen(double worldX, double worldY, double worldZ, float[] output) {
        return worldToScreen(worldX, worldY, worldZ, screenMatrix, output);
    }

    public boolean renderedWorldToScreen(double worldX, double worldY, double worldZ, float[] output) {
        if (!projectionReady)
            beginWorldProjection();
        return worldToScreen(
                worldX,
                worldY,
                worldZ,
                renderedWorldProjectionReady ? renderedWorldScreenMatrix : screenMatrix,
                output
        );
    }

    public boolean renderedWorldToClip(double worldX, double worldY, double worldZ, Vector4f output) {
        if (!projectionReady)
            beginWorldProjection();
        if (!projectionReady)
            return false;

        output.set(
                (float) (worldX - projectionCameraX),
                (float) (worldY - projectionCameraY),
                (float) (worldZ - projectionCameraZ),
                1.0f
        ).mul(renderedWorldProjectionReady ? renderedWorldScreenMatrix : screenMatrix);
        return true;
    }

    public boolean isAabInRenderedWorldFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (!projectionReady)
            beginWorldProjection();
        if (!projectionReady)
            return false;

        return (renderedWorldProjectionReady ? renderedWorldFrustum : screenFrustum).testAab(
                (float) (minX - projectionCameraX),
                (float) (minY - projectionCameraY),
                (float) (minZ - projectionCameraZ),
                (float) (maxX - projectionCameraX),
                (float) (maxY - projectionCameraY),
                (float) (maxZ - projectionCameraZ)
        );
    }

    private boolean worldToScreen(double worldX, double worldY, double worldZ, Matrix4fc projectionMatrix, float[] output) {
        if (output == null || output.length < 3)
            throw new IllegalArgumentException("World projection output must contain at least three floats");
        if (!projectionReady)
            beginWorldProjection();
        if (!projectionReady)
            return false;

        Vector4f vec = screenVector.set(
                (float) (worldX - projectionCameraX),
                (float) (worldY - projectionCameraY),
                (float) (worldZ - projectionCameraZ),
                1.0f
        );

        vec.mul(projectionMatrix);

        if (vec.w <= 1e-6f || Float.isNaN(vec.w) || Float.isInfinite(vec.w)) return false;

        vec.x /= vec.w;
        vec.y /= vec.w;
        vec.z /= vec.w;

        if (Float.isNaN(vec.x) || Float.isNaN(vec.y) || Float.isNaN(vec.z) || Float.isInfinite(vec.x) || Float.isInfinite(vec.y) || Float.isInfinite(vec.z))
            return false;

        float screenX = (vec.x * 0.5f + 0.5f) * projectionScreenWidth;
        float screenY = (1.0f - (vec.y * 0.5f + 0.5f)) * projectionScreenHeight;

        if (screenX < 0.0f || screenX > projectionScreenWidth
                || screenY < 0.0f || screenY > projectionScreenHeight
                || vec.z < -1.0f || vec.z > 1.0f)
            return false;

        output[0] = screenX;
        output[1] = screenY;
        output[2] = vec.z;
        return true;
    }

    public Vec3 worldToScreen(Vec3 worldPos) {
        if (worldPos == null)
            return null;

        if (!worldToScreen(worldPos.x, worldPos.y, worldPos.z, compatibilityProjectionOutput))
            return null;

        return new Vec3(
                compatibilityProjectionOutput[0],
                compatibilityProjectionOutput[1],
                compatibilityProjectionOutput[2]
        );
    }

    public Vec3 getLerpedPos(Entity entity, float partialTicks) {
        if (entity.isRemoved()) return entity.position();
        return entity.getPosition(partialTicks);
    }
}
