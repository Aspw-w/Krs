package com.instrumentalist.krs.utils.render.custommodel;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.instrumentalist.krs.utils.render.custommodel.AutoRig.*;

public final class ModelAnimator {
    private static final float PI = (float) Math.PI;
    private static final Quaternionf FALL = new Quaternionf().rotationTo(0.0f, -0.25f, -1.0f, 0.0f, -1.0f, -0.4f);

    public static final class Smoothing {
        float crouch, sit, air, sprint, swim, glide, walk;
        float holdL, holdR, blockL, blockR, eatL, eatR;
        float aim, xhold, xcharge;
        int aimArm = 1;
        float verticalSpeed;
        float lastBodyYaw, yawVelocity;
        float servoYaw, servoPitch;
        long lastNanos;
        boolean initialized;
    }

    private final Quaternionf[] rotations = new Quaternionf[BONE_COUNT];
    private final Vector3f rootOffset = new Vector3f();

    public final float[] armYawOut = new float[2];
    public final float[] armForwardOut = new float[2];

    private float ikWeight;
    private float ikFollowYaw;
    private final float[] ikYaw = new float[2];
    private final float[] ikForward = new float[2];
    private final float[] ikReach = new float[2];
    private final float[] ikPoleUp = new float[2];

    public ModelAnimator() {
        for (int i = 0; i < BONE_COUNT; i++)
            rotations[i] = new Quaternionf();
    }

