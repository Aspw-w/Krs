package com.instrumentalist.krs.utils.render.custommodel;

import com.instrumentalist.krs.utils.render.custommodel.ModelProfile.BodyType;

import java.util.List;

public final class ModelCatalog {
    public static final List<ModelProfile> MODELS = List.of(
            ModelProfile.builder("Keita", "keita", BodyType.CHILD)
                    .neck(0.63f).shoulder(0.08f, 0.57f).elbow(0.15f, 0.47f).hand(0.21f, 0.37f)
                    .hip(0.05f, 0.32f).knee(0.15f).waist(0.45f).build(),
            ModelProfile.builder("Fumi-chan", "fumi", BodyType.CHILD)
                    .neck(0.62f).shoulder(0.09f, 0.58f).elbow(0.17f, 0.49f).hand(0.24f, 0.40f)
                    .hip(0.05f, 0.33f).knee(0.16f).waist(0.45f).feminine().build(),
            ModelProfile.builder("Inaho", "inaho", BodyType.CHILD)
                    .neck(0.68f).shoulder(0.09f, 0.66f).elbow(0.15f, 0.585f).hand(0.235f, 0.42f)
                    .hip(0.05f, 0.34f).knee(0.16f).waist(0.50f).skirt(0.3f)
                    .exclude("skin--watch3--", "skin3", "skin4", "skin5")
                    .excludeFaces(0.12f, 0.84f, 0.17f, 0.89f, 0.7f, 0.9f).feminine().build(),
            ModelProfile.builder("Kanchi", "kanchi", BodyType.CHILD)
                    .neck(0.67f).shoulder(0.10f, 0.58f).elbow(0.18f, 0.49f).hand(0.24f, 0.40f)
                    .hip(0.05f, 0.31f).knee(0.14f).waist(0.45f).skirt(0.25f)
                    .style(s -> s.armSwing = 0.45f).build(),
            ModelProfile.builder("Kuma", "kuma", BodyType.CHILD)
                    .neck(0.705f).shoulder(0.165f, 0.6f).elbow(0.27f, 0.46f).hand(0.36f, 0.32f)
                    .hip(0.09f, 0.29f).knee(0.11f).waist(0.42f).armRadius(0.075f).legRadius(0.06f)
                    .style(s -> {
                        s.cadence = 0.95f;
                        s.armHang = 0.4f;
                        s.hipSway = 0.07f;
                        s.legSpread = 0.06f;
                        s.bounce = 0.012f;
                    }).build(),
            ModelProfile.builder("Keita's Dad", "keita_dad", BodyType.ADULT)
                    .neck(0.69f).shoulder(0.10f, 0.63f).elbow(0.20f, 0.52f).hand(0.27f, 0.41f)
                    .hip(0.05f, 0.42f).knee(0.21f).waist(0.52f).build(),
            ModelProfile.builder("Keita's Mom", "keita_mom", BodyType.ADULT)
                    .neck(0.72f).shoulder(0.09f, 0.63f).elbow(0.19f, 0.52f).hand(0.28f, 0.42f)
                    .hip(0.05f, 0.42f).knee(0.20f).waist(0.52f).skirt(0.3f).armRadius(0.075f).feminine().build(),
            ModelProfile.builder("Keizo", "keizo", BodyType.CHILD)
                    .neck(0.62f).shoulder(0.10f, 0.58f).elbow(0.18f, 0.49f).hand(0.24f, 0.40f)
                    .hip(0.05f, 0.32f).knee(0.13f).waist(0.45f)
                    .style(s -> {
                        s.armSwing = 0.65f;
                        s.bounce = 0.012f;
                    }).build(),
            ModelProfile.builder("Keinosuke", "keinosuke", BodyType.CHILD)
                    .neck(0.65f).shoulder(0.10f, 0.60f).elbow(0.19f, 0.51f).hand(0.25f, 0.42f)
                    .hip(0.05f, 0.30f).knee(0.14f).waist(0.45f).skirt(0.45f).sleeve(0.09f, 0.2f)
                    .style(s -> s.stride = 0.5f).build(),
            ModelProfile.builder("O-Fumi", "ofumi", BodyType.ROBE)
                    .neck(0.62f).shoulder(0.10f, 0.58f).elbow(0.17f, 0.49f).hand(0.24f, 0.40f)
                    .hip(0.05f, 0.33f).knee(0.14f).waist(0.45f).skirt(0.85f).sleeve(0.13f, 0.2f).feminine().build(),
            ModelProfile.builder("Mac", "mac", BodyType.CHILD)
                    .neck(0.69f).shoulder(0.10f, 0.60f).elbow(0.18f, 0.50f).hand(0.25f, 0.40f)
                    .hip(0.05f, 0.36f).knee(0.16f).waist(0.48f).skirt(0.2f)
                    .style(s -> s.armSwing = 0.6f).build(),
            ModelProfile.builder("Dorothy", "dorothy", BodyType.CHILD)
                    .neck(0.64f).shoulder(0.10f, 0.60f).elbow(0.19f, 0.50f).hand(0.27f, 0.40f)
                    .hip(0.05f, 0.30f).knee(0.15f).waist(0.45f).skirt(0.7f).armRadius(0.065f).feminine().build(),
            ModelProfile.builder("Enma", "enma", BodyType.CHILD)
                    .neck(0.60f).shoulder(0.10f, 0.57f).elbow(0.17f, 0.48f).hand(0.22f, 0.38f)
                    .hip(0.05f, 0.32f).knee(0.14f).waist(0.45f)
                    .style(s -> {
                        s.cadence = 1.0f;
                        s.armSwing = 0.35f;
                        s.bounce = 0.004f;
                        s.lean = 0.02f;
                        s.idle = 0.7f;
                    }).build(),
            ModelProfile.builder("Jibanyan", "jibanyan", BodyType.CAT)
                    .neck(0.43f).shoulder(0.13f, 0.36f).hand(0.36f, 0.36f)
                    .hip(0.08f, 0.10f).knee(0.05f).waist(0.25f).armRadius(0.06f).legRadius(0.07f)
                    .tail(0.12f, -0.08f, 0.12f, -0.56f, 0.04f).exclude("skin2").build(),
            ModelProfile.builder("Whisper", "whisper", BodyType.FLOAT)
                    .neck(0.45f).shoulder(0.19f, 0.70f).hand(0.36f, 0.70f).hip(0.0f, 0.45f).noLegs()
                    .armRadius(0.03f).tail(0.45f, Float.NaN, 0.0f, Float.NaN, 0.22f)
                    .style(s -> {
                        s.lean = 0.18f;
                        s.runLean = 0.32f;
                    }).build(),
            ModelProfile.builder("Komasan", "komasan", BodyType.CAT)
                    .neck(0.38f).shoulder(0.16f, 0.36f).hand(0.36f, 0.36f)
                    .hip(0.08f, 0.12f).knee(0.06f).waist(0.24f).armRadius(0.05f).legRadius(0.07f)
                    .backpack(-0.08f, 0.47f)
                    .tail(0.12f, -0.08f, 0.04f, -0.22f, 0.05f)
                    .style(s -> {
                        s.waddle = 0.16f;
                        s.headBob = 0.07f;
                        s.cadence = 2.6f;
                    }).build(),
            ModelProfile.builder("Komajiro", "komajiro", BodyType.CAT)
                    .neck(0.38f).shoulder(0.16f, 0.36f).hand(0.36f, 0.36f)
                    .hip(0.08f, 0.12f).knee(0.06f).waist(0.24f).armRadius(0.05f).legRadius(0.07f)
                    .backpack(-0.08f, 0.47f)
                    .tail(0.12f, -0.10f, 0.04f, -0.22f, 0.05f)
                    .style(s -> {
                        s.cadence = 2.8f;
                        s.bounce = 0.03f;
                        s.armSwing = 0.65f;
                    }).build(),
            ModelProfile.builder("Robonyan", "robonyan", BodyType.ROBOT)
                    .neck(0.42f).shoulder(0.13f, 0.36f).hand(0.36f, 0.36f)
                    .hip(0.10f, 0.12f).knee(0.06f).waist(0.25f).armRadius(0.06f).legRadius(0.07f)
                    .tail(0.10f, -0.08f, 0.10f, -0.55f, 0.04f).build(),
            ModelProfile.builder("Fuyunyan", "fuyunyan", BodyType.CAT)
                    .neck(0.46f).shoulder(0.12f, 0.33f).hand(0.34f, 0.33f)
                    .hip(0.08f, 0.10f).knee(0.05f).waist(0.25f).armRadius(0.05f).legRadius(0.07f)
                    .tail(0.08f, -0.08f, 0.07f, -0.16f, 0.04f).exclude("skin2")
                    .style(s -> {
                        s.waddle = 0.08f;
                        s.headBob = 0.03f;
                        s.lean = 0.06f;
                    }).build(),
            ModelProfile.builder("Bushinyan", "bushinyan", BodyType.CAT)
                    .neck(0.45f).shoulder(0.13f, 0.36f).hand(0.36f, 0.36f)
                    .hip(0.08f, 0.10f).knee(0.05f).waist(0.25f).armRadius(0.05f).legRadius(0.07f)
                    .tail(0.17f, -0.05f, 0.17f, -0.55f, 0.035f).exclude("skin2")
                    .style(s -> {
                        s.waddle = 0.1f;
                        s.cadence = 2.2f;
                        s.lean = 0.0f;
                    }).build(),
            ModelProfile.builder("Blizzaria", "blizzaria", BodyType.ROBE)
                    .neck(0.53f).shoulder(0.10f, 0.50f).elbow(0.23f, 0.50f).hand(0.36f, 0.50f)
                    .hip(0.05f, 0.25f).knee(0.12f).waist(0.40f).armRadius(0.05f).skirt(0.9f).sleeve(0.37f, 0.15f).sleeveDown(0.05f).sleeveFold(0.2f).shoulderZ(0.0f).exclude("skin2")
                    .feminine()
                    .style(s -> {
                        s.armHang = 0.7f;
                        s.hover = 0.02f;
                        s.hoverBob = 0.008f;
                        s.stride = 0.1f;
                        s.knee = 0.1f;
                        s.lean = 0.08f;
                    }).build(),
            ModelProfile.builder("Lord Enma", "lord_enma", BodyType.ROBE)
                    .neck(0.60f).shoulder(0.10f, 0.55f).elbow(0.23f, 0.54f).hand(0.36f, 0.53f)
                    .hip(0.05f, 0.33f).knee(0.17f).waist(0.45f).armRadius(0.07f).skirt(0.75f).sleeve(0.2f, 0.1f)
                    .style(s -> {
                        s.cadence = 1.1f;
                        s.stride = 0.32f;
                        s.armSwing = 0.25f;
                        s.lean = -0.04f;
                        s.armHang = 0.45f;
                        s.idle = 0.5f;
                    }).build(),
            ModelProfile.builder("Belle", "belle", BodyType.ADULT)
                    .pmx().exclude("髮+").armRadius(0.035f).feminine()
                    .style(s -> {
                        s.cadence = 1.0f;
                        s.stride = 0.38f;
                        s.runStride = 0.5f;
                        s.knee = 0.75f;
                        s.armSwing = 0.3f;
                        s.runArm = 0.45f;
                        s.runElbow = 1.35f;
                        s.twist = 0.1f;
                        s.hipSway = 0.022f;
                        s.lean = 0.02f;
                        s.runLean = 0.16f;
                        s.runSpread = 0.42f;
                        s.bounce = 0.0f;
                        s.headBob = 0.0f;
                        s.plant = 0.45f;
                        s.idle = 0.6f;
                        s.breath = 0.01f;
                    }).build(),
            ModelProfile.builder("TungTung", "tungtung", BodyType.ADULT)
                    .neck(1.2f)
                    .shoulder(0.13f, 0.645f).elbow(0.163f, 0.49f).hand(0.16f, 0.315f)
                    .rightArm(-0.13f, 0.645f, -0.1f, -0.158f, 0.45f, -0.065f, -0.165f, 0.3f, 0.01f)
                    .hip(0.05f, 0.37f).knee(0.2f).waist(0.41f).armRadius(0.03f).legRadius(0.03f)
                    .sleeveCap(1.0f).armOutsideX(0.121f)
                    .prop(AutoRig.FORE_ARM_R,
                            -0.28f, -0.01f, 0.29f, 0.07f,
                            -0.26f, 0.05f, 0.25f, 0.085f,
                            -0.22f, 0.17f, 0.15f, 0.06f,
                            -0.185f, 0.25f, 0.06f, 0.045f,
                            -0.163f, 0.30f, 0.005f, 0.032f,
                            -0.15f, 0.36f, -0.05f, 0.022f,
                            -0.13f, 0.405f, -0.115f, 0.036f,
                            -0.12f, 0.43f, -0.16f, 0.03f,
                            -0.095f, 0.47f, -0.23f, 0.034f)
                    .propBodyBox(0.1125f, -0.16f, 0.06f, 0.36f)
                    .style(s -> {
                        s.twist = 0.0f;
                        s.hipSway = 0.0f;
                        s.lean = 0.0f;
                        s.runLean = 0.06f;
                        s.breath = 0.004f;
                        s.headToChest = 0.3f;
                        s.headStabilize = 0.0f;
                        s.lookYawToBody = 0.45f;
                        s.headUp = 0.0f;
                        s.headDown = 0.0f;
                        s.stride = 0.38f;
                        s.runStride = 0.55f;
                        s.runArm = 0.6f;
                        s.runElbow = 0.9f;
                        s.armSwing = 0.45f;
                        s.armHang = 0.2f;
                        s.bounce = 0.006f;
                    }).build(),
            ModelProfile.builder("Sergeant Burly", "sergeant_burly", BodyType.BULKY)
                    .neck(0.68f).shoulder(0.17f, 0.66f).elbow(0.32f, 0.50f).hand(0.45f, 0.38f)
                    .hip(0.10f, 0.30f).knee(0.15f).waist(0.45f).armRadius(0.09f).legRadius(0.07f).skirt(0.35f)
                    .headPivot(0.06f)
                    .style(s -> {
                        s.armHang = 0.25f;
                        s.headUp = 0.4f;
                        s.headDown = 0.35f;
                        s.headToChest = 0.35f;
                    }).build()
    );

    private ModelCatalog() {
    }

    public static String[] labels() {
        String[] labels = new String[MODELS.size()];
        for (int i = 0; i < labels.length; i++)
            labels[i] = MODELS.get(i).label;
        return labels;
    }
}
