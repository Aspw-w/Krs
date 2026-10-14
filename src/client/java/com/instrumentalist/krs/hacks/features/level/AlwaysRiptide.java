package com.instrumentalist.krs.hacks.features.level;

import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.mixin.injector.LivingEntityAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import com.instrumentalist.krs.utils.input.Keys;

public class AlwaysRiptide extends Module {
    private AABB lastBox = null;

    public AlwaysRiptide() {
        super("Always Riptide", ModuleCategory.Level, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        var player = mc.player;
        if (player == null) return;

        applySpinPose(player);
    }

    @Override
    public void onDisable() {
        lastBox = null;

        var player = mc.player;
        if (player == null) return;

        syncPublishedServerSpinAttack(player, false);

        if (!player.hasPose(Pose.SPIN_ATTACK) || player.isAutoSpinAttack()) return;

        if (canFit(player, Pose.STANDING))
            player.setPose(Pose.STANDING);
        else if (canFit(player, Pose.CROUCHING))
            player.setPose(Pose.CROUCHING);
        else player.setPose(Pose.SWIMMING);

        player.refreshDimensions();
    }

    @Override
    public void onWorld(WorldEvent event) {
        lastBox = null;
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) return;

        applySpinPose(player);
        prepareAutoSpinAttack(player);
        syncPublishedServerSpinAttack(player, true);
        attackTouchedEntities(player, level);
    }

    public static boolean shouldForceSpinAttack(LivingEntity entity) {
        return entity == mc.player && ModuleManager.getModuleState(AlwaysRiptide.class);
    }

    public static Pose hookDesiredPose(Pose original, Entity entity) {
        if (shouldApplyTo(entity))
            return Pose.SPIN_ATTACK;

        return original;
    }

    public static EntityDimensions hookDimensions(EntityDimensions original, Entity entity, Pose pose) {
        if (pose != Pose.SPIN_ATTACK && shouldApplyTo(entity))
            return EntityDimensions.scalable(0.6F, 0.6F).withEyeHeight(0.4F);

        return original;
    }

    public static void prepareAutoSpinAttack(LivingEntity entity) {
        if (!shouldApplyTo(entity)) return;

        ItemStack weaponStack = entity.getMainHandItem();
        if (weaponStack.isEmpty())
            weaponStack = entity.getOffhandItem();

        entity.autoSpinAttackDmg = 8.0F;
        entity.autoSpinAttackItemStack = weaponStack;
    }

    private static boolean shouldApplyTo(Entity entity) {
        var player = mc.player;
        return player != null && entity != null && ModuleManager.getModuleState(AlwaysRiptide.class) && entity.getUUID().equals(player.getUUID());
    }

    private void applySpinPose(LocalPlayer player) {
        if (!player.hasPose(Pose.SPIN_ATTACK))
            player.setPose(Pose.SPIN_ATTACK);

        if (player.getBoundingBox().getYsize() > 0.7)
            player.refreshDimensions();
    }

    private void syncPublishedServerSpinAttack(LocalPlayer player, boolean enabled) {
        var server = mc.getSingleplayerServer();
        if (server == null || !server.isPublished()) return;

        var uuid = player.getUUID();
        server.executeIfPossible(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(uuid);
            if (serverPlayer == null) return;

            if (enabled) {
                if (!ModuleManager.getModuleState(AlwaysRiptide.class)) return;

                applyServerSpinAttack(serverPlayer);
            } else {
                if (ModuleManager.getModuleState(AlwaysRiptide.class)) return;

                clearServerSpinAttack(serverPlayer);
            }
        });
    }

    private void applyServerSpinAttack(ServerPlayer player) {
        prepareAutoSpinAttack(player);
        ((LivingEntityAccessor) player).krs$setAutoSpinAttackTicks(20);
        ((LivingEntityAccessor) player).krs$invokeSetLivingEntityFlag(4, true);

        if (!player.hasPose(Pose.SPIN_ATTACK))
            player.setPose(Pose.SPIN_ATTACK);

        if (player.getBoundingBox().getYsize() > 0.7)
            player.refreshDimensions();
    }

    private void clearServerSpinAttack(ServerPlayer player) {
        ((LivingEntityAccessor) player).krs$setAutoSpinAttackTicks(0);
        player.autoSpinAttackDmg = 0.0F;
        player.autoSpinAttackItemStack = null;
        ((LivingEntityAccessor) player).krs$invokeSetLivingEntityFlag(4, false);

        if (!player.hasPose(Pose.SPIN_ATTACK)) return;

        if (canFit(player, Pose.STANDING))
            player.setPose(Pose.STANDING);
        else if (canFit(player, Pose.CROUCHING))
            player.setPose(Pose.CROUCHING);
        else player.setPose(Pose.SWIMMING);

        player.refreshDimensions();
    }

    private void attackTouchedEntities(LocalPlayer player, ClientLevel level) {
        if (mc.gameMode == null) return;

        AABB currentBox = player.getBoundingBox();
        AABB scanBox = lastBox == null ? currentBox : lastBox.minmax(currentBox);
        lastBox = currentBox;
        boolean attacked = false;

        for (Entity entity : level.getEntities(player, scanBox)) {
            if (!(entity instanceof LivingEntity livingEntity)
                    || !livingEntity.isAlive()
                    || !entity.isAttackable()
                    || entity.skipAttackInteraction(player))
                continue;

            prepareAutoSpinAttack(player);
            mc.gameMode.attack(player, entity);
            PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
            attacked = true;
        }

        if (attacked)
            player.setDeltaMovement(player.getDeltaMovement().scale(-0.2));
    }

    private boolean canFit(LivingEntity player, Pose pose) {
        var level = player.level();
        if (level == null) return false;

        return level.noCollision(player, player.getDimensions(pose).makeBoundingBox(player.position()).deflate(1.0E-7));
    }
}
