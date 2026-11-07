package com.instrumentalist.krs.utils.math;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

public class TimerUtil {

    public static float timerSpeed = 0f;

    public static boolean selfTick = true;

    public static boolean worldTick = true;

    public static boolean inClientTick;

    private static float selfResidual;

    private static int pendingSelfTicks;

    private static int pendingWorldTicks;

    public static void reset() {
        timerSpeed = 0f;
    }

    public static boolean isActive() {
        return timerSpeed > 0f && timerSpeed != 1f;
    }

    public static float realTickStep() {
        return isActive() ? 1f / timerSpeed : 1f;
    }

    public static int scheduleTicks(int worldTicks, float deltaTicks, float worldResidual) {
        if (!isActive()) {
            selfResidual = worldResidual;
            pendingSelfTicks = worldTicks;
            pendingWorldTicks = worldTicks;
            return worldTicks;
        }

        selfResidual += deltaTicks * timerSpeed;
        int selfTicks = (int) selfResidual;
        selfResidual -= selfTicks;

        pendingSelfTicks = selfTicks;
        pendingWorldTicks = worldTicks;
        return Math.max(selfTicks, worldTicks);
    }

    public static void beginClientTick() {
        inClientTick = true;

        if (pendingSelfTicks <= 0 && pendingWorldTicks <= 0) {
            selfTick = true;
            worldTick = true;
            return;
        }

        selfTick = pendingSelfTicks > 0;
        worldTick = pendingWorldTicks > 0;

        if (pendingSelfTicks > 0)
            pendingSelfTicks--;

        if (pendingWorldTicks > 0)
            pendingWorldTicks--;
    }

    public static void endClientTick() {
        inClientTick = false;
        selfTick = true;
        worldTick = true;
    }

    public static float selfPartialTick(float worldPartial) {
        if (!isActive() || Minecraft.getInstance().isPaused())
            return worldPartial;

        return selfResidual;
    }

    public static boolean isSelfEntity(Entity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || entity == null)
            return false;

        return entity == player || entity.getRootVehicle() == player.getRootVehicle();
    }

    public static float partialTickFor(Entity entity, float worldPartial) {
        return isSelfEntity(entity) ? selfPartialTick(worldPartial) : worldPartial;
    }
}