    public void animate(AutoRig rig, ModelProfile.Style st, AnimInput in, Smoothing sm,
                        float swingScale, boolean idleMotion, Matrix4f[] out) {
        for (Quaternionf q : rotations)
            q.identity();
        rootOffset.zero();

        float h = rig.height;
        float age = in.ageInTicks;
        float dt = ease(in, st, sm);

        float walk = Math.min(sm.walk, 1.0f) * swingScale;
        float run = sm.sprint;
        float phase = in.walkPos * 0.6662f * st.cadence;
        float c = (float) Math.cos(phase);
        float s = (float) Math.sin(phase);
        float smoothS = s;
        if (st.stiff) {
            c = clamp(c * 1.8f, -1.0f, 1.0f);
            s = clamp(s * 1.8f, -1.0f, 1.0f);
        }
        float grounded = 1.0f - sm.air;
        float upright = 1.0f - Math.max(sm.swim, sm.glide);

        float stride = lerp(st.stride, st.runStride, run) * walk * grounded;
        float kneeAmp = st.knee * lerp(1.0f, 1.5f, run) * walk * grounded;
        float armAmp = lerp(st.armSwing, st.runArm, run) * walk;

        float thighR = c * stride;
        float thighL = -c * stride;
        float kneeR = kneeAmp * smoothPositive(-s) + 0.12f * walk * st.knee;
        float kneeL = kneeAmp * smoothPositive(s) + 0.12f * walk * st.knee;
        float runKnee = run * walk * grounded * st.knee;
        kneeR += runKnee * (0.3f + 0.7f * smoothPositive(-c));
        kneeL += runKnee * (0.3f + 0.7f * smoothPositive(c));

        float pelvisYaw = c * st.twist * 0.5f * walk;
        float chestYaw = -c * st.twist * walk;
        float hipRoll = s * st.hipSway * walk;
        float waddleRoll = s * st.waddle * walk * grounded;
        pelvisYaw += c * st.waddle * 0.6f * walk;
        float lean = lerp(st.lean, st.runLean, run) * walk;

        float armR = -c * armAmp;
        float armL = c * armAmp;
        float elbowBase = lerp(st.elbow, st.runElbow, run * Math.min(1.0f, walk * 1.5f));
        float elbowSwing = st.elbow > 0.05f ? 0.35f : 0.05f;
        float elbowR = elbowBase + elbowSwing * smoothPositive(armR) * (1.0f - run);
        float elbowL = elbowBase + elbowSwing * smoothPositive(armL) * (1.0f - run);
        float spread = 0.04f * walk + st.runSpread * run * Math.min(1.0f, walk);

        float headBob = (float) Math.cos(phase * 2.0f) * st.headBob * walk;
        rootOffset.y += st.bounce * h * walk * grounded * smoothS * smoothS;

        float t = age * st.idleSpeed;
        float breath = (float) Math.sin(t * 0.09f);
        float idle = idleMotion ? (1.0f - Math.min(1.0f, walk)) * st.idle : 0.0f;
        float chestPitch = lean + breath * st.breath;
        float headYawIdle = 0.0f, headPitchIdle = 0.0f, headRollIdle = 0.0f;
        if (idleMotion && !st.stiff) {
            float sway = (float) Math.sin(t * 0.031f) * 0.6f + (float) Math.sin(t * 0.073f + 1.3f) * 0.4f;
            hipRoll += sway * 0.035f * idle;
            pelvisYaw += (float) Math.sin(t * 0.023f + 0.7f) * 0.03f * idle;
            thighL += -sway * 0.02f * idle;
            thighR += sway * 0.02f * idle;
            kneeL += Math.max(0.0f, sway) * 0.08f * idle * Math.min(1.0f, st.knee);
            kneeR += Math.max(0.0f, -sway) * 0.08f * idle * Math.min(1.0f, st.knee);
            headYawIdle = ((float) Math.sin(t * 0.019f) * 0.6f + (float) Math.sin(t * 0.047f + 2.1f) * 0.4f) * 0.08f * idle;
            headPitchIdle = (float) Math.sin(t * 0.037f + 0.4f) * 0.035f * idle;
            headRollIdle = (float) Math.sin(t * 0.029f + 1.9f) * 0.04f * idle;
            armL += (float) Math.sin(t * 0.067f) * 0.03f * idle;
            armR += (float) Math.sin(t * 0.067f + 0.8f) * 0.03f * idle;
            spread += breath * 0.012f * idle;
        } else if (idleMotion) {
            chestPitch += (float) Math.sin(age * 2.3f) * 0.004f * st.idle;
        }

        float cr = sm.crouch;
        boolean bendsKnees = st.knee >= 0.2f;
        chestPitch += (bendsKnees ? 0.32f : 0.2f) * cr;
        float pelvisPitch = (bendsKnees ? 0.12f : 0.05f) * cr;
        if (bendsKnees) {
            thighL += 0.55f * cr;
            thighR += 0.55f * cr;
            kneeL += 0.85f * cr;
            kneeR += 0.85f * cr;
        } else {
            rootOffset.y -= 0.035f * h * cr;
            spread += 0.15f * cr;
        }
        armL += 0.25f * cr;
        armR += 0.25f * cr;
        elbowL += 0.25f * cr * Math.min(1.0f, st.elbow * 4.0f);
        elbowR += 0.25f * cr * Math.min(1.0f, st.elbow * 4.0f);

        float air = st.floating ? 0.0f : sm.air * upright * (1.0f - sm.sit);
        float falling = clamp(-sm.verticalSpeed * 2.5f, 0.0f, 1.0f);
        if (bendsKnees) {
            thighR += air * 0.55f;
            kneeR += air * 1.0f;
            thighL += air * 0.12f;
            kneeL += air * 0.45f;
        } else {
            thighR += air * 0.35f;
            thighL -= air * 0.15f;
        }
        spread += air * (0.25f + 0.35f * falling);
        armL += air * 0.2f;
        armR += air * 0.2f;
        elbowL += air * 0.3f * Math.min(1.0f, st.elbow * 4.0f);
        elbowR += air * 0.3f * Math.min(1.0f, st.elbow * 4.0f);
        chestPitch -= air * 0.05f;

        float sit = sm.sit;
        thighL = lerp(thighL, 1.6f, sit);
        thighR = lerp(thighR, 1.6f, sit);
        kneeL = lerp(kneeL, bendsKnees ? 0.35f : 0.0f, sit);
        kneeR = lerp(kneeR, bendsKnees ? 0.35f : 0.0f, sit);
        armL = lerp(armL, 0.35f, sit);
        armR = lerp(armR, 0.35f, sit);
        elbowL = lerp(elbowL, Math.max(0.55f * Math.min(1.0f, st.elbow * 4.0f), elbowBase), sit);
        elbowR = lerp(elbowR, Math.max(0.55f * Math.min(1.0f, st.elbow * 4.0f), elbowBase), sit);
        float legSpread = st.legSpread + 0.08f * sit;
        float sitLegYaw = 0.3f * sit;

        float swim = sm.swim;
        if (swim > 0.0f) {
            float kick = (float) Math.sin(age * 0.55f) * 0.35f;
            thighL = lerp(thighL, kick, swim);
            thighR = lerp(thighR, -kick, swim);
            kneeL = lerp(kneeL, (0.25f + Math.max(0.0f, kick)) * Math.min(1.0f, st.knee), swim);
            kneeR = lerp(kneeR, (0.25f + Math.max(0.0f, -kick)) * Math.min(1.0f, st.knee), swim);
            float stroke = age * 0.22f;
            armL = lerp(armL, PI + (float) Math.sin(stroke) * 1.4f, swim);
            armR = lerp(armR, PI + (float) Math.sin(stroke + PI) * 1.4f, swim);
            elbowL = lerp(elbowL, 0.3f + 0.4f * Math.max(0.0f, (float) Math.cos(stroke)), swim);
            elbowR = lerp(elbowR, 0.3f + 0.4f * Math.max(0.0f, (float) Math.cos(stroke + PI)), swim);
            chestPitch = lerp(chestPitch, 0.0f, swim);
        }
        float glide = sm.glide;
        if (glide > 0.0f) {
            thighL = lerp(thighL, 0.0f, glide);
            thighR = lerp(thighR, 0.0f, glide);
            kneeL = lerp(kneeL, 0.1f * st.knee, glide);
            kneeR = lerp(kneeR, 0.1f * st.knee, glide);
            armL = lerp(armL, -0.15f, glide);
            armR = lerp(armR, -0.15f, glide);
            spread = lerp(spread, 0.55f, glide);
            elbowL = lerp(elbowL, 0.1f, glide);
            elbowR = lerp(elbowR, 0.1f, glide);
            chestPitch = lerp(chestPitch, 0.0f, glide);
        }

        float floatLean = 0.0f;
        if (st.floating) {
            rootOffset.y += st.hover * h + st.hoverBob * h * (float) Math.sin(age * 0.1f);
            floatLean = lean;
            chestPitch -= lean;
            pelvisPitch += lean;
        }

        float headYaw = st.stiff ? sm.servoYaw : -clamp(in.headYaw, -80.0f, 80.0f) * PI / 180.0f;
        float headPitch = clamp(st.stiff ? sm.servoPitch : in.headPitch * PI / 180.0f, -st.headUp - st.headToChest * st.headUp, st.headDown + st.headToChest * st.headDown);
        if (swim > 0.0f || glide > 0.0f)
            headPitch = lerp(headPitch, -0.9f, Math.max(swim, glide));
        float chestLookYaw = headYaw * st.lookYawToBody * upright;
        float chestLookPitch = (st.headToChest > 0.15f ? headPitch : Math.max(0.0f, headPitch)) * st.headToChest * upright;
        float stab = st.headStabilize;
        float headLocalYaw = headYaw - chestLookYaw - (pelvisYaw + chestYaw) * 0.9f * stab + headYawIdle;
        float headLocalPitch = headPitch - chestLookPitch - (chestPitch * 0.85f + pelvisPitch) * stab + headPitchIdle + headBob;
        float headLocalRoll = (-waddleRoll * 0.5f - hipRoll * 0.2f) * stab + headRollIdle;

        float[] armForward = {armL, armR};
        float[] elbow = {elbowL, elbowR};
        float[] armYaw = {0.0f, 0.0f};
        float[] armInward = {0.0f, 0.0f};
        float[] aim = {0.0f, 0.0f};

        float[] hold = {sm.holdL, sm.holdR};
        float[] block = {sm.blockL, sm.blockR};
        float[] eat = {sm.eatL, sm.eatR};
        float elbowRange = Math.min(1.0f, 0.3f + st.elbow * 3.0f);
        for (int i = 0; i < 2; i++) {
            armForward[i] = lerp(armForward[i], armForward[i] * 0.5f + 0.35f, hold[i]);
            elbow[i] = lerp(elbow[i], Math.max(elbow[i], 0.45f * elbowRange), hold[i]);

            armForward[i] = lerp(armForward[i], 0.95f, block[i]);
            elbow[i] = lerp(elbow[i], 0.7f * elbowRange, block[i]);
            armInward[i] += 0.45f * block[i];

            float chew = (float) Math.sin(age * 1.6f) * 0.06f;
            armForward[i] = lerp(armForward[i], 1.15f + chew, eat[i]);
            elbow[i] = lerp(elbow[i], 1.85f * elbowRange, eat[i]);
            armInward[i] += 0.55f * eat[i];
        }

        ikWeight = Math.max(sm.aim, Math.max(sm.xhold, sm.xcharge));
        if (ikWeight > 0.001f) {
            int main = sm.aimArm;
            int off = 1 - main;
            float sum = sm.aim + sm.xhold + sm.xcharge;
            float wb = sm.aim / sum, wh = sm.xhold / sum, wc = sm.xcharge / sum;
            float draw = clamp(in.useTicks / 20.0f, 0.0f, 1.0f);
            float charge = in.chargeDuration > 0.0f ? clamp(in.useTicks / in.chargeDuration, 0.0f, 1.0f) : 0.0f;
            float look = PI * 0.5f - headPitch;
            float follow = wb + wh;

            float mainIn = wb * 0.1f + wh * 0.3f + wc * 0.8f;
            float offIn = wb * 0.5f + wh * 0.6f + wc * lerp(0.4f, 0.85f, charge);
            float mainFwd = wb * look + wh * (look - 0.1f) + wc * 0.97f;
            float offFwd = wb * look + wh * (1.5f - headPitch) + wc * lerp(0.97f, 1.3f, charge);
            float[] side = {1.0f, -1.0f};
            ikYaw[main] = headYaw * follow - side[main] * mainIn;
            ikYaw[off] = headYaw * follow - side[off] * offIn;
            ikForward[main] = mainFwd;
            ikForward[off] = offFwd;
            ikReach[main] = wb * 0.97f + wh * 0.8f + wc * 0.85f;
            ikReach[off] = wb * lerp(0.95f, 0.45f, draw) + wh * 0.97f + wc * lerp(0.9f, 0.55f, charge);
            ikPoleUp[main] = -1.0f;
            ikPoleUp[off] = lerp(0.3f, -0.4f, wh);
            ikFollowYaw = headYaw * follow;

            for (int i = 0; i < 2; i++) {
                armYaw[i] = lerp(armYaw[i], ikYaw[i], ikWeight);
                armForward[i] = lerp(armForward[i], ikForward[i], ikWeight);
                aim[i] = ikWeight;
            }
        }
        for (int i = 0; i < 2; i++) {
            AnimInput.ArmPose pose = i == 0 ? in.leftPose : in.rightPose;
            if (pose == AnimInput.ArmPose.SPYGLASS) {
                armForward[i] = 1.45f - headPitch * 0.8f;
                elbow[i] = 2.2f * elbowRange;
                armInward[i] = 0.4f;
                armYaw[i] = headYaw;
                aim[i] = 1.0f;
            } else if (pose == AnimInput.ArmPose.THROW) {
                armForward[i] = PI * 0.92f;
                elbow[i] = 0.25f;
                armYaw[i] = headYaw * 0.5f;
                aim[i] = 0.5f;
            } else if (pose == AnimInput.ArmPose.BRUSH) {
                armForward[i] = 0.9f + (float) Math.sin(age * 0.8f) * 0.15f;
                elbow[i] = 0.6f * elbowRange;
            }
        }

        float sw = in.swing;
        if (sw > 0.0f) {
            int arm = in.swingArmLeft ? 0 : 1;
            float side = arm == 1 ? 1.0f : -1.0f;
            float reach = (float) Math.sin(Math.sqrt(sw) * PI);
            float windUp = (float) Math.sin(Math.min(1.0f, sw / 0.35f) * PI);
            chestYaw += reach * 0.32f * side;
            pelvisYaw += reach * 0.08f * side;
            chestPitch += reach * 0.08f;
            armForward[arm] += reach * 1.25f;
            elbow[arm] = lerp(elbow[arm] + windUp * 0.5f * elbowRange, 0.08f, reach);
            armInward[arm] += reach * 0.4f;
            armYaw[arm] += headYaw * 0.4f * reach;
            int other = 1 - arm;
            armForward[other] -= reach * 0.25f;
            elbow[other] += reach * 0.3f * elbowRange;
            headLocalYaw -= reach * 0.25f * side;
        }

        float restFix = st.armHang - rig.restArmSpread;

        setRotation(PELVIS, pelvisYaw, pelvisPitch, hipRoll + waddleRoll);
        setRotation(CHEST, chestYaw + chestLookYaw, chestPitch + chestLookPitch, -hipRoll * 0.8f);
        setRotation(HEAD, headLocalYaw, headLocalPitch, headLocalRoll);

        float torsoYaw = pelvisYaw + chestYaw + chestLookYaw;
        float torsoPitch = pelvisPitch + chestPitch + chestLookPitch;
        for (int i = 0; i < 2; i++) {
            armYaw[i] -= torsoYaw * aim[i];
            armForward[i] -= torsoPitch * aim[i];
        }

        armYawOut[0] = armYaw[0];
        armYawOut[1] = armYaw[1];
        armForwardOut[0] = armForward[0];
        armForwardOut[1] = armForward[1];

        armRotation(UPPER_ARM_L, armYaw[0], armForward[0], restFix + spread - armInward[0], 1.0f);
        armRotation(UPPER_ARM_R, armYaw[1], armForward[1], restFix + spread - armInward[1], -1.0f);
        rotations[FORE_ARM_L].rotationAxis(Math.max(0.0f, elbow[0]), rig.elbowHingeL);
        rotations[FORE_ARM_R].rotationAxis(Math.max(0.0f, elbow[1]), rig.elbowHingeR);

        float pelvisRollTotal = hipRoll + waddleRoll;
        rotations[THIGH_L].rotationZ(legSpread - pelvisRollTotal).rotateY(sitLegYaw).rotateX(-(thighL + pelvisPitch));
        rotations[THIGH_R].rotationZ(-legSpread - pelvisRollTotal).rotateY(-sitLegYaw).rotateX(-(thighR + pelvisPitch));
        rotations[SHIN_L].rotationX(Math.max(0.0f, kneeL));
        rotations[SHIN_R].rotationX(Math.max(0.0f, kneeR));

        if (rig.hasTail) {
            float tailT = age * 0.12f * st.tailSpeed;
            float sway = (float) Math.sin(tailT) * st.tailSway * (0.6f + 0.4f * idle)
                    + s * st.tailSway * 0.7f * walk
                    - clamp(sm.yawVelocity * 0.012f, -0.6f, 0.6f);
            float lift = 0.15f * walk + 0.2f * run - 0.25f * cr + floatLean * 1.2f + air * 0.3f;
            float sway2 = (float) Math.sin(tailT - 0.9f) * st.tailSway * 0.8f + s * st.tailSway * 0.4f * walk;
            if (st.stiff) {
                sway = Math.round(sway * 8.0f) / 8.0f;
                sway2 = 0.0f;
            }
            rotations[TAIL_1].rotationY(sway).rotateX(lift - pelvisPitch);
            rotations[TAIL_2].rotationY(sway2).rotateX(lift * 0.5f);
        }

        float plant = rig.hasLegs && !st.floating && !st.stiff ? grounded * upright * (1.0f - sit) : 0.0f;
        if (plant > 0.0f) {
            float extL = legExtension(rig, THIGH_L, SHIN_L, rig.footL, thighL, kneeL);
            float extR = legExtension(rig, THIGH_R, SHIN_R, rig.footR, thighR, kneeR);
            float rest = rig.pivots[THIGH_L].y - rig.footL.y;
            float drop = rest - softMax(extL, extR, rest * 0.04f);
            rootOffset.y -= Math.max(0.0f, drop) * plant * st.plant;
        }
        if (sit > 0.0f && rig.hasLegs && !st.floating)
            rootOffset.y += (in.seatHipY - rig.pivots[THIGH_L].y) * sit;
        rootOffset.y += breath * h * 0.002f * idle;

        if (ikWeight > 0.001f)
            solveAimArms(rig);

        buildMatrices(rig, out);
    }

