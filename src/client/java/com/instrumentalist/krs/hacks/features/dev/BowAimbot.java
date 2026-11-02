package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.CombatTargets;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.math.Ballistics;
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
    private static final int MAX_FLIGHT_TICKS = 120;
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
        Ballistics.Solution solution = Ballistics.aim(eye, target, speed, Ballistics.ARROW_GRAVITY, false, prediction.get());
        float yaw = solution.yaw();
        float pitch = solution.pitch();
        int flightTicks = solution.flightTicks();
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

    private void showTrajectory(Vec3 eye, float yaw, float pitch, double speed, int ticks) {
        trajectory.clear();
        Vec3 velocity = Vec3.directionFromRotation(pitch, yaw).scale(speed);
        Vec3 position = eye;
        for (int tick = 0; tick <= Math.min(ticks + 2, MAX_FLIGHT_TICKS); tick++) {
            trajectory.add(position);
            position = position.add(velocity);
            velocity = velocity.scale(Ballistics.AIR_DRAG).subtract(0.0D, Ballistics.ARROW_GRAVITY, 0.0D);
        }
        PathLines.show(this, TRAJECTORY_LINE, eye, trajectory, RenderColors.BOW_AIMBOT);
    }
}
