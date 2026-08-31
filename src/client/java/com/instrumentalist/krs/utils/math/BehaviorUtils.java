package com.instrumentalist.krs.utils.math;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.combat.AntiBot;
import com.instrumentalist.krs.hacks.features.combat.KillAura;
import com.instrumentalist.krs.hacks.features.combat.Teams;
import java.util.*;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

public class BehaviorUtils implements IMinecraft {

    public static boolean noKillAura = false;

    public static boolean isBot(LivingEntity entity) {
        return isBot(entity, ModuleManager.getModuleState(AntiBot.class));
    }

    public static boolean isBot(LivingEntity entity, boolean antiBotEnabled) {
        if (!antiBotEnabled || !(entity instanceof Player) || entity instanceof LocalPlayer) return false;
        return AntiBot.inBotList(entity);
    }

    public static boolean isTeammate(LivingEntity entity) {
        return isTeammate(entity, ModuleManager.getModuleState(Teams.class));
    }

    public static boolean isTeammate(LivingEntity entity, boolean teamsEnabled) {
        if (!teamsEnabled || !(entity instanceof Player) || entity instanceof LocalPlayer) return false;
        return Teams.isInClientPlayersTeam(entity);
    }

    public static void fillTargetList(List<Entity> result) {
        if (result == null) return;

        result.clear();

        boolean killAura = ModuleManager.getModuleState(KillAura.class);
        List<Entity> multi = killAura ? KillAura.multiTargets : null;
        Entity closest = killAura ? KillAura.closestEntity : null;
        Entity targeted = (mc.crosshairPickEntity instanceof LivingEntity
                && !(mc.crosshairPickEntity instanceof ArmorStand))
                ? mc.crosshairPickEntity : null;

        if (multi != null && !multi.isEmpty()) {
            for (int i = 0, n = multi.size(); i < n; i++) {
                Entity e = multi.get(i);
                if (!result.contains(e)) result.add(e);
            }
        }
        if (closest != null && !result.contains(closest)) result.add(closest);
        if (targeted != null && !result.contains(targeted)) result.add(targeted);
    }

    public static boolean isTarget(Entity entity) {
        if (entity == null) return false;

        boolean killAura = ModuleManager.getModuleState(KillAura.class);
        if (killAura) {
            if (entity == KillAura.closestEntity) return true;

            List<Entity> multi = KillAura.multiTargets;
            for (int i = 0, n = multi.size(); i < n; i++) {
                if (entity == multi.get(i)) return true;
            }
        }

        return mc.crosshairPickEntity == entity
                && entity instanceof LivingEntity
                && !(entity instanceof ArmorStand);
    }
}
