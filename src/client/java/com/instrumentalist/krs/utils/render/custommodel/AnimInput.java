package com.instrumentalist.krs.utils.render.custommodel;

public final class AnimInput {
    public enum ArmPose { EMPTY, ITEM, BLOCK, AIM, CROSSBOW_HOLD, CROSSBOW_CHARGE, SPYGLASS, THROW, BRUSH }

    public float ageInTicks;
    public float walkPos;
    public float walkSpeed;
    public float headYaw;
    public float headPitch;
    public float bodyYaw;
    public boolean crouching;
    public boolean riding;
    public float seatHipY;
    public boolean swimming;
    public boolean gliding;
    public boolean onGround = true;
    public boolean sprinting;
    public float verticalSpeed;
    public float swing;
    public boolean swingArmLeft;
    public ArmPose leftPose = ArmPose.EMPTY;
    public ArmPose rightPose = ArmPose.EMPTY;
    public boolean eatingLeft;
    public boolean eatingRight;
    public float useTicks;
    public float chargeDuration;
    public long nanos = System.nanoTime();
}
