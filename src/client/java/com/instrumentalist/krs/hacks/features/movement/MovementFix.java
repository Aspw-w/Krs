package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.move.InputUtil;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class MovementFix extends Module {
    private static Input rawInput = Input.EMPTY;
    private static Input correctedInput = Input.EMPTY;
    private static Input sprintConditionInput = Input.EMPTY;
    private static boolean inputOverridden = false;
    private static boolean sprintConditionOverridden = false;

    public MovementFix() {
        super("Movement Fix", ModuleCategory.Movement, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    public static boolean shouldFixMovement() {
        return shouldUseRotationFix();
    }

    public static boolean shouldFixSprintCondition() {
        return isSprintCorrectionEnabled() && (shouldUseRotationFix() || sprintConditionOverridden);
    }

    public static float getMovementYaw() {
        if (!shouldFixMovement())
            return mc.player != null ? mc.player.getYRot() : 0.0f;

        return Client.rotationManager.getRotationYaw();
    }

    public static float getMovementPitch() {
        if (!shouldFixMovement())
            return mc.player != null ? mc.player.getXRot() : 0.0f;

        return Client.rotationManager.getRotationPitch();
    }

    public static Vec3 getMovementLookVector() {
        return Vec3.directionFromRotation(getMovementPitch(), getMovementYaw());
    }

    public static float getJumpMovementYaw() {
        return getMovementYaw();
    }

    public static void updateInput(ClientInput input) {
        if (input == null) {
            resetInputState();
            return;
        }

        rawInput = input.keyPresses;
        Input movementInput = rawInput;
        sprintConditionInput = rawInput;
        sprintConditionOverridden = false;

        if (shouldUseRotationFix()) {
            float rotationYaw = Client.rotationManager.getRotationYaw();

            movementInput = fixStrafeInput(rawInput, rotationYaw);

            sprintConditionInput = fixStrafeInput(rawInput, rotationYaw);
            sprintConditionOverridden = !sprintConditionInput.equals(rawInput);
        }

        Input sprintInput = Sprint.applyInputSprint(mc.player, sprintConditionInput);
        correctedInput = withSprint(movementInput, sprintInput.sprint());
        inputOverridden = !correctedInput.equals(rawInput);

        if (inputOverridden)
            InputUtil.applyInput(input, correctedInput);
    }

    public static Input getRawInput() {
        return rawInput;
    }

    public static float getCorrectionDirectionError(float movementYaw, float targetYaw) {
        int sector = correctionSector(movementYaw, targetYaw);
        float correctedMovementYaw = targetYaw + (sector - 4) * 45.0F;
        return Mth.wrapDegrees(correctedMovementYaw - movementYaw);
    }

    public static boolean hasForwardImpulseForSprint(ClientInput input) {
        Input keyPresses = input != null ? input.keyPresses : correctedInput;
        if (Sprint.shouldSprintOmnidirectional())
            return isMoving(keyPresses);

        if (shouldFixSprintCondition())
            return hasForwardImpulse(sprintConditionInput);

        return input == null ? hasForwardImpulse(keyPresses) : input.hasForwardImpulse();
    }

    private static boolean shouldUseRotationFix() {
        return ModuleManager.getModuleState(MovementFix.class) && Client.rotationManager != null && Client.rotationManager.isRotating() && mc.player != null;
    }

    private static boolean isSprintCorrectionEnabled() {
        return ModuleManager.getModuleState(MovementFix.class) && mc.player != null;
    }

    private static Input fixStrafeInput(Input input, float targetYaw) {
        if (input == null || !isMoving(input))
            return input == null ? Input.EMPTY : input;

        LocalPlayer player = mc.player;
        if (player == null)
            return input;

        float movementYaw = adjustYaw(player.getYRot(), forwardValue(input), leftValue(input));
        return sectorInput(input, correctionSector(movementYaw, targetYaw));
    }

    private static int correctionSector(float movementYaw, float targetYaw) {
        float angle = Mth.wrapDegrees(movementYaw - targetYaw + 22.5F);
        return (int) (angle + 180.0F) / 45 % 8;
    }

    private static boolean isMoving(Input input) {
        return input.forward() != input.backward() || input.left() != input.right();
    }

    private static boolean hasForwardImpulse(Input input) {
        return input != null && KeyboardInput.calculateImpulse(input.forward(), input.backward()) > 1.0E-5F;
    }

    private static int forwardValue(Input input) {
        int value = 0;
        if (input.forward())
            value++;
        if (input.backward())
            value--;
        return value;
    }

    private static int leftValue(Input input) {
        int value = 0;
        if (input.left())
            value++;
        if (input.right())
            value--;
        return value;
    }

    private static float adjustYaw(float yaw, float forward, float strafe) {
        if (forward < 0.0F)
            yaw += 180.0F;

        if (strafe != 0.0F) {
            float multiplier = forward == 0.0F ? 1.0F : 0.5F * Math.signum(forward);
            yaw += -90.0F * multiplier * Math.signum(strafe);
        }

        return Mth.wrapDegrees(yaw);
    }

    private static Input sectorInput(Input source, int sector) {
        return switch (sector) {
            case 0 -> directionalInput(source, -1, 0);
            case 1 -> directionalInput(source, -1, 1);
            case 2 -> directionalInput(source, 0, 1);
            case 3 -> directionalInput(source, 1, 1);
            case 4 -> directionalInput(source, 1, 0);
            case 5 -> directionalInput(source, 1, -1);
            case 6 -> directionalInput(source, 0, -1);
            case 7 -> directionalInput(source, -1, -1);
            default -> source;
        };
    }

    private static Input directionalInput(Input source, int forward, int left) {
        return new Input(
                forward > 0,
                forward < 0,
                left > 0,
                left < 0,
                source.jump(),
                source.shift(),
                source.sprint()
        );
    }

    private static Input withSprint(Input source, boolean sprint) {
        if (source.sprint() == sprint)
            return source;

        return new Input(
                source.forward(),
                source.backward(),
                source.left(),
                source.right(),
                source.jump(),
                source.shift(),
                sprint
        );
    }

    private static void restoreInput() {
        LocalPlayer player = mc.player;
        if (player != null && inputOverridden)
            InputUtil.applyInput(player.input, rawInput);

        resetInputState();
    }

    private static void resetInputState() {
        rawInput = Input.EMPTY;
        correctedInput = Input.EMPTY;
        sprintConditionInput = Input.EMPTY;
        inputOverridden = false;
        sprintConditionOverridden = false;
    }

    @Override
    public void onDisable() {
        restoreInput();
    }

    @Override
    public void onEnable() {
        resetInputState();
    }
}
