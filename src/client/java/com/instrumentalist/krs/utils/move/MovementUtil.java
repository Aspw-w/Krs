package com.instrumentalist.krs.utils.move;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.hacks.features.combat.KillAura;
import com.instrumentalist.krs.hacks.features.combat.TargetStrafe;
import com.instrumentalist.krs.utils.entity.EntityExtension;
import com.instrumentalist.krs.utils.math.RotationScraper;
import net.minecraft.client.player.ClientInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MovementUtil implements IMinecraft {

    public static int fallTicks = 0;

    public static boolean hasMotion() {
        var player = mc.player;
        if (player == null) return false;

        var motion = player.getDeltaMovement();
        return motion.x != 0.0 || motion.y != 0.0 || motion.z != 0.0;
    }

    public static boolean hasMotion(double threshold) {
        var player = mc.player;
        if (player == null) return false;

        var motion = player.getDeltaMovement();
        return motion.length() > Math.max(0.0, threshold);
    }

    public static boolean hasXZMotion() {
        var player = mc.player;
        if (player == null) return false;

        var motion = player.getDeltaMovement();
        return motion.x != 0.0 || motion.z != 0.0;
    }

    public static boolean hasXZMotion(double threshold) {
        return getSpeed() > Math.max(0.0, threshold);
    }

    public static boolean isMoving() {
        var player = mc.player;
        return player != null && (InputUtil.forwardImpulse(player.input) != 0.0 || InputUtil.leftImpulse(player.input) != 0.0);
    }

    public static float getForwardImpulse() {
        var player = mc.player;
        return player == null ? 0.0f : InputUtil.forwardImpulse(player.input);
    }

    public static float getLeftImpulse() {
        var player = mc.player;
        return player == null ? 0.0f : InputUtil.leftImpulse(player.input);
    }

    public static boolean isMovingForward() {
        return getForwardImpulse() > 0.0f;
    }

    public static boolean isMovingBackward() {
        return getForwardImpulse() < 0.0f;
    }

    public static boolean isMovingLeft() {
        return getLeftImpulse() > 0.0f;
    }

    public static boolean isMovingRight() {
        return getLeftImpulse() < 0.0f;
    }

    public static boolean isMovingOnlyForward() {
        return isMovingForward() && getLeftImpulse() == 0.0f;
    }

    public static double getBaseMoveSpeed(double customSpeed) {
        double baseSpeed = customSpeed;

        var player = mc.player;
        if (player != null && player.hasEffect(MobEffects.SPEED)) {
            MobEffectInstance effect = player.getEffect(MobEffects.SPEED);

            if (effect != null) {
                int amplifier = effect.getAmplifier();
                baseSpeed *= 1.0 + 0.2 * (amplifier + 1);
            }
        }

        return baseSpeed;
    }

    public static int getSpeedEffect() {
        var player = mc.player;
        if (player != null && player.hasEffect(MobEffects.SPEED)) {
            MobEffectInstance effect = player.getEffect(MobEffects.SPEED);

            if (effect != null)
                return effect.getAmplifier() + 1;
        }

        return 0;
    }

    public static void stopMoving() {
        var player = mc.player;
        if (player == null) return;

        player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
    }

    public static void stopXZ() {
        setVelocityXZ(0.0, 0.0);
    }

    public static boolean isDiagonal(float threshold) {
        float yaw = getPlayerDirection();
        yaw = Math.abs(((yaw + 360) % 360));
        boolean isNorth = Math.abs(yaw) < threshold || Math.abs(yaw - 360) < threshold;
        boolean isSouth = Math.abs(yaw - 180) < threshold;
        boolean isEast = Math.abs(yaw - 90) < threshold;
        boolean isWest = Math.abs(yaw - 270) < threshold;
        return (!isNorth && !isSouth && !isEast && !isWest);
    }

    public static float getPlayerDirection() {
        var player = mc.player;
        if (player == null) return 0f;

        float yaw = player.getYRot();

        if (TargetStrafe.targetStrafeHook()) {
            float targetYaw = RotationScraper.INSTANCE.getRotationsEntity((LivingEntity) KillAura.closestEntity).getFirst();
            double targetDistance = EntityExtension.distanceToWithoutY(player, KillAura.closestEntity);
            float desiredYaw = TargetStrafe.getDesiredMovementYaw(targetYaw, targetDistance);
            yaw = TargetStrafe.getCurrentMovementYaw(desiredYaw);
        }

        ClientInput input = player.input;
        float forward = 1F;

        float forwardImpulse = InputUtil.forwardImpulse(input);
        float leftImpulse = InputUtil.leftImpulse(input);

        if (forwardImpulse < 0f) yaw += 180f;

        if (forwardImpulse < 0f) forward = -0.5f;
        else if (forwardImpulse > 0f) forward = 0.5f;

        if (leftImpulse > 0f) yaw -= 90f * forward;
        if (leftImpulse < 0f) yaw += 90f * forward;

        yaw = (yaw % 360 + 360) % 360;

        return yaw;
    }

    public static void setVelocityY(Double y) {
        if (y == null) return;

        setVelocityY(y.doubleValue());
    }

    public static void setVelocityY(double y) {
        var player = mc.player;
        if (player == null) return;

        var motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, y, motion.z);
    }

    public static void setVelocity(double x, double y, double z) {
        var player = mc.player;
        if (player == null) return;

        player.setDeltaMovement(x, y, z);
    }

    public static Vec3 getVelocity() {
        var player = mc.player;
        if (player == null) return Vec3.ZERO;

        return player.getDeltaMovement();
    }

    public static Vec3 getVelocityXZ() {
        var motion = getVelocity();
        return new Vec3(motion.x, 0.0, motion.z);
    }

    public static void setVelocityXZ(double x, double z) {
        var player = mc.player;
        if (player == null) return;

        var motion = player.getDeltaMovement();
        player.setDeltaMovement(x, motion.y, z);
    }

    public static void setVelocityXZ(Vec3 motion) {
        if (motion == null) return;

        setVelocityXZ(motion.x, motion.z);
    }

    public static void addVelocity(double x, double y, double z) {
        var player = mc.player;
        if (player == null) return;

        var motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x + x, motion.y + y, motion.z + z);
    }

    public static void addVelocityXZ(double x, double z) {
        addVelocity(x, 0.0, z);
    }

    public static void addVelocityXZ(Vec3 motion) {
        if (motion == null) return;

        addVelocityXZ(motion.x, motion.z);
    }

    public static void scaleVelocityXZ(double multiplier) {
        var player = mc.player;
        if (player == null) return;

        var motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x * multiplier, motion.y, motion.z * multiplier);
    }

    public static void applyFriction(double friction) {
        scaleVelocityXZ(1.0 - clamp(friction, 0.0, 1.0));
    }

    public static void decelerate(double amount) {
        double speed = getSpeed();
        if (speed == 0.0) return;

        double nextSpeed = Math.max(0.0, speed - Math.max(0.0, amount));
        setSpeed(nextSpeed, getMotionDirection());
    }

    public static void limitSpeed(double maxSpeed) {
        var player = mc.player;
        if (player == null) return;

        double limit = Math.max(0.0, maxSpeed);
        double speed = getSpeed();
        if (speed <= limit || speed == 0.0) return;

        var motion = player.getDeltaMovement();
        double multiplier = limit / speed;
        player.setDeltaMovement(motion.x * multiplier, motion.y, motion.z * multiplier);
    }

    public static void accelerate(double acceleration, double maxSpeed) {
        Vec3 movement = getMovementVector(acceleration);
        if (getSpeed(movement.x, movement.z) == 0.0) return;

        addVelocityXZ(movement.x, movement.z);
        limitSpeed(maxSpeed);
    }

    public static void boost(double amount) {
        addVelocityXZ(getMovementVector(amount));
    }

    public static void boost(double amount, double maxSpeed) {
        boost(amount);
        limitSpeed(maxSpeed);
    }

    public static void approachSpeed(double targetSpeed, double step) {
        targetSpeed = Math.max(0.0, targetSpeed);

        double currentSpeed = getSpeed();
        double absoluteStep = Math.abs(step);
        if (absoluteStep == 0.0) return;

        double nextSpeed;
        if (currentSpeed < targetSpeed)
            nextSpeed = Math.min(targetSpeed, currentSpeed + absoluteStep);
        else nextSpeed = Math.max(targetSpeed, currentSpeed - absoluteStep);

        if (currentSpeed > 0.0)
            setSpeed(nextSpeed, getMotionDirection());
        else setSpeed(nextSpeed);
    }

    public static void redirectSpeed(float yaw) {
        setSpeed(getSpeed(), yaw);
    }

    public static void smoothStrafe(Float speed) {
        var player = mc.player;
        if (player == null) return;

        if (isMoving()) {
            Vec3 movement = getMovementVector(speed / 4.0);
            addVelocityXZ(movement.x, movement.z);
        }
    }

    public static float getSpeed() {
        var player = mc.player;
        if (player == null) return 0f;

        var motion = player.getDeltaMovement();
        return (float) getSpeed(motion.x, motion.z);
    }

    public static double getSpeed(double velocityX, double velocityZ) {
        return Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
    }

    public static double getSpeedSquared() {
        var motion = getVelocity();
        return getSpeedSquared(motion.x, motion.z);
    }

    public static double getSpeedSquared(double velocityX, double velocityZ) {
        return velocityX * velocityX + velocityZ * velocityZ;
    }

    public static double getMotionY() {
        var player = mc.player;
        if (player == null) return 0.0;

        return player.getDeltaMovement().y;
    }

    public static Vec3 getHorizontalDirectionVector(float yaw) {
        double yawRad = Math.toRadians(yaw);
        return normalizeHorizontal(-Math.sin(yawRad), Math.cos(yawRad));
    }

    public static float getMotionDirection() {
        var motion = getVelocity();
        if (getSpeed(motion.x, motion.z) == 0.0) return getPlayerDirection();

        return getDirectionFromVector(motion.x, motion.z);
    }

    public static float getDirectionFromVector(double x, double z) {
        if (getSpeed(x, z) == 0.0) return 0.0f;

        return (float) ((Math.toDegrees(Math.atan2(-x, z)) + 360.0) % 360.0);
    }

    public static Vec3 normalizeHorizontal(double x, double z) {
        double length = getSpeed(x, z);
        if (length == 0.0) return Vec3.ZERO;

        return new Vec3(x / length, 0.0, z / length);
    }

    public static Vec3 getMovementVector() {
        if (!isMoving()) return Vec3.ZERO;

        return getHorizontalDirectionVector(getPlayerDirection());
    }

    public static Vec3 getMovementVector(double speed) {
        return getMovementVector().scale(speed);
    }

    public static void setSpeed(double speed) {
        Vec3 movement = getMovementVector(speed);
        setVelocityXZ(movement.x, movement.z);
    }

    public static void setSpeed(double speed, float yaw) {
        Vec3 movement = getHorizontalDirectionVector(yaw).scale(speed);
        setVelocityXZ(movement.x, movement.z);
    }

    public static double getVerticalInputMotion(double speed) {
        return getVerticalInputMotion(speed, speed);
    }

    public static double getVerticalInputMotion(double upSpeed, double downSpeed) {
        double motionY = 0.0;

        if (mc.options.keyJump.isDown()) motionY += upSpeed;
        if (mc.options.keyShift.isDown()) motionY -= downSpeed;

        return motionY;
    }

    public static void jump(double motionY) {
        setVelocityY(motionY);
    }

    public static boolean tryJump(double motionY) {
        var player = mc.player;
        if (player == null || !player.onGround()) return false;

        setVelocityY(motionY);
        return true;
    }

    public static Vec3 getPredictedPosition(int ticks) {
        var player = mc.player;
        if (player == null) return Vec3.ZERO;

        return player.position().add(player.getDeltaMovement().scale(Math.max(0, ticks)));
    }

    public static MovementState snapshot() {
        var player = mc.player;
        if (player == null) return new MovementState(false, false, false, 0.0f, 0.0f, 0.0, 0.0, 0, false, false, 0.0f, 0.0f);

        var motion = player.getDeltaMovement();
        return new MovementState(
                isMoving(),
                hasMotion(),
                hasXZMotion(),
                getPlayerDirection(),
                getMotionDirection(),
                getSpeed(motion.x, motion.z),
                motion.y,
                fallTicks,
                player.onGround(),
                isBlockBelow(),
                getForwardImpulse(),
                getLeftImpulse()
        );
    }

    public static boolean isBlockBelow() {
        return isBlockBelow(2.0);
    }

    public static boolean isBlockBelow(double maxDistance) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) return false;

        double startY = player.getY() - 0.01;
        double x = player.getX();
        double z = player.getZ();
        int steps = (int) Math.ceil(Math.max(0.0, maxDistance));

        for (int i = 0; i <= steps; i++) {
            double checkY = startY - i;
            BlockPos pos = BlockPos.containing(x, checkY, z);
            BlockState state = level.getBlockState(pos);

            if (state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.LAVA)) continue;

            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty())
                return true;
        }

        return false;
    }

    public static void strafe(Float speed) {
        var player = mc.player;
        if (player == null) return;

        if (isMoving()) {
            if (TargetStrafe.targetStrafeHook()) {
                float targetYaw = RotationScraper.INSTANCE.getRotationsEntity((LivingEntity) KillAura.closestEntity).getFirst();
                double targetDistance = EntityExtension.distanceToWithoutY(player, KillAura.closestEntity);
                float desiredYaw = TargetStrafe.getDesiredMovementYaw(targetYaw, targetDistance);
                TargetStrafe.SteeringResult steering = TargetStrafe.steer(desiredYaw, speed);

                if (steering.safe())
                    setSpeed(speed, steering.yaw());
                else stopMoving();
            } else {
                Vec3 movement = getMovementVector(speed);
                setVelocityXZ(movement.x, movement.z);
            }
        } else stopMoving();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record MovementState(
            boolean moving,
            boolean hasMotion,
            boolean hasXZMotion,
            float direction,
            float motionDirection,
            double speed,
            double motionY,
            int fallTicks,
            boolean onGround,
            boolean blockBelow,
            float forwardImpulse,
            float leftImpulse
    ) {
        public String format() {
            return "moving=" + moving
                    + ", xz=" + formatDouble(speed)
                    + ", y=" + formatDouble(motionY)
                    + ", yaw=" + formatDouble(direction)
                    + ", motionYaw=" + formatDouble(motionDirection)
                    + ", input=" + formatDouble(forwardImpulse) + "/" + formatDouble(leftImpulse)
                    + ", fallTicks=" + fallTicks
                    + ", ground=" + onGround
                    + ", below=" + blockBelow;
        }

        private static String formatDouble(double value) {
            return String.format(java.util.Locale.ROOT, "%.3f", value);
        }
    }
}
