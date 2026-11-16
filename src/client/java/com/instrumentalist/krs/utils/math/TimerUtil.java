package com.instrumentalist.krs.utils.math;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public class TimerUtil {

    private static final float RESYNC_RATE = 0.1f;

    private static final float MAX_RESIDUAL = Math.nextDown(1f);

    public static float timerSpeed = 0f;

    public static boolean selfTick = true;

    public static boolean worldTick = true;

    public static boolean inClientTick;

    private static float selfResidual;

    private static int pendingSelfTicks;

    private static int pendingWorldTicks;

    private static boolean detached;

    private static boolean resyncing;

    private static float resyncLead;

    private static int resyncCell;

    public static void reset() {
        timerSpeed = 0f;
    }

    public static boolean isActive() {
        return timerSpeed > 0f && timerSpeed != 1f;
    }

    public static boolean isDetached() {
        return detached || isActive();
    }

    private static float selfSpeed() {
        if (isActive())
            return timerSpeed;

        if (!detached || !resyncing || resyncLead == 0f)
            return 1f;

        return resyncLead > 0f ? 1f - RESYNC_RATE : 1f + RESYNC_RATE;
    }

    public static float realTickStep() {
        return 1f / selfSpeed();
    }

    public static float smoothing(float factor) {
        float step = realTickStep();
        if (step == 1f)
            return factor;

        return 1f - (float) Math.pow(1f - factor, step);
    }

    public static int scheduleTicks(int worldTicks, float deltaTicks, float worldResidual) {
        int selfTicks;

        if (isActive()) {
            detached = true;
            resyncing = false;
            selfResidual += deltaTicks * timerSpeed;
            selfTicks = (int) selfResidual;
            selfResidual -= selfTicks;
        } else if (detached && Minecraft.getInstance().level != null) {
            selfTicks = resyncTicks(worldTicks, deltaTicks, worldResidual);
        } else {
            detached = false;
            resyncing = false;
            selfResidual = worldResidual;
            selfTicks = worldTicks;
        }

        pendingSelfTicks = selfTicks;
        pendingWorldTicks = worldTicks;
        return Math.max(selfTicks, worldTicks);
    }

    private static int resyncTicks(int worldTicks, float deltaTicks, float worldResidual) {
        if (!resyncing) {
            resyncing = true;
            resyncLead = selfResidual - (worldResidual + worldTicks - deltaTicks);
            resyncCell = 0;

            if (resyncLead > 0.5f) {
                resyncLead -= 1f;
                resyncCell = -1;
            } else if (resyncLead <= -0.5f) {
                resyncLead += 1f;
                resyncCell = 1;
            }
        }

        float step = deltaTicks * RESYNC_RATE;
        resyncLead = resyncLead > 0f ? Math.max(0f, resyncLead - step) : Math.min(0f, resyncLead + step);

        float position = worldResidual + resyncLead;
        int cell = position >= 1f ? 1 : position < 0f ? -1 : 0;
        int selfTicks = worldTicks + cell - resyncCell;
        if (selfTicks < 0) {
            selfTicks = 0;
            cell = resyncCell - worldTicks;
        }

        resyncCell = cell;

        if (resyncLead == 0f && cell == 0) {
            detached = false;
            resyncing = false;
            selfResidual = worldResidual;
        } else {
            selfResidual = Mth.clamp(position - cell, 0f, MAX_RESIDUAL);
        }

        return selfTicks;
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
        if (!detached || Minecraft.getInstance().isPaused())
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
