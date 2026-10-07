package com.instrumentalist.krs.hacks.features.movement.fly.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyEvent;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.InputUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

public class MatrixFly implements FlyEvent {

    private boolean positionLookReceived;
    private boolean highBoost;
    private boolean highRestoreMotion;
    private Vec3 highMotion = Vec3.ZERO;
    private int highSelfDamageJumps;
    private int damageTicks;
    private int damageSelfDamageJumps;
    private int damageBoostTicks;
    private boolean damageStarted;
    private int airTicks;
    private String lastMode = "";

    @Override
    public String getName() {
        return "Matrix";
    }

    public void reset() {
        positionLookReceived = false;
        highBoost = false;
        highRestoreMotion = false;
        highMotion = Vec3.ZERO;
        highSelfDamageJumps = 0;
        damageTicks = 0;
        damageSelfDamageJumps = 0;
        damageBoostTicks = 0;
        damageStarted = false;
        airTicks = 0;
        lastMode = FlyModule.matrixMode.get();
    }

    public void disable() {
        if (FlyModule.matrixMode.get().equalsIgnoreCase("high"))
            MovementUtil.stopMoving();

        if (FlyModule.matrixMode.get().equalsIgnoreCase("damage")) {
            TimerUtil.reset();

            var player = mc.player;
            if (player != null && FlyModule.matrixDamagePacket.get()) {
                Vec3 position = player.position();
                PacketUtil.sendPacketAsSilent(new ServerboundMovePlayerPacket.PosRot(
                        position.x,
                        position.y,
                        position.z,
                        player.getYRot(),
                        player.getXRot(),
                        false,
                        player.horizontalCollision
                ));
                PacketUtil.sendPacketAsSilent(new ServerboundMovePlayerPacket.Pos(
                        position.x,
                        position.y,
                        position.z,
                        false,
                        player.horizontalCollision
                ));
                PacketUtil.sendPacketAsSilent(new ServerboundMovePlayerPacket.PosRot(
                        position.x,
                        position.y,
                        position.z,
                        player.getYRot(),
                        player.getXRot(),
                        false,
                        player.horizontalCollision
                ));
            }
        }

        reset();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (!FlyModule.matrixMode.get().equalsIgnoreCase(lastMode)) {
            if (lastMode.equalsIgnoreCase("damage"))
                TimerUtil.reset();
            reset();
        }

        if (player.onGround())
            airTicks = 0;
        else
            airTicks++;

        switch (FlyModule.matrixMode.get().toLowerCase(java.util.Locale.ROOT)) {
            case "normal" -> {
                if (player.onGround())
                    player.input.makeJump();

                if (airTicks > 1)
                    MovementUtil.setVelocityY(player.getDeltaMovement().y + 0.00348);

                if (player.fallDistance > 0.1F && !positionLookReceived) {
                    MovementUtil.setVelocityY(0.42);
                    MovementUtil.strafe(1.97F);
                }

                if (positionLookReceived && airTicks == 20) {
                    MovementUtil.setVelocityY(0.42);
                    MovementUtil.strafe(9.3F);
                }
            }
            case "high" -> {
                if (highBoost)
                    MovementUtil.setVelocityY(FlyModule.matrixHighHeight.get());

                if (player.hurtTime >= 1 && player.hurtTime <= 8) {
                    if (player.onGround())
                        player.input.makeJump();
                    else if (player.getDeltaMovement().y < 0.2)
                        highBoost = true;
                }

                if (FlyModule.matrixHighSelfDamage.get() && highSelfDamageJumps < 4 && player.onGround()) {
                    player.input.makeJump();
                    highSelfDamageJumps++;
                }
            }
            case "damage" -> {
                if (player.hurtTime > 0 && !damageStarted && airTicks >= 3) {
                    damageBoostTicks = 20 * (int) FlyModule.matrixDamageTimerSpeed.get().floatValue();
                    damageStarted = true;
                }

                if (FlyModule.matrixDamageSelfDamage.get() && player.hurtTime <= 0 && damageSelfDamageJumps < 4 && player.onGround()) {
                    player.input.makeJump();
                    damageSelfDamageJumps++;
                }

                if (!FlyModule.matrixDamageDetectDamage.get() || damageTicks <= FlyModule.matrixDamageFlyTicks.get() || !damageStarted) {
                    float speed = damageBoostTicks > 0 ? FlyModule.matrixDamageSpeed.get() : 0.03F;

                    if (!FlyModule.matrixDamageNoSpeed.get())
                        MovementUtil.strafe(speed);

                    TimerUtil.timerSpeed = FlyModule.matrixDamageTimerSpeed.get();

                    switch (FlyModule.matrixDamageMotionY.get().toLowerCase(java.util.Locale.ROOT)) {
                        case "none" -> MovementUtil.setVelocityY(player.getDeltaMovement().y * 0.039);
                        case "simple" -> MovementUtil.setVelocityY(FlyModule.matrixDamageMotion.get());
                        case "multiply" -> MovementUtil.setVelocityY(player.getDeltaMovement().y * FlyModule.matrixDamageMotion.get());
                    }

                    if (damageBoostTicks > 0)
                        damageBoostTicks--;

                    damageTicks++;
                }

                if (FlyModule.matrixDamageDetectDamage.get() && damageStarted) {
                    if (!FlyModule.matrixDamageAutoDisable.get() && damageTicks >= FlyModule.matrixDamageFlyTicks.get()) {
                        FlyModule flyModule = ModuleManager.getModule(FlyModule.class);
                        if (flyModule != null)
                            flyModule.setState(false);
                    } else if (FlyModule.matrixDamageAutoDisable.get() && damageBoostTicks <= 0) {
                        FlyModule flyModule = ModuleManager.getModule(FlyModule.class);
                        if (flyModule != null)
                            flyModule.setState(false);
                    }
                }
            }
        }
    }