    private float ease(AnimInput in, ModelProfile.Style st, Smoothing sm) {
        float dt = sm.initialized ? Math.min(0.25f, Math.max(0.0f, (in.nanos - sm.lastNanos) / 1.0e9f)) : 1.0f;
        sm.lastNanos = in.nanos;
        boolean first = !sm.initialized;
        sm.initialized = true;

        boolean bow = in.leftPose == AnimInput.ArmPose.AIM || in.rightPose == AnimInput.ArmPose.AIM;
        boolean xhold = in.leftPose == AnimInput.ArmPose.CROSSBOW_HOLD || in.rightPose == AnimInput.ArmPose.CROSSBOW_HOLD;
        boolean xcharge = in.leftPose == AnimInput.ArmPose.CROSSBOW_CHARGE || in.rightPose == AnimInput.ArmPose.CROSSBOW_CHARGE;
        if (isAimPose(in.leftPose))
            sm.aimArm = 0;
        else if (isAimPose(in.rightPose))
            sm.aimArm = 1;

        float k = first ? 1.0f : 1.0f - (float) Math.exp(-dt * 12.0f);
        float kSlow = first ? 1.0f : 1.0f - (float) Math.exp(-dt * 7.0f);
        sm.crouch += ((in.crouching ? 1.0f : 0.0f) - sm.crouch) * k;
        sm.sit += ((in.riding ? 1.0f : 0.0f) - sm.sit) * k;
        boolean onGround = in.onGround || in.riding;
        sm.air += ((onGround ? 0.0f : 1.0f) - sm.air) * (onGround ? k : kSlow);
        sm.sprint += ((in.sprinting ? 1.0f : 0.0f) - sm.sprint) * kSlow;
        sm.swim += ((in.swimming ? 1.0f : 0.0f) - sm.swim) * kSlow;
        sm.glide += ((in.gliding ? 1.0f : 0.0f) - sm.glide) * kSlow;
        sm.walk += (Math.min(in.walkSpeed, 1.0f) - sm.walk) * k;
        sm.verticalSpeed += (in.verticalSpeed - sm.verticalSpeed) * k;
        sm.aim += ((bow ? 1.0f : 0.0f) - sm.aim) * k;
        sm.xhold += ((xhold ? 1.0f : 0.0f) - sm.xhold) * k;
        sm.xcharge += ((xcharge ? 1.0f : 0.0f) - sm.xcharge) * k;

        sm.holdL += ((in.leftPose == AnimInput.ArmPose.ITEM ? 1.0f : 0.0f) - sm.holdL) * k;
        sm.holdR += ((in.rightPose == AnimInput.ArmPose.ITEM ? 1.0f : 0.0f) - sm.holdR) * k;
        sm.blockL += ((in.leftPose == AnimInput.ArmPose.BLOCK ? 1.0f : 0.0f) - sm.blockL) * k;
        sm.blockR += ((in.rightPose == AnimInput.ArmPose.BLOCK ? 1.0f : 0.0f) - sm.blockR) * k;
        sm.eatL += ((in.eatingLeft ? 1.0f : 0.0f) - sm.eatL) * k;
        sm.eatR += ((in.eatingRight ? 1.0f : 0.0f) - sm.eatR) * k;

        float bodyYaw = in.bodyYaw;
        if (!first && dt > 0.0f) {
            float delta = wrapDegrees(bodyYaw - sm.lastBodyYaw);
            sm.yawVelocity += (delta / dt - sm.yawVelocity) * kSlow;
        }
        sm.lastBodyYaw = bodyYaw;

        float targetYaw = -clamp(in.headYaw, -80.0f, 80.0f) * PI / 180.0f;
        float targetPitch = clamp(in.headPitch, -80.0f, 80.0f) * PI / 180.0f;
        float maxStep = (first ? 100.0f : 5.0f) * dt;
        sm.servoYaw += clamp(targetYaw - sm.servoYaw, -maxStep, maxStep);
        sm.servoPitch += clamp(targetPitch - sm.servoPitch, -maxStep, maxStep);
        return dt;
    }

