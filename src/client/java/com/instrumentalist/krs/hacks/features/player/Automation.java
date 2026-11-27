package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.Locale;

public class Automation extends Module {
    @Setting
    private final BooleanValue autoRespawn = new BooleanValue("Auto Respawn", false);

    @Setting
    private final ListValue respawnMode = new ListValue("Respawn Mode", new String[]{"Packet", "Client", "Both"}, "Packet", autoRespawn::get);

    @Setting
    private final IntValue respawnDelay = new IntValue("Respawn Delay", 0, 0, 20, "t", autoRespawn::get);

    @Setting
    private final BooleanValue closeDeathScreen = new BooleanValue("Close Death Screen", true, autoRespawn::get);

    @Setting
    private final BooleanValue antiAfk = new BooleanValue("Anti AFK", false);

    @Setting
    private final ListValue afkAction = new ListValue("AFK Action", new String[]{"Swing", "Rotate", "Jump", "Cycle"}, "Cycle", antiAfk::get);

    @Setting
    private final IntValue afkInterval = new IntValue("AFK Interval", 5, 1, 300, "s", antiAfk::get);

    @Setting
    private final BooleanValue afkPauseWhileHurt = new BooleanValue("AFK Pause While Hurt", true, antiAfk::get);

    @Setting
    private final BooleanValue afkNotifications = new BooleanValue("AFK Notifications", false, antiAfk::get);

    @Setting
    private final BooleanValue autoLeave = new BooleanValue("Auto Leave", false);

    @Setting
    private final ListValue leaveMode = new ListValue("Leave Mode", new String[]{"Health", "Void", "Both", "No Totem", "Smart"}, "Health", autoLeave::get);

    @Setting
    private final FloatValue leaveHealth = new FloatValue("Leave Health", 4f, 1f, 20f, autoLeave::get);

    @Setting
    private final BooleanValue leaveMultiplayerOnly = new BooleanValue("Leave Multiplayer Only", true, autoLeave::get);

    private int deathTicks = 0;
    private boolean respawnRequested = false;

    private int idleTicks;
    private int cycleIndex;
    private float lastYaw;
    private float lastPitch;
    private boolean afkInitialized;
    private boolean rotateRight;

    private boolean left = false;

    public Automation() {
        super("Automation", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public void onEnable() {
        resetRespawn();
        left = false;
    }

    @Override
    public void onDisable() {
        resetRespawn();
        resetAfk();
        left = false;
    }

    @Override
    public void onWorld(WorldEvent event) {
        resetAfk();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (autoRespawn.get())
            updateRespawn();
        else
            resetRespawn();

        if (antiAfk.get())
            updateAfk();
        else
            resetAfk();

        if (autoLeave.get())
            updateLeave();
    }

    private void updateRespawn() {
        var player = mc.player;
        if (player == null || !player.isDeadOrDying()) {
            resetRespawn();
            return;
        }

        if (deathTicks < respawnDelay.get()) {
            deathTicks++;
            return;
        }

        if (respawnRequested)
            return;

        switch (respawnMode.get().toLowerCase(Locale.ROOT)) {
            case "client" -> player.respawn();
            case "both" -> {
                PacketUtil.sendPacket(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                player.respawn();
            }
            default -> PacketUtil.sendPacket(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        }
        if (closeDeathScreen.get() && mc.gui.screen() instanceof DeathScreen)
            mc.gui.setScreen(null);
        respawnRequested = true;
    }

    private void resetRespawn() {
        deathTicks = 0;
        respawnRequested = false;
    }

    private void updateAfk() {
        var player = mc.player;
        if (player == null || player.isDeadOrDying()) {
            resetAfk();
            return;
        }

        float yaw = player.getYRot();
        float pitch = player.getXRot();
        if (!afkInitialized) {
            lastYaw = yaw;
            lastPitch = pitch;
            afkInitialized = true;
            return;
        }

        boolean cameraMoved = Math.abs(Mth.wrapDegrees(yaw - lastYaw)) > 0.05f
                || Math.abs(pitch - lastPitch) > 0.05f;
        lastYaw = yaw;
        lastPitch = pitch;

        if (hasManualInput() || cameraMoved || afkPauseWhileHurt.get() && player.hurtTime > 0) {
            idleTicks = 0;
            return;
        }

        if (++idleTicks < afkInterval.get() * 20)
            return;

        performAfkAction();
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

    private void performAfkAction() {
        String selectedAction = afkAction.get().toLowerCase(Locale.ROOT);
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

        if (afkNotifications.get() && Client.notificationManager != null)
            Client.notificationManager.addNotification("Anti AFK", "Performed " + selectedAction);
    }

    private void swing() {
        if (mc.player != null)
            PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
    }

    private void rotate() {
        var player = mc.player;
        if (player == null)
            return;

        float offset = rotateRight ? 3.0f : -3.0f;
        rotateRight = !rotateRight;
        float currentYaw = player.getYRot();
        float targetYaw = currentYaw + offset;
        if (Client.rotationManager != null)
            targetYaw = Client.rotationManager.normalizeRotation(
                    targetYaw,
                    player.getXRot(),
                    currentYaw,
                    player.getXRot()
            )[0];

        player.turn(Mth.wrapDegrees(targetYaw - currentYaw) / 0.15D, 0.0D);
    }

    private void jump() {
        if (mc.player != null)
            mc.player.input.makeJump();
    }

    private void resetAfk() {
        idleTicks = 0;
        cycleIndex = 0;
        lastYaw = 0.0f;
        lastPitch = 0.0f;
        afkInitialized = false;
        rotateRight = false;
    }

    private void updateLeave() {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null || left || leaveMultiplayerOnly.get() && mc.hasSingleplayerServer())
            return;

        boolean healthDanger = usesHealth() && player.getHealth() <= leaveHealth.get();
        boolean voidDanger = usesVoid() && player.getY() < level.getMinY() - 8 && !MovementUtil.isBlockBelow();
        boolean totemDanger = usesTotem() && player.getHealth() <= leaveHealth.get() && !hasTotem();
        if (!healthDanger && !voidDanger && !totemDanger)
            return;

        left = true;
        level.disconnect(Component.literal("Auto Leave"));
    }

    private boolean usesHealth() {
        return leaveMode.get().equalsIgnoreCase("health") || leaveMode.get().equalsIgnoreCase("both") || leaveMode.get().equalsIgnoreCase("smart");
    }

    private boolean usesVoid() {
        return leaveMode.get().equalsIgnoreCase("void") || leaveMode.get().equalsIgnoreCase("both") || leaveMode.get().equalsIgnoreCase("smart");
    }

    private boolean usesTotem() {
        return leaveMode.get().equalsIgnoreCase("no totem") || leaveMode.get().equalsIgnoreCase("smart");
    }

    private boolean hasTotem() {
        var player = mc.player;
        if (player == null)
            return false;

        if (player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING)
            return true;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == Items.TOTEM_OF_UNDYING)
                return true;
        }
        return false;
    }
}
