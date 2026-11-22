package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.combat.KillAura;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.ArrayDeque;
import java.util.Locale;

public class NoSlow extends Module {

    @Setting
    private static final ListValue mode = new ListValue("Mode", new String[]{"Vanilla", "Hypixel NCP", "Watchdog Legacy", "Prediction", "Grim"}, "Vanilla");

    @Setting
    private static final BooleanValue sneak = new BooleanValue("Sneak", false, () -> mode.get().equalsIgnoreCase("vanilla"));

    @Setting
    private static final BooleanValue predictionFoodSprint = new BooleanValue("Food Sprint", true, () -> mode.get().equalsIgnoreCase("prediction"));

    @Setting
    private static final ListValue grimMode = new ListValue("Grim Mode", new String[]{"2 Tick", "1.19"}, "2 Tick", () -> mode.get().equalsIgnoreCase("grim"));

    private static int lastSneakInputTick = Integer.MIN_VALUE;
    private boolean waitingPacket = false;

    private static int grimUseTicks = 0;
    private static boolean grimSlowdownCancelled = false;

    private static boolean predictionAwaitingReblock = false;
    private static boolean predictionBlocking = false;
    private static boolean predictionJustReleased = false;
    private static boolean predictionBlinking = false;
    private static boolean predictionReblockPending = false;
    private static boolean predictionInternalStartUse = false;
    private static final ArrayDeque<Packet<?>> predictionQueuedPackets = new ArrayDeque<>();

