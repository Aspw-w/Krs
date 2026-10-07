package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.render.GuiEntityRenderGuard;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {

    @WrapMethod(method = "extractEntityInInventoryFollowsMouse")
    private static void krs$guardInventoryEntityExtract(
            GuiGraphicsExtractor extractor,
            int x0,
            int y0,
            int x1,
            int y1,
            int scale,
            float extraY,
            float mouseX,
            float mouseY,
            LivingEntity entity,
            Operation<Void> original
    ) {
        GuiEntityRenderGuard.run(() -> original.call(extractor, x0, y0, x1, y1, scale, extraY, mouseX, mouseY, entity));
    }
}
