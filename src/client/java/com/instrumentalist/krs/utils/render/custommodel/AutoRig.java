package com.instrumentalist.krs.utils.render.custommodel;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AutoRig {
    public static final int PELVIS = 0;
    public static final int CHEST = 1;
    public static final int HEAD = 2;
    public static final int UPPER_ARM_L = 3;
    public static final int FORE_ARM_L = 4;
    public static final int UPPER_ARM_R = 5;
    public static final int FORE_ARM_R = 6;
    public static final int THIGH_L = 7;
    public static final int SHIN_L = 8;
    public static final int THIGH_R = 9;
    public static final int SHIN_R = 10;
    public static final int TAIL_1 = 11;
    public static final int TAIL_2 = 12;
    public static final int SLEEVE_L = 13;
    public static final int SLEEVE_R = 14;
    public static final int SLEEVE_BACK_L = 15;
    public static final int SLEEVE_BACK_R = 16;
    public static final int BONE_COUNT = 17;

    public static final int[] PARENT = {-1, PELVIS, CHEST, CHEST, UPPER_ARM_L, CHEST, UPPER_ARM_R, PELVIS, THIGH_L, PELVIS, THIGH_R, PELVIS, TAIL_1,
            FORE_ARM_L, FORE_ARM_R, FORE_ARM_L, FORE_ARM_R};

    public static final int INFLUENCES = 4;

    public float height;
    public final Vector3f[] pivots = new Vector3f[BONE_COUNT];
    public final Vector3f handL = new Vector3f();
    public final Vector3f handR = new Vector3f();
    public final Vector3f footL = new Vector3f();
    public final Vector3f footR = new Vector3f();
    public final Vector3f tailTip = new Vector3f();
    public final Vector3f fistEndL = new Vector3f();
    public final Vector3f fistEndR = new Vector3f();
    public float fistHalfDepth;
    public final Vector3f sleeveAttachL = new Vector3f();
    public final Vector3f sleeveAttachR = new Vector3f();
    public final Vector3f elbowHingeL = new Vector3f(-1.0f, 0.0f, 0.0f);
    public final Vector3f elbowHingeR = new Vector3f(-1.0f, 0.0f, 0.0f);
    public float restArmSpread;
    public boolean hasLegs;
    public float itemScale = 1.0f;
    public float sleeveFold;
    public boolean hasTail;

    public final List<int[]> boneIndices = new ArrayList<>();
    public final List<float[]> boneWeights = new ArrayList<>();
    public final List<int[]> indicesWithoutProp = new ArrayList<>();
    private final List<boolean[]> propVertices = new ArrayList<>();

    private ModelProfile.Landmarks lm;
    private boolean tailMeasured;
    private final Vector3f tailRootPoint = new Vector3f();
    private final Vector3f tailTipPoint = new Vector3f();
    private float elbowT;
    private float sleeveShare;
    private float sleeveBackShare;
    private float tailMidT;

    private AutoRig() {
    }

    public static AutoRig build(GlbModel model, ModelProfile profile) {
        model.primitives.removeIf(p -> profile.excludes(p.meshName));
        AutoRig rig = new AutoRig();
        rig.lm = profile.landmarks;
        rig.normalize(model);
        rig.excludeFaces(model, profile.excludedFaces);
        rig.placeJoints(model);
        rig.itemScale = profile.style.itemScale;
        rig.computeElbowHinges();
        rig.skin(model);
        rig.measureFists(model);
        return rig;
    }

    private void normalize(GlbModel model) {
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (GlbModel.Primitive p : model.primitives) {
            for (int i = 1; i < p.positions.length; i += 3) {
                minY = Math.min(minY, p.positions[i]);
                maxY = Math.max(maxY, p.positions[i]);
            }
        }
        for (GlbModel.Primitive p : model.primitives) {
            for (int i = 1; i < p.positions.length; i += 3)
                p.positions[i] -= minY;
        }
        height = Math.max(maxY - minY, 1.0e-4f);
    }

    private void excludeFaces(GlbModel model, float[] regions) {
        if (regions.length == 0)
            return;
        for (GlbModel.Primitive p : model.primitives) {
            int[] kept = new int[p.indices.length];
            int count = 0;
            for (int i = 0; i + 2 < p.indices.length; i += 3) {
                if (!inExcludedRegion(p, p.indices[i], p.indices[i + 1], p.indices[i + 2], regions)) {
                    kept[count++] = p.indices[i];
                    kept[count++] = p.indices[i + 1];
                    kept[count++] = p.indices[i + 2];
                }
            }
            if (count != p.indices.length)
                p.indices = Arrays.copyOf(kept, count);
        }
    }

    private boolean inExcludedRegion(GlbModel.Primitive p, int a, int b, int c, float[] regions) {
        float y = (p.positions[a * 3 + 1] + p.positions[b * 3 + 1] + p.positions[c * 3 + 1]) / (3.0f * height);
        for (int r = 0; r + 5 < regions.length; r += 6) {
            if (y < regions[r + 4] || y > regions[r + 5])
                continue;
            if (uvInside(p, a, regions, r) && uvInside(p, b, regions, r) && uvInside(p, c, regions, r))
                return true;
        }
        return false;
    }

    private static boolean uvInside(GlbModel.Primitive p, int v, float[] regions, int r) {
        float u = p.uvs[v * 2], w = p.uvs[v * 2 + 1];
        return u >= regions[r] && u <= regions[r + 2] && w >= regions[r + 1] && w <= regions[r + 3];
    }

    private void placeJoints(GlbModel model) {
        float h = height;
        if (lm.tail && !Float.isNaN(lm.tailRootZ) && !Float.isNaN(lm.tailTipZ))
            measureTail(model);
        float torsoZ = depthAt(model, 0.0f, (lm.hipY + lm.neckY) * 0.5f, 0.0f);
        float neckZ = lm.headPivotRaise > 0.0f ? headCenterZ(model, depthAt(model, 0.0f, lm.neckY, torsoZ)) : depthAt(model, 0.0f, lm.neckY, torsoZ);
        Vector3f shoulder = new Vector3f(lm.shoulderX, lm.shoulderY, 0.0f).mul(h);
        shoulder.z = Float.isNaN(lm.shoulderZ) ? depthAt(model, lm.shoulderX, lm.shoulderY, torsoZ) : lm.shoulderZ * h;
        Vector3f hand = new Vector3f(lm.handX, lm.handY, 0.0f).mul(h);
        hand.z = depthAt(model, lm.handX, lm.handY, shoulder.z);
        Vector3f elbow = new Vector3f(lm.elbowX, lm.elbowY, 0.0f).mul(h);
        elbow.z = (shoulder.z + hand.z) * 0.5f;

        float hipZ = depthAt(model, lm.hipX, lm.hipY, torsoZ);
        float ankleZ = lm.legs ? depthAt(model, lm.hipX, Math.max(lm.ankleY, lm.kneeY * 0.5f), hipZ) : hipZ;
        float legZ = (hipZ + ankleZ) * 0.5f;

        if (lm.headPivotRaise > 0.0f)
            torsoZ = (hipZ + neckZ) * 0.5f;
        pivots[PELVIS] = new Vector3f(0.0f, lm.hipY * h, torsoZ);
        pivots[CHEST] = new Vector3f(0.0f, lm.waistY * h, torsoZ);
        pivots[HEAD] = new Vector3f(0.0f, (lm.neckY + lm.headPivotRaise) * h, neckZ);
        pivots[UPPER_ARM_L] = new Vector3f(shoulder);
        pivots[FORE_ARM_L] = new Vector3f(elbow);
        pivots[UPPER_ARM_R] = mirror(shoulder);
        pivots[FORE_ARM_R] = mirror(elbow);
        pivots[THIGH_L] = new Vector3f(lm.hipX * h, lm.hipY * h, legZ);
        pivots[SHIN_L] = new Vector3f(lm.hipX * h, lm.kneeY * h, legZ);
        pivots[THIGH_R] = mirror(pivots[THIGH_L]);
        pivots[SHIN_R] = mirror(pivots[SHIN_L]);
        handL.set(hand);
        handR.set(mirror(hand));
        if (lm.rightArm != null) {
            float[] r = lm.rightArm;
            pivots[UPPER_ARM_R] = new Vector3f(r[0], r[1], r[2]).mul(h);
            pivots[FORE_ARM_R] = new Vector3f(r[3], r[4], r[5]).mul(h);
            handR.set(r[6], r[7], r[8]).mul(h);
        }
        sleeveAttachL.set(elbow).lerp(hand, 0.5f);
        sleeveAttachR.set(pivots[FORE_ARM_R]).lerp(handR, 0.5f);
        pivots[SLEEVE_L] = new Vector3f(sleeveAttachL);
        pivots[SLEEVE_R] = new Vector3f(sleeveAttachR);
        pivots[SLEEVE_BACK_L] = new Vector3f(elbow);
        pivots[SLEEVE_BACK_R] = new Vector3f(pivots[FORE_ARM_R]);
        footL.set(lm.hipX * h, lm.ankleY * h, legZ);
        footR.set(mirror(footL));
        hasLegs = lm.legs;
        sleeveFold = lm.sleeveFold;

        hasTail = lm.tail;
        if (hasTail) {
            Vector3f root;
            if (tailMeasured) {
                root = new Vector3f(tailRootPoint);
                tailTip.set(tailTipPoint);
            } else {
                float rootZ = depthAt(model, 0.0f, lm.tailRootY, torsoZ);
                root = new Vector3f(0.0f, lm.tailRootY * h, rootZ);
                tailTip.set(0.0f, lm.tailTipY * h, depthAt(model, 0.0f, lm.tailTipY, rootZ));
            }
            pivots[TAIL_1] = root;
            pivots[TAIL_2] = new Vector3f(root).lerp(tailTip, 0.5f);
        } else {
            pivots[TAIL_1] = new Vector3f(pivots[PELVIS]);
            pivots[TAIL_2] = new Vector3f(pivots[PELVIS]);
        }

        float l1 = shoulder.distance(elbow);
        elbowT = l1 / Math.max(1.0e-6f, l1 + elbow.distance(hand));
        tailMidT = 0.5f;

        Vector3f armDir = new Vector3f(hand).sub(shoulder);
        restArmSpread = armDir.y >= 0.0f ? (float) (Math.PI / 2.0)
                : (float) Math.atan2(Math.abs(armDir.x), -armDir.y);
    }

    private float depthAt(GlbModel model, float x, float y, float fallback) {
        float h = height;
        for (float radius = 0.04f; radius <= 0.17f; radius *= 2.0f) {
            float sum = 0.0f;
            int n = 0;
            float r2 = radius * h * radius * h;
            for (GlbModel.Primitive p : model.primitives) {
                float[] pos = p.positions;
                for (int i = 0; i < pos.length; i += 3) {
                    float dx = pos[i] - x * h, dy = pos[i + 1] - y * h;
                    if (dx * dx + dy * dy > r2 || onTail(pos[i], pos[i + 1], pos[i + 2]))
                        continue;
                    sum += pos[i + 2];
                    n++;
                }
            }
            if (n >= 3)
                return sum / n;
        }
        return fallback;
    }

    private float headCenterZ(GlbModel model, float fallback) {
        float h = height;
        float from = (lm.neckY + (1.0f - lm.neckY) * 0.2f) * h;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (GlbModel.Primitive p : model.primitives) {
            float[] pos = p.positions;
            for (int i = 0; i < pos.length; i += 3) {
                if (pos[i + 1] < from)
                    continue;
                minZ = Math.min(minZ, pos[i + 2]);
                maxZ = Math.max(maxZ, pos[i + 2]);
            }
        }
        return minZ <= maxZ ? (minZ + maxZ) * 0.5f : fallback;
    }

    private void measureTail(GlbModel model) {
        float h = height;
        tailRootPoint.set(0.0f, lm.tailRootY * h, lm.tailRootZ * h);
        Vector3f dir = new Vector3f(0.0f, lm.tailTipY * h, lm.tailTipZ * h).sub(tailRootPoint);
        float length = dir.length();
        dir.normalize();
        float reach = length;
        float radius = lm.tailRadius * h * 3.0f;
        Vector3f d = new Vector3f();
        for (GlbModel.Primitive p : model.primitives) {
            float[] pos = p.positions;
            for (int i = 0; i < pos.length; i += 3) {
                d.set(pos[i], pos[i + 1], pos[i + 2]).sub(tailRootPoint);
                float along = d.dot(dir);
                if (along < length * 0.5f)
                    continue;
                float perp2 = d.lengthSquared() - along * along;
                if (perp2 < radius * radius)
                    reach = Math.max(reach, along);
            }
        }
        tailTipPoint.set(dir).mul(reach).add(tailRootPoint);
        tailMeasured = true;
    }

    private boolean onTail(float x, float y, float z) {
        if (!tailMeasured)
            return false;
        Vector3f a = tailRootPoint, b = tailTipPoint;
        float abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
        float len2 = abx * abx + aby * aby + abz * abz;
        float u = len2 < 1.0e-12f ? 0.0f : ((x - a.x) * abx + (y - a.y) * aby + (z - a.z) * abz) / len2;
        if (u < 0.0f)
            return false;
        u = Math.min(1.0f, u);
        float dx = x - (a.x + abx * u), dy = y - (a.y + aby * u), dz = z - (a.z + abz * u);
        float r = lm.tailRadius * height * (2.0f + 6.0f * u * u);
        return dx * dx + dy * dy + dz * dz < r * r;
    }
    private void measureFists(GlbModel model) {
        float h = height;
        Vector3f elbow = pivots[FORE_ARM_L];
        Vector3f dir = new Vector3f(handL).sub(elbow).normalize();
        float end = -Float.MAX_VALUE;
        for (int pi = 0; pi < model.primitives.size(); pi++) {
            float[] pos = model.primitives.get(pi).positions;
            for (int v = 0; v < pos.length / 3; v++) {
                if (foreWeight(pi, v) >= 0.5f)
                    end = Math.max(end, (pos[v * 3] - elbow.x) * dir.x + (pos[v * 3 + 1] - elbow.y) * dir.y + (pos[v * 3 + 2] - elbow.z) * dir.z);
            }
        }
        if (end == -Float.MAX_VALUE) {
            fistEndL.set(handL);
            fistEndR.set(handR);
            fistHalfDepth = lm.armRadius * h * 0.5f;
            return;
        }

        float handLength = Math.max(0.035f * h, lm.armRadius * h);
        Vector3f centre = new Vector3f();
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        int n = 0;
        for (int pi = 0; pi < model.primitives.size(); pi++) {
            float[] pos = model.primitives.get(pi).positions;
            for (int v = 0; v < pos.length / 3; v++) {
                if (foreWeight(pi, v) < 0.5f)
                    continue;
                float along = (pos[v * 3] - elbow.x) * dir.x + (pos[v * 3 + 1] - elbow.y) * dir.y + (pos[v * 3 + 2] - elbow.z) * dir.z;
                if (along < end - handLength)
                    continue;
                centre.add(pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2]);
                minZ = Math.min(minZ, pos[v * 3 + 2]);
                maxZ = Math.max(maxZ, pos[v * 3 + 2]);
                n++;
            }
        }
        centre.div(n);
        float centreAlong = new Vector3f(centre).sub(elbow).dot(dir);
        fistEndL.set(dir).mul(end - centreAlong).add(centre);
        fistEndR.set(mirror(fistEndL));
        if (lm.rightArm != null)
            fistEndR.set(handR).add(new Vector3f(fistEndL).sub(handL).mul(-1.0f, 1.0f, 1.0f));
        fistHalfDepth = (maxZ - minZ) * 0.5f;
    }

    private float foreWeight(int pi, int v) {
        int[] idx = boneIndices.get(pi);
        float[] w = boneWeights.get(pi);
        float total = 0.0f;
        for (int k = 0; k < INFLUENCES; k++) {
            if (idx[v * INFLUENCES + k] == FORE_ARM_L)
                total += w[v * INFLUENCES + k];
        }
        return total;
    }

    private static Vector3f mirror(Vector3f v) {
        return new Vector3f(-v.x, v.y, v.z);
    }

    public static AutoRig fromPmx(PmxModel pmx, ModelProfile profile) {
        GlbModel model = pmx.mesh;
        AutoRig rig = new AutoRig();
        rig.lm = profile.landmarks;

        for (int i = model.primitives.size() - 1; i >= 0; i--) {
            if (profile.excludes(model.primitives.get(i).meshName)) {
                model.primitives.remove(i);
                pmx.vertexBones.remove(i);
                pmx.vertexWeights.remove(i);
            }
        }

        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (GlbModel.Primitive p : model.primitives) {
            for (int i = 1; i < p.positions.length; i += 3) {
                minY = Math.min(minY, p.positions[i]);
                maxY = Math.max(maxY, p.positions[i]);
            }
        }
        for (GlbModel.Primitive p : model.primitives) {
            for (int i = 1; i < p.positions.length; i += 3)
                p.positions[i] -= minY;
        }
        rig.height = Math.max(maxY - minY, 1.0e-4f);
        float offsetY = minY;

        java.util.function.Function<String, Vector3f> bone = name -> {
            int i = pmx.boneNames.indexOf(name);
            if (i < 0)
                return null;
            float[] b = pmx.bonePositions.get(i);
            return new Vector3f(b[0], b[1] - offsetY, b[2]);
        };
        Vector3f lowerBody = orElse(bone.apply("下半身"), bone.apply("センター"), new Vector3f(0.0f, rig.height * 0.5f, 0.0f));
        Vector3f upperBody = orElse(bone.apply("上半身"), lowerBody, lowerBody);
        Vector3f neck = orElse(bone.apply("首"), bone.apply("頭"), new Vector3f(0.0f, rig.height * 0.8f, 0.0f));
        Vector3f shoulder = orElse(bone.apply("左腕"), null, new Vector3f(rig.height * 0.1f, neck.y - rig.height * 0.05f, 0.0f));
        Vector3f elbow = orElse(bone.apply("左ひじ"), null, new Vector3f(shoulder).add(rig.height * 0.08f, -rig.height * 0.08f, 0.0f));
        Vector3f wrist = orElse(bone.apply("左手首"), null, new Vector3f(elbow).add(rig.height * 0.08f, -rig.height * 0.08f, 0.0f));
        Vector3f hip = orElse(bone.apply("左足"), null, new Vector3f(rig.height * 0.05f, lowerBody.y - rig.height * 0.03f, 0.0f));
        Vector3f knee = orElse(bone.apply("左ひざ"), null, new Vector3f(hip.x, hip.y * 0.5f, hip.z));
        Vector3f ankle = orElse(bone.apply("左足首"), null, new Vector3f(hip.x, rig.height * 0.05f, hip.z));

        rig.pivots[PELVIS] = new Vector3f(0.0f, lowerBody.y, lowerBody.z);
        rig.pivots[CHEST] = new Vector3f(0.0f, upperBody.y, upperBody.z);
        rig.pivots[HEAD] = new Vector3f(0.0f, neck.y, neck.z);
        rig.pivots[UPPER_ARM_L] = new Vector3f(shoulder);
        rig.pivots[FORE_ARM_L] = new Vector3f(elbow);
        rig.pivots[UPPER_ARM_R] = mirror(shoulder);
        rig.pivots[FORE_ARM_R] = mirror(elbow);
        rig.pivots[THIGH_L] = new Vector3f(hip);
        rig.pivots[SHIN_L] = new Vector3f(knee);
        rig.pivots[THIGH_R] = mirror(hip);
        rig.pivots[SHIN_R] = mirror(knee);
        rig.pivots[TAIL_1] = new Vector3f(rig.pivots[PELVIS]);
        rig.pivots[TAIL_2] = new Vector3f(rig.pivots[PELVIS]);
        rig.handL.set(wrist);
        rig.handR.set(mirror(wrist));
        rig.footL.set(ankle);
        rig.footR.set(mirror(ankle));
        rig.sleeveAttachL.set(elbow).lerp(wrist, 0.5f);
        rig.sleeveAttachR.set(mirror(rig.sleeveAttachL));
        rig.pivots[SLEEVE_L] = new Vector3f(rig.sleeveAttachL);
        rig.pivots[SLEEVE_R] = new Vector3f(rig.sleeveAttachR);
        rig.pivots[SLEEVE_BACK_L] = new Vector3f(elbow);
        rig.pivots[SLEEVE_BACK_R] = mirror(elbow);
        rig.hasLegs = true;
        rig.hasTail = false;
        rig.sleeveFold = rig.lm.sleeveFold;
        float l1 = shoulder.distance(elbow);
        rig.elbowT = l1 / Math.max(1.0e-6f, l1 + elbow.distance(wrist));
        rig.tailMidT = 0.5f;
        Vector3f armDir = new Vector3f(wrist).sub(shoulder);
        rig.restArmSpread = armDir.y >= 0.0f ? (float) (Math.PI / 2.0) : (float) Math.atan2(Math.abs(armDir.x), -armDir.y);

        int boneCount = pmx.boneNames.size();
        float[][] map = new float[boneCount][];
        for (int i = 0; i < boneCount; i++)
            map[i] = rig.mapPmxBone(pmx, i, offsetY);

        float[] w = new float[BONE_COUNT];
        for (int pi = 0; pi < model.primitives.size(); pi++) {
            int count = model.primitives.get(pi).vertexCount();
            int[] vb = pmx.vertexBones.get(pi);
            float[] vw = pmx.vertexWeights.get(pi);
            int[] indices = new int[count * INFLUENCES];
            float[] packed = new float[count * INFLUENCES];
            for (int v = 0; v < count; v++) {
                Arrays.fill(w, 0.0f);
                for (int k = 0; k < 4; k++) {
                    int b = vb[v * 4 + k];
                    float weight = vw[v * 4 + k];
                    if (weight <= 0.0f || b < 0 || b >= boneCount)
                        continue;
                    for (int r = 0; r < BONE_COUNT; r++)
                        w[r] += weight * map[b][r];
                }
                pack(w, indices, packed, v * INFLUENCES);
            }
            rig.boneIndices.add(indices);
            rig.boneWeights.add(packed);
            rig.indicesWithoutProp.add(null);
        }
        rig.itemScale = profile.style.itemScale;
        rig.computeElbowHinges();
        rig.measureFists(model);
        return rig;
    }

    void computeElbowHinges() {
        elbowHingeL.set(hinge(pivots[FORE_ARM_L], handL));
        elbowHingeR.set(hinge(pivots[FORE_ARM_R], handR));
    }

    private static Vector3f hinge(Vector3f elbow, Vector3f hand) {
        Vector3f fore = new Vector3f(hand).sub(elbow);
        Vector3f axis = fore.cross(0.0f, 0.0f, 1.0f, new Vector3f());
        if (axis.lengthSquared() < 1.0e-10f)
            return new Vector3f(-1.0f, 0.0f, 0.0f);
        return axis.normalize();
    }

    private static Vector3f orElse(Vector3f a, Vector3f b, Vector3f fallback) {
        return a != null ? a : b != null ? b : fallback;
    }

    private float[] mapPmxBone(PmxModel pmx, int index, float offsetY) {
        float[] out = new float[BONE_COUNT];
        int i = index;
        for (int guard = 0; guard < 64 && i >= 0 && i < pmx.boneNames.size(); guard++) {
            int rigBone = standardBone(pmx.boneNames.get(i));
            if (rigBone >= 0) {
                out[rigBone] = 1.0f;
                if (rigBone == PELVIS && i != index)
                    skirtShare(pmx, index, offsetY, out);
                return out;
            }
            i = pmx.boneParents.get(i);
        }
        out[CHEST] = 1.0f;
        return out;
    }

    private void skirtShare(PmxModel pmx, int index, float offsetY, float[] out) {
        float[] b = pmx.bonePositions.get(index);
        float y = b[1] - offsetY;
        float hipY = pivots[THIGH_L].y;
        float kneeY = pivots[SHIN_L].y;
        float below = smoothstep(hipY, kneeY, y);
        float side = smoothstep(0.1f * pivots[THIGH_L].x, 0.9f * pivots[THIGH_L].x, Math.abs(b[0]));
        float share = 0.45f * below * side;
        if (share <= 0.0f)
            return;
        out[PELVIS] = 1.0f - share;
        out[b[0] > 0.0f ? THIGH_L : THIGH_R] = share;
    }

    private static int standardBone(String name) {
        switch (name) {
            case "全ての親", "操作中心", "センター", "グルーブ", "腰", "下半身" -> {
                return PELVIS;
            }
            case "上半身", "上半身2", "上半身3", "左肩P", "左肩", "左肩C", "右肩P", "右肩", "右肩C" -> {
                return CHEST;
            }
            case "首", "頭" -> {
                return HEAD;
            }
            default -> {
            }
        }
        boolean left = name.startsWith("左");
        boolean right = name.startsWith("右");
        if (!left && !right)
            return -1;
        String part = name.substring(1);
        if (part.startsWith("腕"))
            return left ? UPPER_ARM_L : UPPER_ARM_R;
        if (part.startsWith("ひじ") || part.startsWith("手"))
            return left ? FORE_ARM_L : FORE_ARM_R;
        if (part.equals("足") || part.equals("足D"))
            return left ? THIGH_L : THIGH_R;
        if (part.startsWith("ひざ") || part.startsWith("足首") || part.startsWith("つま先") || part.startsWith("足先"))
            return left ? SHIN_L : SHIN_R;
        return -1;
    }

    private void skin(GlbModel model) {
        float h = height;
        int prims = model.primitives.size();
        float[][][] weights = new float[prims][][];
        boolean[][] fixed = new boolean[prims][];

        for (int pi = 0; pi < prims; pi++) {
            GlbModel.Primitive p = model.primitives.get(pi);
            int count = p.vertexCount();
            float[] pos = p.positions;
            weights[pi] = new float[count][];
            fixed[pi] = new boolean[count];

            int[] component = components(p);
            Map<Integer, float[]> bounds = new HashMap<>();
            for (int v = 0; v < count; v++) {
                float[] b = bounds.computeIfAbsent(component[v], k -> new float[]{
                        Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, 0, 0, 0, 0});
                for (int a = 0; a < 3; a++) {
                    b[a] = Math.min(b[a], pos[v * 3 + a]);
                    b[a + 3] = Math.max(b[a + 3], pos[v * 3 + a]);
                    b[6 + a] += pos[v * 3 + a];
                }
                b[9]++;
            }

            Map<Integer, float[]> rigid = new HashMap<>();
            for (Map.Entry<Integer, float[]> e : bounds.entrySet()) {
                float[] b = e.getValue();
                float dx = b[3] - b[0], dy = b[4] - b[1], dz = b[5] - b[2];
                float diag = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                float cx = b[6] / b[9], cy = b[7] / b[9], cz = b[8] / b[9];
                if (!Float.isNaN(lm.backpackZ) && cz < lm.backpackZ * h && cy < lm.backpackY * h) {
                    float[] w = new float[BONE_COUNT];
                    w[CHEST] = 1.0f;
                    rigid.put(e.getKey(), w);
                } else if (b[1] > (lm.neckY - 0.18f) * h && cy > (lm.neckY + 0.03f) * h && !insideArm(cx, cy, cz)) {
                    float[] w = new float[BONE_COUNT];
                    w[HEAD] = 1.0f;
                    rigid.put(e.getKey(), w);
                } else if (diag < h * 0.14f) {
                    float[] centre = new float[BONE_COUNT];
                    computeWeights(cx, cy, cz, centre);
                    int best = 0;
                    for (int bone = 1; bone < BONE_COUNT; bone++) {
                        if (centre[bone] > centre[best])
                            best = bone;
                    }
                    float[] w = new float[BONE_COUNT];
                    w[best] = 1.0f;
                    rigid.put(e.getKey(), w);
                }
            }

            boolean[] prop = new boolean[count];
            boolean[] rigidProp = new boolean[count];
            boolean anyProp = false;
            for (int v = 0; v < count; v++) {
                float[] w = rigid.get(component[v]);
                if (w == null && inProp(pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2])) {
                    w = new float[BONE_COUNT];
                    w[lm.propBone] = 1.0f;
                    rigidProp[v] = true;
                    if (!inPropKeep(pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2])) {
                        prop[v] = true;
                        anyProp = true;
                    }
                }
                if (w != null) {
                    weights[pi][v] = w;
                    fixed[pi][v] = true;
                } else {
                    weights[pi][v] = new float[BONE_COUNT];
                    computeWeights(pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2], weights[pi][v]);
                }
            }
            indicesWithoutProp.add(anyProp ? withoutVertices(p.indices, prop) : null);
            propVertices.add(rigidProp);
        }

        refineAlongSurface(model, weights, fixed);

        if (!Float.isNaN(lm.armOutsideX)) {
            float[] split = new float[BONE_COUNT];
            for (int pi = 0; pi < prims; pi++) {
                float[] pos = model.primitives.get(pi).positions;
                for (int v = 0; v < pos.length / 3; v++) {
                    float x = pos[v * 3], y = pos[v * 3 + 1], z = pos[v * 3 + 2];
                    float limit = y < lm.hipY * h ? (lm.hipX + lm.legRadius * 2.0f) * h : lm.armOutsideX * h;
                    if (Math.abs(x) <= limit || y > (lm.shoulderY - 0.06f) * h || y < (lm.handY - 0.03f) * h
                            || inProp(x, y, z))
                        continue;
                    partSplit(x > 0.0f ? PART_ARM_L : PART_ARM_R, x, y, z, new float[BONE_COUNT], split);
                    weights[pi][v] = split.clone();
                    if (y < lm.hipY * h + 0.03f * h)
                        propVertices.get(pi)[v] = true;
                }
            }
        }

        for (int pi = 0; pi < prims; pi++) {
            int count = model.primitives.get(pi).vertexCount();
            int[] indices = new int[count * INFLUENCES];
            float[] packed = new float[count * INFLUENCES];
            for (int v = 0; v < count; v++)
                pack(weights[pi][v], indices, packed, v * INFLUENCES);
            boneIndices.add(indices);
            boneWeights.add(packed);
        }

        for (int pi = 0; pi < prims; pi++) {
            boolean[] rigidProp = propVertices.get(pi);
            GlbModel.Primitive p = model.primitives.get(pi);
            int[] idx = boneIndices.get(pi);
            boolean[] cut = new boolean[p.vertexCount()];
            boolean any = false;
            for (int v = 0; v < cut.length; v++) {
                if (rigidProp[v])
                    continue;
                int dominant = idx[v * INFLUENCES];
                cut[v] = dominant == PELVIS || dominant == CHEST || dominant == HEAD
                        || dominant == THIGH_L || dominant == THIGH_R || dominant == SHIN_L || dominant == SHIN_R;
            }
            int[] kept = new int[p.indices.length];
            int n = 0;
            for (int i = 0; i + 2 < p.indices.length; i += 3) {
                int a = p.indices[i], b = p.indices[i + 1], c = p.indices[i + 2];
                boolean hasProp = rigidProp[a] || rigidProp[b] || rigidProp[c];
                boolean hasBody = cut[a] || cut[b] || cut[c];
                if (hasProp && hasBody) {
                    any = true;
                    continue;
                }
                kept[n++] = a;
                kept[n++] = b;
                kept[n++] = c;
            }
            if (any) {
                p.indices = Arrays.copyOf(kept, n);
                int[] without = indicesWithoutProp.get(pi);
                if (without != null)
                    indicesWithoutProp.set(pi, withoutVertices(p.indices, hiddenProp(model, pi)));
            }
        }
    }

    private boolean[] hiddenProp(GlbModel model, int pi) {
        float[] pos = model.primitives.get(pi).positions;
        boolean[] out = new boolean[pos.length / 3];
        for (int v = 0; v < out.length; v++)
            out[v] = inProp(pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2]) && !inPropKeep(pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2]);
        return out;
    }

    private static final int PART_HEAD = 0, PART_TORSO = 1, PART_ARM_L = 2, PART_ARM_R = 3,
            PART_LEG_L = 4, PART_LEG_R = 5, PART_TAIL = 6, PARTS = 7;
    private static final int[][] PART_BONES = {
            {HEAD}, {PELVIS, CHEST}, {UPPER_ARM_L, FORE_ARM_L, SLEEVE_L, SLEEVE_BACK_L}, {UPPER_ARM_R, FORE_ARM_R, SLEEVE_R, SLEEVE_BACK_R},
            {THIGH_L, SHIN_L}, {THIGH_R, SHIN_R}, {TAIL_1, TAIL_2}
    };

    private void refineAlongSurface(GlbModel model, float[][][] weights, boolean[][] fixed) {
        float h = height;
        int prims = model.primitives.size();
        int[][] node = new int[prims][];
        List<float[]> nodePos = new ArrayList<>();
        Map<Long, Integer> welded = new HashMap<>();
        float q = h * 1.0e-4f;
        for (int pi = 0; pi < prims; pi++) {
            float[] pos = model.primitives.get(pi).positions;
            node[pi] = new int[pos.length / 3];
            for (int v = 0; v < pos.length / 3; v++) {
                long key = ((long) Math.round(pos[v * 3] / q) & 0x1FFFFF) << 42
                        | ((long) Math.round(pos[v * 3 + 1] / q) & 0x1FFFFF) << 21
                        | ((long) Math.round(pos[v * 3 + 2] / q) & 0x1FFFFF);
                Integer id = welded.get(key);
                if (id == null) {
                    id = nodePos.size();
                    welded.put(key, id);
                    nodePos.add(new float[]{pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2]});
                }
                node[pi][v] = id;
            }
        }
        int n = nodePos.size();

        List<List<Integer>> adjacency = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            adjacency.add(new ArrayList<>(6));
        for (int pi = 0; pi < prims; pi++) {
            int[] idx = model.primitives.get(pi).indices;
            for (int i = 0; i + 2 < idx.length; i += 3) {
                int a = node[pi][idx[i]], b = node[pi][idx[i + 1]], c = node[pi][idx[i + 2]];
                link(adjacency, a, b);
                link(adjacency, b, c);
                link(adjacency, c, a);
            }
        }

        List<List<Integer>> seeds = new ArrayList<>(PARTS);
        for (int p = 0; p < PARTS; p++)
            seeds.add(new ArrayList<>());
        for (int i = 0; i < n; i++) {
            float[] np = nodePos.get(i);
            int part = seedPart(np[0], np[1], np[2]);
            if (part >= 0)
                seeds.get(part).add(i);
        }

        float[][] dist = new float[PARTS][];
        for (int p = 0; p < PARTS; p++)
            dist[p] = seeds.get(p).isEmpty() ? null : dijkstra(n, adjacency, nodePos, seeds.get(p));

        float sigma = h * 0.012f;
        float[] part = new float[PARTS];
        float[] split = new float[BONE_COUNT];
        for (int pi = 0; pi < prims; pi++) {
            float[] pos = model.primitives.get(pi).positions;
            for (int v = 0; v < node[pi].length; v++) {
                if (fixed[pi][v])
                    continue;
                int id = node[pi][v];
                float min = Float.MAX_VALUE;
                for (int p = 0; p < PARTS; p++) {
                    if (dist[p] != null)
                        min = Math.min(min, dist[p][id]);
                }
                if (min == Float.MAX_VALUE)
                    continue;

                float total = 0.0f;
                for (int p = 0; p < PARTS; p++) {
                    part[p] = dist[p] == null || dist[p][id] == Float.MAX_VALUE ? 0.0f
                            : (float) Math.exp(-(dist[p][id] - min) / sigma);
                    total += part[p];
                }
                float x = pos[v * 3], y = pos[v * 3 + 1], z = pos[v * 3 + 2];
                float[] old = weights[pi][v];
                float[] out = new float[BONE_COUNT];
                for (int p = 0; p < PARTS; p++) {
                    float share = part[p] / total;
                    if (share < 1.0e-3f)
                        continue;
                    partSplit(p, x, y, z, old, split);
                    for (int b = 0; b < BONE_COUNT; b++)
                        out[b] += share * split[b];
                }
                applySkirt(x, y, z, out);
                weights[pi][v] = out;
            }
        }
    }

    private static void link(List<List<Integer>> adjacency, int a, int b) {
        if (a == b)
            return;
        if (!adjacency.get(a).contains(b))
            adjacency.get(a).add(b);
        if (!adjacency.get(b).contains(a))
            adjacency.get(b).add(a);
    }

    private static float[] dijkstra(int n, List<List<Integer>> adjacency, List<float[]> pos, List<Integer> sources) {
        float[] dist = new float[n];
        Arrays.fill(dist, Float.MAX_VALUE);
        java.util.PriorityQueue<float[]> queue = new java.util.PriorityQueue<>((a, b) -> Float.compare(a[0], b[0]));
        for (int s : sources) {
            dist[s] = 0.0f;
            queue.add(new float[]{0.0f, s});
        }
        while (!queue.isEmpty()) {
            float[] top = queue.poll();
            int u = (int) top[1];
            if (top[0] > dist[u])
                continue;
            float[] pu = pos.get(u);
            for (int w : adjacency.get(u)) {
                float[] pw = pos.get(w);
                float dx = pu[0] - pw[0], dy = pu[1] - pw[1], dz = pu[2] - pw[2];
                float nd = dist[u] + (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (nd < dist[w]) {
                    dist[w] = nd;
                    queue.add(new float[]{nd, w});
                }
            }
        }
        return dist;
    }

    private int seedPart(float x, float y, float z) {
        float h = height;
        float ny = y / h;
        float[] w = new float[BONE_COUNT];
        computeWeights(x, y, z, w);
        int part = -1;
        for (int p = 0; p < PARTS; p++) {
            float share = 0.0f;
            for (int b : PART_BONES[p])
                share += w[b];
            if (share >= 0.98f)
                part = p;
        }
        if (part < 0)
            return -1;

        float[] t = new float[2];
        switch (part) {
            case PART_ARM_L, PART_ARM_R -> {
                armMembership(x, y, z, part == PART_ARM_L ? 1.0f : -1.0f, t);
                return t[0] >= 0.2f ? part : -1;
            }
            case PART_TAIL -> {
                tailMembership(x, y, z, t);
                return t[0] >= 0.3f ? part : -1;
            }
            case PART_HEAD -> {
                return ny > lm.neckY + 0.025f && !nearArm(x, y, z, 1.6f) && !onBackpack(y, z) ? part : -1;
            }
            case PART_TORSO -> {
                return ny < lm.neckY - 0.025f && ny > lm.hipY + 0.03f && !nearArm(x, y, z, 1.15f) ? part : -1;
            }
            default -> {
                return ny < Math.min(lm.kneeY, lm.hipY - 0.04f) ? part : -1;
            }
        }
    }

    private boolean nearArm(float x, float y, float z, float factor) {
        float[] t = new float[2];
        float radius = lm.armRadius * height * factor;
        for (int side = 0; side < 2; side++) {
            Vector3f s = side == 0 ? pivots[UPPER_ARM_L] : pivots[UPPER_ARM_R];
            Vector3f e = side == 0 ? pivots[FORE_ARM_L] : pivots[FORE_ARM_R];
            Vector3f hnd = side == 0 ? handL : handR;
            if (segment(x, y, z, s, e, t) < radius || segment(x, y, z, e, hnd, t) < radius)
                return true;
        }
        return false;
    }
    private void partSplit(int part, float x, float y, float z, float[] old, float[] out) {
        Arrays.fill(out, 0.0f);
        float inside = 0.0f;
        for (int b : PART_BONES[part])
            inside += old[b];
        if (inside > 1.0e-3f) {
            for (int b : PART_BONES[part])
                out[b] = old[b] / inside;
            return;
        }

        float h = height;
        float ny = y / h;
        float[] t = new float[2];
        switch (part) {
            case PART_HEAD -> out[HEAD] = 1.0f;
            case PART_TORSO -> {
                float chest = smoothstep(lm.waistY - 0.05f, lm.waistY + 0.05f, ny);
                out[PELVIS] = 1.0f - chest;
                out[CHEST] = chest;
            }
            case PART_ARM_L, PART_ARM_R -> {
                boolean leftArm = part == PART_ARM_L;
                armMembership(x, y, z, leftArm ? 1.0f : -1.0f, t);
                float sleeve = sleeveShare;
                float back = sleeveBackShare;
                splitChain(out, leftArm ? UPPER_ARM_L : UPPER_ARM_R, leftArm ? FORE_ARM_L : FORE_ARM_R, 1.0f - sleeve, t[0], elbowT);
                out[leftArm ? SLEEVE_L : SLEEVE_R] += sleeve * (1.0f - back);
                out[leftArm ? SLEEVE_BACK_L : SLEEVE_BACK_R] += sleeve * back;
            }
            case PART_LEG_L, PART_LEG_R -> {
                boolean leftLeg = part == PART_LEG_L;
                float shin = 1.0f - smoothstep((lm.kneeY - 0.02f) * h, (lm.kneeY + 0.02f) * h, y);
                out[leftLeg ? THIGH_L : THIGH_R] = 1.0f - shin;
                out[leftLeg ? SHIN_L : SHIN_R] = shin;
            }
            default -> {
                tailMembership(x, y, z, t);
                splitChain(out, TAIL_1, TAIL_2, 1.0f, t[0], tailMidT);
            }
        }
    }

    private void applySkirt(float x, float y, float z, float[] w) {
        if (lm.skirt <= 0.0f || !hasLegs)
            return;
        float legs = w[THIGH_L] + w[SHIN_L] + w[THIGH_R] + w[SHIN_R];
        if (legs <= 0.0f)
            return;
        float h = height;
        float legX = lm.hipX * h;
        float axisDist = (float) Math.sqrt(Math.min(sq(x - legX), sq(x + legX)) + sq(z - pivots[THIGH_L].z));
        float radius = lm.legRadius * h;
        float keep = (1.0f - smoothstep(radius, radius * 2.0f, axisDist) * lm.skirt)
                * (1.0f - lm.skirt * 0.5f * smoothstep(lm.kneeY, lm.hipY, y / h));
        float moved = legs * (1.0f - keep);
        for (int b : new int[]{THIGH_L, SHIN_L, THIGH_R, SHIN_R})
            w[b] *= keep;
        float chest = smoothstep(lm.waistY - 0.05f, lm.waistY + 0.05f, y / h);
        w[PELVIS] += moved * (1.0f - chest);
        w[CHEST] += moved * chest;
    }
    private static int[] withoutVertices(int[] indices, boolean[] drop) {
        int[] out = new int[indices.length];
        int n = 0;
        for (int i = 0; i + 2 < indices.length; i += 3) {
            if (drop[indices[i]] || drop[indices[i + 1]] || drop[indices[i + 2]])
                continue;
            out[n++] = indices[i];
            out[n++] = indices[i + 1];
            out[n++] = indices[i + 2];
        }
        return Arrays.copyOf(out, n);
    }

    private boolean inProp(float x, float y, float z) {
        if (lm.propBone < 0)
            return false;
        float h = height;
        float[] path = lm.propPath;
        float[] t = new float[2];
        boolean inside = false;
        for (int i = 0; i + 7 < path.length && !inside; i += 4) {
            Vector3f a = new Vector3f(path[i], path[i + 1], path[i + 2]).mul(h);
            Vector3f b = new Vector3f(path[i + 4], path[i + 5], path[i + 6]).mul(h);
            float d = segment(x, y, z, a, b, t);
            float r = (path[i + 3] + (path[i + 7] - path[i + 3]) * t[0]) * h;
            inside = d <= r;
        }
        if (!inside)
            return false;
        if (y < lm.hipY * h && Math.abs(x) < (lm.hipX + lm.legRadius * 2.0f) * h)
            return false;
        float[] box = lm.propBodyBox;
        return box == null || !(Math.abs(x) <= box[0] * h && z >= box[1] * h && z <= box[2] * h && y >= box[3] * h);
    }

    private boolean inPropKeep(float x, float y, float z) {
        if (lm.propKeep == null)
            return false;
        float h = height;
        float kx = x - lm.propKeep[0] * h, ky = y - lm.propKeep[1] * h, kz = z - lm.propKeep[2] * h;
        return kx * kx + ky * ky + kz * kz < lm.propKeepRadius * h * lm.propKeepRadius * h;
    }

    private boolean insideArm(float x, float y, float z) {
        float[] t = new float[2];
        return armMembership(x, y, z, x >= 0.0f ? 1.0f : -1.0f, t) > 0.5f;
    }

    private float armMembership(float x, float y, float z, float side, float[] out) {
        if (x * side <= 0.0f)
            return 0.0f;
        Vector3f s = side > 0 ? pivots[UPPER_ARM_L] : pivots[UPPER_ARM_R];
        Vector3f e = side > 0 ? pivots[FORE_ARM_L] : pivots[FORE_ARM_R];
        Vector3f hnd = side > 0 ? handL : handR;
        float h = height;
        float r1 = segment(x, y, z, s, e, out);
        float c1 = out[0];
        float u1 = out[1];
        float r2 = segment(x, y, z, e, hnd, out);
        float t2 = out[0];
        float l1 = s.distance(e), l2 = e.distance(hnd), total = Math.max(1.0e-6f, l1 + l2);
        float r, t;
        if (r1 <= r2) {
            r = r1;
            t = (u1 < 0.0f ? u1 : c1) * l1 / total;
        } else {
            r = r2;
            t = (l1 + t2 * l2) / total;
        }
        out[0] = t;
        float radius = lm.armRadius * h * (1.0f + 0.6f * smoothstep(0.75f, 1.05f, t));
        float along = smoothstep(-0.06f, 0.06f, t);
        float near = 1.0f - smoothstep(radius, radius * 1.6f, r);
        if (r1 <= r2 && u1 > 0.05f && lm.sleeveCap > 1.0f) {
            Vector3f closest = closestOn(s, e, x, y, z);
            boolean outer = x * side >= closest.x * side - radius * 0.3f;
            if (lm.sleeve > 0.0f && y < closest.y - radius * 0.5f)
                outer = false;
            if (outer) {
                float cap = radius * lm.sleeveCap;
                near = Math.max(near, (1.0f - smoothstep(cap, cap * 1.3f, r)) * smoothstep(0.05f, 0.2f, u1));
            }
        }
        sleeveShare = 0.0f;
        sleeveBackShare = 0.0f;

        if (lm.sleeve > 0.0f && t > lm.sleeveStart) {
            Vector3f closest = r1 <= r2 ? closestOn(s, e, x, y, z) : closestOn(e, hnd, x, y, z);
            float py = y - closest.y, pz = z - closest.z;
            float dist = (float) Math.sqrt(py * py + pz * pz);
            float down = dist > 1.0e-6f ? -py / dist : 0.0f;
            float back = dist > 1.0e-6f ? -pz / dist : 0.0f;
            float reach = (down > back && !Float.isNaN(lm.sleeveDown) ? lm.sleeveDown : lm.sleeve) * h;
            boolean besideBody = Math.abs(x) > Math.abs(closest.x) - radius;
            if (dist > 1.0e-6f && dist < reach && py < 0.03f * h && besideBody) {
                float hanging = smoothstep(0.35f, 0.6f, Math.max(down, back));
                if (hanging > near) {
                    near = hanging;
                    sleeveShare = smoothstep(radius * 0.6f, radius * 1.5f, dist);
                    sleeveBackShare = smoothstep(0.25f, 0.5f, back - down);
                }
            }
        }
        return along * near;
    }

    private static Vector3f closestOn(Vector3f a, Vector3f b, float px, float py, float pz) {
        float abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
        float len2 = abx * abx + aby * aby + abz * abz;
        float u = len2 < 1.0e-12f ? 0.0f : ((px - a.x) * abx + (py - a.y) * aby + (pz - a.z) * abz) / len2;
        u = Math.max(0.0f, Math.min(1.0f, u));
        return new Vector3f(a.x + abx * u, a.y + aby * u, a.z + abz * u);
    }

    private float tailMembership(float x, float y, float z, float[] out) {
        if (!hasTail)
            return 0.0f;
        float r = segment(x, y, z, pivots[TAIL_1], tailTip, out);
        float t = out[1];
        out[0] = Math.max(0.0f, Math.min(1.0f, t));
        float radius = lm.tailRadius * height * (1.0f + 4.0f * out[0] * out[0]);
        float along = smoothstep(-0.04f, 0.05f, t);
        float near = 1.0f - smoothstep(radius, radius * 1.5f, r);
        return along * near;
    }

    private void computeWeights(float x, float y, float z, float[] out) {
        Arrays.fill(out, 0.0f);
        float h = height;
        float ny = y / h;
        float[] t = new float[2];

        float rest = 1.0f;

        if (!Float.isNaN(lm.headRimY) && !onBackpack(y, z)) {
            float rim = smoothstep(lm.headRimY - 0.01f, lm.headRimY + 0.01f, ny)
                    * smoothstep(lm.headRimX - 0.015f, lm.headRimX + 0.015f, Math.abs(x) / h);
            if (rim > 0.0f) {
                out[HEAD] += rim;
                rest -= rim;
            }
        }

        float armL = armMembership(x, y, z, 1.0f, t) * rest;
        float armLT = t[0];
        float sleeveL = sleeveShare;
        float sleeveBackL = sleeveBackShare;
        float armR = armMembership(x, y, z, -1.0f, t) * rest;
        float armRT = t[0];
        float sleeveR = sleeveShare;
        float sleeveBackR = sleeveBackShare;
        float arms = Math.min(rest, armL + armR);
        if (arms > 0.0f) {
            float scale = arms / Math.max(1.0e-6f, armL + armR);
            splitChain(out, UPPER_ARM_L, FORE_ARM_L, armL * scale * (1.0f - sleeveL), armLT, elbowT);
            splitChain(out, UPPER_ARM_R, FORE_ARM_R, armR * scale * (1.0f - sleeveR), armRT, elbowT);
            out[SLEEVE_L] += armL * scale * sleeveL * (1.0f - sleeveBackL);
            out[SLEEVE_R] += armR * scale * sleeveR * (1.0f - sleeveBackR);
            out[SLEEVE_BACK_L] += armL * scale * sleeveL * sleeveBackL;
            out[SLEEVE_BACK_R] += armR * scale * sleeveR * sleeveBackR;
            rest -= arms;
        }

        float tail = tailMembership(x, y, z, t) * rest;
        if (tail > 0.0f) {
            splitChain(out, TAIL_1, TAIL_2, tail, t[0], tailMidT);
            rest -= tail;
        }

        float head = onBackpack(y, z) ? 0.0f : rest * smoothstep(lm.neckY - lm.neckBlend, lm.neckY + lm.neckBlend, ny);
        out[HEAD] += head;
        rest -= head;

        if (hasLegs && rest > 0.0f) {
            float leg = 1.0f - smoothstep(lm.hipY - 0.03f, lm.hipY + 0.03f, ny);
            if (lm.skirt > 0.0f && leg > 0.0f) {
                float legX = lm.hipX * h;
                float axisDist = (float) Math.sqrt(Math.min(sq(x - legX), sq(x + legX)) + sq(z - pivots[THIGH_L].z));
                float radius = lm.legRadius * h;
                float cloth = smoothstep(radius, radius * 2.0f, axisDist);
                leg *= 1.0f - cloth * lm.skirt;
                leg *= 1.0f - lm.skirt * 0.5f * smoothstep(lm.kneeY, lm.hipY, ny);
            }
            leg *= rest;
            if (leg > 0.0f) {
                float sideL = smoothstep(-0.012f * h, 0.012f * h, x);
                float kneeShare = 1.0f - smoothstep((lm.kneeY - 0.02f) * h, (lm.kneeY + 0.02f) * h, y);
                out[THIGH_L] += leg * sideL * (1.0f - kneeShare);
                out[SHIN_L] += leg * sideL * kneeShare;
                out[THIGH_R] += leg * (1.0f - sideL) * (1.0f - kneeShare);
                out[SHIN_R] += leg * (1.0f - sideL) * kneeShare;
                rest -= leg;
            }
        }

        if (rest > 0.0f) {
            float chest = smoothstep(lm.waistY - 0.05f, lm.waistY + 0.05f, ny);
            out[PELVIS] += rest * (1.0f - chest);
            out[CHEST] += rest * chest;
        }
    }

    private boolean onBackpack(float y, float z) {
        return !Float.isNaN(lm.backpackZ) && z < lm.backpackZ * height && y < lm.backpackY * height;
    }

    private static void splitChain(float[] out, int upper, int lower, float weight, float t, float joint) {
        if (weight <= 0.0f)
            return;
        float lowerShare = smoothstep(joint - 0.08f, joint + 0.08f, t);
        out[upper] += weight * (1.0f - lowerShare);
        out[lower] += weight * lowerShare;
    }

    private static float segment(float px, float py, float pz, Vector3f a, Vector3f b, float[] out) {
        float abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
        float len2 = abx * abx + aby * aby + abz * abz;
        float u = len2 < 1.0e-12f ? 0.0f : ((px - a.x) * abx + (py - a.y) * aby + (pz - a.z) * abz) / len2;
        out[1] = u;
        float c = Math.max(0.0f, Math.min(1.0f, u));
        out[0] = c;
        float dx = px - (a.x + abx * c), dy = py - (a.y + aby * c), dz = pz - (a.z + abz * c);
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void pack(float[] weights, int[] indices, float[] packed, int offset) {
        boolean[] used = new boolean[BONE_COUNT];
        float total = 0.0f;
        for (int slot = 0; slot < INFLUENCES; slot++) {
            int best = -1;
            for (int b = 0; b < BONE_COUNT; b++) {
                if (!used[b] && weights[b] > 0.0f && (best < 0 || weights[b] > weights[best]))
                    best = b;
            }
            if (best < 0 || (slot > 0 && weights[best] < 0.01f)) {
                indices[offset + slot] = 0;
                packed[offset + slot] = 0.0f;
                continue;
            }
            used[best] = true;
            indices[offset + slot] = best;
            packed[offset + slot] = weights[best];
            total += weights[best];
        }
        if (total <= 0.0f) {
            indices[offset] = CHEST;
            packed[offset] = 1.0f;
            return;
        }
        for (int slot = 0; slot < INFLUENCES; slot++)
            packed[offset + slot] /= total;
    }

    private int[] components(GlbModel.Primitive p) {
        int count = p.vertexCount();
        int[] parent = new int[count];
        for (int i = 0; i < count; i++)
            parent[i] = i;

        float q = height * 1.0e-4f;
        Map<Long, Integer> welded = new HashMap<>();
        float[] pos = p.positions;
        for (int v = 0; v < count; v++) {
            long key = ((long) Math.round(pos[v * 3] / q) & 0x1FFFFF) << 42
                    | ((long) Math.round(pos[v * 3 + 1] / q) & 0x1FFFFF) << 21
                    | ((long) Math.round(pos[v * 3 + 2] / q) & 0x1FFFFF);
            Integer other = welded.putIfAbsent(key, v);
            if (other != null)
                union(parent, v, other);
        }

        int[] idx = p.indices;
        for (int i = 0; i + 2 < idx.length; i += 3) {
            union(parent, idx[i], idx[i + 1]);
            union(parent, idx[i], idx[i + 2]);
        }

        int[] out = new int[count];
        for (int v = 0; v < count; v++)
            out[v] = find(parent, v);
        return out;
    }

    private static int find(int[] parent, int v) {
        while (parent[v] != v) {
            parent[v] = parent[parent[v]];
            v = parent[v];
        }
        return v;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb)
            parent[ra] = rb;
    }

    private static float sq(float v) {
        return v * v;
    }

    static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0.0f, Math.min(1.0f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }
}
