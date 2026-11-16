package com.instrumentalist.krs.utils.render;

import com.instrumentalist.krs.hacks.features.render.LegacyRenderer;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.mixin.injector.LivingEntityAccessor;
import com.instrumentalist.mixin.injector.SwingStateAccessor;
import com.instrumentalist.mixin.oringo.ILegacySwing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class SelfRotationVisual {

    private static boolean active;

    private static float bodyYaw;

    private static float prevBodyYaw;

    private static float headYaw;

    private static float prevHeadYaw;

    private static float pitch;

    private static float prevPitch;

    private SelfRotationVisual() {
    }

    public static void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !TimerUtil.isActive()) {
            active = false;
            return;
        }

        if (!active) {
            active = true;
            bodyYaw = prevBodyYaw = player.yBodyRot;
            headYaw = prevHeadYaw = player.yHeadRot;
            pitch = prevPitch = player.getXRot();
            return;
        }

        prevBodyYaw = bodyYaw;
        prevHeadYaw = headYaw;
        prevPitch = pitch;

        float yaw = player.getYRot();
        headYaw += Mth.wrapDegrees(yaw - headYaw);
        pitch = player.getXRot();

        double xd = player.getX() - player.xo;
        double zd = player.getZ() - player.zo;

        if (LegacyRenderer.isPlayerAnimationsActive()) {
            bodyYaw = LegacyRenderer.updateDistance(bodyYaw, yaw, xd, zd, ((ILegacySwing) player).krs$isLegacySwinging());
            return;
        }

        float sideDist = (float) (xd * xd + zd * zd);
        float target = bodyYaw;
        if (sideDist > 0.0025000002F) {
            float walkDirection = (float) Mth.atan2(zd, xd) * (180.0F / (float) Math.PI) - 90.0F;
            float diff = Mth.abs(Mth.wrapDegrees(yaw) - walkDirection);
            target = 95.0F < diff && diff < 265.0F ? walkDirection - 180.0F : walkDirection;
        }

        if (((SwingStateAccessor) ((LivingEntityAccessor) player).krs$getSwingState()).krs$getAnimation() > 0.0F)
            target = yaw;

        bodyYaw += Mth.wrapDegrees(target - bodyYaw) * 0.3F;
        float headDiff = Mth.wrapDegrees(yaw - bodyYaw);
        float maxHeadRotation = player.isBlocking() ? 15.0F : 50.0F;
        if (Math.abs(headDiff) > maxHeadRotation)
            bodyYaw += headDiff - Mth.sign(headDiff) * maxHeadRotation;
    }

    public static boolean applies(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || !TimerUtil.isActive() || mc.level == null || entity != mc.player || GuiEntityRenderGuard.isActive() || mc.isPaused())
            return false;

        return !(entity.getVehicle() instanceof LivingEntity) && !mc.level.tickRateManager().isEntityFrozen(entity);
    }

    private static float partialTicks() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }

    public static float bodyYaw() {
        return Mth.rotLerp(partialTicks(), prevBodyYaw, bodyYaw);
    }

    public static float headYaw() {
        return Mth.rotLerp(partialTicks(), prevHeadYaw, headYaw);
    }

    public static float pitch() {
        return Mth.lerp(partialTicks(), prevPitch, pitch);
    }
}
