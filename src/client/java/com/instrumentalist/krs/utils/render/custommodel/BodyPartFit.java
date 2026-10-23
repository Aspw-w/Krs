package com.instrumentalist.krs.utils.render.custommodel;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;

final class BodyPartFit {
    static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5, COUNT = 6;

    private static final float PX = 1.0f / 16.0f;
    private static final float[][] VANILLA_BOX = {
            {-4, 0, -4, 4, 8, 4},
            {-4, -12, -2, 4, 0, 2},
            {-3, -10, -2, 1, 2, 2},
            {-1, -10, -2, 3, 2, 2},
            {-2, -12, -2, 2, 0, 2},
            {-2, -12, -2, 2, 0, 2},
    };
    private final int[] bone = {AutoRig.HEAD, AutoRig.CHEST, AutoRig.UPPER_ARM_R, AutoRig.UPPER_ARM_L, AutoRig.THIGH_R, AutoRig.THIGH_L};
    private static final int[][] MEMBERS = {
            {AutoRig.HEAD},
            {AutoRig.CHEST, AutoRig.PELVIS},
            {AutoRig.UPPER_ARM_R, AutoRig.FORE_ARM_R, AutoRig.SLEEVE_R, AutoRig.SLEEVE_BACK_R},
            {AutoRig.UPPER_ARM_L, AutoRig.FORE_ARM_L, AutoRig.SLEEVE_L, AutoRig.SLEEVE_BACK_L},
            {AutoRig.THIGH_R, AutoRig.SHIN_R},
            {AutoRig.THIGH_L, AutoRig.SHIN_L},
    };
    private static final Quaternionf HALF_TURN_X = new Quaternionf().rotationX((float) Math.PI);

    private final Vector3f[] origin = new Vector3f[COUNT];
    private final Quaternionf[] restRotation = new Quaternionf[COUNT];
    private final Vector3f[] offset = new Vector3f[COUNT];
    private final Vector3f[] stretch = new Vector3f[COUNT];

    static final class Poses {
        final float[] values = new float[COUNT * 9];

        void apply(int part, ModelPart target) {
            int i = part * 9;
            target.x = values[i];
            target.y = values[i + 1];
            target.z = values[i + 2];
            target.setRotation(values[i + 3], values[i + 4], values[i + 5]);
            target.xScale = values[i + 6];
            target.yScale = values[i + 7];
            target.zScale = values[i + 8];
        }
    }

    BodyPartFit(GlbModel model, AutoRig rig) {
        float h = rig.height;
        origin[HEAD] = new Vector3f(rig.pivots[AutoRig.HEAD]);
        origin[BODY] = new Vector3f(rig.pivots[AutoRig.CHEST]);
        origin[RIGHT_ARM] = new Vector3f(rig.pivots[AutoRig.UPPER_ARM_R]);
        origin[LEFT_ARM] = new Vector3f(rig.pivots[AutoRig.UPPER_ARM_L]);
        origin[RIGHT_LEG] = new Vector3f(rig.pivots[AutoRig.THIGH_R]);
        origin[LEFT_LEG] = new Vector3f(rig.pivots[AutoRig.THIGH_L]);

        Vector3f down = new Vector3f(0.0f, -1.0f, 0.0f);
        restRotation[HEAD] = new Quaternionf();
        restRotation[BODY] = new Quaternionf();
        restRotation[RIGHT_ARM] = limbRotation(down, rig.pivots[AutoRig.UPPER_ARM_R], rig.handR);
        restRotation[LEFT_ARM] = limbRotation(down, rig.pivots[AutoRig.UPPER_ARM_L], rig.handL);
        restRotation[RIGHT_LEG] = limbRotation(down, rig.pivots[AutoRig.THIGH_R], rig.footR);
        restRotation[LEFT_LEG] = limbRotation(down, rig.pivots[AutoRig.THIGH_L], rig.footL);

        float hipY = Math.max(rig.pivots[AutoRig.THIGH_L].y, rig.pivots[AutoRig.THIGH_R].y);
        float armLength = Math.max(rig.pivots[AutoRig.UPPER_ARM_L].distance(rig.handL), rig.pivots[AutoRig.UPPER_ARM_R].distance(rig.handR));
        float shoulderTop = Math.max(rig.pivots[AutoRig.UPPER_ARM_L].y, rig.pivots[AutoRig.UPPER_ARM_R].y) + armLength * 0.2f;
        float bodyTop = Math.min(rig.pivots[AutoRig.HEAD].y, shoulderTop);
        for (int part = 0; part < COUNT; part++) {
            float[] box = switch (part) {
                case HEAD -> headBox(model, rig, bodyTop);
                case BODY -> bodyBox(model, rig, hipY, bodyTop);
                case RIGHT_ARM, LEFT_ARM -> limbBox(model, rig, part, ARM_CORE[part - RIGHT_ARM], ARM_CORE[part - RIGHT_ARM], true);
                default -> limbBox(model, rig, part, MEMBERS[part], new int[]{part == RIGHT_LEG ? AutoRig.SHIN_R : AutoRig.SHIN_L}, false);
            };
            float[] vanilla = VANILLA_BOX[part];
            Vector3f s = new Vector3f();
            Vector3f c = new Vector3f();
            for (int a = 0; a < 3; a++) {
                float vMin = vanilla[a] * PX, vMax = vanilla[a + 3] * PX;
                float scale;
                float min;
                if (box != null && box[a + 3] - box[a] > 1.0e-4f) {
                    scale = (box[a + 3] - box[a]) / (vMax - vMin);
                    min = box[a];
                } else {
                    scale = h / 2.0f;
                    min = vMin * scale;
                }
                s.setComponent(a, scale);
                c.setComponent(a, min - vMin * scale);
            }
            if ((part == RIGHT_LEG || part == LEFT_LEG) && !rig.hasLegs)
                s.zero();
            stretch[part] = s;
            offset[part] = c;
        }
    }

