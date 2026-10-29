package com.instrumentalist.krs.utils.entity;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.level.VisualSpoofer;
import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class PlayerUtil implements IMinecraft {
    public static final PlayerUtil INSTANCE = new PlayerUtil();

    private static final int MAX_SPOOF_RELEASE_TICKS = 10;

    private boolean spoofing = false;
    private Integer spoofSlot = null;
    private int spoofReleaseTicks = 0;

    private PlayerUtil() {
    }

    public boolean getSpoofing() {
        return spoofing;
    }

    public void setSpoofing(boolean spoofing) {
        this.spoofing = spoofing;
    }

    public Integer getSpoofSlot() {
        return spoofSlot;
    }

    public void setSpoofSlot(Integer spoofSlot) {
        this.spoofSlot = spoofSlot;
    }

    public BlockHitResult blockHitResult(BlockPos pos) {
        Direction direction = getBlockHitDirection(pos);
        Vec3 hitPos = Vec3.atCenterOf(pos).add(
                direction.getStepX() * 0.5,
                direction.getStepY() * 0.5,
                direction.getStepZ() * 0.5
        );

        return new BlockHitResult(hitPos, direction, pos, false);
    }

    public void stopDestroyBlock() {
        var gameMode = mc.gameMode;
        if (gameMode != null)
            gameMode.stopDestroyBlock();
    }

    public void nukeBlockWithPacket(BlockHitResult rayTraceResult) {
        nukeBlockWithPacket(rayTraceResult, "Packet");
    }

    public void nukeBlockWithPacket(BlockHitResult rayTraceResult, String swingOrderMode) {
        var direction = rayTraceResult.getDirection();
        var blockPos = rayTraceResult.getBlockPos();

        PacketUtil.sendPacket(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                blockPos,
                direction
        ));
        swingHand(InteractionHand.MAIN_HAND, swingOrderMode);
        PacketUtil.sendPacket(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                blockPos,
                direction
        ));
    }

    public void destroyBlock(BlockPos pos) {
        destroyBlock(blockHitResult(pos));
    }

    public void destroyBlock(BlockHitResult rayTraceResult) {
        destroyBlock(rayTraceResult, "Normal");
    }

    public void destroyBlock(BlockPos pos, String swingOrderMode) {
        destroyBlock(blockHitResult(pos), swingOrderMode);
    }

    public void destroyBlock(BlockHitResult rayTraceResult, String swingOrderMode) {
        var gameMode = mc.gameMode;
        var level = mc.level;
        var player = mc.player;
        if (gameMode == null || level == null || player == null) return;

        var direction = rayTraceResult.getDirection();
        var blockPos = rayTraceResult.getBlockPos();
        if (gameMode.continueDestroyBlock(blockPos, direction))
            swingHand(InteractionHand.MAIN_HAND, swingOrderMode);
    }

    private Direction getBlockHitDirection(BlockPos pos) {
        var player = mc.player;
        if (player == null)
            return Direction.UP;

        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 eyes = player.getEyePosition();
        double x = eyes.x - center.x;
        double y = eyes.y - center.y;
        double z = eyes.z - center.z;
        double absX = Math.abs(x);
        double absY = Math.abs(y);
        double absZ = Math.abs(z);

        if (absY >= absX && absY >= absZ)
            return y > 0.0 ? Direction.UP : Direction.DOWN;
        if (absX >= absZ)
            return x > 0.0 ? Direction.EAST : Direction.WEST;

        return z > 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    public void swingHand(InteractionHand hand) {
        swingHandWithoutPacket(hand);
        sendSwingPacket(hand);
    }

    public void sendSwingPacket(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND)
            PacketUtil.sendPacket(ServerboundPunchPacket.INSTANCE);
    }

    public void swingHandWithoutPacket(InteractionHand hand) {
        var player = mc.player;
        if (player == null) return;
        player.swing(hand, player.getItemInHand(hand).getAttackAnimation(), false);
    }

    public void swingInteractHand(InteractionHand hand) {
        var player = mc.player;
        if (player == null) return;
        player.swing(hand, player.getItemInHand(hand).getInteractAnimation(), false);
    }

    public void swingHand(InteractionHand hand, String swingOrderMode) {
        switch (swingOrderMode.toLowerCase(Locale.ROOT)) {
            case "packet" -> sendSwingPacket(hand);
            case "no order" -> swingHandWithoutPacket(hand);
            default -> swingHand(hand);
        }
    }

    public void swingInteractHand(InteractionHand hand, String swingOrderMode) {
        if (!swingOrderMode.equalsIgnoreCase("packet"))
            swingInteractHand(hand);
    }

    public void doSpoof(Integer spoofTargetSlot) {
        if (!ModuleManager.getModuleState(VisualSpoofer.class))
            return;

        if (spoofTargetSlot != null && spoofTargetSlot >= 0 && spoofTargetSlot <= 8) {
            spoofSlot = spoofTargetSlot;
            spoofing = true;
            spoofReleaseTicks = 0;
        }
    }

    public void stopSpoof() {
        spoofing = false;
        if (canClearSpoofSlot())
            fullResetSpoofState();
        else
            spoofReleaseTicks = 0;
    }

    public void tickSpoofRelease() {
        if (spoofing || spoofSlot == null)
            return;

        if (canClearSpoofSlot() || ++spoofReleaseTicks >= MAX_SPOOF_RELEASE_TICKS)
            fullResetSpoofState();
    }

    public void finishSpoofReleaseIfReady() {
        if (spoofing || spoofSlot == null)
            return;

        if (canClearSpoofSlot())
            fullResetSpoofState();
    }

    public void fullResetSpoofState() {
        spoofing = false;
        spoofSlot = null;
        spoofReleaseTicks = 0;
    }

    private boolean canClearSpoofSlot() {
        var player = mc.player;
        if (player == null || spoofSlot == null || spoofSlot < 0 || spoofSlot > 8)
            return true;

        if (player.getInventory().getSelectedSlot() == spoofSlot)
            return true;

        return player.isUsingItem();
    }
}
