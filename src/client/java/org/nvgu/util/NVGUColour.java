package org.nvgu.util;

import org.lwjgl.nanovg.NVGPaint;
import org.nvgu.NVGU;

import java.awt.*;
import java.awt.color.ColorSpace;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.nvgu.util.TrackedNanoVG.nvgFill;
import static org.nvgu.util.TrackedNanoVG.nvgStroke;

public class NVGUColour extends Color {

    private NVGPaint paint = null;
    private Mode mode = Mode.FILL;

    public NVGUColour(NVGPaint paint) {
        super(0, 0, 0);
        setPaint(paint);
    }

    public NVGUColour(int r, int g, int b) {
        super(r, g, b);
    }

    public NVGUColour(int r, int g, int b, int a) {
        super(r, g, b, a);
    }

    public NVGUColour(int rgb) {
        super(rgb);
    }

    public NVGUColour(int rgba, boolean hasalpha) {
        super(rgba, hasalpha);
    }

    public NVGUColour(float r, float g, float b) {
        super(r, g, b);
    }

    public NVGUColour(float r, float g, float b, float a) {
        super(r, g, b, a);
    }

    public NVGUColour(ColorSpace cspace, float[] components, float alpha) {
        super(cspace, components, alpha);
    }

    public void apply(NVGU instance, RenderType type) {
        switch (mode) {
            case FILL: {
                nvgFillColor(instance.getHandle(), instance.createAndStoreColour(this));
                nvgFill(instance.getHandle());

                break;
            }

            case PAINT: {
                switch (type) {
                    case FILL: {
                        nvgFillPaint(instance.getHandle(), paint);
                        nvgFill(instance.getHandle());
                        break;
                    }

                    case STROKE: {
                        nvgStrokePaint(instance.getHandle(), paint);
                        nvgStroke(instance.getHandle());
                        break;
                    }
                }

                break;
            }
        }
    }

    public NVGPaint getPaint() {
        return paint;
    }

    public NVGUColour setPaint(NVGPaint paint) {
        this.paint = paint;
        this.mode = Mode.PAINT;

        return this;
    }

    private enum Mode {
        FILL,
        PAINT
    }

    public enum RenderType {
        FILL,
        STROKE
    }

}
