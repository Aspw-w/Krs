package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class NoSlow extends Module {

    @Setting
    private static final ListValue mode = new ListValue("Mode", new String[]{"Vanilla", "Hypixel NCP"}, "Vanilla");

    @Setting
    private static final BooleanValue sneak = new BooleanValue("Sneak", false, () -> mode.get().equalsIgnoreCase("vanilla"));

    private static int lastSneakInputTick = Integer.MIN_VALUE;
    private boolean waitingPacket = false;

    public NoSlow() {
        super("No Slow", ModuleCategory.Movement, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onDisable() {
        lastSneakInputTick = Integer.MIN_VALUE;
        waitingPacket = false;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onMotion(MotionEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (mode.get().equalsIgnoreCase("hypixel ncp") && !ToolUtil.INSTANCE.isSword(player.getMainHandItem()) && player.isUsingItem() && player.onGround())
            event.y += player.tickCount % 2 == 0 ? 0.03 : 1E-5;
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        var player = mc.player;
        if (player == null) return;

        var packet = event.packet;
        ItemStack mainHandItem = player.getMainHandItem();

        if (mode.get().equalsIgnoreCase("hypixel ncp") && MovementUtil.fallTicks < 2 && isHypixelUseItem(mainHandItem)) {
            if (packet instanceof ServerboundUseItemPacket) {
                event.cancel();

                if (player.onGround()) {
                    player.input.makeJump();
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
            case "hypixel ncp":
                return shouldNoSlowUseItem();
        }
        return false;
    }

    public static boolean shouldNoSlowUseItem() {
        var player = mc.player;
        if (player == null || !ModuleManager.getModuleState(NoSlow.class) || !player.isUsingItem())
            return false;

        boolean fastUseItem = isFastUseAnimation(player.getUseItem().getUseAnimation());
        switch (mode.get().toLowerCase(Locale.ROOT)) {
            case "vanilla":
                return fastUseItem;
            case "hypixel ncp":
                return !ToolUtil.INSTANCE.isSword(player.getMainHandItem()) && !player.isShiftKeyDown() && fastUseItem;
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
