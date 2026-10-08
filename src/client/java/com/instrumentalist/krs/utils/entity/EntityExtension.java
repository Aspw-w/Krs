package com.instrumentalist.krs.utils.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public final class EntityExtension {
    private EntityExtension() {
    }

    public static float distanceToWithoutY(Entity self, Entity entity) {
        float f = (float) (self.getX() - entity.getX());
        float h = (float) (self.getZ() - entity.getZ());
        return Mth.sqrt(f * f + h * h);
    }

    public static float boundingDistanceTo(Entity self, Entity entity) {
        float f = (float) (self.getBoundingBox().getCenter().x - entity.getBoundingBox().getCenter().x);
        float g = (float) (self.getBoundingBox().getCenter().y - entity.getBoundingBox().getCenter().y);
        float h = (float) (self.getBoundingBox().getCenter().z - entity.getBoundingBox().getCenter().z);
        return Mth.sqrt(f * f + g * g + h * h);
    }

    public static double squaredBoxedDistanceTo(Entity entity, Entity other) {
        if (entity == null || other == null)
            return Double.POSITIVE_INFINITY;

        return squaredBoxedDistanceTo(entity, other.getEyePosition());
    }

    public static double squaredBoxedDistanceTo(Entity entity, Vec3 otherPos) {
        if (entity == null || otherPos == null)
            return Double.POSITIVE_INFINITY;

        return entity.getBoundingBox().distanceToSqr(otherPos);
    }

    public static float squaredDistanceToWithoutY(Entity self, double x, double z) {
        double d = self.getX() - x;
        double f = self.getZ() - z;
        return Mth.sqrt((float) (d * d + f * f));
    }

    public static String stripMinecraftColorCodes(String text) {
        StringBuilder stripped = null;
        int copyStart = 0;

        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) != '\u00A7' || !isMinecraftColorCode(text.charAt(i + 1)))
                continue;

            if (stripped == null)
                stripped = new StringBuilder(text.length());

            stripped.append(text, copyStart, i);
            i++;
            copyStart = i + 1;
        }

        if (stripped == null)
            return text;

        stripped.append(text, copyStart, text.length());
        return stripped.toString();
    }

    private static boolean isMinecraftColorCode(char code) {
        code = Character.toLowerCase(code);
        return code >= '0' && code <= '9' || code >= 'a' && code <= 'f' || code >= 'k' && code <= 'o' || code == 'r';
    }

    public static Integer getArmorColor(ItemStack itemStack) {
        return itemStack.has(DataComponents.DYED_COLOR) ? DyedItemColor.getOrDefault(itemStack, -6265536) : null;
    }

    public static boolean isFallingToVoid(Entity entity) {
        return isFallingToVoid(entity, -64.0, 0.0);
    }

    public static boolean isFallingToVoid(Entity entity, double voidLevel, double safetyExpand) {
        if (entity.getY() < voidLevel || entity.getBoundingBox().minY < voidLevel) return true;

        var boundingBox = entity.getBoundingBox()
                .setMinY(voidLevel)
                .inflate(safetyExpand, 0.0, safetyExpand);
        for (var shape : entity.level().getBlockCollisions(entity, boundingBox)) {
            if (shape != Shapes.empty())
                return false;
        }

        return true;
    }
}
