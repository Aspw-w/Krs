package com.instrumentalist.krs.hacks;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.configs.ConfigManager;
import com.instrumentalist.krs.configs.ConfigObject;
import com.instrumentalist.krs.events.EventManager;
import com.instrumentalist.krs.events.EventListener;
import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.utils.input.Keys;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public abstract class Module implements EventListener {
    public final String moduleName;
    public final ModuleCategory moduleCategory;
    public int key;
    public volatile boolean tempEnabled;
    public boolean showOnArray;
    private boolean disposing;

    public ConfigObject configObject = new ConfigObject(this);

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Setting {
    }

    public Module(String moduleName, ModuleCategory moduleCategory, int key, boolean tempEnabled, boolean showOnArray) {
        this.moduleName = moduleName;
        this.moduleCategory = moduleCategory;
        this.key = key;
        this.tempEnabled = tempEnabled;
        this.showOnArray = showOnArray;
    }

    public void enforceLock() {
        if (!isLocked())
            return;
        if (!isBindable())
            key = Keys.KEY_UNKNOWN;
        showOnArray = false;
    }

    public synchronized void setState(boolean state) {
        if (disposing) return;
        if (tempEnabled == state) return;

        if (state) {
            if (isLocked())
                return;
            if (moduleCategory == ModuleCategory.Dev && !ConfigManager.isDeveloperFeaturesEnabled())
                return;

            EventManager eventManager = Client.eventManager;
            if (eventManager == null)
                throw new IllegalStateException("Event manager is not initialized");

            this.tempEnabled = true;
            eventManager.register(this);
            try {
                onEnable();
            } catch (RuntimeException | Error failure) {
                this.tempEnabled = false;
                eventManager.unregister(this);
                try {
                    onDisable();
                } catch (RuntimeException | Error cleanupFailure) {
                    if (cleanupFailure != failure)
                        failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        } else {
            this.tempEnabled = false;
            EventManager eventManager = Client.eventManager;
            if (eventManager != null)
                eventManager.unregister(this);
            onDisable();
        }
    }

    public synchronized void toggle() {
        if (disposing) return;

        try {
            setState(!this.tempEnabled);
        } finally {
            Interface.reloadSortedModules();
        }
    }

    final synchronized void activateInitialState() {
        if (!tempEnabled)
            return;

        tempEnabled = false;
        setState(true);
    }

    final synchronized void dispose(EventManager eventManager) {
        boolean wasEnabled = tempEnabled;
        disposing = true;
        tempEnabled = false;
        try {
            if (eventManager != null)
                eventManager.unregister(this);
            if (Client.eventManager != null && Client.eventManager != eventManager)
                Client.eventManager.unregister(this);

            if (wasEnabled)
                onDisable();
        } finally {
            tempEnabled = false;
            if (eventManager != null)
                eventManager.unregister(this);
            if (Client.eventManager != null && Client.eventManager != eventManager)
                Client.eventManager.unregister(this);
            disposing = false;
        }
    }

    public boolean isLocked() {
        return false;
    }

    public boolean isBindable() {
        return !isLocked();
    }

    public boolean isFunctional() {
        return tempEnabled || isLocked();
    }

    public String description() {
        return null;
    }

    public String tag() {
        return null;
    }

    public abstract void onEnable();
    public abstract void onDisable();
}
