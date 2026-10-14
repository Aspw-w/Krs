package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.events.features.AttackEvent;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class KillEffect extends Module {
    private static final AtomicInteger NEXT_CLIENT_ENTITY_ID = new AtomicInteger(-1);
    private final Map<UUID, TrackedTarget> trackedTargets = new HashMap<>();
    private final Map<Integer, UUID> trackedEntityIds = new HashMap<>();

    public KillEffect() {
        super("Kill Effect", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public void onDisable() {
        clearTrackedTargets();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onWorld(WorldEvent event) {
        clearTrackedTargets();
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (mc.player == null || mc.level == null) return;

        Entity entity = event.entity;
        if (!(entity instanceof LivingEntity livingEntity) || entity == mc.player) return;

        TrackedTarget trackedTarget = new TrackedTarget(livingEntity);
        trackedTargets.put(trackedTarget.uuid, trackedTarget);
        trackedEntityIds.put(trackedTarget.entityId, trackedTarget.uuid);
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (event == null || event.packet == null)
            return;

        Packet<?> packet = event.packet;
        if (mc.isSameThread()) {
            handleReceivedPacket(packet);
        } else {
            mc.execute(() -> handleReceivedPacket(packet));
        }
    }

    private void handleReceivedPacket(Packet<?> packet) {
        if (mc.level == null) return;

        if (packet instanceof ClientboundEntityEventPacket entityEventPacket
                && entityEventPacket.getEventId() == 3) {
            Entity entity = entityEventPacket.getEntity(mc.level);
            if (!(entity instanceof LivingEntity livingEntity)) return;

            TrackedTarget trackedTarget = removeTrackedTarget(livingEntity.getUUID());
            if (trackedTarget != null)
                spawnLightning(trackedTarget.currentPosition());
        } else if (packet instanceof ClientboundRemoveEntitiesPacket removeEntitiesPacket) {
            for (int entityId : removeEntitiesPacket.entityIds()) {
                TrackedTarget trackedTarget = removeTrackedTarget(entityId);
                if (trackedTarget == null) continue;

                Entity removedEntity = mc.level.getEntity(entityId);
                if (removedEntity != null)
                    trackedTarget.lastPosition = removedEntity.position();

                if (trackedTarget.trackedTicks <= 60)
                    spawnLightning(trackedTarget.currentPosition());
            }
        }
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) {
            clearTrackedTargets();
            return;
        }

        Iterator<Map.Entry<UUID, TrackedTarget>> iterator = trackedTargets.entrySet().iterator();
        while (iterator.hasNext()) {
            TrackedTarget trackedTarget = iterator.next().getValue();
            LivingEntity entity = trackedTarget.entity;

            if (!entity.isRemoved())
                trackedTarget.lastPosition = entity.position();

            if (isKilled(entity)) {
                spawnLightning(trackedTarget.currentPosition());
                iterator.remove();
                trackedEntityIds.remove(trackedTarget.entityId);
                continue;
            }

            trackedTarget.trackedTicks++;
            if (trackedTarget.trackedTicks > 80) {
                iterator.remove();
                trackedEntityIds.remove(trackedTarget.entityId);
            }
        }
    }

    private boolean isKilled(LivingEntity entity) {
        return entity.isDeadOrDying()
                || entity.getHealth() <= 0.0f
                || entity.getRemovalReason() == Entity.RemovalReason.KILLED;
    }

    private void spawnLightning(Vec3 position) {
        if (!mc.isSameThread()) {
            mc.execute(() -> spawnLightning(position));
            return;
        }

        if (mc.level == null) return;

        LightningBolt lightningBolt = EntityTypes.LIGHTNING_BOLT.create(mc.level, EntitySpawnReason.TRIGGERED);
        if (lightningBolt == null) return;

        lightningBolt.setId(NEXT_CLIENT_ENTITY_ID.getAndDecrement());
        lightningBolt.setVisualOnly(true);
        lightningBolt.setSilent(true);
        lightningBolt.setPos(position);
        ThunderDetector.excludeLightningAt(position);
        mc.level.addEntity(lightningBolt);
    }

    private void clearTrackedTargets() {
        trackedTargets.clear();
        trackedEntityIds.clear();
    }

    private TrackedTarget removeTrackedTarget(UUID uuid) {
        TrackedTarget trackedTarget = trackedTargets.remove(uuid);
        if (trackedTarget != null)
            trackedEntityIds.remove(trackedTarget.entityId);

        return trackedTarget;
    }

    private TrackedTarget removeTrackedTarget(int entityId) {
        UUID uuid = trackedEntityIds.remove(entityId);
        if (uuid == null) return null;

        return trackedTargets.remove(uuid);
    }

    private static final class TrackedTarget {
        private final LivingEntity entity;
        private final UUID uuid;
        private final int entityId;
        private Vec3 lastPosition;
        private int trackedTicks;

        private TrackedTarget(LivingEntity entity) {
            this.entity = entity;
            this.uuid = entity.getUUID();
            this.entityId = entity.getId();
            this.lastPosition = entity.position();
        }

        private Vec3 currentPosition() {
            return entity.isRemoved() ? lastPosition : entity.position();
        }
    }
}
