package com.instrumentalist.krs;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.configs.ConfigManager;
import com.instrumentalist.krs.events.EventManager;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.screen.CustomTitleScreen;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.network.FileUtil;
import com.instrumentalist.krs.utils.network.WebAccessUtil;
import com.instrumentalist.krs.utils.NotificationManager;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.render.GraphicsApiCompatibility;
import com.instrumentalist.krs.utils.rotation.RotationManager;
import com.mojang.logging.LogUtils;
import org.nvgu.NVGU;
import org.slf4j.Logger;
import xyz.breadloaf.imguimc.config.WindowConfigManager;
import xyz.breadloaf.imguimc.imgui.ImguiLoader;

import java.util.concurrent.atomic.AtomicBoolean;

public class Client implements IMinecraft {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static String clientVersion = "1.0.0-Release";

    public static volatile EventManager eventManager = null;
    public static ConfigManager configManager = null;
    public static RotationManager rotationManager = null;
    public static NanoVGManager nanoVgManager = null;
    public static NotificationManager notificationManager = null;
    public static volatile boolean loaded = false;
    public static String configLocation = "Krs-cfg";
    private static final AtomicBoolean shutdown = new AtomicBoolean();

    public static void mixinInitializeHook() {
        WindowConfigManager.loadAll();
    }

    public static void inject() {
        ChatUtil.showLog("Started loading Krs...");

        eventManager = new EventManager();
        ChatUtil.showLog("Initialized Event Manager");

        configManager = new ConfigManager();
        ChatUtil.showLog("Initialized Config Manager");

        rotationManager = new RotationManager();
        ChatUtil.showLog("Initialized Rotation Manager");

        nanoVgManager = new NanoVGManager();
        ChatUtil.showLog("Initialized NanoVG Manager");

        notificationManager = new NotificationManager();
        ChatUtil.showLog("Initialized Notification Manager");

        FileUtil.INSTANCE.doCfgNetLoaderAsync();

        ModuleManager.onInitialize();
        ChatUtil.showLog("Initialized Module Manager");

        configManager.load();
        ChatUtil.showLog("Loaded config");

        FileUtil.INSTANCE.updateCheckAsync();

        ChatUtil.showLog("Loaded Krs Client " + clientVersion);
        loaded = true;
    }

    public static void shutdown() {
        if (!shutdown.compareAndSet(false, true))
            return;

        loaded = false;
        shutdownStep("main menu audio", CustomTitleScreen::stopMainMenuMusic);
        shutdownStep("modules", ModuleManager::shutdown);
        shutdownStep("configuration network executor", FileUtil.INSTANCE::shutdown);
        shutdownStep("web executor", WebAccessUtil::shutdown);
        shutdownStep("notifications", () -> {
            if (notificationManager != null)
                notificationManager.clear();
        });
        shutdownStep("NanoVG render queue", () -> {
            if (nanoVgManager != null)
                nanoVgManager.shutdown();
        });
        shutdownStep("ImGui", ImguiLoader::shutdown);
        shutdownStep("NanoVG", () -> GraphicsApiCompatibility.runWithOpenGlContext(() -> {
            if (NVGU.INSTANCE != null)
                NVGU.INSTANCE.destroy();
        }));
        shutdownStep("graphics API compatibility renderer", GraphicsApiCompatibility::shutdown);
    }

    private static void shutdownStep(String resource, Runnable shutdownAction) {
        try {
            shutdownAction.run();
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to shut down {} resources", resource, throwable);
        }
    }

    public static void copyToClipboard(String string) {
        if (string == null) return;

        mc.keyboardHandler.setClipboard(string);
    }
}
