package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.mixin.injector.MultiPlayerGameModeAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class AutoToolSaver extends Module {
    private static final long NOTIFY_COOLDOWN_MILLIS = 4_000L;

    @Setting
    private static final IntValue durability = new IntValue("Durability", 5, 1, 100);

    @Setting
    private final BooleanValue switchTool = new BooleanValue("Switch Tool", true);

    @Setting
    private final BooleanValue notify = new BooleanValue("Notify", true);

    private long lastNotifyMillis;

    public AutoToolSaver() {
        super("Auto Tool Saver", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return String.valueOf(durability.get());
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onWorld(WorldEvent event) {
    }

    public static boolean isAboutToBreak(ItemStack stack) {
        return ModuleManager.getModuleState(AutoToolSaver.class)
                && stack != null
                && !stack.isEmpty()
                && stack.isDamageableItem()
                && stack.getMaxDamage() - stack.getDamageValue() <= durability.get();
    }

    public static boolean shouldBlockMining(HitResult hitResult) {
        LocalPlayer player = mc.player;
        return player != null
                && !player.getAbilities().instabuild
                && hitResult instanceof BlockHitResult
                && hitResult.getType() == HitResult.Type.BLOCK
                && isAboutToBreak(player.getMainHandItem());
    }

    @Override
    public void onTick(TickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || player.getAbilities().instabuild) {
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (!isAboutToBreak(held)) {
            return;
        }

        boolean attacking = mc.options.keyAttack.isDown() && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK;
        MultiPlayerGameModeAccessor accessor = (MultiPlayerGameModeAccessor) mc.gameMode;
        if (!attacking && !accessor.krs$isDestroying()) {
            return;
        }

        if (switchTool.get()) {
            int replacement = findReplacement(player.getInventory(), held);
            if (replacement != -1) {
                player.getInventory().setSelectedSlot(replacement);
                return;
            }
        }

        if (accessor.krs$isDestroying())
            mc.gameMode.stopDestroyBlock();

        long now = System.currentTimeMillis();
        if (notify.get() && now - lastNotifyMillis > NOTIFY_COOLDOWN_MILLIS) {
            lastNotifyMillis = now;
            int left = held.getMaxDamage() - held.getDamageValue();
            Client.notificationManager.addNotification("Tool Saver", held.getHoverName().getString() + " has " + left + " durability left");
        }
    }

    private static int findReplacement(Inventory inventory, ItemStack held) {
        int category = ToolUtil.INSTANCE.getToolCategory(held.getItem());
        int fallback = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (slot == inventory.getSelectedSlot())
                continue;

            ItemStack stack = inventory.getItem(slot);
            if (isAboutToBreak(stack))
                continue;

            if (category != -1 && ToolUtil.INSTANCE.getToolCategory(stack.getItem()) == category)
                return slot;
            if (fallback == -1 && stack.isEmpty())
                fallback = slot;
        }
        return fallback;
    }
}
