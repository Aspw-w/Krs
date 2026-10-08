package com.instrumentalist.krs.configs;

import com.instrumentalist.krs.utils.IMinecraft;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.utils.network.FileUtil;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class ConfigManager implements IMinecraft {

    private final Gson gson = new Gson();
    private static final String ONLINE_CONFIG_SUFFIX = "-online";
    private static final String UPDATED_AT_KEY = "_updated";
    private static final float DEFAULT_MAIN_MENU_MUSIC_VOLUME = 0f;
    private static final long MAX_LOCAL_CONFIG_BYTES = 4L * 1024L * 1024L;
    public static final int MAX_CONFIG_NAME_LENGTH = 96;
    public final File BASE_DIR = new File(mc.gameDirectory, Client.configLocation);
    private final File CLIENT_FILE = new File(BASE_DIR, "client.json");
    public String configCurrent = "default", bindCurrent = "default";
    private float mainMenuMusicVolume = DEFAULT_MAIN_MENU_MUSIC_VOLUME;
    private int mainMenuMusicFrame;
    private final AtomicLong onlineConfigRequestGeneration = new AtomicLong();
    private final Map<String, Long> moduleUpdatedCache = new HashMap<>();
    private final Map<String, Long> bindUpdatedCache = new HashMap<>();

    public void saveConfigFile(String configName, Boolean saveToDefaultFile) {
        saveConfigFile(configName, saveToDefaultFile, null);
    }

    public void saveConfigFile(String configName, Boolean saveToDefaultFile, Long incomingUpdated) {
        configName = normalizeConfigName(configName);

        this.configCurrent = configName;

        if (Boolean.TRUE.equals(saveToDefaultFile))
            this.saveClientJS();

        final JsonObject configObject = new JsonObject();
        ModuleManager.modules.forEach(m -> {
            if (m.configObject != null && m.moduleCategory != null)
                configObject.add(m.moduleName, m.configObject.save().getFirst());
        });

        File base = new File(BASE_DIR, "module_configs");
        File dir = new File(base, this.configCurrent + ".json");

        writeJson(dir, withUpdatedTimestamp(configObject, dir, false, this.configCurrent, incomingUpdated), "module config");
    }

    public void saveBindFile(String bindName, Boolean saveToDefaultFile) {
        bindName = normalizeConfigName(bindName);

        this.bindCurrent = bindName;

        if (Boolean.TRUE.equals(saveToDefaultFile))
            this.saveClientJS();

        final JsonObject bindObject = new JsonObject();
        ModuleManager.modules.forEach(m -> {
            if (m.configObject != null && m.moduleCategory != null)
                bindObject.add(m.moduleName, m.configObject.save().getSecond());
        });

        File base = new File(BASE_DIR, "bind_configs");
        File bindFile = new File(base, this.bindCurrent + ".json");

        writeJson(bindFile, withUpdatedTimestamp(bindObject, bindFile, true, this.bindCurrent, null), "bind config");
    }

    public void loadConfig(String configName, boolean online) {
        if (!online) {
            loadLocalConfig(configName, true);
        } else {
            final String onlineConfigName = stripJsonExtension(configName);
            final long requestGeneration = onlineConfigRequestGeneration.incrementAndGet();
            boolean accepted = FileUtil.INSTANCE.loadOnlineAsync(onlineConfigName, jsonString -> mc.execute(() -> {
                if (!Client.loaded || requestGeneration != onlineConfigRequestGeneration.get()
                        || jsonString == null || jsonString.isBlank())
                    return;

                try {
                    final JsonObject configObject = parseJsonObject(jsonString);
                    if (configObject == null) return;

                    loadModuleConfigObject(configObject);
                    saveConfigFile(onlineConfigClientName(onlineConfigName), true, readUpdatedMillis(configObject));
                    Interface.reloadSortedModules();
                } catch (Exception e) {
                    System.err.println("Failed to load online module config: " + e.getMessage());
                }
            }));
            if (!accepted)
                System.err.println("Failed to queue online module config: " + onlineConfigName);
        }
    }

    private void loadLocalConfig(String configName, boolean persistSelection) {
        File base = new File(BASE_DIR, "module_configs");
        configName = normalizeConfigName(configName);
        onlineConfigRequestGeneration.incrementAndGet();
        this.configCurrent = configName;

        File configFile = new File(base, this.configCurrent + ".json");
        if (!configFile.isFile()) return;

        if (persistSelection)
            this.saveClientJS();

        try {
            final JsonObject configObject = readJsonObject(configFile);
            if (configObject == null) return;
            loadModuleConfigObject(configObject);
        } catch (Exception e) {
            System.err.println("Failed to load module config: " + e.getMessage());
        }
    }

    public void loadBind(String bindName) {
        loadBind(bindName, true);
    }

    private void loadBind(String bindName, boolean persistSelection) {
        bindName = normalizeConfigName(bindName);

        this.bindCurrent = bindName;

        File base = new File(BASE_DIR, "bind_configs");
        File bindFile = new File(base, this.bindCurrent + ".json");

        if (!bindFile.isFile()) return;

        if (persistSelection)
            this.saveClientJS();

        try {
            final JsonObject bindData = readJsonObject(bindFile);
            if (bindData == null) return;
            bindData.entrySet().forEach(entry -> {
                if (isConfigMetadataKey(entry.getKey()) || !entry.getValue().isJsonObject()) return;

                final String moduleName = entry.getKey();
                final JsonObject moduleData = entry.getValue().getAsJsonObject();
                com.instrumentalist.krs.hacks.Module module = ModuleManager.getModuleByName(moduleName);
                if (module == null) return;

                if (moduleData.has("bind")) {
                    try {
                        module.key = moduleData.get("bind").getAsInt();
                    } catch (RuntimeException ignored) {
                    }
                }
                if (moduleData.has("show")) {
                    try {
                        module.showOnArray = moduleData.get("show").getAsBoolean();
                    } catch (RuntimeException ignored) {
                    }
                }
                if (module.configObject != null)
                    module.configObject.loadBind(moduleData);
            });
            Interface.reloadSortedModules();
        } catch (Exception e) {
            System.err.println("Failed to load bind config: " + e.getMessage());
        }
    }

    public void saveClientJS() {
        JsonObject client = new JsonObject();
        client.addProperty("module-config", this.configCurrent);
        client.addProperty("bind-config", this.bindCurrent);
        client.addProperty("main-menu-music-volume", this.mainMenuMusicVolume);
        client.addProperty("main-menu-music-frame", this.mainMenuMusicFrame);

        writeJson(CLIENT_FILE, client, "client config");
    }

    public void load() {
        File configBase = new File(BASE_DIR, "module_configs");
        File configDir = new File(configBase, "default.json");
        File bindBase = new File(BASE_DIR, "bind_configs");
        File bindDir = new File(bindBase, "default.json");
        if (!CLIENT_FILE.exists())
            this.saveClientJS();
        if (!configBase.exists() || !configDir.exists())
            saveConfigFile(this.configCurrent, true);
        if (!bindBase.exists() || !bindDir.exists())
            saveBindFile(this.bindCurrent, true);
        freshConfig();
        loadLocalConfig(this.configCurrent, false);
        loadBind(this.bindCurrent, false);
    }

    public void saveCurrentIfFilesExist() {
        boolean savedAnyConfig = false;
        File configFile = new File(new File(BASE_DIR, "module_configs"), this.configCurrent + ".json");
        if (configFile.isFile()) {
            saveConfigFile(this.configCurrent, false);
            savedAnyConfig = true;
        }

        File bindFile = new File(new File(BASE_DIR, "bind_configs"), this.bindCurrent + ".json");
        if (bindFile.isFile()) {
            saveBindFile(this.bindCurrent, false);
            savedAnyConfig = true;
        }

        if (savedAnyConfig)
            saveClientJS();
    }

    public void freshConfig() {
        try {
            final JsonObject client = readJsonObject(CLIENT_FILE);
            if (client == null) return;
            if (client.has("module-config")) {
                this.configCurrent = normalizeConfigName(client.get("module-config").getAsString());
            }
            if (client.has("bind-config")) {
                this.bindCurrent = normalizeConfigName(client.get("bind-config").getAsString());
            }
            if (client.has("main-menu-music-volume"))
                this.mainMenuMusicVolume = clampMainMenuMusicVolume(client.get("main-menu-music-volume").getAsFloat());
            if (client.has("main-menu-music-frame"))
                this.mainMenuMusicFrame = clampMainMenuMusicFrame(client.get("main-menu-music-frame").getAsInt());
        } catch (Exception e) {
            System.err.println("Failed to load client config: " + e.getMessage());
        }
    }

    public float getMainMenuMusicVolume() {
        return mainMenuMusicVolume;
    }

    public void setMainMenuMusicVolume(float volume, boolean save) {
        float clampedVolume = clampMainMenuMusicVolume(volume);
        if (Float.compare(this.mainMenuMusicVolume, clampedVolume) == 0) {
            if (save && !CLIENT_FILE.isFile())
                saveClientJS();
            return;
        }

        this.mainMenuMusicVolume = clampedVolume;
        if (save)
            saveClientJS();
    }

    public int getMainMenuMusicFrame() {
        return mainMenuMusicFrame;
    }

    public void setMainMenuMusicFrame(int frame, boolean save) {
        int clampedFrame = clampMainMenuMusicFrame(frame);
        if (this.mainMenuMusicFrame == clampedFrame) {
            if (save && !CLIENT_FILE.isFile())
                saveClientJS();
            return;
        }

        this.mainMenuMusicFrame = clampedFrame;
        if (save)
            saveClientJS();
    }

    public static String onlineConfigClientName(String configName) {
        String normalizedName = normalizeConfigName(configName);

        if (endsWithIgnoreCase(normalizedName, ONLINE_CONFIG_SUFFIX))
            return normalizedName;

        int maxBaseLength = MAX_CONFIG_NAME_LENGTH - ONLINE_CONFIG_SUFFIX.length();
        if (normalizedName.length() > maxBaseLength)
            normalizedName = normalizedName.substring(0, maxBaseLength).stripTrailing();
        return normalizedName + ONLINE_CONFIG_SUFFIX;
    }

    public static String normalizeConfigName(String configName) {
        String stripped = stripJsonExtension(configName);
        if (stripped.isBlank())
            return "default";

        StringBuilder safeName = new StringBuilder(Math.min(stripped.length(), MAX_CONFIG_NAME_LENGTH));
        for (int i = 0; i < stripped.length() && safeName.length() < MAX_CONFIG_NAME_LENGTH; i++) {
            char c = stripped.charAt(i);
            if (Character.isISOControl(c) || c == '/' || c == '\\' || c == ':' || c == '*' || c == '?'
                    || c == '"' || c == '<' || c == '>' || c == '|') {
                safeName.append('_');
            } else {
                safeName.append(c);
            }
        }

        int end = safeName.length();
        if (end > 0 && Character.isHighSurrogate(safeName.charAt(end - 1)))
            end--;
        while (end > 0 && (safeName.charAt(end - 1) == ' ' || safeName.charAt(end - 1) == '.'))
            end--;

        String result = safeName.substring(0, end).trim();
        if (result.isEmpty() || result.equals(".") || result.equals(".."))
            return "default";

        String windowsBaseName = result;
        int extensionIndex = windowsBaseName.indexOf('.');
        if (extensionIndex >= 0)
            windowsBaseName = windowsBaseName.substring(0, extensionIndex);
        if (isReservedWindowsFileName(windowsBaseName))
            result = '_' + result;

        return result;
    }

    private static boolean isReservedWindowsFileName(String name) {
        if (name == null)
            return false;

        String upperName = name.toUpperCase(java.util.Locale.ROOT);
        if (upperName.equals("CON") || upperName.equals("PRN") || upperName.equals("AUX") || upperName.equals("NUL"))
            return true;
        if (upperName.length() != 4)
            return false;

        char suffix = upperName.charAt(3);
        return suffix >= '1' && suffix <= '9'
                && (upperName.startsWith("COM") || upperName.startsWith("LPT"));
    }

    private static String stripJsonExtension(String configName) {
        if (configName == null)
            return "";

        String trimmedName = configName.trim();
        return endsWithIgnoreCase(trimmedName, ".json") ? trimmedName.substring(0, trimmedName.length() - 5) : trimmedName;
    }

    private static boolean endsWithIgnoreCase(String value, String suffix) {
        return value.length() >= suffix.length() && value.regionMatches(true, value.length() - suffix.length(), suffix, 0, suffix.length());
    }

    public Long configUpdatedAt(boolean bind, String configName) {
        String cacheKey = updatedCacheKey(configName);
        if (cacheKey == null)
            return null;

        Map<String, Long> cache = bind ? bindUpdatedCache : moduleUpdatedCache;
        Long cached = cache.get(cacheKey);
        if (cached != null)
            return cached;

        File file = configFile(bind, configName);
        if (file == null || !file.isFile())
            return null;

        Long updated = readUpdatedAt(file);
        if (updated != null)
            cache.put(cacheKey, updated);
        return updated;
    }

    public void forgetConfigUpdated(boolean bind, String configName) {
        String cacheKey = updatedCacheKey(configName);
        if (cacheKey == null)
            return;

        (bind ? bindUpdatedCache : moduleUpdatedCache).remove(cacheKey);
    }

    public String renameConfig(boolean bind, String oldName, String newName) {
        String from = normalizeConfigName(oldName);
        String to = normalizeConfigName(newName);
        if (from.isBlank() || to.isBlank())
            return null;
        if (from.equals(to))
            return from;

        File fromFile = configFile(bind, from);
        File toFile = configFile(bind, to);
        if (fromFile == null || toFile == null || !fromFile.isFile())
            return null;

        boolean caseOnly = from.equalsIgnoreCase(to);
        if (!caseOnly && toFile.isFile())
            return null;

        Path source = fromFile.toPath();
        Path target = toFile.toPath();
        Path temp = null;
        try {
            if (caseOnly) {
                temp = source.resolveSibling("." + fromFile.getName() + "." + System.nanoTime() + ".rename");
                moveConfigFile(source, temp, false);
                moveConfigFile(temp, target, false);
                temp = null;
            } else {
                moveConfigFile(source, target, false);
            }
        } catch (IOException e) {
            System.err.println("Failed to rename config: " + e.getMessage());
            if (temp != null) {
                try {
                    moveConfigFile(temp, source, true);
                } catch (IOException ignored) {
                }
            }
            return null;
        }

        Long updated = (bind ? bindUpdatedCache : moduleUpdatedCache).remove(updatedCacheKey(from));
        if (updated != null)
            rememberUpdated(bind, to, updated);
        else {
            Long stored = readUpdatedAt(toFile);
            if (stored != null)
                rememberUpdated(bind, to, stored);
        }

        if (bind) {
            if (from.equalsIgnoreCase(this.bindCurrent)) {
                this.bindCurrent = to;
                saveClientJS();
            }
        } else if (from.equalsIgnoreCase(this.configCurrent)) {
            this.configCurrent = to;
            saveClientJS();
        }

        FileUtil.INSTANCE.invalidateLocalConfigCache();
        return to;
    }

    private static void moveConfigFile(Path source, Path target, boolean replace) throws IOException {
        try {
            if (replace)
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            else
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            if (replace)
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            else
                Files.move(source, target);
        }
    }

    private void loadModuleConfigObject(JsonObject configObject) {
        configObject.entrySet().forEach(entry -> {
            if (isConfigMetadataKey(entry.getKey()) || !entry.getValue().isJsonObject()) return;

            com.instrumentalist.krs.hacks.Module module = ModuleManager.getModuleByName(entry.getKey());
            if (module != null && module.configObject != null)
                module.configObject.load(entry.getValue().getAsJsonObject());
        });
    }

    private JsonObject readJsonObject(File file) throws IOException {
        long fileSize = Files.size(file.toPath());
        if (fileSize > MAX_LOCAL_CONFIG_BYTES)
            throw new IOException("Config file is too large: " + fileSize + " bytes");

        try (InputStream is = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            try {
                return gson.fromJson(isr, JsonObject.class);
            } catch (StackOverflowError error) {
                throw new IOException("Config JSON is nested too deeply", error);
            }
        }
    }

    private JsonObject parseJsonObject(String json) throws IOException {
        if (json == null)
            return null;
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_LOCAL_CONFIG_BYTES)
            throw new IOException("Config JSON is too large");

        try {
            return gson.fromJson(json, JsonObject.class);
        } catch (StackOverflowError error) {
            throw new IOException("Config JSON is nested too deeply", error);
        }
    }

    private float clampMainMenuMusicVolume(float volume) {
        if (Float.isNaN(volume))
            return DEFAULT_MAIN_MENU_MUSIC_VOLUME;

        return Math.max(0f, Math.min(1f, volume));
    }

    private int clampMainMenuMusicFrame(int frame) {
        return Math.max(0, frame);
    }

    private JsonObject withUpdatedTimestamp(JsonObject payload, File file, boolean bind, String configName, Long incomingUpdated) {
        long updated = incomingUpdated != null && incomingUpdated > 0L
                ? incomingUpdated
                : System.currentTimeMillis();
        if (file.isFile()) {
            try {
                JsonObject existing = readJsonObject(file);
                if (existing != null) {
                    Long previous = readUpdatedMillis(existing);
                    existing.remove(UPDATED_AT_KEY);
                    if (existing.equals(payload)) {
                        if (previous != null && previous > 0L)
                            updated = previous;
                        else {
                            long lastModified = file.lastModified();
                            updated = lastModified > 0L ? lastModified : updated;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        rememberUpdated(bind, configName, updated);

        JsonObject output = new JsonObject();
        output.addProperty(UPDATED_AT_KEY, updated);
        payload.entrySet().forEach(entry -> output.add(entry.getKey(), entry.getValue()));
        return output;
    }

    private Long readUpdatedAt(File file) {
        try {
            JsonObject existing = readJsonObject(file);
            Long stored = readUpdatedMillis(existing);
            if (stored != null && stored > 0L)
                return stored;
        } catch (Exception ignored) {
        }

        long lastModified = file.lastModified();
        return lastModified > 0L ? lastModified : null;
    }

    private void rememberUpdated(boolean bind, String configName, long updated) {
        String cacheKey = updatedCacheKey(configName);
        if (cacheKey == null)
            return;

        (bind ? bindUpdatedCache : moduleUpdatedCache).put(cacheKey, updated);
    }

    private File configFile(boolean bind, String configName) {
        String normalized = normalizeConfigName(configName);
        if (normalized.isBlank())
            return null;

        return new File(new File(BASE_DIR, bind ? "bind_configs" : "module_configs"), normalized + ".json");
    }

    private static String updatedCacheKey(String configName) {
        String normalized = normalizeConfigName(configName);
        return normalized.isBlank() ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static Long readUpdatedMillis(JsonObject object) {
        if (object == null || !object.has(UPDATED_AT_KEY) || object.get(UPDATED_AT_KEY).isJsonNull())
            return null;

        JsonElement element = object.get(UPDATED_AT_KEY);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
            return null;

        try {
            long updated = element.getAsLong();
            return updated > 0L ? updated : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isConfigMetadataKey(String key) {
        return UPDATED_AT_KEY.equals(key);
    }

    private void writeJson(File file, JsonObject jsonObject, String label) {
        Path tempFile = null;
        try {
            Path targetFile = file.toPath();
            Path parent = targetFile.getParent();
            if (parent == null)
                throw new IOException("Config path has no parent directory: " + targetFile);
            Files.createDirectories(parent);

            tempFile = Files.createTempFile(parent, file.getName() + ".", ".tmp");
            byte[] jsonBytes = gson.toJson(jsonObject).getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(jsonBytes);
                while (buffer.hasRemaining())
                    channel.write(buffer);
                channel.force(true);
            }

            try {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            tempFile = null;
            Path directoryName = parent.getFileName();
            if (directoryName != null && (directoryName.toString().equals("module_configs")
                    || directoryName.toString().equals("bind_configs")))
                FileUtil.INSTANCE.invalidateLocalConfigCache();
        } catch (Exception e) {
            System.err.println("Failed to save " + label + ": " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
