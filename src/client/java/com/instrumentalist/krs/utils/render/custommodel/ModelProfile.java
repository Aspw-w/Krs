package com.instrumentalist.krs.utils.render.custommodel;

import java.util.Arrays;
import java.util.function.Consumer;

public final class ModelProfile {
    public enum BodyType {
        CHILD,
        ADULT,
        ROBE,
        CAT,
        ROBOT,
        FLOAT,
        BULKY
    }

    public static final class Landmarks {
        public float neckY;
        public float headPivotRaise;
        public float neckBlend = 0.015f;
        public float headRimY = Float.NaN, headRimX = Float.NaN;
        public float backpackZ = Float.NaN, backpackY = Float.NaN;
        public float waistY;
        public float shoulderX, shoulderY;
        public float shoulderZ = Float.NaN;
        public float elbowX = Float.NaN, elbowY = Float.NaN;
        public float handX, handY;
        public float hipX, hipY;
        public float kneeY = Float.NaN;
        public float ankleY = 0.03f;
        public boolean legs = true;
        public float armRadius = 0.04f;
        public float sleeveCap = 1.7f;
        public float legRadius = 0.05f;
        public float skirt;
        public float sleeve;
        public float sleeveDown = Float.NaN;
        public float sleeveStart = 0.25f;
        public float sleeveFold;

        public int propBone = -1;
        public float[] propPath;
        public float[] propKeep;
        public float armOutsideX = Float.NaN;
        public float[] rightArm;
        public float[] propBodyBox;
        public float propKeepRadius;

        public boolean tail;
        public float tailRootY, tailRootZ = Float.NaN, tailTipY, tailTipZ = Float.NaN;
        public float tailRadius = 0.05f;
    }

    public static final class Style {
        public float cadence = 1.0f;
        public float stride = 0.55f;
        public float knee = 0.9f;
        public float armSwing = 0.45f;
        public float elbow = 0.18f;
        public float twist = 0.24f;
        public float lean = 0.05f;
        public float hipSway = 0.035f;
        public float waddle;
        public float bounce;
        public float headBob;
        public float armHang = 0.18f;
        public float headUp = 0.75f;
        public float headDown = 0.8f;
        public float headToChest = 0.22f;
        public float legSpread = 0.03f;
        public float idle = 1.0f;
        public float idleSpeed = 1.0f;
        public float breath = 0.018f;
        public float tailSway = 0.3f;
        public float tailSpeed = 1.0f;
        public float hover;
        public float hoverBob;
        public float runStride = 0.85f;
        public float runArm = 0.95f;
        public float runElbow = 1.25f;
        public float runLean = 0.2f;
        public float headStabilize = 1.0f;
        public float lookYawToBody = 0.18f;
        public float itemScale = 1.0f;
        public float runSpread = 0.22f;
        public float plant = 1.0f;
        public boolean stiff;
        public boolean floating;
        public boolean feminine;
    }

    public final String label;
    public final boolean pmx;
    public final String file;
    public final String[] excludedMeshes;
    public final String[] flatMaterials;
    public final float[] excludedFaces;
    public final BodyType type;
    public final Landmarks landmarks;
    public final Style style;

    private ModelProfile(String label, String file, boolean pmx, String[] excludedMeshes, String[] flatMaterials,
                         float[] excludedFaces, BodyType type, Landmarks landmarks, Style style) {
        this.label = label;
        this.pmx = pmx;
        this.flatMaterials = flatMaterials;
        this.excludedFaces = excludedFaces;
        this.file = file;
        this.excludedMeshes = excludedMeshes;
        this.type = type;
        this.landmarks = landmarks;
        this.style = style;
    }

    public boolean isFlat(String meshName) {
        for (String name : flatMaterials) {
            if (meshName.equals(name))
                return true;
        }
        return false;
    }

    public boolean excludes(String meshName) {
        if (meshName.contains("col0"))
            return true;
        for (String suffix : excludedMeshes) {
            if (meshName.endsWith(suffix))
                return true;
        }
        return false;
    }

    public static Builder builder(String label, String file, BodyType type) {
        return new Builder(label, file, type);
    }

    public static final class Builder {
        private final String label;
        private final String file;
        private final BodyType type;
        private final Landmarks lm = new Landmarks();
        private final Style style;
        private String[] excluded = new String[0];
        private boolean pmx;
        private String[] flat = new String[0];
        private float[] excludedFaces = new float[0];

        private Builder(String label, String file, BodyType type) {
            this.label = label;
            this.file = file;
            this.type = type;
            this.style = defaults(type);
            if (type == BodyType.CAT || type == BodyType.ROBOT) {
                lm.headPivotRaise = 0.08f;
                lm.neckBlend = 0.006f;
            }
        }

