package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.player.Scaffold;
import com.instrumentalist.mixin.injector.LocalPlayerSprintAccessor;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import com.instrumentalist.krs.utils.input.Keys;

public class Sprint extends Module {

    @Setting
    private static final BooleanValue autoSprint = new BooleanValue("Auto Sprint", true);

    @Setting
    public static final BooleanValue multiDirection = new BooleanValue("Multi Direction", true, autoSprint::get);

    @Setting
    private static final BooleanValue keepSprint = new BooleanValue("Keep Sprint", true);

    @Setting
    private static final BooleanValue silentSprint = new BooleanValue("Silent", true);

    @Setting
    private static final BooleanValue wallCheck = new BooleanValue("Wall Check", false, autoSprint::get);

    private boolean swimming = false;

    public Sprint() {
        super("Sprint", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
        swimming = false;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onWorld(WorldEvent event) {
        swimming = false;
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (shouldAutoSprint(player, player.input.keyPresses) && !player.isSprinting())
            player.setSprinting(true);
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        var player = mc.player;
        if (player == null) return;

        var packet = event.packet;
        if (silentSprint.get()
                && packet instanceof ServerboundPlayerCommandPacket commandPacket
                && (commandPacket.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING || commandPacket.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING)) {
            event.cancel();
            if (player.isInWater() && player.isSprinting() && !swimming && commandPacket.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
                PacketUtil.sendPacketAsSilent(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
                swimming = true;
            } else if (!player.isSprinting() && swimming && commandPacket.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING) {
                PacketUtil.sendPacketAsSilent(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
                swimming = false;
            }
        }
    }

    public static boolean shouldSprintOmnidirectional() {
        return ModuleManager.getModuleState(Sprint.class) && autoSprint.get() && multiDirection.get();
    }

    public static float getOmnidirectionalJumpYaw(float fallbackYaw) {
        var player = mc.player;
        if (!shouldSprintOmnidirectional() || player == null || player.input == null)
            return fallbackYaw;

        Input input = player.input.keyPresses == null ? Input.EMPTY : player.input.keyPresses;
        if (!isMoving(input))
            return fallbackYaw;

        if (MovementFix.shouldFixMovement())
            return getMovementDirection(MovementFix.getMovementYaw(), input);

        return MovementUtil.getPlayerDirection();
    }

    public static Input applyInputSprint(LocalPlayer player, Input input) {
        if (input == null || input.sprint() || !shouldAutoSprint(player, input))
            return input;

        return new Input(
                input.forward(),
                input.backward(),
                input.left(),
                input.right(),
                input.jump(),
                input.shift(),
                true
        );
    }

    public static boolean tryStartSprinting(LocalPlayer player, Input input, boolean ignoreScaffoldNoSprint) {
        if (player == null || input == null || player.isSprinting())
            return false;

        if (Scaffold.shouldSuppressTellyNormalJumpSprintHook())
            return false;

        if (ignoreScaffoldNoSprint) {
            if (!canStartSprintingLikeVanilla(player, input) || wallCheck.get() && player.horizontalCollision)
                return false;

            player.setSprinting(true);
            return true;
        }

        if (!shouldAutoSprint(player, input, ignoreScaffoldNoSprint) && !canStartPhysicalSprint(player, input))
            return false;

        player.setSprinting(true);
        return true;
    }

    private static boolean shouldAutoSprint(LocalPlayer player, Input input) {
        return shouldAutoSprint(player, input, false);
    }

    private static boolean shouldAutoSprint(LocalPlayer player, Input input, boolean ignoreScaffoldNoSprint) {
        return ModuleManager.getModuleState(Sprint.class)
                && autoSprint.get()
                && player != null
                && input != null
                && !Scaffold.shouldSuppressTellyNormalJumpSprintHook()
                && canStartSprintingLikeVanilla(player, input)
                && (ignoreScaffoldNoSprint || !ModuleManager.getModuleState(Scaffold.class) || !Scaffold.noSprint.get())
                && (!wallCheck.get() || !player.horizontalCollision);
    }

    private static boolean canStartPhysicalSprint(LocalPlayer player, Input input) {
        return input.sprint()
                && canStartSprintingLikeVanilla(player, input)
                && (!wallCheck.get() || !player.horizontalCollision);
    }

    private static boolean canStartSprintingLikeVanilla(LocalPlayer player, Input input) {
        if (player == null || input == null)
            return false;

        LocalPlayerSprintAccessor sprintAccessor = (LocalPlayerSprintAccessor) player;
        boolean noSlowActive = NoSlow.noSlowHook();

        if (player.isSprinting()
                || !hasSprintImpulse(input)
                || !sprintAccessor.krs$invokeIsSprintingPossible(player.getAbilities().flying)
                || sprintAccessor.krs$invokeIsSlowDueToUsingItem() && !noSlowActive)
            return false;

        if (player.isFallFlying() && !player.isUnderWater())
            return false;

        return !player.isMovingSlowly() || player.isUnderWater() || noSlowActive && NoSlow.shouldNoSlowSneak();
    }

    private static boolean hasSprintImpulse(Input input) {
        return multiDirection.get() ? isMoving(input) : hasForwardImpulse(input);
    }

    private static boolean isMoving(Input input) {
        return input.forward() != input.backward() || input.left() != input.right();
    }

    private static boolean hasForwardImpulse(Input input) {
        return KeyboardInput.calculateImpulse(input.forward(), input.backward()) > 1.0E-5F;
    }

    private static float getMovementDirection(float facingYaw, Input input) {
        if (input == null)
            return facingYaw;

        float yaw = facingYaw;
        float forwardMultiplier;

        if (input.backward() && !input.forward()) {
            yaw += 180f;
            forwardMultiplier = -0.5f;
        } else if (input.forward() && !input.backward()) {
            forwardMultiplier = 0.5f;
        } else {
            forwardMultiplier = 1f;
        }

        if (input.left() && !input.right())
            yaw -= 90f * forwardMultiplier;
        if (input.right() && !input.left())
            yaw += 90f * forwardMultiplier;

        return (yaw % 360f + 360f) % 360f;
    }

    public static boolean keepSprintHook(Player entity) {
        return !(ModuleManager.getModuleState(Sprint.class) && keepSprint.get() && entity instanceof LocalPlayer);
    }

}
