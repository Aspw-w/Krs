package com.instrumentalist.krs.utils.math;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class RotationScraper {
    public static final RotationScraper INSTANCE = new RotationScraper();

    public final Minecraft mc = Minecraft.getInstance();

    private RotationScraper() {
    }

    public Tuple<Float, Float> getRotationsEntity(LivingEntity entity) {
        if (mc.player == null || entity == null)
            return Tuple.of(0.0f, 0.0f);
        return getRotations(entity.getX(), entity.getY() + entity.getEyeHeight() - 0.4, entity.getZ());
    }

    private Tuple<Float, Float> getRotations(double posX, double posY, double posZ) {
        var player = mc.player;
        if (player == null)
            return Tuple.of(0.0f, 0.0f);

        double x = posX - player.getX();
        double y = posY - (player.getY() + player.getEyeHeight());
        double z = posZ - player.getZ();
        double dist = Mth.sqrt((float) (x * x + z * z));
        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(y, dist) * 180.0 / Math.PI));
        return Tuple.of(yaw, pitch);
    }

    public float simpleSnap(float yaw) {
        return Math.round(yaw / 45.0f) * 45.0f;
    }
}
