package com.instrumentalist.krs.hacks.features.player;



import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.math.MSTimer;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.pathfinder.MainPathFinder;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.protocol.Packet;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.ArrayList;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class LookTP extends Module {

    public LookTP() {
        super("Look TP", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, true);
    }

    @Setting
    private final BooleanValue tpOnGroundPacket = new BooleanValue(
            "TP On Ground Packet",
            true
    );

    @Setting
    private final BooleanValue clientsideTeleport = new BooleanValue(
            "Clientside Teleport",
            false
    );

    private final MSTimer tpTimer = new MSTimer();
    private ArrayList<Vec3> clientsidePath = null;
    private int clientsidePathIndex = 0;
    private int clientsidePathSize = 0;
    private int clientsideProgress = 0;
    private Vec3 clientsideLockPosition = null;
    private Vec3 clientsideTargetPosition = null;
    private boolean waitingForClientsideTeleport = false;

    @Override
    public String description() {
        return "Use with left alt + right click";
    }

    @Override
    public void onDisable() {
        resetClientsideTeleport();
    }

    @Override
    public void onEnable() {
        resetClientsideTeleport();
    }

    @Override
    public void onMotion(MotionEvent event) {
        if (mc.player == null || !isMovingServerSide()) return;

        if (clientsideLockPosition != null) {
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.player.setPos(clientsideLockPosition);
        }

        Vec3 path = clientsidePath.get(clientsidePathIndex);
        event.x = path.x;
        event.y = path.y;
        event.z = path.z;
        event.onGround = tpOnGroundPacket.get();
        event.isMoving = true;

        clientsidePathIndex++;
        clientsideProgress = clientsidePathSize <= 0 ? 0 : Math.min(100, Math.round((float) clientsidePathIndex / clientsidePathSize * 100f));
        if (clientsidePathIndex >= clientsidePath.size()) {
            clientsidePath = null;
            clientsidePathIndex = 0;
            waitingForClientsideTeleport = true;
        }
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;

        if (waitingForClientsideTeleport) {
            if (clientsideTargetPosition != null) {
                mc.player.setDeltaMovement(Vec3.ZERO);
                mc.player.setPos(clientsideTargetPosition);
            }

            resetClientsideTeleport();
            Client.notificationManager.addNotification("Success", "Teleported!");
            tpTimer.reset();
            return;
        }

        if (isMovingServerSide()) {
            if (clientsideLockPosition != null) {
                mc.player.setDeltaMovement(Vec3.ZERO);
                mc.player.setPos(clientsideLockPosition);
            }
            return;
        }

        if (tpTimer.hasTimePassed(200L) && InputConstants.isKeyDown(Keys.KEY_LEFT_ALT) && mc.options.keyUse.isDown()) {
            Vec3 eyePosition = mc.player.getEyePosition(0.1f);
            Vec3 lookVector = mc.player.getViewVector(0.1f);
            Vec3 targetPosition = eyePosition.add(lookVector.scale(10000.0));
            ClipContext context = new ClipContext(eyePosition, targetPosition, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player);

            BlockHitResult posResult = mc.level.clip(context);
            if (mc.level.getBlockState(posResult.getBlockPos()).isAir()) return;

            ArrayList<Vec3> paths;
            paths = MainPathFinder.computePath(mc.player.position(), posResult.getLocation());

            if (paths.isEmpty()) {
                Client.notificationManager.addNotification("Look TP", "Failed to teleport");
                tpTimer.reset();
                return;
            }

            if (clientsideTeleport.get()) {
                clientsidePath = new ArrayList<>(paths);
                clientsidePathIndex = 0;
                clientsidePathSize = clientsidePath.size();
                clientsideProgress = 0;
                clientsideLockPosition = mc.player.position();
                clientsideTargetPosition = posResult.getLocation();
                waitingForClientsideTeleport = false;
                mc.player.setDeltaMovement(Vec3.ZERO);
                Client.notificationManager.addNotification("Look TP", "Teleporting...");
                tpTimer.reset();
                return;
            }

            for (Vec3 path : paths) {
                PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(path.x, path.y, path.z, tpOnGroundPacket.get(), mc.player.horizontalCollision));
            }

            mc.player.setPos(posResult.getLocation());

            Client.notificationManager.addNotification("Success", "Teleported!");
            tpTimer.reset();
        }
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (mc.player == null) return;

        Packet<?> packet = event.packet;

        if ((isMovingServerSide() || waitingForClientsideTeleport) && packet instanceof ClientboundPlayerPositionPacket) {
            resetClientsideTeleport();
            Client.notificationManager.addNotification("Look TP", "Failed to teleport");
            tpTimer.reset();
        }
    }

    public boolean shouldStopClientInput() {
        return tempEnabled && (isMovingServerSide() || waitingForClientsideTeleport);
    }

    public boolean isClientsideTeleporting() {
        return isMovingServerSide() || waitingForClientsideTeleport;
    }

    public int getTeleportProgressPercent() {
        return isClientsideTeleporting() ? clientsideProgress : 0;
    }

    private boolean isMovingServerSide() {
        return clientsidePath != null && clientsidePathIndex < clientsidePath.size();
    }

    private void resetClientsideTeleport() {
        clientsidePath = null;
        clientsidePathIndex = 0;
        clientsidePathSize = 0;
        clientsideProgress = 0;
        clientsideLockPosition = null;
        clientsideTargetPosition = null;
        waitingForClientsideTeleport = false;
    }
}
