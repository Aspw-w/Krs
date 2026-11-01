package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.CombatTargets;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.render.PathLines;
import com.instrumentalist.krs.utils.render.RenderColors;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

public class BowAimbot extends Module {
    private static final double GRAVITY = 0.05D;
    private static final double DRAG = 0.99D;
    private static final int MAX_FLIGHT_TICKS = 120;
    private static final int SEARCH_STEPS = 24;
    private static final Object TRAJECTORY_LINE = new Object();

    @Setting
    private final FloatValue range = new FloatValue("Range", 60f, 10f, 120f, "m");

    @Setting
    private final FloatValue fov = new FloatValue("FOV", 90f, 10f, 360f, "°");

    @Setting
    private final BooleanValue prediction = new BooleanValue("Prediction", true);

    @Setting
    private final BooleanValue onlyPlayers = new BooleanValue("Only Players", true);

    @Setting
    private final BooleanValue throughWalls = new BooleanValue("Through Walls", false);

    @Setting
    private final BooleanValue showTrajectory = new BooleanValue("Show Trajectory", true);

    private final ArrayList<Vec3> trajectory = new ArrayList<>();
    private boolean rotating;

    public BowAimbot() {
        super("Bow Aimbot", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        stop();
    }

    @Override
    public void onWorld(WorldEvent event) {
        stop();
    }

    private void stop() {
        if (rotating) {
            Client.rotationManager.stopRotation();
            rotating = false;
        }
        PathLines.hide(this, TRAJECTORY_LINE);
    }

    @Override
    public void onTick(TickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            stop();
            return;
        }

        double speed = projectileSpeed(player);
        if (speed <= 0.0D) {
            stop();
            return;
        }

        LivingEntity target = findTarget(player);
        if (target == null) {
            stop();
            return;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 velocity = new Vec3(target.getX() - target.xo, target.onGround() ? 0.0D : target.getY() - target.yo, target.getZ() - target.zo);
        Vec3 aim = center;
        float pitch = 0f;
        int flightTicks = 0;
        for (int iteration = 0; iteration < 4; iteration++) {
            aim = prediction.get() ? center.add(velocity.scale(flightTicks)) : center;
            float[] solution = solvePitch(speed, Math.hypot(aim.x - eye.x, aim.z - eye.z), aim.y - eye.y);
            pitch = solution[0];
            flightTicks = (int) solution[1];
            if (!prediction.get())
                break;
        }

        float yaw = (float) (Mth.atan2(aim.z - eye.z, aim.x - eye.x) * Mth.RAD_TO_DEG) - 90f;
        Client.rotationManager.startRotationUnclampedPitch(yaw, pitch, 180f);
        rotating = true;

        if (showTrajectory.get())
            showTrajectory(eye, yaw, pitch, speed, flightTicks);
        else
            PathLines.hide(this, TRAJECTORY_LINE);
    }

    private double projectileSpeed(LocalPlayer player) {
        ItemStack using = player.getUseItem();
        if (player.isUsingItem() && using.getItem() instanceof BowItem) {
            float power = BowItem.getPowerForTime(player.getTicksUsingItem());
            return power < 0.1F ? 0.0D : power * 3.0D;
        }
        if (player.isUsingItem() && using.getItem() instanceof TridentItem)
            return player.getTicksUsingItem() >= 10 ? 2.5D : 0.0D;

        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main))
            return 3.15D;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(off))
            return 3.15D;
        return 0.0D;
    }

    private LivingEntity findTarget(LocalPlayer player) {
        double maxDistance = range.get() * range.get();
        float maxAngle = fov.get() / 2f;
        Vec3 eye = player.getEyePosition();
        LivingEntity best = null;
        float bestAngle = Float.MAX_VALUE;
        Iterable<? extends Entity> entities = onlyPlayers.get() ? mc.level.players() : mc.level.entitiesForRendering();
        for (Entity entity : entities) {
            if (!CombatTargets.isEnemy(entity, onlyPlayers.get()) || entity.distanceToSqr(player) > maxDistance)
                continue;

            Vec3 center = entity.getBoundingBox().getCenter();
            float yaw = (float) (Mth.atan2(center.z - eye.z, center.x - eye.x) * Mth.RAD_TO_DEG) - 90f;
            float angle = Math.abs(Mth.wrapDegrees(yaw - player.getYRot()));
            if (angle > maxAngle || angle >= bestAngle)
                continue;
            if (!throughWalls.get() && player.level().clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS)
                continue;

            bestAngle = angle;
            best = (LivingEntity) entity;
        }
        return best;
    }

    private static float[] solvePitch(double speed, double horizontal, double height) {
        double low = -89.0D;
        double high = 45.0D;
        float[] best = {-45f, 0f};
        double bestError = Double.MAX_VALUE;
        for (int step = 0; step < SEARCH_STEPS; step++) {
            double elevation = (low + high) / 2.0D;
            double[] result = heightAt(speed, elevation, horizontal);
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

    private static double[] heightAt(double speed, double elevationDegrees, double horizontal) {
        double radians = Math.toRadians(elevationDegrees);
        double vx = Math.cos(radians) * speed;
        double vy = Math.sin(radians) * speed;
        double x = 0.0D;
        double y = 0.0D;
        for (int tick = 1; tick <= MAX_FLIGHT_TICKS; tick++) {
            double nextX = x + vx;
            double nextY = y + vy;
            if (nextX >= horizontal) {
                double fraction = vx <= 1.0E-6D ? 0.0D : (horizontal - x) / vx;
                return new double[]{y + vy * fraction, tick};
            }
            x = nextX;
            y = nextY;
            vx *= DRAG;
            vy = vy * DRAG - GRAVITY;
            if (vx < 1.0E-3D)
                break;
        }
        return new double[]{-1.0E9D, MAX_FLIGHT_TICKS};
    }

    private void showTrajectory(Vec3 eye, float yaw, float pitch, double speed, int ticks) {
        trajectory.clear();
        Vec3 velocity = Vec3.directionFromRotation(pitch, yaw).scale(speed);
        Vec3 position = eye;
        for (int tick = 0; tick <= Math.min(ticks + 2, MAX_FLIGHT_TICKS); tick++) {
            trajectory.add(position);
            position = position.add(velocity);
            velocity = velocity.scale(DRAG).subtract(0.0D, GRAVITY, 0.0D);
        }
        PathLines.show(this, TRAJECTORY_LINE, eye, trajectory, RenderColors.BOW_AIMBOT);
    }
}
