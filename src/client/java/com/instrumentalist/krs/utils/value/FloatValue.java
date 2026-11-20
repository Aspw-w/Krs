package com.instrumentalist.krs.utils.value;

public class FloatValue extends SettingValue<Float> {
    public final float minimum;
    public final float maximum;
    public final String suffix;

    public FloatValue(String name, float value, float minimum, float maximum, String suffix, DisplayableCondition displayable) {
        super(name, value, displayable);
        this.minimum = minimum;
        this.maximum = maximum;
        this.suffix = suffix;
    }

    public FloatValue(String name, float value, float minimum, float maximum, DisplayableCondition displayable) {
        this(name, value, minimum, maximum, "", displayable);
    }

    public FloatValue(String name, float value, float minimum, float maximum, String suffix) {
        this(name, value, minimum, maximum, suffix, () -> true);
    }

    public FloatValue(String name, float value, float minimum, float maximum) {
        this(name, value, minimum, maximum, "", () -> true);
    }

    private float step;

    public FloatValue step(float step) {
        this.step = Math.max(0f, step);
        if (this.step > 0f)
            this.value = snap(this.value);
        return this;
    }

    public String format() {
        return step >= 0.1f - 1.0E-6f
                ? String.format(java.util.Locale.ROOT, "%.1f", get())
                : String.format(java.util.Locale.ROOT, "%.2f", get());
    }

    @Override
    protected void changeValue(Float value) {
        super.changeValue(step > 0f && value != null ? snap(value) : value);
    }

    private float snap(float value) {
        float snapped = Math.round(value / step) * step;
        return Math.round(Math.clamp(snapped, minimum, maximum) * 10000f) / 10000f;
    }

    public void set(Number newValue) {
        set(newValue.floatValue());
    }
}