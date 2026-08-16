package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class AntiAFK extends Module {
    @Setting
    private final ListValue action = new ListValue(
            "Action",
            new String[]{"Swing", "Rotate", "Jump", "Cycle"},
            "Cycle"
    );

    @Setting
    private final IntValue interval = new IntValue("Interval", 5, 1, 300, "s");

    @Setting
    private final BooleanValue pauseWhileHurt = new BooleanValue("Pause While Hurt", true);

    @Setting
    private final BooleanValue notifications = new BooleanValue("Notifications", false);

    private int idleTicks;
    private int cycleIndex;
    private float lastYaw;
    private float lastPitch;
    private boolean initialized;
    private boolean rotateRight;

    public AntiAFK() {
        super("Anti AFK", ModuleCategory.Player, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return action.get();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onWorld(WorldEvent event) {
        reset();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null || player.isDeadOrDying()) {
            reset();
            return;
        }

        float yaw = player.getYRot();
        float pitch = player.getXRot();
        if (!initialized) {
            lastYaw = yaw;
            lastPitch = pitch;
            initialized = true;
            return;
        }

        boolean cameraMoved = Math.abs(Mth.wrapDegrees(yaw - lastYaw)) > 0.05f
                || Math.abs(pitch - lastPitch) > 0.05f;
        lastYaw = yaw;
        lastPitch = pitch;

        if (hasManualInput() || cameraMoved || pauseWhileHurt.get() && player.hurtTime > 0) {
            idleTicks = 0;
            return;
        }

        if (++idleTicks < interval.get() * 20)
            return;

        performConfiguredAction();
        idleTicks = 0;
    }

    private boolean hasManualInput() {
        return mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown()
                || mc.options.keyJump.isDown()
                || mc.options.keyShift.isDown()
                || mc.options.keySprint.isDown()
                || mc.options.keyAttack.isDown()
                || mc.options.keyUse.isDown();
    }

    private void performConfiguredAction() {
        String selectedAction = action.get().toLowerCase(Locale.ROOT);
        if (selectedAction.equals("cycle")) {
            selectedAction = switch (cycleIndex++ % 3) {
                case 1 -> "rotate";
                case 2 -> "jump";
                default -> "swing";
            };
        }

        switch (selectedAction) {
            case "rotate" -> rotate();
            case "jump" -> jump();
            default -> swing();
        }

        if (notifications.get() && Client.notificationManager != null)
            Client.notificationManager.addNotification("Anti AFK", "Performed " + selectedAction);
    }

    private void swing() {
        if (mc.player != null)
            mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void rotate() {
        if (mc.player == null)
            return;

        float offset = rotateRight ? 3.0f : -3.0f;
        rotateRight = !rotateRight;
        mc.player.setYRot(Mth.wrapDegrees(mc.player.getYRot() + offset));
    }

    private void jump() {
        if (mc.player != null && mc.player.onGround() && mc.player.getVehicle() == null)
            mc.player.jumpFromGround();
        else
            swing();
    }

    private void reset() {
        idleTicks = 0;
        cycleIndex = 0;
        lastYaw = 0.0f;
        lastPitch = 0.0f;
        initialized = false;
        rotateRight = false;
    }
}
