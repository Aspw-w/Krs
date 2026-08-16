package com.instrumentalist.krs.hacks.features.level;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.player.AutoTool;
import com.instrumentalist.krs.hacks.features.player.Scaffold;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.math.MSTimer;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class CivBreak extends Module {

    @Setting
    private static final ListValue mode = new ListValue("Mode", new String[]{"Instant", "Wait One Break", "Legit"}, "Instant");

    @Setting
    private static final FloatValue range = new FloatValue("Range", 3f, 0.1f, 6f, "m");

    @Setting
    private static final IntValue fastSpeed = new IntValue("Fast Speed", 10, 1, 10, () -> mode.get().equalsIgnoreCase("instant") || mode.get().equalsIgnoreCase("wait one break"));

    @Setting
    private static final BooleanValue rotations = new BooleanValue("Rotations", true);

    private static boolean wasBreaking = false;

    private final MSTimer rotateTimer = new MSTimer();
    private int originalSlot = -1;
    private BlockPos pos = null;
    private Direction direction = null;
    private int stage = 0;

    public CivBreak() {
        super("Civ Break", ModuleCategory.Level, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
        var player = mc.player;
        if (wasBreaking && player != null) {
            if (originalSlot != -1 && originalSlot != player.getInventory().getSelectedSlot()) {
                player.getInventory().setSelectedSlot(originalSlot);
                originalSlot = -1;
            }
            BehaviorUtils.noKillAura = false;
        }
        PlayerUtil.INSTANCE.stopSpoof();
        if (wasBreaking)
            Client.rotationManager.stopRotation();
        if (wasBreaking)
            PlayerUtil.INSTANCE.stopDestroyBlock();
        wasBreaking = false;
        pos = null;
        direction = null;
        stage = 0;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onWorld(WorldEvent event) {
        if (wasBreaking) {
            var player = mc.player;
            if (originalSlot != -1) {
                if (player != null && originalSlot != player.getInventory().getSelectedSlot())
                    player.getInventory().setSelectedSlot(originalSlot);
                originalSlot = -1;
            }
            PlayerUtil.INSTANCE.stopSpoof();
            BehaviorUtils.noKillAura = false;
        }
        if (wasBreaking)
            Client.rotationManager.stopRotation();
        if (wasBreaking)
            PlayerUtil.INSTANCE.stopDestroyBlock();
        wasBreaking = false;
        pos = null;
        direction = null;
        stage = 0;
    }

    @Override
    public void onMotion(MotionEvent event) {
        var player = mc.player;
        var level = mc.level;
        var gameMode = mc.gameMode;
        if (player == null || level == null || gameMode == null || gameMode.getPlayerMode() == GameType.ADVENTURE || gameMode.getPlayerMode() == GameType.SPECTATOR) return;
        if (ModuleManager.getModuleState(Scaffold.class)) return;

        BlockPos targetPos = pos;
        Direction targetDirection = direction;
        double rangeLimit = range.get() * range.get();
        if (targetPos == null
                || targetDirection == null
                || level.getBlockState(targetPos).isAir() && stage == 1
                || rangeLimit < player.distanceToSqr(Vec3.atCenterOf(targetPos))) {
            if (wasBreaking) {
                PlayerUtil.INSTANCE.stopDestroyBlock();
                if (rotations.get())
                    Client.rotationManager.stopRotation();
                if (originalSlot != -1 && originalSlot != player.getInventory().getSelectedSlot()) {
                    player.getInventory().setSelectedSlot(originalSlot);
                    originalSlot = -1;
                }
                PlayerUtil.INSTANCE.stopSpoof();
                BehaviorUtils.noKillAura = false;
                wasBreaking = false;
            }
            return;
        }

        if (rotations.get())
            Client.rotationManager.faceBlock(targetPos, 180f);

        BehaviorUtils.noKillAura = true;

        if (ModuleManager.getModuleState(AutoTool.class)) {
            int bestToolSlot = ToolUtil.INSTANCE.findBestTool(targetPos);
            if (bestToolSlot != -1 && bestToolSlot != player.getInventory().getSelectedSlot()) {
                if (originalSlot == -1)
                    originalSlot = player.getInventory().getSelectedSlot();
                PlayerUtil.INSTANCE.doSpoof(originalSlot);
                player.getInventory().setSelectedSlot(bestToolSlot);
            }
        }

        if (mode.get().equalsIgnoreCase("legit"))
            stage = 0;
        else if (mode.get().equalsIgnoreCase("instant"))
            stage = 1;

        wasBreaking = true;

        switch (stage) {
            case 0 -> PlayerUtil.INSTANCE.destroyBlock(targetPos);
            case 1 -> {
                if (rotateTimer.hasTimePassed(1000L / (fastSpeed.get() + 2))) {
                    PacketUtil.sendPacket(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                    ServerboundPlayerActionPacket.Action action = mc.player.isCreative() ? ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK : ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK;
                    for (int i = 0; i < 2; i++) {
                        PacketUtil.sendPacket(new ServerboundPlayerActionPacket(
                                action,
                                targetPos,
                                targetDirection
                        ));
                    }
                    rotateTimer.reset();
                }
            }
        }

        if (mode.get().equalsIgnoreCase("wait one break") && level.getBlockState(targetPos).isAir() && stage == 0)
            stage++;
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        var packet = event.packet;
        if (packet instanceof ServerboundPlayerActionPacket actionPacket && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            stage = 0;
            pos = actionPacket.getPos();
            direction = actionPacket.getDirection();
        }
    }

    public static boolean getWasBreaking() {
        return wasBreaking;
    }

}
