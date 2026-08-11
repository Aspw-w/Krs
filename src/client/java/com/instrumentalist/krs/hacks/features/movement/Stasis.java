package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.packet.BlinkUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class Stasis extends Module {
    @Setting
    private final BooleanValue stopX = new BooleanValue("Stop X", true);

    @Setting
    private final BooleanValue stopY = new BooleanValue("Stop Y", true);

    @Setting
    private final BooleanValue stopZ = new BooleanValue("Stop Z", true);

    @Setting
    private final BooleanValue cancelPlayerMovement = new BooleanValue("Cancel Player Movement", true);

    private int ticksSinceMotionRefresh;
    private Vec3 storedMovement = Vec3.ZERO;

    public Stasis() {
        super("Stasis", ModuleCategory.Movement, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        if (mc.level == null) return;

        BlinkUtil.INSTANCE.setLimiter(false);
        ticksSinceMotionRefresh = 0;

        LocalPlayer player = mc.player;
        if (player == null) return;

        player.setDeltaMovement(Vec3.ZERO);
        storedMovement = player.getDeltaMovement();
    }

    @Override
    public void onDisable() {
        resetMotionState();

        if (mc.level != null) {
            BlinkUtil.INSTANCE.setLimiter(false);
        }
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (cancelPlayerMovement.get()) {
            player.setDeltaMovement(Vec3.ZERO);
            return;
        }

        Vec3 currentMovement = player.getDeltaMovement();
        refreshStoredMovement(currentMovement);
        player.setDeltaMovement(applyAxisLocks(currentMovement));
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (mc.level == null) return;

        if (cancelPlayerMovement.get() && event.packet instanceof ServerboundMovePlayerPacket) {
            cancelMovementPacket(event);
            return;
        }

        if (ticksSinceMotionRefresh != 0) {
            event.cancel();
        }
    }

    private void refreshStoredMovement(Vec3 currentMovement) {
        ticksSinceMotionRefresh++;
        if (ticksSinceMotionRefresh < 15) return;

        ticksSinceMotionRefresh = 0;
        storedMovement = currentMovement;
    }

    private Vec3 applyAxisLocks(Vec3 currentMovement) {
        return new Vec3(
                stopX.get() ? storedMovement.x : currentMovement.x,
                stopY.get() ? storedMovement.y : currentMovement.y,
                stopZ.get() ? storedMovement.z : currentMovement.z
        );
    }

    private void cancelMovementPacket(SendPacketEvent event) {
        LocalPlayer player = mc.player;
        if (player != null) {
            Vec3 movement = player.getDeltaMovement();
            player.setDeltaMovement(movement.x, 0.0, movement.z);
            BlinkUtil.INSTANCE.setLimiter(true);
        }

        event.cancel();
    }

    private void resetMotionState() {
        ticksSinceMotionRefresh = 0;
        storedMovement = Vec3.ZERO;
    }
}
