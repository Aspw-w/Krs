package com.instrumentalist.krs.hacks.features.combat;



import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class TargetStrafe extends Module {

    private static final double MIN_LOOK_AHEAD = 0.8;
    private static final double MAX_LOOK_AHEAD = 3.0;
    private static final double LOOK_AHEAD_TICKS = 3.0;
    private static final double PATH_STEP = 0.2;
    private static final double SUPPORT_DEPTH = 2.25;
    private static final double COLLISION_EPSILON = 1.0E-4;
    private static final double FINAL_SWEEP_STEP = 0.05;
    private static final double FINAL_COLLISION_MARGIN = 0.002;
    private static final int FINAL_SWEEP_REFINEMENTS = 10;
    private static final float SEARCH_ANGLE_STEP = 15.0f;
    private static final float MAX_TURN_PER_TICK = 22.5f;

    private static float steeringOffset;

    public TargetStrafe() {
        super("Target Strafe", ModuleCategory.Combat, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Setting
    public static FloatValue distance = new FloatValue(
            "Distance",
            1f,
            0f,
            8f
    );

    @Setting
    private static final BooleanValue jumpOnly = new BooleanValue("Jump Only", true);

    public static int direction = 1;

    public static boolean targetStrafeHook() {
        return ModuleManager.getModuleState(TargetStrafe.class) && shouldDoStrafe();
    }

    private static boolean shouldDoStrafe() {
        if (mc.player == null || !ModuleManager.getModuleState(KillAura.class) || KillAura.closestEntity == null) {
            resetSteering();
            return false;
        }

        return (ModuleManager.getModuleState(FlyModule.class) || ModuleManager.getModuleState(SpeedModule.class)) && (!jumpOnly.get() || InputConstants.isKeyDown(mc.getWindow(), InputConstants.getKey(mc.options.keyJump.saveString()).getValue())) && !InputConstants.isKeyDown(mc.getWindow(), InputConstants.getKey(mc.options.keyLeft.saveString()).getValue()) && !InputConstants.isKeyDown(mc.getWindow(), InputConstants.getKey(mc.options.keyRight.saveString()).getValue()) && !InputConstants.isKeyDown(mc.getWindow(), InputConstants.getKey(mc.options.keyDown.saveString()).getValue());
    }

    public static float getDesiredMovementYaw(float targetYaw, double targetDistance) {
        float yawOffset;

        if (targetDistance >= distance.get() + 2.0f)
            yawOffset = 0.0f;
        else if (targetDistance > distance.get())
            yawOffset = 45.0f;
        else yawOffset = 90.0f;

        return wrapDegrees(targetYaw - direction * yawOffset);
    }

    public static float getCurrentMovementYaw(float fallbackYaw) {
        return wrapDegrees(fallbackYaw + steeringOffset);
    }

    public static SteeringResult steer(float desiredYaw, double speed) {
        var player = mc.player;
        var level = mc.level;
        desiredYaw = wrapDegrees(desiredYaw);

        if (player == null || level == null) {
            steeringOffset = 0.0f;
            return new SteeringResult(desiredYaw, false);
        }

        double absoluteSpeed = Math.abs(speed);
        double immediateDistance = Math.max(PATH_STEP, absoluteSpeed * 1.25);
        double lookAhead = Math.max(
                immediateDistance,
                clamp(absoluteSpeed * LOOK_AHEAD_TICKS, MIN_LOOK_AHEAD, MAX_LOOK_AHEAD)
        );
        boolean requireSupport = !ModuleManager.getModuleState(FlyModule.class);
        SteeringCandidate candidate = findSteeringCandidate(
                desiredYaw,
                lookAhead,
                immediateDistance,
                requireSupport
        );

        if (candidate == null) {
            steeringOffset = 0.0f;
            return new SteeringResult(desiredYaw, false);
        }

        float candidateOffset = wrapDegrees(candidate.yaw() - desiredYaw);
        float nextOffset = rotateTowards(steeringOffset, candidateOffset, MAX_TURN_PER_TICK);
        float nextYaw = wrapDegrees(desiredYaw + nextOffset);

        // Turn smoothly while there is room. Close to a wall or an edge, use the
        // selected escape heading immediately rather than carrying momentum into it.
        if (!isPathSafe(nextYaw, immediateDistance, requireSupport)) {
            nextYaw = candidate.yaw();
            nextOffset = candidateOffset;
        }

        steeringOffset = nextOffset;
        return new SteeringResult(nextYaw, true);
    }

    public static Vec3 preventHorizontalCollision(Vec3 movement) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null)
            return movement;

        double horizontalDistance = MovementUtil.getSpeed(movement.x, movement.z);
        if (horizontalDistance <= COLLISION_EPSILON)
            return movement;

        AABB playerBox = player.getBoundingBox();
        boolean requireSupport = !ModuleManager.getModuleState(FlyModule.class);
        if (isFinalPathSafe(playerBox, movement, requireSupport))
            return movement;

        float movementYaw = MovementUtil.getDirectionFromVector(movement.x, movement.z);
        int searchSteps = Math.round(180.0f / SEARCH_ANGLE_STEP);

        // The predicted velocity can still be changed later in the tick. If that
        // final velocity is unsafe, rotate the complete vector instead of scaling
        // both axes to zero at the wall.
        for (int step = 1; step <= searchSteps; step++) {
            float angle = step * SEARCH_ANGLE_STEP;
            int candidatesAtAngle = angle == 180.0f ? 1 : 2;

            for (int side = 0; side < candidatesAtAngle; side++) {
                float sign = side == 0 ? -direction : direction;
                float candidateYaw = wrapDegrees(movementYaw + sign * angle);
                Vec3 directionVector = MovementUtil.getHorizontalDirectionVector(candidateYaw);
                Vec3 candidateMovement = new Vec3(
                        directionVector.x * horizontalDistance,
                        movement.y,
                        directionVector.z * horizontalDistance
                );

                if (isFinalPathSafe(playerBox, candidateMovement, requireSupport)) {
                    steeringOffset = wrapDegrees(steeringOffset + sign * angle);
                    return candidateMovement;
                }
            }
        }

        // In very tight spaces no full-speed heading may fit. Preserve the free
        // axis so the player slides along a wall instead of remaining pinned to it.
        double safeX = clampHorizontalAxis(playerBox, movement.x, true);
        double safeZ = clampHorizontalAxis(playerBox, movement.z, false);
        Vec3 combinedSlide = new Vec3(safeX, movement.y, safeZ);
        if (isFinalPathSafe(playerBox, combinedSlide, requireSupport))
            return combinedSlide;

        Vec3 xSlide = new Vec3(safeX, movement.y, 0.0);
        Vec3 zSlide = new Vec3(0.0, movement.y, safeZ);
        boolean xSafe = Math.abs(safeX) > COLLISION_EPSILON
                && isFinalPathSafe(playerBox, xSlide, requireSupport);
        boolean zSafe = Math.abs(safeZ) > COLLISION_EPSILON
                && isFinalPathSafe(playerBox, zSlide, requireSupport);

        if (xSafe && (!zSafe || Math.abs(safeX) >= Math.abs(safeZ)))
            return xSlide;
        if (zSafe)
            return zSlide;

        return new Vec3(0.0, movement.y, 0.0);
    }

    private static boolean isFinalPathSafe(AABB playerBox, Vec3 movement, boolean requireSupport) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null)
            return false;

        double horizontalDistance = MovementUtil.getSpeed(movement.x, movement.z);
        if (horizontalDistance <= COLLISION_EPSILON)
            return true;

        int samples = Math.max(1, (int) Math.ceil(horizontalDistance / FINAL_SWEEP_STEP));
        for (int sample = 1; sample <= samples; sample++) {
            double progress = (double) sample / samples;
            AABB candidateBox = playerBox.move(
                    movement.x * progress,
                    0.0,
                    movement.z * progress
            );

            if (!level.noCollision(player, candidateBox)
                    || requireSupport && !hasSupportBelow(candidateBox))
                return false;
        }

        return true;
    }

    private static double clampHorizontalAxis(AABB playerBox, double movement, boolean xAxis) {
        var player = mc.player;
        var level = mc.level;
        double distance = Math.abs(movement);
        if (player == null || level == null || distance <= COLLISION_EPSILON)
            return 0.0;

        int samples = Math.max(1, (int) Math.ceil(distance / FINAL_SWEEP_STEP));
        double lastSafeProgress = 0.0;

        for (int sample = 1; sample <= samples; sample++) {
            double progress = (double) sample / samples;
            AABB candidateBox = playerBox.move(
                    xAxis ? movement * progress : 0.0,
                    0.0,
                    xAxis ? 0.0 : movement * progress
            );
            if (level.noCollision(player, candidateBox)) {
                lastSafeProgress = progress;
                continue;
            }

            double collisionProgress = progress;
            for (int refinement = 0; refinement < FINAL_SWEEP_REFINEMENTS; refinement++) {
                double middle = (lastSafeProgress + collisionProgress) * 0.5;
                AABB middleBox = playerBox.move(
                        xAxis ? movement * middle : 0.0,
                        0.0,
                        xAxis ? 0.0 : movement * middle
                );
                if (level.noCollision(player, middleBox))
                    lastSafeProgress = middle;
                else collisionProgress = middle;
            }

            double safeDistance = Math.max(
                    0.0,
                    lastSafeProgress * distance - FINAL_COLLISION_MARGIN
            );
            return Math.copySign(safeDistance, movement);
        }

        return movement;
    }

    private static SteeringCandidate findSteeringCandidate(float desiredYaw, double lookAhead,
                                                            double requiredClearance,
                                                            boolean requireSupport) {
        SteeringCandidate bestFallback = null;
        int searchSteps = Math.round(180.0f / SEARCH_ANGLE_STEP);

        for (int step = 0; step <= searchSteps; step++) {
            float angle = step * SEARCH_ANGLE_STEP;
            SteeringCandidate bestAtAngle = null;
            int candidatesAtAngle = angle == 0.0f || angle == 180.0f ? 1 : 2;

            for (int side = 0; side < candidatesAtAngle; side++) {
                float sign;
                if (angle == 0.0f)
                    sign = 0.0f;
                else if (angle == 180.0f)
                    sign = -direction;
                else sign = side == 0 ? -direction : direction;

                float yaw = wrapDegrees(desiredYaw + sign * angle);
                double clearance = getPathClearance(yaw, lookAhead, requireSupport);
                double continuity = angleDistance(
                        wrapDegrees(desiredYaw + steeringOffset),
                        yaw
                );
                double preferencePenalty = sign != 0.0f && sign != -direction ? SEARCH_ANGLE_STEP * 0.5 : 0.0;
                double score = angle + continuity * 0.35 + preferencePenalty;
                SteeringCandidate current = new SteeringCandidate(yaw, clearance, score);

                if (clearance >= lookAhead - COLLISION_EPSILON
                        && (bestAtAngle == null || current.score() < bestAtAngle.score()))
                    bestAtAngle = current;

                if (bestFallback == null
                        || current.clearance() > bestFallback.clearance() + COLLISION_EPSILON
                        || Math.abs(current.clearance() - bestFallback.clearance()) <= COLLISION_EPSILON
                        && current.score() < bestFallback.score())
                    bestFallback = current;
            }

            if (bestAtAngle != null)
                return bestAtAngle;
        }

        return bestFallback != null && bestFallback.clearance() >= requiredClearance
                ? bestFallback
                : null;
    }

    private static boolean isPathSafe(float yaw, double distance, boolean requireSupport) {
        return getPathClearance(yaw, distance, requireSupport) >= distance - COLLISION_EPSILON;
    }

    private static double getPathClearance(float yaw, double distance, boolean requireSupport) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null || distance <= 0.0)
            return 0.0;

        var directionVector = MovementUtil.getHorizontalDirectionVector(yaw);
        AABB playerBox = player.getBoundingBox();
        int samples = Math.max(1, (int) Math.ceil(distance / PATH_STEP));
        double clearance = 0.0;

        for (int sample = 1; sample <= samples; sample++) {
            double sampleDistance = distance * sample / samples;
            AABB candidateBox = playerBox.move(
                    directionVector.x * sampleDistance,
                    0.0,
                    directionVector.z * sampleDistance
            );

            if (!level.noCollision(player, candidateBox.deflate(COLLISION_EPSILON))
                    || requireSupport && !hasSupportBelow(candidateBox))
                break;

            clearance = sampleDistance;
        }

        return clearance;
    }

    private static boolean hasSupportBelow(AABB playerBox) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null)
            return false;

        AABB supportBox = new AABB(
                playerBox.minX + COLLISION_EPSILON,
                playerBox.minY - SUPPORT_DEPTH,
                playerBox.minZ + COLLISION_EPSILON,
                playerBox.maxX - COLLISION_EPSILON,
                playerBox.minY - COLLISION_EPSILON,
                playerBox.maxZ - COLLISION_EPSILON
        );
        return level.getBlockCollisions(player, supportBox).iterator().hasNext();
    }

    private static float rotateTowards(float current, float target, float maximumChange) {
        float delta = wrapDegrees(target - current);
        delta = Math.max(-maximumChange, Math.min(maximumChange, delta));
        return wrapDegrees(current + delta);
    }

    private static float angleDistance(float first, float second) {
        return Math.abs(wrapDegrees(second - first));
    }

    private static float wrapDegrees(float yaw) {
        yaw %= 360.0f;
        if (yaw >= 180.0f) yaw -= 360.0f;
        if (yaw < -180.0f) yaw += 360.0f;
        return yaw;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void resetSteering() {
        direction = 1;
        steeringOffset = 0.0f;
    }

    private record SteeringCandidate(float yaw, double clearance, double score) {
    }

    public record SteeringResult(float yaw, boolean safe) {
    }

    @Override
    public String description() {
        return "Use by pressing only the forward key";
    }

    @Override
    public void onDisable() {
        resetSteering();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onWorld(WorldEvent event) {
        resetSteering();
    }

}
