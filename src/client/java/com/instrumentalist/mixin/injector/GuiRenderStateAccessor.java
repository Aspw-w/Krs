package com.instrumentalist.mixin.injector;

import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(GuiRenderState.class)
public interface GuiRenderStateAccessor {
    @Accessor("strata")
    List<?> krs$getStrata();
}
