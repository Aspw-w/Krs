package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.events.features.AttackEvent;
import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.FakePlayerEntity;
import com.instrumentalist.krs.utils.math.MSTimer;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.IntValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Optional;

public class Backtrack extends Module {
    private static final int MAX_STORED_PACKETS = 512;

    @Setting
    private final IntValue trackDelay = new IntValue("Track Delay", 200, 0, 2000, "ms");

    private final ArrayDeque<Packet<?>> storagePackets = new ArrayDeque<>();
    private final ArrayDeque<EntityPacketLoc> storageEntityMove = new ArrayDeque<>();
    private final Object storageLock = new Object();
    private final VecDeltaCodec simulatedPositionCodec = new VecDeltaCodec();
    private final MSTimer timer = new MSTimer();

    private Entity lastAttackedEntity = null;
    private int lastAttackedEntityId = -1;
    private Vec3 simulatedPosition = null;
    private Vec3 simulatedDeltaMovement = Vec3.ZERO;
    private float simulatedYaw = 0.0F;
    private float simulatedPitch = 0.0F;
    private volatile boolean needFreeze = false;

    public Backtrack() {
        super("Backtrack", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    public boolean isBacktracking() {
        return tempEnabled && needFreeze;
    }

    @Override
    public String tag() {
        return trackDelay.get() + "ms";
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        releasePackets();
        clearTracking();
    }

    @Override
    public void onWorld(WorldEvent event) {
        synchronized (storageLock) {
            storagePackets.clear();
            storageEntityMove.clear();
        }
        clearTracking();
    }

    @Override
    public void onAttack(AttackEvent event) {
        Entity target = event.entity;
        if (target != null && target != mc.player && !(target instanceof FakePlayerEntity)) {
            setTarget(target);
        }
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (event == null || event.packet == null || mc.player == null || mc.level == null)
            return;

        try {
            if (!validateTarget())
                return;

            Packet<?> packet = event.packet;
            if (packet instanceof ClientboundMoveEntityPacket movePacket) {
                handleEntityMovePacket(event, movePacket);
            } else if (packet instanceof ClientboundTeleportEntityPacket teleportPacket) {
                handleTeleportPacket(event, teleportPacket);
            } else if (packet instanceof ClientboundEntityPositionSyncPacket positionSyncPacket) {
                handlePositionSyncPacket(event, positionSyncPacket);
            }
        } catch (Exception ignored) {
            releasePackets();
        }
    }

    @Override
    public void onMotion(MotionEvent event) {
        if (mc.player == null || mc.level == null || !needFreeze)
            return;

        try {
            if (!validateTarget())
                return;

            doSmoothRelease();
            if (!needFreeze)
                return;

            Entity target = lastAttackedEntity;
            Vec3 position = getTrackedPosition(target);
            if (target == null || position == null) {
                releasePackets();
                return;
            }

            if (isServerPositionInReleaseRange(position)) {
                releasePackets();
                return;
            }

            if (timer.currentTime() < Math.clamp(trackDelay.get(), 0, 2000))
                return;

            AABB targetBox = makeBoundingBox(target, position);
            double range = getLookingTargetRange(targetBox);
            if (range == Double.MAX_VALUE) {
                range = Math.sqrt(targetBox.distanceToSqr(mc.player.getEyePosition())) + 0.075D;
            }

            if (range <= 1.5D || timer.hasTimePassed(100L) && range >= 1.5D) {
                releasePackets();
            }
        } catch (Exception ignored) {
            releasePackets();
        }
    }

    private void handleEntityMovePacket(ReceivedPacketEvent event, ClientboundMoveEntityPacket packet) {
        Entity entity = packet.getEntity(mc.level);
        if (!(entity instanceof Player) || !isTarget(entity))
            return;

        ensureSimulationInitialized(entity);

        AABB beforeBox = entity.getBoundingBox();
        Vec3 afterPosition = simulateMovePacket(entity, packet);
        AABB afterBox = makeBoundingBox(entity, afterPosition);
        double beforeRange = Math.sqrt(beforeBox.distanceToSqr(mc.player.getEyePosition()));
        double afterRange = Math.sqrt(afterBox.distanceToSqr(mc.player.getEyePosition()));

        if (!needFreeze && packet.hasPosition() && beforeRange <= 8.0D
                && afterRange >= 1.5D
                && afterRange <= 7.5D
                && afterRange > beforeRange + 0.02D
                && (!(entity instanceof Player player) || player.hurtTime <= getCalculatedMaxHurtTime())) {
            startFreeze();
            storePacket(event, packet, entity, afterPosition);
            return;
        }

        if (needFreeze && beforeRange > 8.0D && afterRange <= beforeRange) {
            releasePackets();
            return;
        }

        if (needFreeze) {
            storePacket(event, packet, entity, afterPosition);
        }
    }

    private void handleTeleportPacket(ReceivedPacketEvent event, ClientboundTeleportEntityPacket packet) {
        Entity entity = mc.level.getEntity(packet.id());
        if (!(entity instanceof Player) || !isTarget(entity))
            return;

        ensureSimulationInitialized(entity);
        PositionMoveRotation current = new PositionMoveRotation(simulatedPosition, simulatedDeltaMovement, simulatedYaw, simulatedPitch);
        PositionMoveRotation next = PositionMoveRotation.calculateAbsolute(current, packet.change(), packet.relatives());
        updateSimulation(next.position(), next.deltaMovement(), next.yRot(), next.xRot());

        if (needFreeze) {
            storePacket(event, packet, entity, next.position());
        }
    }

    private void handlePositionSyncPacket(ReceivedPacketEvent event, ClientboundEntityPositionSyncPacket packet) {
        Entity entity = mc.level.getEntity(packet.id());
        if (!(entity instanceof Player) || !isTarget(entity))
            return;

        // Position sync packets no longer carry a velocity since 26.3, so keep the simulated one.
        Vec3 position = packet.position().endPosition();
        updateSimulation(position, simulatedDeltaMovement, packet.yRot(), packet.xRot());

        if (needFreeze) {
            storePacket(event, packet, entity, position);
        }
    }

    private Vec3 simulateMovePacket(Entity entity, ClientboundMoveEntityPacket packet) {
        Vec3 position = simulatedPosition != null ? simulatedPosition : entity.position();

        if (packet.hasPosition()) {
            position = packet.getPositionDelta().decode(simulatedPositionCodec).endPosition();
            simulatedPositionCodec.setBase(position);
            simulatedPosition = position;
        }

        if (packet.hasRotation()) {
            simulatedYaw = packet.getYRot();
            simulatedPitch = packet.getXRot();
        }

        return position;
    }

    private void storePacket(ReceivedPacketEvent event, Packet<?> packet, Entity entity, Vec3 position) {
        boolean overflow;
        synchronized (storageLock) {
            if (!needFreeze)
                return;
            overflow = storagePackets.size() >= MAX_STORED_PACKETS;

            if (!overflow) {
                event.cancel();
                storagePackets.add(packet);
                storageEntityMove.add(new EntityPacketLoc(entity, position));
            }
        }

        if (overflow) {
            releasePackets();
            return;
        }

        if (needFreeze && isServerPositionInReleaseRange(position))
            releasePackets();
    }

    private void startFreeze() {
        timer.reset();
        needFreeze = true;
    }

    private void releasePackets() {
        ArrayDeque<Packet<?>> pendingPackets;
        synchronized (storageLock) {
            pendingPackets = new ArrayDeque<>(storagePackets);
            storagePackets.clear();
            storageEntityMove.clear();
            needFreeze = false;
        }

        while (!pendingPackets.isEmpty())
            PacketUtil.handlePacket(pendingPackets.removeFirst());
    }

    private void clearTracking() {
        synchronized (storageLock) {
            storageEntityMove.clear();
        }
        resetTarget();
        simulatedPosition = null;
        simulatedDeltaMovement = Vec3.ZERO;
        simulatedYaw = 0.0F;
        simulatedPitch = 0.0F;
        needFreeze = false;
    }

    private void setTarget(Entity target) {
        if (lastAttackedEntityId != -1 && lastAttackedEntityId != target.getId()) {
            releasePackets();
        }

        lastAttackedEntity = target;
        lastAttackedEntityId = target.getId();
        initializeSimulation(target);
    }

    private void resetTarget() {
        lastAttackedEntity = null;
        lastAttackedEntityId = -1;
    }

    private boolean isTarget(Entity entity) {
        return entity != null && entity.getId() == lastAttackedEntityId && entity == lastAttackedEntity;
    }

    private boolean validateTarget() {
        if (lastAttackedEntity == null || mc.player == null || mc.level == null) {
            if (needFreeze)
                releasePackets();
            resetTarget();
            return false;
        }

        Entity worldEntity = mc.level.getEntity(lastAttackedEntityId);
        if (worldEntity != lastAttackedEntity
                || lastAttackedEntity.isRemoved()
                || !lastAttackedEntity.isAlive()
                || mc.player.distanceTo(lastAttackedEntity) > 10.0F) {
            if (needFreeze)
                releasePackets();
            resetTarget();
            return false;
        }

        return true;
    }

    private void doSmoothRelease() {
        Entity target = lastAttackedEntity;
        if (target == null) {
            releasePackets();
            return;
        }

        boolean found = false;
        synchronized (storageLock) {
            for (EntityPacketLoc loc : storageEntityMove) {
                if (target == loc.entity) {
                    found = true;
                    break;
                }
            }
        }

        if (!found)
            releasePackets();
    }

    private void ensureSimulationInitialized(Entity entity) {
        if (simulatedPosition == null)
            initializeSimulation(entity);
    }

    private void initializeSimulation(Entity entity) {
        Vec3 base = entity.getPositionCodec().getBase();
        if (base == null)
            base = entity.position();

        simulatedPositionCodec.setBase(base);
        simulatedPosition = entity.position();
        simulatedDeltaMovement = entity.getDeltaMovement();
        simulatedYaw = entity.getYRot();
        simulatedPitch = entity.getXRot();
    }

    private void updateSimulation(Vec3 position, Vec3 deltaMovement, float yaw, float pitch) {
        simulatedPosition = position;
        simulatedDeltaMovement = deltaMovement;
        simulatedYaw = yaw;
        simulatedPitch = pitch;
        simulatedPositionCodec.setBase(position);
    }

    private Vec3 getTrackedPosition(Entity entity) {
        if (entity == null)
            return null;

        synchronized (storageLock) {
            Iterator<EntityPacketLoc> iterator = storageEntityMove.descendingIterator();
            while (iterator.hasNext()) {
                EntityPacketLoc loc = iterator.next();
                if (loc.entity == entity)
                    return loc.position;
            }
        }

        return simulatedPosition;
    }

    private boolean isServerPositionInReleaseRange(Vec3 position) {
        double releaseRange = 3.5D;
        return mc.player != null && position != null
                && position.distanceToSqr(mc.player.position()) <= releaseRange * releaseRange;
    }

    private AABB makeBoundingBox(Entity entity, Vec3 position) {
        double halfWidth = entity.getBbWidth() / 2.0D;
        double minY = position.y - 0.1D;
        double maxY = position.y + entity.getBbHeight() + 0.1D;
        return new AABB(
                position.x - halfWidth,
                minY,
                position.z - halfWidth,
                position.x + halfWidth,
                maxY,
                position.z + halfWidth
        );
    }

    private double getLookingTargetRange(AABB box) {
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 reachVec = eyePos.add(mc.player.getViewVector(1.0F).scale(8.0D));
        Optional<Vec3> hit = box.clip(eyePos, reachVec);
        return hit.map(eyePos::distanceTo).orElse(Double.MAX_VALUE);
    }

    private int getCalculatedMaxHurtTime() {
        return 6;
    }

    private static class EntityPacketLoc {
        final Entity entity;
        final Vec3 position;

        EntityPacketLoc(Entity entity, Vec3 position) {
            this.entity = entity;
            this.position = position;
        }
    }
}
