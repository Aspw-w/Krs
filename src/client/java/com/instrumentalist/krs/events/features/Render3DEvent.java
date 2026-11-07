package com.instrumentalist.krs.events.features;

import com.instrumentalist.krs.events.EventArgument;
import com.instrumentalist.krs.events.EventListener;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Objects;

public class Render3DEvent extends EventArgument {
    public final PoseStack matrix;
    public final float partialTicks;
    public final float selfPartialTicks;

    public Render3DEvent(PoseStack matrix, float partialTicks, float selfPartialTicks) {
        this.matrix = matrix;
        this.partialTicks = partialTicks;
        this.selfPartialTicks = selfPartialTicks;
    }

    @Override
    public void call(EventListener listener) {
        Objects.requireNonNull(listener).onRender3D(this);
    }
}
