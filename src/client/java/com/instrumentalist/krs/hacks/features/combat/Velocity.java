package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.HandleInputEvent;
import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.hacks.features.player.Scaffold;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.move.InputUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.ClientInput;
import net.minecraft.util.Mth;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class Velocity extends Module {
    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Standard", "Hypixel NCP", "Jump Reset", "Prediction"}, "Standard");

    @Setting
    private final FloatValue jumpResetChance = new FloatValue("Jump Reset Chance", 100f, 0f, 100f, "%", this::isJumpResetMode);

    @Setting
    private final ListValue jumpResetCooldown = new ListValue("Jump Reset Cooldown", new String[]{"Delay", "Received Hits", "None"}, "Delay", this::isJumpResetMode);

    @Setting
    private final IntValue jumpResetTicksUntilJump = new IntValue("Jump Reset Until Jump", 2, 0, 20, "t", () -> isJumpResetMode() && jumpResetCooldown.get().equalsIgnoreCase("delay"));

    @Setting
    private final IntValue jumpResetHitsUntilJump = new IntValue("Jump Reset Hits Until Jump", 2, 0, 10, "x", () -> isJumpResetMode() && jumpResetCooldown.get().equalsIgnoreCase("received hits"));

    @Setting
    private final IntValue predictionDelay = new IntValue("Prediction Delay", 4, 0, 10, "t", this::isPredictionMode);

    private int jumpResetLimitUntilJump = 0;
    private boolean jumpResetFallDamage = false;

    private final ArrayDeque<Packet<?>> predictionDelayedPackets = new ArrayDeque<>();
    private boolean predictionForwardKeyForced = false;
    private boolean predictionDelaying = false;
    private boolean predictionForwardRequested = false;
    private boolean predictionAttackReduction = false;
    private boolean predictionKeysOverridden = false;
    private int predictionReduceTicks = 0;
    private int predictionElapsedDelayTicks = -1;
    private int predictionTicksSinceKnockback = -1;
    private float predictionOriginYaw = 0.0F;
    private float predictionOriginPitch = 0.0F;

    public Velocity() {
        super("Velocity", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onDisable() {
        resetJumpReset();
        resetPrediction(true);
    }

    @Override
    public void onEnable() {
        resetJumpReset();
        resetPrediction(false);
    }

    public static void hookJumpResetInput(ClientInput input) {
        Velocity velocity = ModuleManager.getModule(Velocity.class);
        if (velocity == null || !velocity.tempEnabled)
            return;

        velocity.handleJumpResetInput(input);
        velocity.handlePredictionInput(input);
    }

    private void handleJumpResetInput(ClientInput input) {
        var player = mc.player;
        if (input == null || player == null || player.input != input || !isJumpResetMode()) return;

        if (player.hurtTime != 9
                || !player.onGround()
                || !player.isSprinting()
                || jumpResetFallDamage
                || !isJumpResetCooldownOver()
                || !rollJumpResetChance()) {
            updateJumpResetLimit();
            return;
        }

        InputUtil.setJumping(input, true);
        jumpResetLimitUntilJump = 0;
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (isPredictionMode()) {
            handlePredictionPacket(event, player);
            return;
        }

        if (event.packet instanceof ClientboundSetEntityMotionPacket motionPacket && motionPacket.id() == player.getId()
                && shouldDropKnockback(motionPacket.movement()))
            event.cancel();
    }

    public static Optional<Vec3> hookExplosionKnockback(Optional<Vec3> knockback) {
        Velocity velocity = ModuleManager.getModule(Velocity.class);
        if (velocity == null || !velocity.tempEnabled || mc.player == null || knockback.isEmpty())
            return knockback;

        return velocity.shouldDropKnockback(knockback.get()) ? Optional.empty() : knockback;
    }

    private boolean shouldDropKnockback(Vec3 motion) {
        var player = mc.player;
        switch (mode.get().toLowerCase(Locale.ROOT)) {
            case "standard" -> {
                return true;
            }
            case "hypixel ncp" -> {
                if (ModuleManager.getModuleState(FlyModule.class))
                    return false;

                if (!ModuleManager.getModuleState(SpeedModule.class) || MovementUtil.fallTicks >= 10 || player.onGround())
                    MovementUtil.setVelocityY(motion.y);
                return true;
            }
            case "jump reset" -> jumpResetFallDamage = motion.x == 0.0 && motion.z == 0.0 && motion.y < 0.0;
        }

        return false;
    }

    @Override
    public void onMotion(MotionEvent event) {
        if (!isPredictionMode()) return;

        if (!event.post) {
            event.callInPost = true;
            return;
        }

        handlePredictionPostMotion();
    }

    @Override
    public void onHandleInput(HandleInputEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (!isPredictionMode()) {
            if (predictionDelaying || predictionKeysOverridden || predictionTicksSinceKnockback >= 0)
                resetPrediction(true);
            return;
        }

        if (predictionTicksSinceKnockback >= 0)
            predictionTicksSinceKnockback++;
        if (predictionElapsedDelayTicks >= 0)
            predictionElapsedDelayTicks++;

        if (predictionTicksSinceKnockback >= 10) {
            predictionAttackReduction = false;
            predictionForwardRequested = false;
            predictionTicksSinceKnockback = -1;
        }

        if (predictionTicksSinceKnockback >= predictionReduceTicks) {
            predictionAttackReduction = false;
            predictionForwardRequested = false;
        }

        updatePredictionKeys(player);
        if (predictionDelaying || predictionKeysOverridden)
            predictionForwardRequested = true;

        Entity attackTarget = predictionAttackTarget(player);
        Entity distantPlayer = farthestDistantPlayer(player);
        if ((attackTarget != null || distantPlayer != null) && predictionAttackReduction)
            predictionForwardRequested = true;

        if (!predictionAttackReduction || !player.isSprinting() && !player.wasSprinting)
            return;

        if (attackTarget != null) {
            sendPredictionAttack(player, attackTarget, isKillAuraActive() && attackTarget == KillAura.closestEntity);
        } else if (distantPlayer != null) {
            if (isOnHypixel())
                sendPredictionAttack(player, distantPlayer, false);
            else
                player.setDeltaMovement(player.getDeltaMovement().multiply(0.1D, 1.0D, 0.1D));
        }
    }

    @Override
    public void onWorld(WorldEvent event) {
        predictionDelayedPackets.clear();
        resetPrediction(false);
    }

    private void handlePredictionPacket(ReceivedPacketEvent event, LocalPlayer player) {
        Packet<?> packet = event.packet;
        if (packet instanceof ClientboundSystemChatPacket chatPacket && predictionTicksSinceKnockback >= 0) {
            String message = chatPacket.content().getString();
            if (message.contains("failed") && message.contains("Hitboxes")) {
                event.cancel();
                return;
            }
        }

        if (packet instanceof ClientboundSetEntityMotionPacket motionPacket
                && motionPacket.id() == player.getId()
                && !ModuleManager.getModuleState(FlyModule.class)) {
            Vec3 motion = motionPacket.movement();
            double horizontalSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            if (motion.y <= 0.0D || horizontalSpeed < 0.1D || player.onClimbable() || player.isInLiquid())
                return;

            Vec3 originDirection = new Vec3(-motion.x, 0.0D, -motion.z).normalize();
            predictionReduceTicks = computePredictionReduceTicks((int) (motion.x * 8000.0D), (int) (motion.z * 8000.0D));
            Vec3 originPosition = player.position().add(originDirection.x, 0.0D, originDirection.z);
            predictionOriginYaw = Client.rotationManager.getRotationsTo(originPosition.x, originPosition.y, originPosition.z)[0];
            predictionOriginPitch = Client.rotationManager.getRotationPitch();

            if (!predictionDelaying) {
                if (predictionDelay.get() != 0
                        && (hasPredictionTargetInRange(player) || farthestDistantPlayer(player) != null)
                        && !player.isSprinting()
                        && !player.wasSprinting) {
                    predictionDelaying = true;
                    predictionElapsedDelayTicks = 0;
                } else {
                    beginPredictionReduction(player);
                }
            }
        }

        if (predictionDelaying && !isPredictionDelayExempt(packet, player)) {
            event.cancel();
            predictionDelayedPackets.addLast(packet);
        }
    }

    private void handlePredictionPostMotion() {
        var player = mc.player;
        if (player == null || !predictionDelaying) return;

        if (predictionElapsedDelayTicks < predictionDelay.get()
                && !player.isSprinting()
                && !player.wasSprinting
                && !player.hasEffect(MobEffects.SLOWNESS)
                && !isLastSentOnGround(player)
                && (hasPredictionTargetInRange(player) || farthestDistantPlayer(player) != null))
            return;

        flushPredictionPackets();
        predictionDelaying = false;
        predictionElapsedDelayTicks = -1;
        beginPredictionReduction(player);
    }

    private void beginPredictionReduction(LocalPlayer player) {
        if (isLastSentOnGround(player) && !ModuleManager.getModuleState(Scaffold.class)) {
            rotateTowardsPredictionOrigin(player);
            predictionForwardRequested = true;
        }

        predictionAttackReduction = true;
        predictionTicksSinceKnockback = 0;
    }

    private void handlePredictionInput(ClientInput input) {
        var player = mc.player;
        if (input == null || player == null || player.input != input || !isPredictionMode()) return;
        if (!predictionKeysOverridden && !predictionDelaying && !predictionForwardRequested) return;

        Input keyPresses = input.keyPresses == null ? Input.EMPTY : input.keyPresses;
        if (keyPresses.forward()) return;

        InputUtil.applyInput(input, new Input(
                true,
                keyPresses.backward(),
                keyPresses.left(),
                keyPresses.right(),
                keyPresses.jump(),
                keyPresses.shift(),
                keyPresses.sprint()
        ));
    }

    private void updatePredictionKeys(LocalPlayer player) {
        if (mc.gui.screen() instanceof AbstractContainerScreen<?>)
            return;

        if (predictionTicksSinceKnockback >= 0 && !ModuleManager.getModuleState(Scaffold.class)) {
            predictionKeysOverridden = true;
            if (predictionTicksSinceKnockback <= 2 && isLastSentOnGround(player))
                mc.options.keyJump.setDown(true);

            if (predictionTicksSinceKnockback <= 3) {
                mc.options.keyUp.setDown(true);
                predictionForwardKeyForced = true;
            }
        }

        if (predictionTicksSinceKnockback >= 4 && predictionTicksSinceKnockback <= 9)
            restorePredictionKeys();
    }

    private void restorePredictionKeys() {
        if (!predictionKeysOverridden && !predictionForwardKeyForced)
            return;

        mc.options.keyJump.setDown(isPhysicalKeyDown(mc.options.keyJump));
        if (predictionForwardKeyForced) {
            mc.options.keyUp.setDown(isPhysicalKeyDown(mc.options.keyUp));
            predictionForwardKeyForced = false;
        }

        predictionKeysOverridden = false;
    }

    private static boolean isPhysicalKeyDown(KeyMapping keyMapping) {
        InputConstants.Key key = keyMapping.key;
        return key != null
                && key.getType() == InputConstants.Type.KEYBOARD
                && key.getValue() != InputConstants.UNKNOWN.getValue()
                && InputConstants.isKeyDown(key.getValue());
    }

    private void rotateTowardsPredictionOrigin(LocalPlayer player) {
        Entity closestPlayer = closestPlayerInRange(player, KillAura.attackRange.get());
        float pitch = predictionOriginPitch;
        if (closestPlayer != null) {
            Vec3 point = closestPointOnBox(player.getEyePosition(), closestPlayer.getBoundingBox());
            pitch = Client.rotationManager.getRotationsTo(point.x, point.y, point.z)[1];
        }

        Client.rotationManager.startRotation(predictionOriginYaw, pitch, 180.0F);
    }

    private void sendPredictionAttack(LocalPlayer player, Entity target, boolean renderSwing) {
        PacketUtil.sendPacket(new ServerboundAttackPacket(target.getId()));
        if (renderSwing)
            PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
        else
            PlayerUtil.INSTANCE.sendSwingPacket(InteractionHand.MAIN_HAND);

        player.setDeltaMovement(player.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
        player.setSprinting(false);
    }

    private Entity predictionAttackTarget(LocalPlayer player) {
        double range = KillAura.attackRange.get();
        if (isKillAuraActive()) {
            Entity target = KillAura.closestEntity;
            return isValidPredictionTarget(player, target, range)
                    && Client.rotationManager.canHitEntityAtRotation(target, Client.rotationManager.getRotationYaw(), Client.rotationManager.getRotationPitch(), range)
                    ? target
                    : null;
        }

        return crosshairPlayer(player, range);
    }

    private boolean hasPredictionTargetInRange(LocalPlayer player) {
        double range = KillAura.attackRange.get();
        if (isKillAuraActive())
            return isValidPredictionTarget(player, KillAura.closestEntity, range);

        return crosshairPlayer(player, range) != null;
    }

    private Entity crosshairPlayer(LocalPlayer player, double range) {
        if (mc.level == null) return null;

        float yaw = Client.rotationManager.getRotationYaw();
        float pitch = Client.rotationManager.getRotationPitch();
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player other : mc.level.players()) {
            if (!isValidPredictionTarget(player, other, range)
                    || !Client.rotationManager.canHitEntityAtRotation(other, yaw, pitch, range))
                continue;

            double distance = distanceToBox(player, other);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }

        return best;
    }

    private Entity closestPlayerInRange(LocalPlayer player, double range) {
        if (mc.level == null) return null;

        Entity closest = null;
        double closestDistance = range;
        for (Player other : mc.level.players()) {
            if (other == player)
                continue;

            double distance = distanceToBox(player, other);
            if (distance <= closestDistance) {
                closestDistance = distance;
                closest = other;
            }
        }

        return closest;
    }

    private Entity farthestDistantPlayer(LocalPlayer player) {
        if (mc.level == null) return null;

        Entity farthest = null;
        double farthestDistance = 0.0D;
        for (Player other : mc.level.players()) {
            if (other == player)
                continue;

            double distance = distanceToBox(player, other);
            if (distance >= 11.0D && distance > farthestDistance) {
                farthestDistance = distance;
                farthest = other;
            }
        }

        return farthest;
    }

    private static boolean isValidPredictionTarget(LocalPlayer player, Entity target, double range) {
        return target instanceof Player
                && target != player
                && distanceToBox(player, target) <= range;
    }

    private static double distanceToBox(LocalPlayer player, Entity target) {
        Vec3 eye = player.getEyePosition();
        return eye.distanceTo(closestPointOnBox(eye, target.getBoundingBox()));
    }

    private static Vec3 closestPointOnBox(Vec3 point, AABB box) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ)
        );
    }

    private static boolean isKillAuraActive() {
        return ModuleManager.getModuleState(KillAura.class);
    }

    private static boolean isLastSentOnGround(LocalPlayer player) {
        return player.lastOnGround && player.onGround();
    }

    private static boolean isOnHypixel() {
        ServerData server = mc.getCurrentServer();
        return server != null && server.ip != null && server.ip.toLowerCase(Locale.ROOT).contains("hypixel");
    }

    private static boolean isPredictionDelayExempt(Packet<?> packet, LocalPlayer player) {
        return packet instanceof ClientboundSetHealthPacket
                || packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundSoundPacket
                || packet instanceof ClientboundSoundEntityPacket
                || packet instanceof ClientboundPlayerChatPacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ClientboundPlayerCombatKillPacket
                || packet instanceof ClientboundContainerClosePacket
                || packet instanceof ClientboundHurtAnimationPacket
                || packet instanceof ClientboundSetTitleTextPacket
                || packet instanceof ClientboundSetSubtitleTextPacket
                || packet instanceof ClientboundSetPlayerTeamPacket
                || packet instanceof ClientboundAnimatePacket animatePacket && animatePacket.getId() != player.getId();
    }

    private static int computePredictionReduceTicks(int encodedX, int encodedZ) {
        double estimatedTicks = 6.43153527E-4D * Math.hypot(encodedX, encodedZ) + 2.9419087136D;
        return (int) Mth.clamp(Math.round(estimatedTicks), 1L, 10L);
    }

    private void flushPredictionPackets() {
        while (!predictionDelayedPackets.isEmpty())
            PacketUtil.handlePacket(predictionDelayedPackets.pollFirst());
    }

    private void resetPrediction(boolean flush) {
        if (flush && mc.player != null)
            flushPredictionPackets();
        else
            predictionDelayedPackets.clear();

        if (mc.player != null)
            restorePredictionKeys();

        predictionForwardKeyForced = false;
        predictionKeysOverridden = false;
        predictionDelaying = false;
        predictionForwardRequested = false;
        predictionAttackReduction = false;
        predictionReduceTicks = 0;
        predictionElapsedDelayTicks = -1;
        predictionTicksSinceKnockback = -1;
    }

    private boolean isPredictionMode() {
        return mode.get().equalsIgnoreCase("prediction");
    }

    private boolean isJumpResetMode() {
        return mode.get().equalsIgnoreCase("jump reset");
    }

    private void resetJumpReset() {
        jumpResetLimitUntilJump = 0;
        jumpResetFallDamage = false;
    }

    private boolean rollJumpResetChance() {
        float chance = jumpResetChance.get();
        return chance >= 100f || chance > 0f && ThreadLocalRandom.current().nextFloat() * 100.0f < chance;
    }

    private boolean isJumpResetCooldownOver() {
        if (jumpResetCooldown.get().equalsIgnoreCase("received hits"))
            return jumpResetLimitUntilJump >= jumpResetHitsUntilJump.get();

        if (jumpResetCooldown.get().equalsIgnoreCase("delay"))
            return jumpResetLimitUntilJump >= jumpResetTicksUntilJump.get();

        return true;
    }

    private void updateJumpResetLimit() {
        var player = mc.player;
        if (player == null) return;

        if (jumpResetCooldown.get().equalsIgnoreCase("received hits")) {
            if (player.hurtTime == 9)
                jumpResetLimitUntilJump++;
            return;
        }

        if (jumpResetCooldown.get().equalsIgnoreCase("delay"))
            jumpResetLimitUntilJump++;
    }
}
