package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.render.RenderUtil;
import com.instrumentalist.krs.utils.value.ColorValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class BedESP extends Module {

    private static final int SCAN_INTERVAL = 20;
    private static final Direction[] HORIZONTALS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final int[][] FACES = new int[][]{
            {2, 6, 7, 3},
            {0, 1, 5, 4},
            {1, 2, 6, 5},
            {0, 3, 7, 4},
            {4, 5, 6, 7},
            {0, 1, 2, 3},
    };
    private static final int[][] EDGES = new int[][]{
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7},
    };

    @Setting
    private static final ListValue mode = new ListValue("Mode", new String[]{"Solid", "Hitbox"}, "Solid");

    @Setting
    private static final FloatValue distance = new FloatValue("Distance", 16f, 4f, 48f, "m");

    @Setting
    private static final ColorValue color = new ColorValue("Color", new Color(255, 255, 255, 110));

    @Setting
    private static final FloatValue opacity = new FloatValue("Opacity", 65f, 0f, 100f, "%", () -> mode.get().equalsIgnoreCase("Solid"));

    @Setting
    private static final FloatValue lineWidth = new FloatValue("Width", 0.40f, 0.01f, 1f, () -> mode.get().equalsIgnoreCase("Hitbox"));

    private final List<AABB> foundBeds = new ArrayList<>();
    private final List<float[]> solidFaces = new ArrayList<>();
    private final List<float[]> hitboxFaces = new ArrayList<>();
    private int scanTicks = 0;

    public BedESP() {
        super("BedESP", ModuleCategory.Render, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        foundBeds.clear();
        solidFaces.clear();
        hitboxFaces.clear();
        scanTicks = 0;
    }

    @Override
    public void onDisable() {
        foundBeds.clear();
        solidFaces.clear();
        hitboxFaces.clear();
    }

    @Override
    public void onWorld(WorldEvent event) {
        foundBeds.clear();
        solidFaces.clear();
        hitboxFaces.clear();
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) {
            foundBeds.clear();
            solidFaces.clear();
            hitboxFaces.clear();
            return;
        }

        if (++scanTicks % SCAN_INTERVAL != 0)
            return;

        scanBeds(mc.level, mc.player);
    }

    private void scanBeds(ClientLevel level, LocalPlayer player) {
        foundBeds.clear();

        int radius = (int) Math.ceil(distance.get());
        double maxDistanceSquared = (double) distance.get() * distance.get();
        double playerX = player.getX();
        double playerY = player.getY();
        double playerZ = player.getZ();
        BlockPos origin = player.blockPosition();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);

                    double centerX = pos.getX() + 0.5 - playerX;
                    double centerY = pos.getY() + 0.5 - playerY;
                    double centerZ = pos.getZ() + 0.5 - playerZ;
                    if (centerX * centerX + centerY * centerY + centerZ * centerZ > maxDistanceSquared)
                        continue;

                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof BedBlock))
                        continue;
                    if (state.getValue(BedBlock.PART) != BedPart.FOOT)
                        continue;

                    addBedBox(level, pos);
                }
            }
        }
    }

    private void addBedBox(ClientLevel level, BlockPos footPos) {
        BlockPos headPos = footPos;
        for (Direction direction : HORIZONTALS) {
            BlockPos pos = footPos.relative(direction);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                headPos = pos;
                break;
            }
        }

        double minX = Math.min(footPos.getX(), headPos.getX());
        double minZ = Math.min(footPos.getZ(), headPos.getZ());
        double maxX = Math.max(footPos.getX(), headPos.getX()) + 1.0;
        double maxZ = Math.max(footPos.getZ(), headPos.getZ()) + 1.0;
        double minY = footPos.getY();
        double maxY = mode.get().equalsIgnoreCase("Solid") ? footPos.getY() + 1.0 : footPos.getY() + 0.5625;

        foundBeds.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        solidFaces.clear();
        hitboxFaces.clear();
        if (mc.player == null || mc.level == null)
            return;
        if (!RenderUtil.shouldRenderWorldHudOverlays())
            return;
        if (foundBeds.isEmpty())
            return;

        Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
        float framebufferToScaledX = NanoVGManager.getScaledScreenWidth() / Math.max(1, mc.getWindow().getWidth());
        float framebufferToScaledY = NanoVGManager.getScaledScreenHeight() / Math.max(1, mc.getWindow().getHeight());
        float[] projected = new float[3];

        boolean solid = mode.get().equalsIgnoreCase("Solid");

        for (AABB box : foundBeds) {
            double minX = box.minX;
            double minY = box.minY;
            double minZ = box.minZ;
            double maxX = box.maxX;
            double maxY = box.maxY;
            double maxZ = box.maxZ;

            double[][] corners = new double[][]{
                    {minX, minY, minZ},
                    {maxX, minY, minZ},
                    {maxX, minY, maxZ},
                    {minX, minY, maxZ},
                    {minX, maxY, minZ},
                    {maxX, maxY, minZ},
                    {maxX, maxY, maxZ},
                    {minX, maxY, maxZ},
            };

            float[] sx = new float[8];
            float[] sy = new float[8];
            boolean[] located = new boolean[8];
            for (int i = 0; i < 8; i++) {
                if (RenderUtil.INSTANCE.renderedWorldToScreen(corners[i][0], corners[i][1], corners[i][2], projected)) {
                    sx[i] = projected[0] * framebufferToScaledX;
                    sy[i] = projected[1] * framebufferToScaledY;
                    located[i] = true;
                }
            }

            if (solid) {
                boolean[] visible = new boolean[]{
                        cameraPos.z > maxZ,
                        cameraPos.z < minZ,
                        cameraPos.x > maxX,
                        cameraPos.x < minX,
                        cameraPos.y > maxY,
                        cameraPos.y < minY,
                };

                for (int f = 0; f < FACES.length; f++) {
                    if (!visible[f])
                        continue;

                    int a = FACES[f][0];
                    int b = FACES[f][1];
                    int c = FACES[f][2];
                    int d = FACES[f][3];
                    if (!located[a] || !located[b] || !located[c] || !located[d])
                        continue;

                    solidFaces.add(new float[]{sx[a], sy[a], sx[b], sy[b], sx[c], sy[c], sx[d], sy[d]});
                }
            } else {
                double halfWidth = lineWidth.get() * 0.05;
                for (int[] edge : EDGES) {
                    addEdgeBar(
                            corners[edge[0]], corners[edge[1]], halfWidth,
                            cameraPos,
                            framebufferToScaledX, framebufferToScaledY, projected
                    );
                }
            }
        }
    }

    private void addEdgeBar(double[] p0, double[] p1, double halfWidth,
                            Vec3 cameraPos, float scaleX, float scaleY, float[] projected) {
        double dx = p1[0] - p0[0];
        double dy = p1[1] - p0[1];
        double dz = p1[2] - p0[2];
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-6)
            return;

        double[] dir = {dx / length, dy / length, dz / length};

        double[] ref;
        if (Math.abs(dir[0]) < 0.9)
            ref = new double[]{1.0, 0.0, 0.0};
        else if (Math.abs(dir[1]) < 0.9)
            ref = new double[]{0.0, 1.0, 0.0};
        else
            ref = new double[]{0.0, 0.0, 1.0};

        double[] u = normalize(cross(dir, ref));
        double[] v = normalize(cross(dir, u));

        double[][] barCorners = new double[8][3];
        for (int i = 0; i < 4; i++) {
            int su = (i == 0 || i == 3) ? -1 : 1;
            int sv = (i == 0 || i == 1) ? -1 : 1;
            double[] pu = {u[0] * halfWidth * su, u[1] * halfWidth * su, u[2] * halfWidth * su};
            double[] pv = {v[0] * halfWidth * sv, v[1] * halfWidth * sv, v[2] * halfWidth * sv};
            barCorners[i] = add(p0, pu, pv);
            barCorners[i + 4] = add(p1, pu, pv);
        }

        double[] boxCenter = {(p0[0] + p1[0]) * 0.5, (p0[1] + p1[1]) * 0.5, (p0[2] + p1[2]) * 0.5};

        for (int[] face : FACES) {
            double[] a = barCorners[face[0]];
            double[] b = barCorners[face[1]];
            double[] c = barCorners[face[2]];
            double[] d = barCorners[face[3]];

            double[] normal = normalize(cross(sub(b, a), sub(c, a)));
            double[] faceCenter = {
                    (a[0] + b[0] + c[0] + d[0]) * 0.25,
                    (a[1] + b[1] + c[1] + d[1]) * 0.25,
                    (a[2] + b[2] + c[2] + d[2]) * 0.25,
            };
            double[] outward = sub(faceCenter, boxCenter);
            if (dot(normal, outward) < 0)
                normal = neg(normal);

            double[] toCamera = {cameraPos.x - faceCenter[0], cameraPos.y - faceCenter[1], cameraPos.z - faceCenter[2]};
            if (dot(normal, toCamera) <= 0)
                continue;

            float[] quad = new float[8];
            boolean ok = true;
            for (int i = 0; i < 4; i++) {
                double[] corner = barCorners[face[i]];
                if (!RenderUtil.INSTANCE.renderedWorldToScreen(corner[0], corner[1], corner[2], projected)) {
                    ok = false;
                    break;
                }
                quad[i * 2] = projected[0] * scaleX;
                quad[i * 2 + 1] = projected[1] * scaleY;
            }
            if (ok) {
                double area = Math.abs(
                        quad[0] * quad[3] - quad[1] * quad[2]
                                + quad[2] * quad[5] - quad[3] * quad[4]
                                + quad[4] * quad[7] - quad[5] * quad[6]
                                + quad[6] * quad[1] - quad[7] * quad[0]
                ) * 0.5;
                if (area > 1.0)
                    hitboxFaces.add(quad);
            }
        }
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0],
        };
    }

    private static double[] normalize(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1.0e-6)
            return new double[]{0, 0, 0};
        return new double[]{v[0] / len, v[1] / len, v[2] / len};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] add(double[] a, double[] b, double[] c) {
        return new double[]{
                a[0] + b[0] + c[0],
                a[1] + b[1] + c[1],
                a[2] + b[2] + c[2],
        };
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] neg(double[] v) {
        return new double[]{-v[0], -v[1], -v[2]};
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        if (mc.player == null || mc.level == null)
            return;
        if (!RenderUtil.shouldRenderWorldHudOverlays())
            return;

        boolean solid = mode.get().equalsIgnoreCase("Solid");
        if (solid && solidFaces.isEmpty())
            return;
        if (!solid && hitboxFaces.isEmpty())
            return;

        Color baseColor = color.get();
        Client.nanoVgManager.load(vg -> {
            if (solid) {
                Color fillColor = new Color(
                        baseColor.getRed(),
                        baseColor.getGreen(),
                        baseColor.getBlue(),
                        Math.round(baseColor.getAlpha() * (opacity.get() / 100f))
                );

                for (float[] polygon : solidFaces) {
                    float[][] points = new float[][]{
                            {polygon[0], polygon[1]},
                            {polygon[2], polygon[3]},
                            {polygon[4], polygon[5]},
                            {polygon[6], polygon[7]},
                    };
                    vg.polygon(points, fillColor);
                }
            } else {
                for (float[] polygon : hitboxFaces) {
                    float[][] points = new float[][]{
                            {polygon[0], polygon[1]},
                            {polygon[2], polygon[3]},
                            {polygon[4], polygon[5]},
                            {polygon[6], polygon[7]},
                    };
                    vg.polygon(points, baseColor);
                }
            }
        });
    }
}