    @Override
    public void onMotion(MotionEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (FlyModule.matrixMode.get().equalsIgnoreCase("high")) {
            if (FlyModule.matrixHighSelfDamage.get() && highSelfDamageJumps < 4)
                event.onGround = false;
            return;
        }

        if (FlyModule.matrixMode.get().equalsIgnoreCase("damage")) {
            if (FlyModule.matrixDamageSelfDamage.get() && player.hurtTime <= 0 && damageSelfDamageJumps < 4)
                event.onGround = false;

            if (FlyModule.matrixDamageNewSelfDamage.get() && player.fallDistance > 3.0F) {
                event.onGround = true;
                player.setOnGround(true);
                MovementUtil.setVelocityY(0.0);
                player.fallDistance = 0.0F;
            }
        }
    }

    @Override
    public void onTick(TickEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (FlyModule.matrixMode.get().equalsIgnoreCase("normal")) {
            if (airTicks <= 15 || airTicks >= 19) return;

            Input keyPresses = player.input.keyPresses;
            InputUtil.applyInput(player.input, new Input(
                    false,
                    false,
                    keyPresses.left(),
                    keyPresses.right(),
                    keyPresses.jump(),
                    keyPresses.shift(),
                    keyPresses.sprint()
            ));
        } else if (FlyModule.matrixMode.get().equalsIgnoreCase("damage") && FlyModule.matrixDamageSelfDamage.get() && !damageStarted && (damageSelfDamageJumps < 4 || !player.onGround())) {
            Input keyPresses = player.input.keyPresses;
            InputUtil.applyInput(player.input, new Input(
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    keyPresses.sprint()
            ));
        }
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (FlyModule.matrixMode.get().equalsIgnoreCase("high") && highRestoreMotion && event.packet instanceof ServerboundMovePlayerPacket) {
            var player = mc.player;
            if (player != null) {
                highRestoreMotion = false;
                player.setDeltaMovement(highMotion);
            }
        }
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        var player = mc.player;
        if (player == null) return;

        Packet<?> packet = event.packet;

        if (FlyModule.matrixMode.get().equalsIgnoreCase("high")) {
            if (highBoost && packet instanceof ClientboundPlayerPositionPacket) {
                highRestoreMotion = true;
                highMotion = player.getDeltaMovement();
                highBoost = false;
            }
            return;
        }

        if (!FlyModule.matrixMode.get().equalsIgnoreCase("normal"))
            return;

        if (!(packet instanceof ClientboundPlayerPositionPacket(
                int id, PositionMoveRotation change, java.util.Set<net.minecraft.world.entity.Relative> relatives
        )))
            return;

        event.cancel();

        PositionMoveRotation absolute = PositionMoveRotation.calculateAbsolute(
                PositionMoveRotation.of(player),
                change,
                relatives
        );
        Vec3 position = absolute.position();

        PacketUtil.sendPacketAsSilent(new ServerboundAcceptTeleportationPacket(id));
        PacketUtil.sendPacketAsSilent(new ServerboundMovePlayerPacket.PosRot(
                position.x,
                position.y,
                position.z,
                player.getYRot(),
                player.getXRot(),
                false,
                player.horizontalCollision
        ));

        player.setPos(position);
        player.jumpFromGround();

        if (positionLookReceived) {
            FlyModule flyModule = ModuleManager.getModule(FlyModule.class);
            if (flyModule != null)
                flyModule.setState(false);
            return;
        }

        positionLookReceived = true;
    }

    @Override
    public void onBlock(BlockEvent event) {
    }
}