    private void setRotation(int bone, float yaw, float pitch, float roll) {
        rotations[bone].rotationY(yaw).rotateX(pitch).rotateZ(roll);
    }

    private void armRotation(int bone, float yaw, float forward, float spread, float side) {
        rotations[bone].rotationY(yaw).rotateX(-forward).rotateZ(spread * side);
    }

    private static float legExtension(AutoRig rig, int thigh, int shin, Vector3f foot, float thighForward, float knee) {
        float upper = rig.pivots[thigh].y - rig.pivots[shin].y;
        float lower = rig.pivots[shin].y - foot.y;
        return upper * (float) Math.cos(thighForward) + lower * (float) Math.cos(thighForward - Math.max(0.0f, knee));
    }

    private static boolean isAimPose(AnimInput.ArmPose pose) {
        return pose == AnimInput.ArmPose.AIM || pose == AnimInput.ArmPose.CROSSBOW_HOLD || pose == AnimInput.ArmPose.CROSSBOW_CHARGE;
    }

    private Matrix4f local(AutoRig rig, int bone) {
        Vector3f p = rig.pivots[bone];
        return new Matrix4f().translation(p).rotate(rotations[bone]).translate(-p.x, -p.y, -p.z);
    }

    private void solveAimArms(AutoRig rig) {
        Matrix4f chest = new Matrix4f().translation(rootOffset).mul(local(rig, PELVIS)).mul(local(rig, CHEST));
        Quaternionf chestRot = new Quaternionf(rotations[PELVIS]).mul(rotations[CHEST]);
        float[] side = {1.0f, -1.0f};
        for (int i = 0; i < 2; i++) {
            Vector3f dir = new Vector3f(0.0f, -1.0f, 0.0f).rotateX(-ikForward[i]).rotateY(ikYaw[i]);
            Vector3f pole = new Vector3f(side[i], ikPoleUp[i], -0.3f).rotateY(ikFollowYaw);
            solveArm(rig, i == 0 ? UPPER_ARM_L : UPPER_ARM_R, i == 0 ? FORE_ARM_L : FORE_ARM_R,
                    i == 0 ? rig.handL : rig.handR, chest, chestRot, dir, ikReach[i], pole);
        }
    }

