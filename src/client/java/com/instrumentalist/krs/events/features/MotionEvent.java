package com.instrumentalist.krs.events.features;

import com.instrumentalist.krs.events.EventArgument;
import com.instrumentalist.krs.events.EventListener;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class MotionEvent extends EventArgument {
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    public boolean onGround;
    public boolean isMoving;
    public boolean callInPost;
    public final boolean post;

    public MotionEvent(double x, double y, double z, float yaw, float pitch, boolean onGround, boolean isMoving, boolean callInPost, final boolean post) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
        this.isMoving = isMoving;
        this.callInPost = callInPost;
        this.post = post;
    }

    @Override
    public void call(@NotNull EventListener listener) {
        Objects.requireNonNull(listener).onMotion(this);
    }
}