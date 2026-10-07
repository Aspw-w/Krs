package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.render.GuiEntityRenderGuard;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureRendererMixin<T extends PictureInPictureRenderState> {

    @WrapMethod(method = "prepare")
    private void krs$guardGuiEntityPrepare(T state, GuiRenderState guiState, FeatureRenderDispatcher dispatcher, int scale, Operation<Void> original) {
        if (!(state instanceof GuiEntityRenderState)) {
            original.call(state, guiState, dispatcher, scale);
            return;
        }

        GuiEntityRenderGuard.run(() -> original.call(state, guiState, dispatcher, scale));
    }
}
