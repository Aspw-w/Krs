package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.math.ToolUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public class AutoTool extends Module {
    private BlockPos breakingPos = null;
    private int toolOriginalSlot = -1;
    private int toolSelectedSlot = -1;
    private boolean toolSpoofing = false;
    private int restoreDelayTicks = 0;

    public AutoTool() {
        super("Auto Tool", ModuleCategory.Player, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null) {
            resetState();
            return;
        }

        if (breakingPos != null)
            updateBreakingTool();
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (event.packet instanceof ServerboundPlayerActionPacket actionPacket && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            breakingPos = actionPacket.getPos();
            restoreDelayTicks = 4;
        }
    }

    @Override
    public void onWorld(WorldEvent event) {
        resetState();
    }

    public void resetState() {
        resetToolSlot();
    }

    private void updateBreakingTool() {
        if (!isBreakingInputHeld()) {
            resetToolSlot();
            return;
        }

        BlockPos targetPos = getTargetBlockPos();
        if (targetPos != null && mc.level != null && !mc.level.getBlockState(targetPos).isAir()) {
            breakingPos = targetPos;
            if (switchToBestTool(targetPos))
                restoreDelayTicks = 4;
            else
                resetToolSlot();
            return;
        }

        if (restoreDelayTicks > 0) {
            restoreDelayTicks--;
            return;
        }

        resetToolSlot();
    }

    private boolean isBreakingInputHeld() {
        return mc.options.keyAttack.isDown() && !mc.options.keyUse.isDown();
    }

    private BlockPos getTargetBlockPos() {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK)
            return null;

        return ((BlockHitResult) mc.hitResult).getBlockPos();
    }

    private boolean switchToBestTool(BlockPos pos) {
        var player = mc.player;
        if (player == null) return false;
        int bestToolSlot = ToolUtil.INSTANCE.findBestTool(pos);

        if (bestToolSlot == -1)
            return false;

        if (toolOriginalSlot == -1 && player.getInventory().getSelectedSlot() == bestToolSlot)
            return true;

        if (toolOriginalSlot == -1)
            toolOriginalSlot = player.getInventory().getSelectedSlot();

        toolSelectedSlot = bestToolSlot;
        PlayerUtil.INSTANCE.doSpoof(toolOriginalSlot);
        toolSpoofing = true;

        if (player.getInventory().getSelectedSlot() != bestToolSlot)
            player.getInventory().setSelectedSlot(bestToolSlot);

        return true;
    }

    private void resetToolSlot() {
        var player = mc.player;

        if (player != null && toolOriginalSlot >= 0 && toolOriginalSlot <= 8 && player.getInventory().getSelectedSlot() == toolSelectedSlot)
            player.getInventory().setSelectedSlot(toolOriginalSlot);

        breakingPos = null;
        toolOriginalSlot = -1;
        toolSelectedSlot = -1;
        restoreDelayTicks = 0;
        if (toolSpoofing)
            PlayerUtil.INSTANCE.stopSpoof();
        toolSpoofing = false;
    }

}
