package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.Zoom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer", remap = false)
public abstract class IrisHandRendererMixin {

    @Inject(method = "canRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void krs$hideHandWhileZooming(CallbackInfoReturnable<Boolean> cir) {
        if (Zoom.shouldZoom())
            cir.setReturnValue(false);
    }
}
