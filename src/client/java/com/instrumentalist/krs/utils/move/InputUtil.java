package com.instrumentalist.krs.utils.move;

import java.lang.reflect.Field;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

public final class InputUtil {
    private static final Field MOVE_VECTOR_FIELD = findMoveVectorField();

    private InputUtil() {
    }

    public static float forwardImpulse(ClientInput input) {
        return input == null ? 0.0F : input.getMoveVector().y;
    }

    public static float leftImpulse(ClientInput input) {
        return input == null ? 0.0F : input.getMoveVector().x;
    }

    public static void stop(ClientInput input) {
        if (input == null) return;

        applyInput(input, Input.EMPTY);
    }

    public static void setJumping(ClientInput input, boolean jumping) {
        if (input == null) return;

        Input keyPresses = input.keyPresses == null ? Input.EMPTY : input.keyPresses;
        if (keyPresses.jump() == jumping) return;

        applyInput(input, new Input(
                keyPresses.forward(),
                keyPresses.backward(),
                keyPresses.left(),
                keyPresses.right(),
                jumping,
                keyPresses.shift(),
                keyPresses.sprint()
        ));
    }

    public static void applyInput(ClientInput input, Input keyPresses) {
        if (input == null) return;

        input.keyPresses = keyPresses == null ? Input.EMPTY : keyPresses;
        setMoveVector(input, movementVector(input.keyPresses));
    }

    public static Vec2 movementVector(Input keyPresses) {
        if (keyPresses == null) return Vec2.ZERO;

        float forward = KeyboardInput.calculateImpulse(keyPresses.forward(), keyPresses.backward());
        float left = KeyboardInput.calculateImpulse(keyPresses.left(), keyPresses.right());
        return new Vec2(left, forward).normalized();
    }

    public static void setMoveVector(ClientInput input, Vec2 moveVector) {
        if (input == null || moveVector == null) return;

        try {
            MOVE_VECTOR_FIELD.set(input, moveVector);
        } catch (IllegalAccessException ignored) {
        }
    }

    private static Field findMoveVectorField() {
        try {
            Field field = ClientInput.class.getDeclaredField("moveVector");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to access ClientInput move vector", e);
        }
    }
}
