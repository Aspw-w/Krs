package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.events.features.AttackEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.MovementFix;
import com.instrumentalist.krs.utils.math.MSTimer;
import com.instrumentalist.krs.utils.move.InputUtil;
import com.instrumentalist.krs.utils.value.FloatValue;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Input;
import com.instrumentalist.krs.utils.input.Keys;

public class WTap extends Module {
    @Setting
    private final FloatValue delay = new FloatValue("Delay", 5.5f, 0.0f, 10.0f, "t");

    @Setting
    private final FloatValue duration = new FloatValue("Duration", 1.5f, 1.0f, 5.0f, "t");

    private final MSTimer attackTimer = new MSTimer();
    private boolean active = false;
    private boolean stopForward = false;
    private long delayTicks = 0L;
    private long durationTicks = 0L;

    public WTap() {
        super("WTap", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
        resetActive();
    }

    @Override
    public void onEnable() {
        resetActive();
    }

    @Override
    public void onAttack(AttackEvent event) {
        var player = mc.player;
        if (player == null || event.isCancelled() || active || !attackTimer.hasTimePassed(500L) || !player.isSprinting())
            return;

        attackTimer.reset();
        active = true;
        stopForward = false;
        delayTicks += ticksToMillis(delay.get());
        durationTicks += ticksToMillis(duration.get());
    }

    public static void hookWTapInput(ClientInput input) {
        WTap wTap = ModuleManager.getModule(WTap.class);
        if (wTap == null || !wTap.tempEnabled)
            return;

        wTap.handleInput(input);
    }

    private void handleInput(ClientInput input) {
        var player = mc.player;
        if (input == null || player == null || player.input != input) {
            resetActive();
            return;
        }

        if (!active)
            return;

        if (!stopForward && !canTrigger(input)) {
            resetActive();
            return;
        }

        if (delayTicks > 0L) {
            delayTicks -= 50L;
            return;
        }

        if (durationTicks > 0L) {
            durationTicks -= 50L;
            stopForward = true;
            stopForward(input);
        }

        if (durationTicks <= 0L)
            resetActive();
    }

    private boolean canTrigger(ClientInput input) {
        var player = mc.player;
        if (player == null)
            return false;

        Input movementInput = MovementFix.getRawInput();
        if (movementInput == null || movementInput == Input.EMPTY)
            movementInput = input.keyPresses == null ? Input.EMPTY : input.keyPresses;

        return forwardImpulse(movementInput) >= 0.8F
                && !player.horizontalCollision
                && (player.getFoodData().getFoodLevel() > 6.0F || player.getAbilities().flying)
                && (player.isSprinting()
                || !player.isUsingItem() && !player.hasEffect(MobEffects.BLINDNESS) && mc.options.keySprint.isDown());
    }

    private void stopForward(ClientInput input) {
        Input keyPresses = input.keyPresses == null ? Input.EMPTY : input.keyPresses;
        InputUtil.applyInput(input, new Input(
                false,
                false,
                keyPresses.left(),
                keyPresses.right(),
                keyPresses.jump(),
                keyPresses.shift(),
                false
        ));

        var player = mc.player;
        if (player != null && player.isSprinting())
            player.setSprinting(false);
    }

    private void resetActive() {
        active = false;
        stopForward = false;
        delayTicks = 0L;
        durationTicks = 0L;
    }

    private static long ticksToMillis(float ticks) {
        return (long) (50.0F * ticks);
    }

    private static float forwardImpulse(Input input) {
        if (input == null)
            return 0.0F;

        return KeyboardInput.calculateImpulse(input.forward(), input.backward());
    }
}
