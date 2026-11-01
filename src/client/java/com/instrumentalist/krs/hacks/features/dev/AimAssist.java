package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.CombatTargets;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class AimAssist extends Module {
    @Setting
    private final FloatValue range = new FloatValue("Range", 4.5f, 2f, 8f, "m");

    @Setting
    private final FloatValue fov = new FloatValue("FOV", 60f, 10f, 180f, "°");

    @Setting
    private final FloatValue speed = new FloatValue("Speed", 120f, 10f, 600f, "°/s");

    @Setting
    private final BooleanValue vertical = new BooleanValue("Vertical", true);

    @Setting
    private final BooleanValue onlyWhileAttacking = new BooleanValue("Only While Attacking", true);

    @Setting
    private final BooleanValue onlyPlayers = new BooleanValue("Only Players", true);

    @Setting
    private final BooleanValue throughWalls = new BooleanValue("Through Walls", false);

    private long lastFrameNanos;

    public AimAssist() {
        super("Aim Assist", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        lastFrameNanos = 0L;
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        long now = System.nanoTime();
        float frameSeconds = lastFrameNanos == 0L ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gui.screen() != null || frameSeconds <= 0f)
            return;
        if (onlyWhileAttacking.get() && !mc.options.keyAttack.isDown())
            return;

        LivingEntity target = findTarget(player);
        if (target == null)
            return;

        Vec3 eye = player.getEyePosition();
        Vec3 aimPoint = closestPoint(target.getBoundingBox(), eye);
        Vec3 delta = aimPoint.subtract(eye);
        float targetYaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90f;
        float targetPitch = (float) -(Mth.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * Mth.RAD_TO_DEG);

        float step = speed.get() * frameSeconds;
        float yawStep = Mth.clamp(Mth.wrapDegrees(targetYaw - player.getYRot()), -step, step);
        player.setYRot(player.getYRot() + yawStep);
        player.yRotO += yawStep;

        if (vertical.get()) {
            float pitch = Mth.clamp(player.getXRot() + Mth.clamp(targetPitch - player.getXRot(), -step, step), -90f, 90f);
            player.xRotO += pitch - player.getXRot();
            player.setXRot(pitch);
        }
    }

    private LivingEntity findTarget(LocalPlayer player) {
        double maxDistance = range.get() * range.get();
        float maxAngle = fov.get() / 2f;
        Vec3 eye = player.getEyePosition();
        LivingEntity best = null;
        float bestAngle = Float.MAX_VALUE;
        Iterable<? extends Entity> entities = onlyPlayers.get() ? mc.level.players() : mc.level.entitiesForRendering();
        for (Entity entity : entities) {
            if (!CombatTargets.isEnemy(entity, onlyPlayers.get()))
                continue;

            Vec3 point = closestPoint(entity.getBoundingBox(), eye);
            if (point.distanceToSqr(eye) > maxDistance)
                continue;

            Vec3 delta = point.subtract(eye);
            float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90f;
            float angle = Math.abs(Mth.wrapDegrees(yaw - player.getYRot()));
            if (angle > maxAngle || angle >= bestAngle)
                continue;
            if (!throughWalls.get() && !canSee(player, eye, entity.getBoundingBox().getCenter()))
                continue;

            bestAngle = angle;
            best = (LivingEntity) entity;
        }
        return best;
    }

    private static boolean canSee(LocalPlayer player, Vec3 from, Vec3 to) {
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    private static Vec3 closestPoint(AABB box, Vec3 point) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY + (box.maxY - box.minY) * 0.25D, box.maxY - (box.maxY - box.minY) * 0.1D),
                Mth.clamp(point.z, box.minZ, box.maxZ)
        );
    }
}