    private static final int[][] ARM_CORE = {
            {AutoRig.UPPER_ARM_R, AutoRig.FORE_ARM_R},
            {AutoRig.UPPER_ARM_L, AutoRig.FORE_ARM_L},
    };

    private static Quaternionf limbRotation(Vector3f from, Vector3f start, Vector3f end) {
        Vector3f dir = new Vector3f(end).sub(start);
        return dir.lengthSquared() < 1.0e-10f ? new Quaternionf() : new Quaternionf().rotationTo(from, dir.normalize());
    }

    private float[] headBox(GlbModel model, AutoRig rig, float bodyTop) {
        Samples head = collect(model, rig, HEAD, MEMBERS[HEAD], -Float.MAX_VALUE, Float.MAX_VALUE);
        if (head.count < 8) {
            head = collect(model, rig, HEAD, new int[]{AutoRig.HEAD, AutoRig.CHEST, AutoRig.PELVIS}, bodyTop - origin[HEAD].y, Float.MAX_VALUE);
            bone[HEAD] = AutoRig.CHEST;
        }
        if (head.count < 8)
            return null;
        return new float[]{
                head.percentile(0, 0.02f), head.percentile(1, 0.02f), head.percentile(2, 0.02f),
                head.percentile(0, 0.98f), head.percentile(1, 0.98f), head.percentile(2, 0.98f)};
    }

    private float[] bodyBox(GlbModel model, AutoRig rig, float hipY, float neckY) {
        float originY = origin[BODY].y;
        Samples torso = collect(model, rig, BODY, MEMBERS[BODY], hipY - originY, neckY - originY);
        if (torso.count < 8)
            return null;
        float halfWidth = torso.absPercentile(0, 0.0f, 0.9f);
        return new float[]{
                -halfWidth, hipY - originY, torso.percentile(2, 0.08f),
                halfWidth, neckY - originY, torso.percentile(2, 0.92f)};
    }

    private float[] limbBox(GlbModel model, AutoRig rig, int part, int[] members, int[] core, boolean shoulderCap) {
        Samples limb = collect(model, rig, part, members, -Float.MAX_VALUE, Float.MAX_VALUE);
        if (limb.count < 8)
            return null;
        float bottom = Math.min(limb.percentile(1, 0.01f), -1.0e-3f);
        float top = shoulderCap ? limb.percentile(1, 0.99f) : 0.0f;
        float length = top - bottom;

        Samples section = collect(model, rig, part, core, bottom + length * 0.2f, top - length * 0.15f);
        if (section.count < 8)
            section = limb;
        float cx = section.percentile(0, 0.5f), cz = section.percentile(2, 0.5f);
        float rx = section.absPercentile(0, cx, 0.65f), rz = section.absPercentile(2, cz, 0.65f);
        return new float[]{cx - rx, bottom, cz - rz, cx + rx, top, cz + rz};
    }

    private static final class Samples {
        final float[][] coords = new float[3][1024];
        int count;

        void add(Vector3f v) {
            if (count == coords[0].length) {
                for (int a = 0; a < 3; a++)
                    coords[a] = Arrays.copyOf(coords[a], count * 2);
            }
            coords[0][count] = v.x;
            coords[1][count] = v.y;
            coords[2][count] = v.z;
            count++;
        }

