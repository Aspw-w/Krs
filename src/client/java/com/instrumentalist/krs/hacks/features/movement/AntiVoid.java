package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.EntityExtension;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.BlinkUtil;
import com.instrumentalist.krs.utils.render.BlockHighlight;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import com.instrumentalist.krs.utils.input.Keys;

public class AntiVoid extends Module {
    @Setting
    private final BooleanValue stopXZ = new BooleanValue("StopXZ", true);

    @Setting
    private final IntValue distance = new IntValue("Distance", 6, 0, 10, "m");

    private int canTick = 0;
    private Integer unSafeY = null;
    private final BlockHighlight safeBlockHighlight = new BlockHighlight();
    private BlockPos lastSafeBlock;

    public AntiVoid() {
        super("Anti Void", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
        safeBlockHighlight.release();
        lastSafeBlock = null;
        if (mc.player == null) return;
        BlinkUtil.INSTANCE.sync(true, false);
        BlinkUtil.INSTANCE.stopBlink();
        canTick = 0;
        unSafeY = null;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onWorld(WorldEvent event) {
        safeBlockHighlight.release();
        lastSafeBlock = null;
        canTick = 0;
        if (unSafeY != null) {
            BlinkUtil.INSTANCE.sync(true, false);
            BlinkUtil.INSTANCE.stopBlink();
            unSafeY = null;
        }
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        updateSafeBlockHighlight();
        if (player == null || canTick > 0 || !EntityExtension.isFallingToVoid(player) || player.tickCount <= 50 || player.getAbilities().flying || player.isShiftKeyDown() || player.isSpectator() || player.isInWater()) {
            if (unSafeY != null) {
                BlinkUtil.INSTANCE.sync(true, false);
                BlinkUtil.INSTANCE.stopBlink();
                unSafeY = null;
            }
            if (canTick > 0)
                canTick--;
            return;
        }

        if (unSafeY == null)
            unSafeY = player.blockPosition().getY() - distance.get();

        BlinkUtil.INSTANCE.doBlink();
        if (player.blockPosition().getY() <= unSafeY) {
            BlinkUtil.INSTANCE.sync(false, true);
            if (stopXZ.get())
                MovementUtil.stopMoving();
        }
    }

    private void updateSafeBlockHighlight() {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) {
            safeBlockHighlight.release();
            return;
        }

        boolean overVoid = EntityExtension.isFallingToVoid(player);
        if (player.onGround() && !overVoid) {
            BlockPos below = player.getOnPos();
            if (!level.getBlockState(below).isAir())
                lastSafeBlock = below.immutable();
        }

        if (lastSafeBlock != null && !player.onGround() && overVoid && !player.getAbilities().flying && !player.isSpectator())
            safeBlockHighlight.setTarget(lastSafeBlock);
        else
            safeBlockHighlight.release();
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        var player = mc.player;
        if (player == null || player.tickCount <= 50) return;

        if (event.packet instanceof ClientboundPlayerPositionPacket && BlinkUtil.INSTANCE.getBlinking() && unSafeY != null) {
            canTick = 80;
            Client.notificationManager.addNotification("Temporary disabled", "AntiVoid flagged (wait for " + canTick + "s to re-activate)");
            BlinkUtil.INSTANCE.sync(true);
            BlinkUtil.INSTANCE.stopBlink();
            if (stopXZ.get())
                MovementUtil.stopMoving();
            unSafeY = null;
        }
    }
}
