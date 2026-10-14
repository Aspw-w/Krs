package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.utils.move.InputUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.player.ClientInput;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class Velocity extends Module {
    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Standard", "Hypixel NCP", "Jump Reset"}, "Standard");

    @Setting
    private final FloatValue jumpResetChance = new FloatValue("Jump Reset Chance", 100f, 0f, 100f, "%", this::isJumpResetMode);

    @Setting
    private final ListValue jumpResetCooldown = new ListValue("Jump Reset Cooldown", new String[]{"Delay", "Received Hits", "None"}, "Delay", this::isJumpResetMode);

    @Setting
    private final IntValue jumpResetTicksUntilJump = new IntValue("Jump Reset Until Jump", 2, 0, 20, "t", () -> isJumpResetMode() && jumpResetCooldown.get().equalsIgnoreCase("delay"));

    @Setting
    private final IntValue jumpResetHitsUntilJump = new IntValue("Jump Reset Hits Until Jump", 2, 0, 10, "x", () -> isJumpResetMode() && jumpResetCooldown.get().equalsIgnoreCase("received hits"));

    private int jumpResetLimitUntilJump = 0;
    private boolean jumpResetFallDamage = false;

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
    }

    @Override
    public void onEnable() {
        resetJumpReset();
    }

    public static void hookJumpResetInput(ClientInput input) {
        Velocity velocity = ModuleManager.getModule(Velocity.class);
        if (velocity == null || !velocity.tempEnabled)
            return;

        velocity.handleJumpResetInput(input);
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

        var packet = event.packet;
        switch (mode.get().toLowerCase(Locale.ROOT)) {
            case "standard" -> {
                if (packet instanceof ClientboundSetEntityMotionPacket motionPacket && motionPacket.id() == player.getId())
                    event.cancel();
            }
            case "hypixel ncp" -> {
                if (!ModuleManager.getModuleState(FlyModule.class) && packet instanceof ClientboundSetEntityMotionPacket motionPacket && motionPacket.id() == player.getId()) {
                    if (!ModuleManager.getModuleState(SpeedModule.class) || MovementUtil.fallTicks >= 10 || player.onGround())
                        MovementUtil.setVelocityY(motionPacket.movement().y / 8000.0);
                    event.cancel();
                }
            }
            case "jump reset" -> updateJumpResetFallDamage(packet, player.getId());
        }
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

    private void updateJumpResetFallDamage(Object packet, int playerId) {
        if (packet instanceof ClientboundSetEntityMotionPacket motionPacket && motionPacket.id() == playerId) {
            var motion = motionPacket.movement();
            jumpResetFallDamage = motion.x == 0.0 && motion.z == 0.0 && motion.y < 0.0;
        }
    }
}
