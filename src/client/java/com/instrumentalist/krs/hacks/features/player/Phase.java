package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.BlockEvent;
import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.events.guards.BlockEventCollisionGuard;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.math.TickTimer;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class Phase extends Module {
    private static final double VANILLA_SERVER_COLLISION_EPSILON = 1.0E-5;
    private static final double VANILLA_ENTRY_FRACTION = 0.75;
    private static final double VANILLA_CONTACT_SEARCH_DISTANCE = 0.25;
    private static final int VANILLA_CONTACT_SEARCH_ITERATIONS = 24;
    private static final double VANILLA_SCAN_STEP = 0.03125;
    private static final double VANILLA_MAX_DISTANCE = 8.0;
    private static final int VANILLA_PENDING_TICKS = 10;
    private static final int VANILLA_CORRECTION_COOLDOWN_TICKS = 5;

    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Vanilla", "NCP", "AAC 4", "Hypixel", "Intave"}, "NCP");

    private boolean isClipping = false;
    private final TickTimer phaseTimer = new TickTimer();
    private Float cachedDirection = null;
    private boolean mining = false;
    private boolean vanillaClipping = false;
    private int vanillaPendingTicks = 0;
    private int vanillaCorrectionCooldown = 0;

    public Phase() {
        super("Phase", ModuleCategory.Player, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onDisable() {
        resetVanillaPhase();
        isClipping = false;
        phaseTimer.reset();
        cachedDirection = null;
        mining = false;

        var player = mc.player;
        if (player == null) return;

        switch (mode.get().toLowerCase(Locale.ROOT)) {
            case "ncp" -> TimerUtil.reset();
            case "aac 4" -> {
                double x = player.getX();
                double y = player.getY();
                double z = player.getZ();
                boolean horizontalCollision = player.horizontalCollision;
                PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(x, y - 0.00000001, z, false, horizontalCollision));
                PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(x, y - 1, z, false, horizontalCollision));
            }
        }
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        if (mode.get().equalsIgnoreCase("aac 4")) toggle();
    }

    @Override
    public void onWorld(WorldEvent event) {
        resetVanillaPhase();
        isClipping = false;
        phaseTimer.reset();
        cachedDirection = null;
    }

    @Override
    public void onBlock(BlockEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (mode.get().equalsIgnoreCase("hypixel") && isClipping && event.blockPos.getY() != player.blockPosition().below().getY())
            event.cancel();
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (mode.get().equalsIgnoreCase("vanilla")
                && vanillaClipping
                && event.packet instanceof ClientboundPlayerPositionPacket) {

            resetVanillaPhase();
            vanillaCorrectionCooldown = VANILLA_CORRECTION_COOLDOWN_TICKS;
        }
    }

    @Override
    public void onMotion(MotionEvent event) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) return;

        if (mode.get().equalsIgnoreCase("hypixel")) {
            if (!player.horizontalCollision && !isClipping) return;

            phaseTimer.update();

            if (phaseTimer.hasTimePassed(3)) {
                if (phaseTimer.hasTimePassed(20)) {
                    isClipping = false;
                    phaseTimer.reset();
                    cachedDirection = null;
                }
            } else if (phaseTimer.hasTimePassed(1)) {
                float direction = cachedDirection == null ? (float) Math.toRadians((MovementUtil.getPlayerDirection() % 360 + 360) % 360.0) : cachedDirection;
                double sin = Mth.sin(direction);
                double cos = Mth.cos(direction);

                double xPos = event.x - sin * -0.25;
                double zPos = event.z + cos * -0.25;
                Double closestSurfaceY = null;

                var box = player.getBoundingBox();
                double playerX = box.getCenter().x - sin * -0.25;
                double playerZ = box.getCenter().z + cos * -0.25;
                double playerFeetY = box.minY;

                for (int y = (int) playerFeetY; y >= Math.max((int) playerFeetY - 10, level.getMinY()); y--) {
                    BlockPos blockPos = new BlockPos((int) Math.floor(playerX), y, (int) Math.floor(playerZ));
                    var blockState = level.getBlockState(blockPos);

                    if (!blockState.isAir()) {
                        var shape = blockState.getCollisionShape(level, blockPos);
                        double blockMaxY = Double.NEGATIVE_INFINITY;
                        for (var collisionBox : shape.toAabbs()) {
                            if (collisionBox.maxY > blockMaxY)
                                blockMaxY = collisionBox.maxY;
                        }
                        if (blockMaxY == Double.NEGATIVE_INFINITY)
                            blockMaxY = 1.0;
                        double surfaceY = y + blockMaxY;

                        if (surfaceY <= playerFeetY) {
                            closestSurfaceY = surfaceY;
                            break;
                        }
                    }
                }

                if (phaseTimer.hasTimePassed(2)) {
                    if (closestSurfaceY != null) {
                        event.onGround = true;
                        event.x = xPos;
                        event.y = closestSurfaceY - 0.07;
                        event.z = zPos;
                    }
                    phaseTimer.update();
                } else if (closestSurfaceY != null) {
                    MovementUtil.stopMoving();
                    isClipping = true;
                    if (cachedDirection == null)
                        cachedDirection = direction;
                }
            }
        }
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (mode.get().equalsIgnoreCase("vanilla")) {
            updateVanillaPhase(player);
            return;
        } else if (vanillaClipping || vanillaCorrectionCooldown > 0) {
            resetVanillaPhase();
        }

        if (mode.get().equalsIgnoreCase("intave")) {
            if (mc.options.keyAttack.isDown() && player.getXRot() > 80) {
                PacketUtil.sendPacket(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                        player.blockPosition().below(),
                        Direction.UP
                ));

                mining = true;
            } else {
                mining = false;
            }

            if (mining)
                player.position().y -= 0.0042;

            if (player.isShiftKeyDown()) {
                float distance = 0.005f;
                double rotation = Math.toRadians(player.getYRot());

                if (mc.options.keyUp.isDown()) move(player, rotation, distance, 1, 1);
                else if (mc.options.keyDown.isDown()) move(player, rotation, -distance, 1, -1);
                else if (mc.options.keyLeft.isDown()) move(player, rotation, distance, -1, 1);
                else if (mc.options.keyRight.isDown()) move(player, rotation, -distance, -1, -1);
            }
            return;
        }

        if (mode.get().equalsIgnoreCase("ncp")) {
            if (player.horizontalCollision) isClipping = true;
            if (!isClipping) return;

            phaseTimer.update();

            if (phaseTimer.hasTimePassed(3)) {
                MovementUtil.stopMoving();
                TimerUtil.reset();
                phaseTimer.reset();
                isClipping = false;
                cachedDirection = null;
            } else if (phaseTimer.hasTimePassed(1)) {
                double offset = phaseTimer.hasTimePassed(2) ? 1.7 : 0.06;
                float direction = cachedDirection == null ? (float) Math.toRadians((MovementUtil.getPlayerDirection() % 360 + 360) % 360.0) : cachedDirection;
                double sin = Mth.sin(direction);
                double cos = Mth.cos(direction);

                Vec3 newPos = new Vec3(
                        player.getX() + (-sin * offset),
                        player.getY(),
                        player.getZ() + (cos * offset)
                );

                TimerUtil.timerSpeed = 0.3f;
                MovementUtil.stopMoving();
                player.setPos(newPos.x, newPos.y, newPos.z);
                if (cachedDirection == null)
                    cachedDirection = direction;
            }
        }
    }

    private void move(LocalPlayer player, double rotation, float distance, int xMultiplier, int zMultiplier) {
        double xx = Math.cos(rotation) * distance * xMultiplier;
        double zz = Math.sin(rotation) * distance * zMultiplier;

        player.setPos(player.getX() + xx, player.getY(), player.getZ() + zz);
    }

    private void updateVanillaPhase(LocalPlayer player) {
        if (mc.level == null || player.isPassenger() || player.isSpectator()) {
            resetVanillaPhase();
            return;
        }

        if (vanillaCorrectionCooldown > 0) {
            vanillaCorrectionCooldown--;
            return;
        }

        if (vanillaClipping) {
            if (--vanillaPendingTicks <= 0)
                resetVanillaPhase();
            return;
        }

        if (!player.horizontalCollision || !MovementUtil.isMoving())
            return;

        Vec3 movementDirection = MovementUtil.getMovementVector();
        if (movementDirection.horizontalDistanceSqr() == 0.0)
            return;

        VanillaPhasePath path = findVanillaPath(player, movementDirection);
        if (path == null)
            return;

        boolean onGround = player.onGround();
        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(
                path.boundaryPosition.x,
                path.boundaryPosition.y,
                path.boundaryPosition.z,
                onGround,
                player.horizontalCollision
        ));
        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(
                path.entryPosition.x,
                path.entryPosition.y,
                path.entryPosition.z,
                onGround,
                true
        ));
        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(
                path.exitPosition.x,
                path.exitPosition.y,
                path.exitPosition.z,
                onGround,
                false
        ));

        MovementUtil.stopMoving();
        player.setPos(path.exitPosition.x, path.exitPosition.y, path.exitPosition.z);
        vanillaClipping = true;
        vanillaPendingTicks = VANILLA_PENDING_TICKS;
    }

    private VanillaPhasePath findVanillaPath(LocalPlayer player, Vec3 movementDirection) {
        AABB playerBox = player.getBoundingBox();
        Vec3 playerPosition = player.position();

        if (intersectsOriginalCollision(player, playerBox)) {
            Vec3 exitPosition = findVanillaExit(player, playerBox, playerPosition, movementDirection, 0.0);
            return exitPosition == null ? null : new VanillaPhasePath(playerPosition, playerPosition, exitPosition);
        }

        CollisionBoundary exactBoundary = findCollisionBoundary(player, playerBox, movementDirection);
        CollisionBoundary deflatedBoundary = findCollisionBoundary(
                player,
                playerBox.deflate(VANILLA_SERVER_COLLISION_EPSILON),
                movementDirection
        );
        if (exactBoundary == null || deflatedBoundary == null)
            return null;

        double entryWindow = deflatedBoundary.collidingDistance - exactBoundary.collidingDistance;
        if (entryWindow <= 0.0)
            return null;

        double entryDistance = exactBoundary.collidingDistance + entryWindow * VANILLA_ENTRY_FRACTION;
        AABB entryBox = moveBox(playerBox, movementDirection, entryDistance);

        if (!intersectsOriginalCollision(player, entryBox)
                || intersectsOriginalCollision(player, entryBox.deflate(VANILLA_SERVER_COLLISION_EPSILON)))
            return null;

        Vec3 exitPosition = findVanillaExit(
                player,
                playerBox,
                playerPosition,
                movementDirection,
                exactBoundary.collidingDistance
        );
        if (exitPosition == null)
            return null;

        Vec3 boundaryPosition = playerPosition.add(movementDirection.scale(exactBoundary.clearDistance));
        Vec3 entryPosition = playerPosition.add(movementDirection.scale(entryDistance));
        return new VanillaPhasePath(boundaryPosition, entryPosition, exitPosition);
    }

    private CollisionBoundary findCollisionBoundary(LocalPlayer player, AABB playerBox, Vec3 movementDirection) {
        double clearDistance = 0.0;
        double collidingDistance = VANILLA_SCAN_STEP;

        while (collidingDistance <= VANILLA_CONTACT_SEARCH_DISTANCE
                && !intersectsOriginalCollision(player, moveBox(playerBox, movementDirection, collidingDistance))) {
            clearDistance = collidingDistance;
            collidingDistance += VANILLA_SCAN_STEP;
        }

        if (collidingDistance > VANILLA_CONTACT_SEARCH_DISTANCE)
            return null;

        for (int i = 0; i < VANILLA_CONTACT_SEARCH_ITERATIONS; i++) {
            double middle = (clearDistance + collidingDistance) * 0.5;
            if (intersectsOriginalCollision(player, moveBox(playerBox, movementDirection, middle)))
                collidingDistance = middle;
            else
                clearDistance = middle;
        }

        return new CollisionBoundary(clearDistance, collidingDistance);
    }

    private Vec3 findVanillaExit(LocalPlayer player, AABB playerBox, Vec3 playerPosition,
                                 Vec3 movementDirection, double startDistance) {
        double firstDistance = Math.max(VANILLA_SCAN_STEP, startDistance + VANILLA_SCAN_STEP);
        for (double distance = firstDistance; distance <= VANILLA_MAX_DISTANCE; distance += VANILLA_SCAN_STEP) {
            AABB candidateBox = moveBox(playerBox, movementDirection, distance);
            if (!intersectsOriginalCollision(player, candidateBox))
                return playerPosition.add(movementDirection.scale(distance));
        }

        return null;
    }

    private AABB moveBox(AABB box, Vec3 direction, double distance) {
        return box.move(direction.x * distance, 0.0, direction.z * distance);
    }

    private boolean intersectsOriginalCollision(LocalPlayer player, AABB boundingBox) {
        if (mc.level == null)
            return false;

        int minX = Mth.floor(boundingBox.minX);
        int maxX = Mth.floor(boundingBox.maxX);
        int minY = Math.max(mc.level.getMinY(), Mth.floor(boundingBox.minY));
        int maxY = Math.min(mc.level.getMaxY() - 1, Mth.floor(boundingBox.maxY));
        int minZ = Mth.floor(boundingBox.minZ);
        int maxZ = Mth.floor(boundingBox.maxZ);
        CollisionContext context = CollisionContext.of(player);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    var blockState = mc.level.getBlockState(blockPos);
                    if (blockState.isAir())
                        continue;

                    var shape = BlockEventCollisionGuard.getOriginalCollisionShape(
                            blockState,
                            mc.level,
                            blockPos,
                            context
                    );
                    for (AABB localBox : shape.toAabbs()) {
                        if (localBox.move(blockPos).intersects(boundingBox))
                            return true;
                    }
                }
            }
        }

        return false;
    }

    private void resetVanillaPhase() {
        vanillaClipping = false;
        vanillaPendingTicks = 0;
        vanillaCorrectionCooldown = 0;
    }

    private record CollisionBoundary(double clearDistance, double collidingDistance) {
    }

    private record VanillaPhasePath(Vec3 boundaryPosition, Vec3 entryPosition, Vec3 exitPosition) {
    }
}
