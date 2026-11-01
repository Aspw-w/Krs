package com.instrumentalist.krs.utils.entity;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.combat.AntiBot;
import com.instrumentalist.krs.hacks.features.combat.Teams;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

public final class CombatTargets {
    private CombatTargets() {
    }

    public static boolean isEnemy(Entity entity, boolean playersOnly) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || entity == null || entity == player || !(entity instanceof LivingEntity living))
            return false;
        if (!living.isAlive() || living.isDeadOrDying() || entity.isSpectator() || entity instanceof ArmorStand)
            return false;
        if (entity == player.getVehicle())
            return false;
        if (playersOnly && !(entity instanceof Player))
            return false;
        if (entity instanceof Player other) {
            if (BehaviorUtils.isBot(other, ModuleManager.getModuleState(AntiBot.class)))
                return false;
            return !BehaviorUtils.isTeammate(other, ModuleManager.getModuleState(Teams.class));
        }
        return true;
    }

    public static LivingEntity nearest(double range, boolean playersOnly) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null)
            return null;

        double maxDistance = range * range;
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        Iterable<? extends Entity> entities = playersOnly ? minecraft.level.players() : minecraft.level.entitiesForRendering();
        for (Entity entity : entities) {
            if (!isEnemy(entity, playersOnly))
                continue;

            double distance = entity.distanceToSqr(player);
            if (distance <= maxDistance && distance < bestDistance) {
                bestDistance = distance;
                best = (LivingEntity) entity;
            }
        }
        return best;
    }
}