        public Builder neck(float y) {
            lm.neckY = y;
            return this;
        }

        public Builder waist(float y) {
            lm.waistY = y;
            return this;
        }

        public Builder shoulder(float x, float y) {
            lm.shoulderX = x;
            lm.shoulderY = y;
            return this;
        }

        public Builder elbow(float x, float y) {
            lm.elbowX = x;
            lm.elbowY = y;
            return this;
        }

        public Builder hand(float x, float y) {
            lm.handX = x;
            lm.handY = y;
            return this;
        }

        public Builder hip(float x, float y) {
            lm.hipX = x;
            lm.hipY = y;
            return this;
        }

        public Builder knee(float y) {
            lm.kneeY = y;
            return this;
        }

        public Builder noLegs() {
            lm.legs = false;
            return this;
        }

        public Builder armRadius(float r) {
            lm.armRadius = r;
            return this;
        }

        public Builder legRadius(float r) {
            lm.legRadius = r;
            return this;
        }

        public Builder skirt(float amount) {
            lm.skirt = amount;
            return this;
        }

        public Builder tail(float rootY, float rootZ, float tipY, float tipZ, float radius) {
            lm.tail = true;
            lm.tailRootY = rootY;
            lm.tailRootZ = rootZ;
            lm.tailTipY = tipY;
            lm.tailTipZ = tipZ;
            lm.tailRadius = radius;
            return this;
        }

        public Builder sleeve(float depth) {
            lm.sleeve = depth;
            return this;
        }

        public Builder sleeve(float depth, float start) {
            lm.sleeve = depth;
            lm.sleeveStart = start;
            return this;
        }

        public Builder shoulderZ(float z) {
            lm.shoulderZ = z;
            return this;
        }

        public Builder sleeveCap(float multiple) {
            lm.sleeveCap = multiple;
            return this;
        }

        public Builder sleeveDown(float depth) {
            lm.sleeveDown = depth;
            return this;
        }

        public Builder sleeveFold(float fraction) {
            lm.sleeveFold = fraction;
            return this;
        }

        public Builder headRim(float y, float x) {
            lm.headRimY = y;
            lm.headRimX = x;
            return this;
        }

        public Builder backpack(float z, float y) {
            lm.backpackZ = z;
            lm.backpackY = y;
            return this;
        }

        public Builder prop(int bone, float... path) {
            lm.propBone = bone;
            lm.propPath = path;
            return this;
        }

        public Builder propBodyBox(float halfX, float minZ, float maxZ, float minY) {
            lm.propBodyBox = new float[]{halfX, minZ, maxZ, minY};
            return this;
        }

        public Builder rightArm(float sx, float sy, float sz, float ex, float ey, float ez, float hx, float hy, float hz) {
            lm.rightArm = new float[]{sx, sy, sz, ex, ey, ez, hx, hy, hz};
            return this;
        }

        public Builder armOutsideX(float x) {
            lm.armOutsideX = x;
            return this;
        }

        public Builder propKeep(float x, float y, float z, float radius) {
            lm.propKeep = new float[]{x, y, z};
            lm.propKeepRadius = radius;
            return this;
        }

        public Builder neckBlend(float halfWidth) {
            lm.neckBlend = halfWidth;
            return this;
        }

        public Builder headPivot(float raise) {
            lm.headPivotRaise = raise;
            return this;
        }

        public Builder flatShade(String... materials) {
            flat = materials;
            return this;
        }

        public Builder pmx() {
            pmx = true;
            return this;
        }

        public Builder exclude(String... meshSuffixes) {
            excluded = meshSuffixes;
            return this;
        }

        public Builder excludeFaces(float u0, float v0, float u1, float v1, float minY, float maxY) {
            int n = excludedFaces.length;
            excludedFaces = Arrays.copyOf(excludedFaces, n + 6);
            System.arraycopy(new float[]{u0, v0, u1, v1, minY, maxY}, 0, excludedFaces, n, 6);
            return this;
        }

        public Builder feminine() {
            style.feminine = true;
            style.hipSway *= 2.0f;
            style.armSwing *= 0.75f;
            style.armHang *= 0.7f;
            style.legSpread = -0.01f;
            style.stride *= 0.88f;
            style.twist *= 0.8f;
            return this;
        }

        public Builder style(Consumer<Style> tweak) {
            tweak.accept(style);
            return this;
        }

