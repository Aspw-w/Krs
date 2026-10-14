package com.instrumentalist.krs.configs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.*;
import com.instrumentalist.krs.utils.math.Tuple;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class ConfigObject {

    private final Module module;

    public ConfigObject(Module module) {
        this.module = module;
    }

    public Tuple<JsonObject, JsonObject> save() {
        JsonObject jo = new JsonObject();
        JsonObject bjo = new JsonObject();
        jo.addProperty("toggle", module.tempEnabled);
        bjo.addProperty("bind", module.key);
        bjo.addProperty("show", module.showOnArray);
        JsonArray moduleSettings = new JsonArray();
        JsonArray bindSettings = new JsonArray();
        for (Field field : ModuleManager.getSettings(module)) {
            Object setting = getFieldValue(field);
            if (setting == null) continue;
            if (setting instanceof SettingValue<?> settingValue) {
                JsonObject jsonObject = new JsonObject();
                switch (setting) {
                    case TextValue textValue -> jsonObject.addProperty(textValue.name, textValue.value);
                    case BooleanValue booleanValue ->
                            jsonObject.addProperty(booleanValue.name, booleanValue.value);
                    case FloatValue floatValue -> jsonObject.addProperty(floatValue.name, floatValue.value);
                    case IntValue intValue -> jsonObject.addProperty(intValue.name, intValue.value);
                    case ListValue listValue -> jsonObject.addProperty(listValue.name, listValue.value);
                    case KeyBindValue keyBindValue -> jsonObject.addProperty(keyBindValue.name, keyBindValue.value);
                    case ColorValue colorValue -> {
                        JsonObject colorJson = new JsonObject();
                        colorJson.addProperty("Red", colorValue.value.getRed());
                        colorJson.addProperty("Green", colorValue.value.getGreen());
                        colorJson.addProperty("Blue", colorValue.value.getBlue());
                        colorJson.addProperty("Alpha", colorValue.value.getAlpha());
                        jsonObject.add(colorValue.name, colorJson);
                    }
                    default -> {
                    }
                }
                if (!jsonObject.isEmpty()) {
                    if (isStoredIn(settingValue, KeyBindValue.ConfigStorage.BIND_CONFIG))
                        bindSettings.add(jsonObject);
                    else
                        moduleSettings.add(jsonObject);
                }
            }
        }
        jo.add("settings", moduleSettings);
        if (!bindSettings.isEmpty())
            bjo.add("settings", bindSettings);
        return Tuple.of(jo, bjo);
    }

    public void load(JsonObject jsonObject) {
        load(jsonObject, false);
    }

    /**
     * @param legacyKeys whether key binds in {@code jsonObject} are GLFW key codes written by a pre 26.3 client
     */
    public void load(JsonObject jsonObject, boolean legacyKeys) {
        if (jsonObject == null) return;

        Boolean requestedState = null;
        JsonElement toggle = jsonObject.get("toggle");
        if (toggle != null && !toggle.isJsonNull()) {
            try {
                requestedState = toggle.getAsBoolean();
            } catch (RuntimeException ignored) {
            }
        }

        // Disable with the old settings so module cleanup sees the state it was
        // initialized with. Enable only after the new settings have been applied.
        if (Boolean.FALSE.equals(requestedState))
            applyModuleState(false);

        loadSettings(jsonObject, KeyBindValue.ConfigStorage.MODULE_CONFIG, legacyKeys);

        if (Boolean.TRUE.equals(requestedState))
            applyModuleState(true);
    }

    private void applyModuleState(boolean state) {
        try {
            module.setState(state);
        } catch (RuntimeException ignored) {
        }
    }

    public void loadBind(JsonObject jsonObject) {
        loadBind(jsonObject, false);
    }

    public void loadBind(JsonObject jsonObject, boolean legacyKeys) {
        if (jsonObject == null) return;

        loadSettings(jsonObject, KeyBindValue.ConfigStorage.BIND_CONFIG, legacyKeys);
    }

    private void loadSettings(JsonObject jsonObject, KeyBindValue.ConfigStorage configStorage, boolean legacyKeys) {
        JsonElement settings = jsonObject.get("settings");
        if (settings == null || !settings.isJsonArray()) return;

        Map<String, SettingValue<?>> settingByName = getSettingValuesByName();
        settings.getAsJsonArray().forEach(jsonElement -> {
            if (!jsonElement.isJsonObject()) return;

            jsonElement.getAsJsonObject().entrySet().forEach(entry -> {
                SettingValue<?> value = settingByName.get(entry.getKey());
                if (value != null && isStoredIn(value, configStorage))
                    loadSettingValue(value, entry.getValue(), legacyKeys);
            });
        });
    }

    private boolean isStoredIn(SettingValue<?> value, KeyBindValue.ConfigStorage configStorage) {
        if (value instanceof KeyBindValue keyBindValue)
            return keyBindValue.configStorage() == configStorage;

        return configStorage == KeyBindValue.ConfigStorage.MODULE_CONFIG;
    }

    private Object getFieldValue(Field field) {
        try {
            return field.get(module);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private Map<String, SettingValue<?>> getSettingValuesByName() {
        Map<String, SettingValue<?>> settingByName = new HashMap<>();
        for (Field field : ModuleManager.getSettings(module)) {
            Object value = getFieldValue(field);
            if (value instanceof SettingValue<?> settingValue)
                settingByName.put(settingValue.name, settingValue);
        }
        return settingByName;
    }

    private void loadSettingValue(SettingValue<?> value, JsonElement jsonValue, boolean legacyKeys) {
        if (jsonValue == null || jsonValue.isJsonNull()) return;

        try {
            switch (value) {
                case TextValue textValue -> textValue.set(jsonValue.getAsString());
                case BooleanValue booleanValue -> booleanValue.set(jsonValue.getAsBoolean());
                case FloatValue floatValue -> floatValue.set(jsonValue.getAsFloat());
                case IntValue intValue -> intValue.set(jsonValue.getAsInt());
                case ListValue listValue -> listValue.set(jsonValue.getAsString());
                case KeyBindValue keyBindValue -> {
                    int key = jsonValue.getAsInt();
                    keyBindValue.set(legacyKeys ? Keys.fromLegacyGlfw(key) : key);
                }
                case ColorValue colorValue -> loadColorValue(colorValue, jsonValue);
                default -> {
                }
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void loadColorValue(ColorValue colorValue, JsonElement jsonValue) {
        if (!jsonValue.isJsonObject()) return;

        JsonObject colorJson = jsonValue.getAsJsonObject();
        if (!colorJson.has("Red") || !colorJson.has("Green") || !colorJson.has("Blue") || !colorJson.has("Alpha"))
            return;

        int r = clampColor(colorJson.get("Red").getAsInt());
        int g = clampColor(colorJson.get("Green").getAsInt());
        int b = clampColor(colorJson.get("Blue").getAsInt());
        int a = clampColor(colorJson.get("Alpha").getAsInt());
        colorValue.set(new Color(r, g, b, a));
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