    private void solveArm(AutoRig rig, int upper, int fore, Vector3f handRest, Matrix4f chest, Quaternionf chestRot,
                          Vector3f dir, float reachFactor, Vector3f pole) {
        Vector3f shoulderRest = rig.pivots[upper];
        Vector3f elbowRest = rig.pivots[fore];
        float l1 = shoulderRest.distance(elbowRest);
        float l2 = elbowRest.distance(handRest);
        if (l1 < 1.0e-5f || l2 < 1.0e-5f)
            return;

        Vector3f shoulder = chest.transformPosition(shoulderRest, new Vector3f());
        float reach = clamp((l1 + l2) * reachFactor, Math.abs(l1 - l2) + 1.0e-4f, l1 + l2 - 1.0e-4f);
        Vector3f u = new Vector3f(dir).normalize();
        Vector3f target = new Vector3f(u).mul(reach).add(shoulder);

        float cosA = clamp((l1 * l1 + reach * reach - l2 * l2) / (2.0f * l1 * reach), -1.0f, 1.0f);
        float sinA = (float) Math.sqrt(1.0f - cosA * cosA);
        Vector3f v = new Vector3f(pole).sub(new Vector3f(u).mul(pole.dot(u)));
        if (v.lengthSquared() < 1.0e-8f)
            v.set(0.0f, -1.0f, 0.0f).sub(new Vector3f(u).mul(-u.y));
        v.normalize();
        Vector3f elbow = new Vector3f(u).mul(cosA * l1).add(new Vector3f(v).mul(sinA * l1)).add(shoulder);

        Quaternionf toChest = new Quaternionf(chestRot).conjugate();
        Vector3f upperDir = toChest.transform(new Vector3f(elbow).sub(shoulder)).normalize();
        Vector3f restUpper = new Vector3f(elbowRest).sub(shoulderRest).normalize();
        Quaternionf upperRot = new Quaternionf().rotationTo(restUpper, upperDir);

        Vector3f foreDir = toChest.transform(new Vector3f(target).sub(elbow));
        new Quaternionf(upperRot).conjugate().transform(foreDir).normalize();
        Vector3f restFore = new Vector3f(handRest).sub(elbowRest).normalize();
        Quaternionf foreRot = new Quaternionf().rotationTo(restFore, foreDir);

        rotations[upper].slerp(upperRot, ikWeight);
        rotations[fore].slerp(foreRot, ikWeight);
    }