        public ModelProfile build() {
            if (lm.waistY == 0.0f)
                lm.waistY = lm.hipY + (lm.shoulderY - lm.hipY) * 0.45f;
            if (Float.isNaN(lm.kneeY))
                lm.kneeY = lm.ankleY + (lm.hipY - lm.ankleY) * 0.5f;
            if (Float.isNaN(lm.elbowX)) {
                lm.elbowX = (lm.shoulderX + lm.handX) * 0.5f;
                lm.elbowY = (lm.shoulderY + lm.handY) * 0.5f;
            }
            return new ModelProfile(label, file, pmx, excluded, flat, excludedFaces, type, lm, style);
        }
    }

    private static Style defaults(BodyType type) {
        Style s = new Style();
        switch (type) {
            case CHILD -> {
                s.cadence = 1.1f;
                s.stride = 0.6f;
                s.knee = 1.0f;
                s.armSwing = 0.55f;
                s.bounce = 0.008f;
                s.idleSpeed = 1.3f;
                s.armHang = 0.2f;
            }
            case ADULT -> {
                s.cadence = 0.95f;
                s.stride = 0.5f;
                s.knee = 0.8f;
                s.armSwing = 0.38f;
                s.twist = 0.18f;
                s.idle = 0.7f;
                s.idleSpeed = 0.8f;
                s.armHang = 0.15f;
            }
            case ROBE -> {
                s.cadence = 1.35f;
                s.stride = 0.26f;
                s.knee = 0.35f;
                s.armSwing = 0.18f;
                s.elbow = 0.35f;
                s.twist = 0.12f;
                s.lean = 0.0f;
                s.armHang = 0.22f;
                s.runStride = 0.4f;
                s.runArm = 0.5f;
                s.runElbow = 0.8f;
                s.runLean = 0.12f;
            }
            case CAT -> {
                s.cadence = 2.4f;
                s.stride = 0.75f;
                s.knee = 0.0f;
                s.armSwing = 0.5f;
                s.elbow = 0.05f;
                s.twist = 0.12f;
                s.lean = 0.03f;
                s.hipSway = 0.0f;
                s.waddle = 0.13f;
                s.bounce = 0.02f;
                s.headBob = 0.05f;
                s.armHang = 0.85f;
                s.idleSpeed = 1.5f;
                s.tailSway = 0.35f;
                s.runStride = 1.0f;
                s.runArm = 0.8f;
                s.runElbow = 0.1f;
                s.runLean = 0.15f;
                s.headUp = 0.4f;
                s.headDown = 0.35f;
                s.headToChest = 0.3f;
            }
            case ROBOT -> {
                s.cadence = 2.0f;
                s.stride = 0.55f;
                s.knee = 0.0f;
                s.armSwing = 0.45f;
                s.elbow = 0.0f;
                s.twist = 0.0f;
                s.lean = 0.0f;
                s.hipSway = 0.0f;
                s.bounce = 0.012f;
                s.armHang = 0.9f;
                s.idle = 0.4f;
                s.tailSway = 0.12f;
                s.tailSpeed = 0.6f;
                s.runStride = 0.75f;
                s.runArm = 0.7f;
                s.runElbow = 0.0f;
                s.runLean = 0.1f;
                s.stiff = true;
                s.headUp = 0.35f;
                s.headDown = 0.3f;
                s.headToChest = 0.3f;
            }
            case FLOAT -> {
                s.floating = true;
                s.stride = 0.0f;
                s.knee = 0.0f;
                s.armSwing = 0.3f;
                s.elbow = 0.1f;
                s.twist = 0.1f;
                s.lean = 0.3f;
                s.hipSway = 0.0f;
                s.armHang = 1.0f;
                s.hover = 0.06f;
                s.hoverBob = 0.025f;
                s.tailSway = 0.25f;
                s.tailSpeed = 0.8f;
                s.runLean = 0.5f;
                s.runArm = 0.5f;
                s.runElbow = 0.3f;
                s.headUp = 0.5f;
                s.headDown = 0.45f;
                s.headToChest = 0.0f;
            }
            case BULKY -> {
                s.cadence = 0.8f;
                s.stride = 0.45f;
                s.knee = 0.7f;
                s.armSwing = 0.35f;
                s.elbow = 0.35f;
                s.twist = 0.3f;
                s.lean = 0.08f;
                s.hipSway = 0.07f;
                s.bounce = 0.012f;
                s.armHang = 0.4f;
                s.legSpread = 0.08f;
                s.breath = 0.03f;
                s.idleSpeed = 0.7f;
                s.runStride = 0.7f;
                s.runArm = 0.7f;
                s.runElbow = 0.9f;
                s.headUp = 0.6f;
                s.headDown = 0.55f;
                s.headToChest = 0.3f;
            }
        }
        return s;
    }
}