        float percentile(int axis, float q) {
            float[] sorted = Arrays.copyOf(coords[axis], count);
            Arrays.sort(sorted);
            return sorted[Math.min(count - 1, (int) (q * count))];
        }

        float absPercentile(int axis, float centre, float q) {
            float[] sorted = new float[count];
            for (int i = 0; i < count; i++)
                sorted[i] = Math.abs(coords[axis][i] - centre);
            Arrays.sort(sorted);
            return Math.max(1.0e-3f, sorted[Math.min(count - 1, (int) (q * count))]);
        }
    }

    private Samples collect(GlbModel model, AutoRig rig, int part, int[] bones, float minY, float maxY) {
        boolean[] member = new boolean[AutoRig.BONE_COUNT];
        for (int bone : bones)
            member[bone] = true;

        Quaternionf toFrame = new Quaternionf(restRotation[part]).conjugate();
        Samples samples = new Samples();
        Vector3f a = new Vector3f(), b = new Vector3f(), c = new Vector3f(), point = new Vector3f();
        List<GlbModel.Primitive> primitives = model.primitives;
        for (int p = 0; p < primitives.size(); p++) {
            GlbModel.Primitive prim = primitives.get(p);
            int[] withoutProp = rig.indicesWithoutProp.get(p);
            int[] triangles = withoutProp != null ? withoutProp : prim.indices;
            int[] boneIndex = rig.boneIndices.get(p);
            float[] boneWeight = rig.boneWeights.get(p);
            for (int t = 0; t + 2 < triangles.length; t += 3) {
                int va = triangles[t], vb = triangles[t + 1], vc = triangles[t + 2];
                float owned = owned(member, boneIndex, boneWeight, va) + owned(member, boneIndex, boneWeight, vb) + owned(member, boneIndex, boneWeight, vc);
                if (owned < 1.5f)
                    continue;
                local(prim.positions, va, part, toFrame, a);
                local(prim.positions, vb, part, toFrame, b);
                local(prim.positions, vc, part, toFrame, c);
                for (float[] w : SAMPLE_WEIGHTS) {
                    point.set(a).mul(w[0]).fma(w[1], b).fma(w[2], c);
                    if (point.y >= minY && point.y <= maxY)
                        samples.add(point);
                }
            }
        }
        return samples;
    }

    private static final float[][] SAMPLE_WEIGHTS = {
            {1, 0, 0}, {0, 1, 0}, {0, 0, 1},
            {0.5f, 0.5f, 0}, {0, 0.5f, 0.5f}, {0.5f, 0, 0.5f},
            {1 / 3f, 1 / 3f, 1 / 3f},
    };

    private static float owned(boolean[] member, int[] boneIndex, float[] boneWeight, int v) {
        float owned = 0.0f;
        for (int k = 0; k < AutoRig.INFLUENCES; k++) {
            if (member[boneIndex[v * AutoRig.INFLUENCES + k]])
                owned += boneWeight[v * AutoRig.INFLUENCES + k];
        }
        return owned;
    }

    private void local(float[] positions, int v, int part, Quaternionf toFrame, Vector3f dest) {
        dest.set(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2]).sub(origin[part]).rotate(toFrame);
    }

    void pose(Matrix4f[] bones, float scale, float rootY, Poses out) {
        Vector3f point = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        Vector3f angles = new Vector3f();
        Matrix3f basis = new Matrix3f();
        for (int part = 0; part < COUNT; part++) {
            Matrix4f bone = bones[this.bone[part]];
            point.set(offset[part]).rotate(restRotation[part]).add(origin[part]);
            bone.transformPosition(point);

            bone.getUnnormalizedRotation(rotation).normalize();
            rotation.premul(HALF_TURN_X).mul(restRotation[part]).mul(HALF_TURN_X);
            basis.rotation(rotation).getEulerAnglesZYX(angles);

            int i = part * 9;
            out.values[i] = point.x * scale * 16.0f;
            out.values[i + 1] = (rootY - point.y * scale) * 16.0f;
            out.values[i + 2] = -point.z * scale * 16.0f;
            out.values[i + 3] = angles.x;
            out.values[i + 4] = angles.y;
            out.values[i + 5] = angles.z;
            out.values[i + 6] = stretch[part].x * scale;
            out.values[i + 7] = stretch[part].y * scale;
            out.values[i + 8] = stretch[part].z * scale;
        }
    }
}
