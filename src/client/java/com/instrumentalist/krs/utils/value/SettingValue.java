package com.instrumentalist.krs.utils.value;

import com.instrumentalist.krs.utils.ChatUtil;

import java.util.Objects;

public abstract class SettingValue<T> {
    public final String name;
    public T value;
    public final DisplayableCondition canDisplay;

    public SettingValue(String name, T value, DisplayableCondition canDisplay) {
        this.name = name;
        this.value = value;
        this.canDisplay = canDisplay;
    }

    public void set(T newValue) {
        if (Objects.equals(newValue, value)) return;

        T oldValue = get();

        try {
            onChange(oldValue, newValue);
            changeValue(newValue);
            onChanged(oldValue, newValue);
        } catch (Exception e) {
            ChatUtil.showLog("[ValueSystem (" + name + ")]: " + e.getClass().getName() + " (" + e.getMessage() + ") [" + oldValue + " >> " + newValue + "]");
        }
    }

    public T get() {
        return value;
    }

    protected void changeValue(T value) {
        this.value = value;
    }

    protected void onChange(T oldValue, T newValue) {}

    protected void onChanged(T oldValue, T newValue) {}

    @FunctionalInterface
    public interface DisplayableCondition {
        boolean canDisplay();
    }
}