    private void buildMatrices(AutoRig rig, Matrix4f[] out) {
        for (int b = 0; b < BONE_COUNT; b++) {
            if (b == SLEEVE_L || b == SLEEVE_R || b == SLEEVE_BACK_L || b == SLEEVE_BACK_R) {
                boolean left = b == SLEEVE_L || b == SLEEVE_BACK_L;
                int fore = left ? FORE_ARM_L : FORE_ARM_R;
                Vector3f attach = b == SLEEVE_L ? rig.sleeveAttachL : b == SLEEVE_R ? rig.sleeveAttachR : rig.pivots[b];
                if (b == SLEEVE_L || b == SLEEVE_R) {
                    Vector3f hand = left ? rig.handL : rig.handR;
                    Vector3f axis = new Vector3f(hand).sub(rig.pivots[fore]).normalize();
                    out[b].set(out[fore]).translate(attach).rotate(hangTwist(out[fore], axis)).translate(-attach.x, -attach.y, -attach.z);
                    continue;
                }
                Vector3f moved = out[fore].transformPosition(attach, new Vector3f());
                out[b].translation(moved);
                if (b == SLEEVE_BACK_L || b == SLEEVE_BACK_R) {
                    if (rig.sleeveFold > 0.0f)
                        out[b].scale(1.0f, 1.0f, rig.sleeveFold);
                    else
                        out[b].rotate(FALL);
                }
                out[b].translate(-attach.x, -attach.y, -attach.z);
                continue;
            }
            Vector3f p = rig.pivots[b];
            Matrix4f local = new Matrix4f().translation(p).rotate(rotations[b]).translate(-p.x, -p.y, -p.z);
            int parent = PARENT[b];
            if (parent < 0)
                out[b].translation(rootOffset).mul(local);
            else
                out[b].set(out[parent]).mul(local);
        }
    }

