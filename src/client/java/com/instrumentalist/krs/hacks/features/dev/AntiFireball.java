package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class AntiFireball extends Module {
    private static final int RETRY_TICKS = 4;

    @Setting
    private final FloatValue range = new FloatValue("Range", 3.0f, 1.5f, 6f, "m");

    @Setting
    private final BooleanValue rotate = new BooleanValue("Rotate", true);

    @Setting
    private final BooleanValue returnToSender = new BooleanValue("Return To Sender", true);

    private Fireball aiming;
    private int aimTick;
    private int lastHitId = -1;
    private int lastHitTick;
    private boolean rotating;

    public AntiFireball() {
        super("Anti Fireball", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        aiming = null;
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onWorld(WorldEvent event) {
        reset();
    }

    private void reset() {
        aiming = null;
        if (rotating) {
            Client.rotationManager.stopRotation();
            rotating = false;
        }
    }

    @Override
    public void onTick(TickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            reset();
            return;
        }

        Fireball fireball = findFireball(player);
        if (fireball == null) {
            reset();
            return;
        }

        if (rotate.get()) {
            Vec3 look = aimPoint(player, fireball);
            Client.rotationManager.facePosition(look.x, look.y, look.z, 180f);
            rotating = true;
            if (aiming != fireball) {
                aiming = fireball;
                aimTick = player.tickCount;
                return;
            }
            if (player.tickCount <= aimTick)
                return;
        }

        mc.gameMode.attack(player, fireball);
        PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
        lastHitId = fireball.getId();
        lastHitTick = player.tickCount;
        aiming = null;
    }

    private Vec3 aimPoint(LocalPlayer player, Fireball fireball) {
        Entity owner = fireball.getOwner();
        if (returnToSender.get() && owner != null && owner != player && owner.isAlive())
            return owner.getBoundingBox().getCenter();

        Vec3 velocity = fireball.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-4D)
            return fireball.position();
        return player.getEyePosition().subtract(velocity.normalize().scale(10.0D));
    }

    private Fireball findFireball(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        double maxDistance = range.get() * range.get();
        Fireball best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Fireball fireball) || fireball.getOwner() == player)
                continue;
            if (fireball.getId() == lastHitId && player.tickCount - lastHitTick < RETRY_TICKS)
                continue;

            double distance = fireball.getBoundingBox().distanceToSqr(eye);
            if (distance > maxDistance || distance >= bestDistance)
                continue;

            Vec3 velocity = fireball.getDeltaMovement();
            if (velocity.lengthSqr() > 1.0E-4D && velocity.dot(eye.subtract(fireball.position())) <= 0.0D)
                continue;

            best = fireball;
            bestDistance = distance;
        }
        return best;
    }
}
