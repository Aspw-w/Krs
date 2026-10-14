package com.instrumentalist.krs.utils.render;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;

/**
 * Holds first person item render states that Krs resolves in addition to vanilla's.
 * <p>
 * Since 26.3 held items are resolved into render states while the frame is extracted, so a spoofed hotbar item has to
 * be resolved at that point too before the hand renderer can submit it.
 */
public final class FirstPersonItemOverrides {
    private static final ItemStackRenderState SPOOF_RENDER_STATE = new ItemStackRenderState();
    private static ItemStack spoofStack = ItemStack.EMPTY;
    private static boolean spoofResolved;

    private FirstPersonItemOverrides() {
    }

    public static ItemStackRenderState beginSpoofExtraction(ItemStack stack) {
        SPOOF_RENDER_STATE.clear();
        spoofStack = stack == null ? ItemStack.EMPTY : stack;
        spoofResolved = stack != null;
        return SPOOF_RENDER_STATE;
    }

    public static void clearSpoof() {
        SPOOF_RENDER_STATE.clear();
        spoofStack = ItemStack.EMPTY;
        spoofResolved = false;
    }

    /**
     * Returns the resolved render state for the spoofed item, or {@code null} when it was not resolved for this stack.
     */
    public static ItemStackRenderState spoofRenderState(ItemStack stack) {
        return spoofResolved && stack == spoofStack ? SPOOF_RENDER_STATE : null;
    }
}
