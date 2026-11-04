package com.instrumentalist.mixin.oringo;

import net.minecraft.world.item.ItemStack;

public interface ILegacyHeldItems {
    void krs$scheduleMainHandReequip(ItemStack visible, int delay);

    void krs$scheduleSyncedMainHandReequip(ItemStack current, int delay);
}
