package com.instrumentalist.krs.utils.math;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class Ballistics {
    public static final double ARROW_GRAVITY = 0.05D;
    public static final double THROWABLE_GRAVITY = 0.03D;
    public static final double AIR_DRAG = 0.99D;
    private static final int MAX_FLIGHT_TICKS = 120;
    private static final int SEARCH_STEPS = 24;
    private static final int PREDICTION_PASSES = 4;

    private Ballistics() {
    }

    public record Solution(float yaw, float pitch, int flightTicks, Vec3 aimPoint) {
    }

    public static Solution aim(Vec3 eye, Entity target, double speed, double gravity, boolean gravityFirst, boolean predict) {
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 velocity = new Vec3(target.getX() - target.xo, target.onGround() ? 0.0D : target.getY() - target.yo, target.getZ() - target.zo);
        Vec3 aim = center;
        float pitch = 0f;
        int flightTicks = 0;
        for (int pass = 0; pass < PREDICTION_PASSES; pass++) {
            aim = predict ? center.add(velocity.scale(flightTicks)) : center;
            float[] solution = solvePitch(speed, gravity, gravityFirst, Math.hypot(aim.x - eye.x, aim.z - eye.z), aim.y - eye.y);
            pitch = solution[0];
            flightTicks = (int) solution[1];
            if (!predict)
                break;
        }

        float yaw = (float) (Mth.atan2(aim.z - eye.z, aim.x - eye.x) * Mth.RAD_TO_DEG) - 90f;
        return new Solution(yaw, pitch, flightTicks, aim);
    }

    public static float[] solvePitch(double speed, double gravity, boolean gravityFirst, double horizontal, double height) {
        double low = -89.0D;
        double high = 45.0D;
        float[] best = {-45f, 0f};
        double bestError = Double.MAX_VALUE;
        for (int step = 0; step < SEARCH_STEPS; step++) {
            double elevation = (low + high) / 2.0D;
            double[] result = heightAt(speed, gravity, gravityFirst, elevation, horizontal);
            double error = Math.abs(result[0] - height);
            if (error < bestError) {
                bestError = error;
                best = new float[]{(float) -elevation, (float) result[1]};
            }
            if (result[0] < height)
                low = elevation;
            else
                high = elevation;
        }
        return best;
    }

    private static double[] heightAt(double speed, double gravity, boolean gravityFirst, double elevationDegrees, double horizontal) {
        double radians = Math.toRadians(elevationDegrees);
        double vx = Math.cos(radians) * speed;
        double vy = Math.sin(radians) * speed;
        double x = 0.0D;
        double y = 0.0D;
        for (int tick = 1; tick <= MAX_FLIGHT_TICKS; tick++) {
            if (gravityFirst) {
                vy = (vy - gravity) * AIR_DRAG;
                vx *= AIR_DRAG;
            }

            double nextX = x + vx;
            double nextY = y + vy;
            if (nextX >= horizontal) {
                double fraction = vx <= 1.0E-6D ? 0.0D : (horizontal - x) / vx;
                return new double[]{y + vy * fraction, tick};
            }
            x = nextX;
            y = nextY;

            if (!gravityFirst) {
                vx *= AIR_DRAG;
                vy = vy * AIR_DRAG - gravity;
            }
            if (vx < 1.0E-3D)
                break;
        }
        return new double[]{-1.0E9D, MAX_FLIGHT_TICKS};
    }
}
