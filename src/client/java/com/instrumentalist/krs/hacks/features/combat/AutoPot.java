package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.player.InvManager;
import com.instrumentalist.krs.hacks.features.player.Scaffold;
import net.minecraft.client.player.LocalPlayer;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.math.TickTimer;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AutoPot extends Module {
    private boolean wasRotating = false;
    private boolean potted = false;
    private boolean resetPotiHaveAEffect = false;
    private int oldSlot = -1;
    private final TickTimer potTimer = new TickTimer();
    private static final List<Holder<MobEffect>> requestedPotions = new ArrayList<>();
    private static boolean throwing = false;
    private static int lastActionTick = Integer.MIN_VALUE / 2;
    private static final int ACTION_GAP_TICKS = 2;

    private final List<Holder<MobEffect>> potions = List.of(
            MobEffects.SPEED,
            MobEffects.RESISTANCE,
            MobEffects.REGENERATION,
            MobEffects.FIRE_RESISTANCE,
            MobEffects.INVISIBILITY,
            MobEffects.WATER_BREATHING,
            MobEffects.STRENGTH,
            MobEffects.INSTANT_HEALTH
    );
    private final ArrayList<Holder<MobEffect>> potionOrder = new ArrayList<>(potions.size());
    private final ArrayList<Holder<MobEffect>> needPots = new ArrayList<>(potions.size());

    public AutoPot() {
        super("Auto Pot", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;
        BehaviorUtils.noKillAura = false;
        PlayerUtil.INSTANCE.stopSpoof();
        Client.rotationManager.stopRotation();
        resetPotiHaveAEffect = false;
        potted = false;
        wasRotating = false;
        oldSlot = -1;
        potTimer.reset();
        requestedPotions.clear();
        throwing = false;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null
                || !MovementUtil.isBlockBelow() && !wasRotating
                || mc.gui.screen() instanceof ContainerScreen
                || resetPotiHaveAEffect
                || potted
                || ModuleManager.getModuleState(Scaffold.class)
                || player.isUsingItem()) {
            if (wasRotating) {
                Client.rotationManager.stopRotation();
                BehaviorUtils.noKillAura = false;
            }
            if (wasRotating || oldSlot != -1)
                PlayerUtil.INSTANCE.stopSpoof();
            if (oldSlot != -1) {
                if (player != null) {
                    player.getInventory().setSelectedSlot(oldSlot);
                    markAction(player);
                }
                oldSlot = -1;
            }
            potted = false;
            resetPotiHaveAEffect = false;
            wasRotating = false;
            requestedPotions.clear();
            throwing = false;
            return;
        }

        Inventory inventory = player.getInventory();
        needPots.clear();
        potionOrder.clear();
        potionOrder.addAll(potions);
        Collections.shuffle(potionOrder);

        for (Holder<MobEffect> effect : potionOrder) {
            if (!player.hasEffect(effect) && effect != MobEffects.INSTANT_HEALTH && effect != MobEffects.REGENERATION
                    || player.getHealth() <= 10 && (effect == MobEffects.INSTANT_HEALTH || effect == MobEffects.REGENERATION))
                needPots.add(effect);
        }

        requestedPotions.clear();
        requestedPotions.addAll(needPots);
        throwing = wasRotating || oldSlot != -1;

        if (needPots.isEmpty()) {
            if (wasRotating) {
                Client.rotationManager.stopRotation();
                BehaviorUtils.noKillAura = false;
            }
            if (wasRotating || oldSlot != -1)
                PlayerUtil.INSTANCE.stopSpoof();
            if (oldSlot != -1) {
                player.getInventory().setSelectedSlot(oldSlot);
                markAction(player);
                oldSlot = -1;
            }
            potted = false;
            wasRotating = false;
            throwing = false;
            return;
        }

        if (!potTimer.hasTimePassed(10)) {
            potTimer.update();
            return;
        }

        if (!wasRotating && ModuleManager.getModuleState(InvManager.class) && InvManager.getTicksSinceClick(player) < ACTION_GAP_TICKS)
            return;

        ItemStack mainHandStack = player.getMainHandItem();
        if (!(mainHandStack.getItem() instanceof SplashPotionItem)) {
            int hotbarIndex = findPotInHotbar(inventory, needPots);
            if (hotbarIndex != -1) {
                if (oldSlot == -1)
                    oldSlot = inventory.getSelectedSlot();
                PlayerUtil.INSTANCE.doSpoof(oldSlot);
                inventory.setSelectedSlot(hotbarIndex);
                markAction(player);
                throwing = true;
            } else if (oldSlot != -1) {
                PlayerUtil.INSTANCE.stopSpoof();
                oldSlot = -1;
                throwing = wasRotating;
            }
            return;
        }

        if (mainHandStack.getItem() instanceof SplashPotionItem) {
            if (!isThrowablePotion(mainHandStack, needPots)) {
                resetPotiHaveAEffect = true;
                wasRotating = true;
                return;
            }
        }

        PlayerUtil.INSTANCE.doSpoof(oldSlot);
        BehaviorUtils.noKillAura = true;
        if (!wasRotating) {
            Client.rotationManager.stopRotation();
            ModuleManager.rotTick = 0;
        }
        Client.rotationManager.startRotation(player.getYRot(), 90f, 180f);
        wasRotating = true;
        throwing = true;

        if (ModuleManager.rotTick < 1) return;

        PacketUtil.sendPacket(new ServerboundUseItemPacket(
                InteractionHand.MAIN_HAND,
                0,
                Client.rotationManager.getRotationYaw(),
                Client.rotationManager.getRotationPitch()
        ));

        markAction(player);

        potTimer.reset();
        potted = true;
    }

    private static void markAction(LocalPlayer player) {
        lastActionTick = player.tickCount;
    }

    public static int getTicksSinceAction(LocalPlayer player) {
        int ticks = player.tickCount - lastActionTick;
        return ticks < 0 ? Integer.MAX_VALUE : ticks;
    }

    private int findPotInHotbar(Inventory inventory, List<Holder<MobEffect>> needPots) {
        for (int i = 0; i <= 8; i++) {
            if (isThrowablePotion(inventory.getItem(i), needPots)) return i;
        }
        return -1;
    }

    private static boolean isThrowablePotion(ItemStack stack, List<Holder<MobEffect>> wanted) {
        if (stack.getItem() != Items.SPLASH_POTION) return false;

        boolean hasWanted = false;
        PotionContents potionContents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (var effectInstance : potionContents.getAllEffects()) {
            if (isBadEffect(effectInstance.getEffect())) return false;
            if (wanted.contains(effectInstance.getEffect())) hasWanted = true;
        }
        return hasWanted;
    }

    private static boolean isBadEffect(Holder<MobEffect> effect) {
        return effect.value().getCategory() == MobEffectCategory.HARMFUL;
    }

    public static List<Holder<MobEffect>> getRequestedPotions() {
        return Collections.unmodifiableList(requestedPotions);
    }

    public static boolean isThrowing() {
        return throwing;
    }

    public static boolean shouldBlockUse() {
        var player = mc.player;
        return player != null
                && throwing
                && ModuleManager.getModuleState(AutoPot.class)
                && player.getMainHandItem().getItem() instanceof SplashPotionItem;
    }

    public static boolean isRequestedPotion(ItemStack stack) {
        return isThrowablePotion(stack, requestedPotions);
    }
}
