package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import java.util.Locale;

public class VulcanOldSpeed implements SpeedEvent {
    private static int jumps;
    private static int groundStage;
    private static double groundMotionY;
    private static String lastMode = "";

    @Override
    public String getName() {
        return "Vulcan Old";
    }

    public static void reset() {
        jumps = 0;
        groundStage = 0;
        groundMotionY = 0.0;
        lastMode = SpeedModule.vulcan2Mode.get();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || player.isInWater() || player.isInLava() || player.onClimbable()) return;

        if (!SpeedModule.vulcan2Mode.get().equalsIgnoreCase(lastMode))
            reset();

        if (!MovementUtil.isMoving()) {
            if (!SpeedModule.vulcan2Mode.get().equalsIgnoreCase("ground"))
                MovementUtil.stopMoving();
            return;
        }

        switch (SpeedModule.vulcan2Mode.get().toLowerCase(Locale.ROOT)) {
            case "yport" -> yport(player);
            case "lowhop" -> lowhop(player);
            case "bhop" -> bhop(player);
            case "ground" -> {
            }
        }
    }

    @Override
    public void onMotion(MotionEvent event) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (player.tickCount < 2) {
            MovementUtil.scaleVelocityXZ(1.4);
            if (!MovementUtil.isMoving())
                MovementUtil.scaleVelocityXZ(-1.0);
        }

        if (SpeedModule.vulcan2Mode.get().equalsIgnoreCase("bhop")) {
            int airTicks = MovementUtil.fallTicks;
            if (airTicks == 0 || airTicks == 1 || airTicks == 2 || airTicks == 4)
                strafe();
            else if (airTicks == 5) {
                player.setOnGround(true);
                event.onGround = true;
                strafe();
            }
            return;
        }

        if (!SpeedModule.vulcan2Mode.get().equalsIgnoreCase("ground"))
            return;

        if (player.horizontalCollision)
            MovementUtil.scaleVelocityXZ(0.75);

        if (player.onGround() && MovementUtil.isMoving()) {
            double speed = MovementUtil.getSpeed();
            boolean speedTwoPlus = MovementUtil.getSpeedEffect() >= 2;

            switch (groundStage) {
                case 1 -> {
                    groundMotionY = 0.5799999833106995;
                    speed = speedTwoPlus ? speed + 0.2 : 0.487;
                    event.onGround = true;
                    player.setOnGround(true);
                }
                case 2 -> {
                    speed = speedTwoPlus ? speed * 0.71 : 0.197;
                    groundMotionY -= 0.07840000092983246;
                    event.onGround = false;
                    player.setOnGround(true);
                }
                default -> {
                    groundStage = 0;
                    speed /= speedTwoPlus ? 0.64 : 0.58;
                    event.onGround = true;
                    player.setOnGround(true);
                }
            }

            strafe(speed);
            groundStage++;
            event.y += groundMotionY;
        } else {
            strafe();
            groundStage = 0;
        }
    }

    @Override
    public void onTick(TickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (SpeedModule.vulcan2Mode.get().equalsIgnoreCase("bhop"))
            mc.options.keyJump.setDown(false);

        if (SpeedModule.vulcan2Mode.get().equalsIgnoreCase("yport")) {
            int airTicks = MovementUtil.fallTicks;
            if (airTicks == 0 || airTicks == 2 || airTicks == 3)
                strafe();
        }
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
    }

    private void yport(LocalPlayer player) {
        strafe();

        int airTicks = MovementUtil.fallTicks;
        if (airTicks == 1)
            MovementUtil.setVelocityY(-0.15);
        else if (airTicks == 2)
            MovementUtil.setVelocityY(-0.3);

        if (player.onGround())
            jump(player);

        if (airTicks == 1) {
            if (MovementUtil.getSpeedEffect() > 0 && player.tickCount > 11)
                strafe(0.308 + 0.05 * MovementUtil.getSpeedEffect());
            else if (player.tickCount > 11)
                strafe(0.3035);
            else
                strafe();
        } else if (airTicks == 2 || !isAirAtMotionY(player)) {
            strafe();
        }
    }

    private void lowhop(LocalPlayer player) {
        if (player.hurtTime > 0 || player.tickCount < 40)
            strafe();

        if (MovementUtil.getSpeed() < 0.22)
            strafe(0.22);

        switch (MovementUtil.fallTicks) {
            case 0 -> {
                jump(player);
                if (MovementUtil.getSpeedEffect() > 0 && player.tickCount > 11)
                    strafe(0.485 + 0.06 * MovementUtil.getSpeedEffect());
                else if (player.tickCount > 11)
                    strafe(0.485);
                else
                    strafe();
            }
            case 1, 8 -> strafe();
            case 2 -> {
                if (jumps % 4 != 1 && !player.horizontalCollision)
                    MovementUtil.setVelocityY(predictedMotion(player.getDeltaMovement().y, 2));
            }
            case 4 -> {
                if (jumps % 4 == 1 || player.horizontalCollision)
                    MovementUtil.setVelocityY(predictedMotion(player.getDeltaMovement().y, 4));
            }
            case 5 -> {
                if (jumps % 4 == 1)
                    strafe();
            }
        }

        if (!isAirAtMotionY(player))
            strafe();
    }

    private void bhop(LocalPlayer player) {
        if (MovementUtil.getSpeed() < 0.22)
            strafe(0.22);

        switch (MovementUtil.fallTicks) {
            case 0 -> {
                if (MovementUtil.getSpeedEffect() == 0 && player.tickCount > 30)
                    MovementUtil.scaleVelocityXZ(0.985);

                if (!isAirAtMotionY(player))
                    jump(player);

                if (player.tickCount > 10)
                    strafe(0.487);
                else
                    strafe();
            }
            case 1 -> {
                if (MovementUtil.getSpeed() < 0.3355 && player.tickCount > 10)
                    strafe(0.3355);
                else
                    strafe();

                int speedEffect = MovementUtil.getSpeedEffect();
                if (speedEffect >= 2 && MovementUtil.getSpeed() < 0.487 + 0.06 * speedEffect)
                    return;
                if (speedEffect == 1 && MovementUtil.getSpeed() < 0.41 + 0.06 * speedEffect)
                    strafe();
            }
            case 5 -> {
                if (player.tickCount > 10)
                    strafe(0.27);
                if (!isAirAtOffset(player, 1.0))
                    MovementUtil.setVelocityY(0.003);
            }
            case 9 -> {
                if (!isAirAtMotionY(player))
                    strafe();

                int speedEffect = MovementUtil.getSpeedEffect();
                if (speedEffect >= 2 && MovementUtil.getSpeed() < 0.46 + 0.06 * speedEffect)
                    return;
                if (speedEffect == 1 && MovementUtil.getSpeed() < 0.385 + 0.06 * speedEffect)
                    return;

                if (MovementUtil.getSpeed() < 0.299)
                    strafe(0.299);
                else
                    strafe();

                MovementUtil.setVelocityY(-0.8);
            }
            case 10 -> MovementUtil.setVelocityY(-10.0);
        }
    }

    private void jump(LocalPlayer player) {
        player.input.makeJump();
        jumps++;
    }

    private void strafe() {
        MovementUtil.strafe(MovementUtil.getSpeed());
    }

    public static void strafe(double speed) {
        MovementUtil.strafe((float) Math.max(0.0, speed));
    }

    private boolean isAirAtMotionY(LocalPlayer player) {
        return isAirAtOffset(player, player.getDeltaMovement().y);
    }

    private boolean isAirAtOffset(LocalPlayer player, double yOffset) {
        if (mc.level == null) return true;

        BlockPos pos = BlockPos.containing(player.getX(), player.getY() + yOffset, player.getZ());
        return mc.level.getBlockState(pos).isAir();
    }

    private double predictedMotion(double motionY, int ticks) {
        double motion = motionY;
        for (int i = 0; i < ticks; i++)
            motion = (motion - 0.08) * 0.98;
        return motion;
    }
}