    public NoSlow() {
        super("No Slow", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onDisable() {
        lastSneakInputTick = Integer.MIN_VALUE;
        waitingPacket = false;
        resetGrim();
        stopPredictionBlink();
        resetPredictionFlags();
    }

    @Override
    public void onWorld(WorldEvent event) {
        predictionQueuedPackets.clear();
        predictionBlinking = false;
        resetPredictionFlags();
        resetGrim();
    }

    @Override
    public void onTick(TickEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (mode.get().equalsIgnoreCase("grim"))
            updateGrim(player);
        else
            resetGrim();

        if (!mode.get().equalsIgnoreCase("prediction")) {
            if (predictionBlinking || predictionBlocking || predictionAwaitingReblock || predictionReblockPending) {
                stopPredictionBlink();
                resetPredictionFlags();
            }
            return;
        }

        if (!predictionReblockPending)
            return;

        predictionInternalStartUse = true;
        try {
            mc.startUseItem();
        } finally {
            predictionInternalStartUse = false;
        }
        predictionBlocking = true;
        stopPredictionBlink();
        predictionAwaitingReblock = false;
        predictionReblockPending = false;
    }

    private void updateGrim(LocalPlayer player) {
        grimUseTicks = player.isUsingItem() ? grimUseTicks + 1 : 0;
        boolean eligible = isGrimNoSlowItem(player);

        if (grimMode.get().equalsIgnoreCase("1.19")
                && player.isUsingItem()
                && eligible
                && grimUseTicks > 3
                && grimUseTicks < 7
                && mc.gameMode != null
                && mc.level != null) {
            InteractionHand otherHand = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            float yaw = player.getYRot();
            float pitch = player.getXRot();
            mc.gameMode.startPrediction(mc.level, sequence -> new ServerboundUseItemPacket(otherHand, sequence, yaw, pitch));
        }

        grimSlowdownCancelled = false;
        if (!eligible && !player.isUsingItem())
            return;

        if (grimMode.get().equalsIgnoreCase("1.19")) {
            grimSlowdownCancelled = grimUseTicks > 2;
            return;
        }

        if (grimUseTicks >= 2) {
            grimSlowdownCancelled = true;
            grimUseTicks = 0;
        }
    }

    private static void resetGrim() {
        grimUseTicks = 0;
        grimSlowdownCancelled = false;
    }

    private static boolean isGrimNoSlowItem(LocalPlayer player) {
        boolean autoBlocking = KillAura.isServerBlocking();
        if (!player.isUsingItem() && !autoBlocking)
            return false;

        ItemStack stack = autoBlocking ? player.getMainHandItem() : player.getUseItem();
        if (stack.isEmpty())
            return false;

        var item = stack.getItem();
        return stack.has(DataComponents.FOOD)
                || ToolUtil.INSTANCE.isSword(stack)
                || item instanceof ShieldItem
                || item instanceof PotionItem && !(item instanceof SplashPotionItem)
                || item instanceof BowItem
                || item instanceof CrossbowItem;
    }

    private void handlePredictionMotion(MotionEvent event, LocalPlayer player) {
        if (!event.post)
            event.callInPost = true;

        if (!shouldPredictionBlock(player)) {
            resetPredictionState(player);
            return;
        }

        if (event.post) {
            if (predictionAwaitingReblock) {
                predictionReblockPending = true;
            } else if (predictionJustReleased) {
                predictionAwaitingReblock = true;
                predictionJustReleased = false;
            }
            return;
        }

        if (predictionAwaitingReblock) {
            predictionReblockPending = true;
            return;
        }

        predictionReblockPending = false;
        predictionBlinking = true;
        if (mc.gameMode != null)
            mc.gameMode.releaseUsingItem(player);
        predictionBlocking = false;
        predictionJustReleased = true;
    }

    private static void resetPredictionState(LocalPlayer player) {
        predictionReblockPending = false;
        stopPredictionBlink();
        if (predictionBlocking) {
            if (mc.gameMode != null)
                mc.gameMode.releaseUsingItem(player);
            predictionBlocking = false;
        }
    }

    private static void resetPredictionFlags() {
        predictionAwaitingReblock = false;
        predictionBlocking = false;
        predictionJustReleased = false;
        predictionReblockPending = false;
        predictionInternalStartUse = false;
    }

    private static void stopPredictionBlink() {
        if (!predictionBlinking)
            return;

        predictionBlinking = false;
        while (!predictionQueuedPackets.isEmpty())
            PacketUtil.sendPacketAsSilent(predictionQueuedPackets.pollFirst());
    }

    private static boolean shouldPredictionBlock(LocalPlayer player) {
        return ToolUtil.INSTANCE.isSword(player.getMainHandItem()) && mc.options.keyUse.isDown();
    }

    private static boolean shouldPredictionNoSlow(LocalPlayer player) {
        return shouldPredictionBlock(player)
                || ToolUtil.INSTANCE.isSword(player.getMainHandItem()) && (player.isUsingItem() || predictionBlocking);
    }

    public static boolean shouldBlockStartUseItem() {
        var player = mc.player;
        return player != null
                && !predictionInternalStartUse
                && ModuleManager.getModuleState(NoSlow.class)
                && mode.get().equalsIgnoreCase("prediction")
                && ToolUtil.INSTANCE.isSword(player.getMainHandItem());
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onMotion(MotionEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (mode.get().equalsIgnoreCase("prediction")) {
            handlePredictionMotion(event, player);
            return;
        }

        if (mode.get().equalsIgnoreCase("hypixel ncp") && !ToolUtil.INSTANCE.isSword(player.getMainHandItem()) && player.isUsingItem() && player.onGround())
            event.y += player.tickCount % 2 == 0 ? 0.03 : 1E-5;

        if (mode.get().equalsIgnoreCase("watchdog legacy")) {
            event.callInPost = true;

            if (!isBlocking(player)) return;

            if (!event.post) {
                PacketUtil.sendPacket(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                        BlockPos.ZERO,
                        Direction.DOWN
                ));
            } else {
                PacketUtil.sendPacket(new ServerboundUseItemPacket(
                        player.isUsingItem() ? player.getUsedItemHand() : InteractionHand.MAIN_HAND,
                        0,
                        player.getYRot(),
                        player.getXRot()
                ));
            }
        }
    }

    private static boolean isBlocking(LocalPlayer player) {
        return currentUseAnimation(player) == ItemUseAnimation.BLOCK;
    }

    private static ItemUseAnimation currentUseAnimation(LocalPlayer player) {
        if (player.isUsingItem())
            return player.getUseItem().getUseAnimation();

        return KillAura.isServerBlocking() ? ItemUseAnimation.BLOCK : null;
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        var player = mc.player;
        if (player == null) return;

        var packet = event.packet;
        if (predictionBlinking && mode.get().equalsIgnoreCase("prediction")) {
            event.cancel();
            predictionQueuedPackets.addLast(packet);
            return;
        }

        ItemStack mainHandItem = player.getMainHandItem();

        if (mode.get().equalsIgnoreCase("hypixel ncp") && MovementUtil.fallTicks < 2 && isHypixelUseItem(mainHandItem)) {
            if (packet instanceof ServerboundUseItemPacket) {
                event.cancel();

                if (player.onGround()) {
                    player.jumpFromGround();
                }

                waitingPacket = true;
            }

            if (packet instanceof ServerboundUseItemOnPacket)
                event.cancel();
        }
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (mode.get().equalsIgnoreCase("prediction")
                && predictionFoodSprint.get()
                && player.isUsingItem()
                && player.getUseItem().has(DataComponents.FOOD))
            player.setSprinting(true);

        if (mode.get().equalsIgnoreCase("hypixel ncp") && waitingPacket && !player.isUsingItem())
            waitingPacket = false;

        if (mode.get().equalsIgnoreCase("hypixel ncp") && waitingPacket && MovementUtil.fallTicks >= 2 && isHypixelUseItem(player.getMainHandItem())) {
            PacketUtil.sendPacket(new ServerboundUseItemPacket(
                    InteractionHand.MAIN_HAND,
                    0,
                    player.getYRot(),
                    player.getXRot()
            ));
            waitingPacket = false;
        }
    }

    public static boolean shouldNoSlowSneak() {
        var player = mc.player;
        if (player == null
                || !ModuleManager.getModuleState(NoSlow.class)
                || !mode.get().equalsIgnoreCase("vanilla")
                || !sneak.get())
            return false;

        if (isSneakInputActive()) {
            lastSneakInputTick = player.tickCount;
            return true;
        }

        int ticksSinceSneak = player.tickCount - lastSneakInputTick;
        return ticksSinceSneak >= 0 && ticksSinceSneak <= 1 && player.isMovingSlowly();
    }

    public static boolean noSlowHook() {
        if (!ModuleManager.getModuleState(NoSlow.class))
            return false;

        switch (mode.get().toLowerCase(Locale.ROOT)) {
            case "vanilla":
                return shouldNoSlowSneak() || shouldNoSlowUseItem();
            case "hypixel ncp", "watchdog legacy":
                return shouldNoSlowUseItem();
            case "prediction": {
                var player = mc.player;
                return player != null && shouldPredictionNoSlow(player);
            }
            case "grim": {
                var player = mc.player;
                return player != null && isGrimNoSlowItem(player) && !ToolUtil.INSTANCE.isSword(player.getMainHandItem());
            }
        }
        return false;
    }

    public static boolean shouldNoSlowUseItem() {
        var player = mc.player;
        if (player == null || !ModuleManager.getModuleState(NoSlow.class))
            return false;

        if (mode.get().equalsIgnoreCase("prediction"))
            return shouldPredictionNoSlow(player);
        if (mode.get().equalsIgnoreCase("grim"))
            return grimSlowdownCancelled && player.isUsingItem();

        ItemUseAnimation animation = currentUseAnimation(player);
        if (animation == null)
            return false;

        boolean fastUseItem = isFastUseAnimation(animation);
        switch (mode.get().toLowerCase(Locale.ROOT)) {
            case "vanilla":
                return fastUseItem;
            case "hypixel ncp":
                return !ToolUtil.INSTANCE.isSword(player.getMainHandItem()) && !player.isShiftKeyDown() && fastUseItem;
            case "watchdog legacy": {
                return animation == ItemUseAnimation.EAT
                        || animation == ItemUseAnimation.DRINK
                        || animation == ItemUseAnimation.BLOCK
                        || animation == ItemUseAnimation.BOW;
            }
        }

        return false;
    }

    private static boolean isSneakInputActive() {
        var player = mc.player;
        if (player == null)
            return false;

        if (player.isShiftKeyDown())
            return true;

        if (player.input == null)
            return false;

        Input input = player.input.keyPresses;
        return input != null && input.shift();
    }

    private static boolean isFastUseAnimation(ItemUseAnimation animation) {
        return animation == ItemUseAnimation.SPYGLASS
                || animation == ItemUseAnimation.TOOT_HORN
                || animation == ItemUseAnimation.BUNDLE
                || animation == ItemUseAnimation.DRINK
                || animation == ItemUseAnimation.BRUSH
                || animation == ItemUseAnimation.BLOCK
                || animation == ItemUseAnimation.EAT
                || animation == ItemUseAnimation.CROSSBOW
                || animation == ItemUseAnimation.SPEAR
                || animation == ItemUseAnimation.BOW
                || animation == ItemUseAnimation.TRIDENT;
    }

    private static boolean isHypixelUseItem(ItemStack stack) {
        var item = stack.getItem();
        return stack.has(DataComponents.FOOD) || item == Items.POTION || item instanceof BowItem || item == Items.MILK_BUCKET;
    }

}