    private static float smoothPositive(float x) {
        final float e = 0.3f;
        return 0.5f * (x + (float) Math.sqrt(x * x + e * e)) - 0.5f * e;
    }

    private static float softMax(float a, float b, float softness) {
        float half = (a - b) * 0.5f;
        return (a + b) * 0.5f + (float) Math.sqrt(half * half + softness * softness) - softness;
    }

    private static Quaternionf hangTwist(Matrix4f fore, Vector3f restAxis) {
        Quaternionf rot = fore.getUnnormalizedRotation(new Quaternionf()).normalize();
        Vector3f axis = rot.transform(new Vector3f(restAxis));
        Vector3f current = rot.transform(new Vector3f(0.0f, -1.0f, 0.0f));
        Vector3f down = new Vector3f(0.0f, -1.0f, 0.0f);
        current.fma(-current.dot(axis), axis);
        down.fma(-down.dot(axis), axis);
        float strength = down.length();
        if (strength < 1.0e-3f || current.lengthSquared() < 1.0e-6f)
            return new Quaternionf();
        float angle = (float) Math.atan2(axis.dot(new Vector3f(current).cross(down)), current.dot(down));
        return new Quaternionf().rotationAxis(angle * Math.min(1.0f, strength * 1.5f), restAxis);
    }

    private static float wrapDegrees(float degrees) {
        float d = degrees % 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
