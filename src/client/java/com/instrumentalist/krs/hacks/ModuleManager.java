package com.instrumentalist.krs.hacks;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.EventManager;
import com.instrumentalist.krs.events.EventListener;
import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.combat.*;
import com.instrumentalist.krs.hacks.features.dev.*;
import com.instrumentalist.krs.hacks.features.exploit.*;
import com.instrumentalist.krs.hacks.features.exploit.disabler.DisablerModule;
import com.instrumentalist.krs.hacks.features.movement.*;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.hacks.features.nulling.PluginsDetector;
import com.instrumentalist.krs.hacks.features.player.*;
import com.instrumentalist.krs.hacks.features.render.*;
import com.instrumentalist.krs.hacks.features.level.*;
import com.instrumentalist.krs.hacks.features.level.Timer;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.GuiInputBlocker;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.packet.BlinkUtil;
import com.instrumentalist.krs.utils.render.RenderUtil;
import com.instrumentalist.mixin.Initializer;
import com.instrumentalist.krs.utils.math.Tuple;
import com.instrumentalist.krs.screen.NanoVGClickGuiScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import org.lwjgl.glfw.GLFW;
import org.nvgu.NVGU;
import xyz.breadloaf.imguimc.config.WindowConfigManager;
import xyz.breadloaf.imguimc.customwindow.ModuleRenderable;
import xyz.breadloaf.imguimc.screen.EmptyScreen;

import java.lang.reflect.Field;
import java.util.*;

public class ModuleManager implements EventListener {
    private static final ModuleManager INSTANCE = new ModuleManager();

    public static final List<Module> modules = new ArrayList<>();
    public static final List<Module> devModules = new ArrayList<>();
    public static final List<Module> allModules = new ArrayList<>();
    public static boolean isDebugRendering = false;

    public static int rotTick = 0;

    public static int transactionCounter = 0;
    public static boolean gettingTransactions = false;

    public static boolean plChecked = false;
    private static boolean needPlReCheck = false;

    private static final Map<Class<?>, List<Field>> settingsCache = new HashMap<>();
    private static final Map<Class<?>, Module> moduleByClass = new HashMap<>();
    private static final Map<String, Module> moduleByName = new HashMap<>();
    private static EventManager registeredEventManager;

    public static synchronized void onInitialize() {
        unregisterExistingListeners();

        clearModuleRegistry();

        modules.addAll(List.of(
                new SpeedModule(), new FlyModule(), new ElytraFly(), new EntityControl(), new NoBreakCooldown(),
                new Interface(), new KillAura(), new Backtrack(),
                new SpinBot(), new BrandSpoofer(), new AntiBot(), new AutoPot(), new Criticals(),
                new Teams(), new Velocity(), new Phase(), new ServerCrasher(), new Spammer(),
                new PerfectHorseJump(), new InventoryMove(), new Jesus(), new NoSlow(), new LongJump(),
                new AutoTool(), new ChestStealer(), new FastLadder(), new InvManager(), new NoFall(),
                new Sprint(), new ThunderDetector(), new KillEffect(), new CameraNoClip(),
                new AntiBlind(), new Breaker(), new CivBreak(), new Timer(), new Nuker(),
                new Xray(), new CaveFinder(), new Scaffold(), new Blink(), new TransactionConfirmBlinker(), new ExploitPatcher(),
                new PortalScreen(), new FullBright(), new TargetStrafe(), new DisablerModule(),
                new OldHitting(), new Freecam(), new ClientCape(), new EntityYawFix(), new MurdererDetector(),
                new NoHurtCam(), new FastBow(), new FastEat(), new Zoom(), new PathFinder(),
                new FastBreak(), new ViewModel(), new EntityDesync(), new Step(), new AntiVoid(),
                new AutoFish(), new NoJumpCooldown(), new AlwaysRiptide(),
                new WorldTime(), new ChatCommands(), new Rotations(),
                new PluginsDetector(), new NameTags(), new ESP(), new MovementFix(), new LongThrow(),
                new MaceExploit(), new NoPush(), new LookTP(),
                new AntiCheatDetector(), new HackerDetector(), new PackSpoofer(),
                new EntityFly(), new WidelyPutin(), new Stalker(),
                new PacketDuper(), new SneakSpam(), new ImGui(), new ClickGui(), new ConsoleSpammer(),
                new Reach(), new NoCombatDelay(), new AutoBypass(), new FakePinger(), new QuickMacro(),
                new FPSBobbing(), new CrossbowExploit(), new WTap(), new HatenaPiano(),
                new SafeWalk(), new Parkour(), new AutoWalk(), new AutoRespawn(),
                new AutoLeave(), new AutoSneak(), new MiddleClick(),
                new NoWeb(), new AntiLevitation(), new WaterSpeed(), new FastFall(),
                new Spider(), new AutoRocket(), new AutoTotem(), new ItemDropChanger(),
                new XCarry(), new PortalGodMode(), new Stasis()
        ));

        devModules.addAll(List.of(
                new BlockBreakSimulator2(), new Debugga(), new NoteBot(), new NoMoreAutism(), new ChatExcepChecker(),
                new FukumaiPlayerTracker(), new MovementUtilTest(), new TuckMod(), new Sex()
        ));

        allModules.addAll(modules);
        allModules.addAll(devModules);

        sortModules(modules);
        sortModules(devModules);
        sortModules(allModules);

        for (Module module : allModules) {
            moduleByClass.put(module.getClass(), module);
            moduleByName.put(module.moduleName, module);
        }

        registeredEventManager = Client.eventManager;
        registeredEventManager.register(INSTANCE);

        activateInitialModuleStates();
    }

    public static synchronized void shutdown() {
        unregisterExistingListeners();
    }

    private static void clearModuleRegistry() {
        modules.clear();
        devModules.clear();
        allModules.clear();
        moduleByClass.clear();
        moduleByName.clear();
        settingsCache.clear();
    }

    private static void sortModules(List<Module> moduleList) {
        moduleList.sort(Comparator.comparing(module -> module.moduleName));
    }

    private static void unregisterExistingListeners() {
        if (registeredEventManager != null) {
            registeredEventManager.unregister(INSTANCE);
            for (Module module : allModules)
                registeredEventManager.unregister(module);
        }
        for (Module module : allModules) {
            try {
                module.dispose(registeredEventManager);
            } catch (RuntimeException | Error failure) {
                System.err.println("Failed to dispose module: " + module.moduleName);
                failure.printStackTrace(System.err);
            }
        }
        registeredEventManager = null;
    }

    private static void activateInitialModuleStates() {
        for (Module module : allModules) {
            try {
                module.activateInitialState();
            } catch (RuntimeException | Error failure) {
                System.err.println("Failed to activate module: " + module.moduleName);
                failure.printStackTrace(System.err);
            }
        }

        Interface.reloadSortedModules();
    }

    public static List<Field> getSettings(Object module) {
        if (module == null) return Collections.emptyList();
        return settingsCache.computeIfAbsent(module.getClass(), ModuleManager::collectSettings);
    }

    private static List<Field> collectSettings(Class<?> moduleClass) {
        List<Field> settings = new ArrayList<>();
        for (Field field : moduleClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Module.Setting.class)) continue;

            field.setAccessible(true);
            settings.add(field);
        }
        return List.copyOf(settings);
    }

    public static boolean getModuleState(Class<? extends Module> moduleClass) {
        Module m = moduleByClass.get(moduleClass);
        return m != null && m.tempEnabled;
    }

    public static <T extends Module> T getModule(Class<T> moduleClass) {
        Module m = moduleByClass.get(moduleClass);
        return m == null ? null : moduleClass.cast(m);
    }

    public static Module getModuleByName(String moduleName) {
        return moduleName == null ? null : moduleByName.get(moduleName);
    }

    public static void pullDebugScreen() {
        WindowConfigManager.saveAll();
        Initializer.pullEveryRenderable();
        if (mc.gui.screen() instanceof EmptyScreen) {
            GuiInputBlocker.setClosingDebugScreen(true);
            try {
                mc.gui.setScreen(null);
            } finally {
                GuiInputBlocker.setClosingDebugScreen(false);
            }
        }
        isDebugRendering = false;
        transactionCounter = 0;
        gettingTransactions = false;
    }

    @Override
    public void onWorld(WorldEvent event) {
        for (Module module : modules) {
            if (module.tempEnabled && module instanceof PluginsDetector)
                module.toggle();
            else if (module.tempEnabled && (module instanceof InvManager || module instanceof ChestStealer || module instanceof Scaffold || module instanceof KillAura || module instanceof Stasis)) {
                module.toggle();
                Client.notificationManager.addNotification("World Change", "Automatically disabled " + module.moduleName);
            }
        }

        if (!ModuleManager.getModuleState(Breaker.class) && Breaker.resetAutoWhitelist(true))
            Client.notificationManager.addNotification("Auto Whitelist", "Reset bed whitelist");

        if (Client.loaded && event.previousWorld != null)
            Client.configManager.saveCurrentIfFilesExist();

        BehaviorUtils.noKillAura = false;

        PlayerUtil.INSTANCE.stopSpoof();

        if (Client.nanoVgManager != null)
            Client.nanoVgManager.discardQueuedRenderers();
        NVGU.INSTANCE.freeResources();
        NVGU.INSTANCE.clearTexture();

        plChecked = false;
        PluginsDetector.plugins = null;
        PluginsDetector.detectedAcs = null;
        needPlReCheck = true;
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (mc.player == null || mc.level == null) return;

        Packet<?> packet = event.packet;

        if (gettingTransactions && packet instanceof ServerboundPongPacket) {
            transactionCounter += 1;
            ChatUtil.printChat(((ServerboundPongPacket) packet).getId() + " (x" + transactionCounter + ")");
            ModuleRenderable.addCommandLog(((ServerboundPongPacket) packet).getId() + " (x" + transactionCounter + ")");
            if (transactionCounter >= 10) {
                ChatUtil.printChat("Logged 10x transactions!");
                ModuleRenderable.addCommandLog("Logged 10x transactions!");
                transactionCounter = 0;
                gettingTransactions = false;
            }
        }

        if (BlinkUtil.INSTANCE.getBlinking() && !BlinkUtil.INSTANCE.getLimiter() && !getModuleState(Freecam.class)) {
            if (packet instanceof ServerboundMovePlayerPacket || ModuleManager.getModuleState(KillAura.class) && KillAura.closestEntity != null && KillAura.shouldCancelUseItemOnWhileBlinking() && packet instanceof ServerboundUseItemOnPacket)
                event.cancel();

            if (packet instanceof ServerboundMovePlayerPacket.Pos || packet instanceof ServerboundMovePlayerPacket.Rot || packet instanceof ServerboundMovePlayerPacket.PosRot || packet instanceof ServerboundMovePlayerPacket.StatusOnly || packet instanceof ServerboundPlayerCommandPacket || packet instanceof ServerboundUseItemOnPacket || packet instanceof ServerboundPlayerActionPacket || packet instanceof ServerboundUseItemPacket || ModuleManager.getModuleState(TransactionConfirmBlinker.class) && packet instanceof ServerboundPongPacket) {
                event.cancel();
                BlinkUtil.INSTANCE.addPacket(packet);
            }
        }
    }

    @Override
    public void onKey(KeyboardEvent event) {
        boolean shouldOpenEmptyScreen = false;

        if (GuiInputBlocker.shouldBlockMinecraftKeyEvent(event.key) && (event.key != ImGui.getOpenGuiKey() || event.key != ClickGui.getOpenGuiKey()))
            return;

        if (event.key == ClickGui.getOpenGuiKey() && event.action == GLFW.GLFW_PRESS) {
            if (mc.gui.screen() instanceof NanoVGClickGuiScreen screen) {
                screen.onClose();
            } else if (!(mc.gui.screen() instanceof ChatScreen)) {
                GuiInputBlocker.releaseMovementKeys();
                mc.mouseHandler.releaseMouse();
                mc.gui.setScreen(new NanoVGClickGuiScreen(mc.gui.screen()));
            }
        }

        if ((event.key == ImGui.getOpenGuiKey() || event.key == GLFW.GLFW_KEY_ESCAPE) && event.action == GLFW.GLFW_PRESS) {
            if (!isDebugRendering) {
                if (event.key != ImGui.getOpenGuiKey()) return;
                Initializer.pushRenderable(new ModuleRenderable());
                isDebugRendering = true;
                GuiInputBlocker.releaseMovementKeys();
                mc.mouseHandler.releaseMouse();
                shouldOpenEmptyScreen = mc.gui.screen() == null;
            } else if (event.key == GLFW.GLFW_KEY_ESCAPE && !(mc.gui.screen() instanceof EmptyScreen) || event.key == ImGui.getOpenGuiKey())
                pullDebugScreen();
        }

        Interface interfaceModule = getModule(Interface.class);
        if (interfaceModule != null && interfaceModule.isHandlingTabGuiKey(event.key, event.action))
            return;

        if (mc.gui.screen() == null) {
            for (Module m : allModules) {
                if (event.key == m.key && event.action == GLFW.GLFW_PRESS)
                    m.toggle();
            }
        }

        if (shouldOpenEmptyScreen && mc.gui.screen() == null)
            mc.gui.setScreen(new EmptyScreen());
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.level == null) return;

        Queue<Tuple<Integer, Integer>> pending = RenderUtil.getPending();
        if (!pending.isEmpty()) {
            int perTick = 4;

            int count = Math.min(perTick, pending.size());
            for (int i = 0; i < count; i++) {
                Tuple<Integer, Integer> pos = pending.poll();
                if (pos == null) break;

                int cx = pos.getFirst();
                int cz = pos.getSecond();
                mc.level.setSectionRangeDirty(cx >> 4, -64 >> 4, cz >> 4, (cx + 15) >> 4, mc.level.getHeight() >> 4, (cz + 15) >> 4);
            }
        }
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        Client.rotationManager.update();

        if (needPlReCheck && mc.player.tickCount >= 50) {
            for (Module module : modules) {
                if (!module.tempEnabled && module instanceof PluginsDetector) {
                    module.toggle();
                    break;
                }
            }

            needPlReCheck = false;
            if (getModuleState(AntiCheatDetector.class))
                AntiCheatDetector.shouldNotify = true;
        }
    }

    @Override
    public void onMotion(MotionEvent event) {
        if (mc.player == null) return;

        if (isDebugRendering && mc.gui.screen() == null)
            mc.gui.setScreen(new EmptyScreen());

        if (Client.rotationManager.isRotating()) {
            event.yaw = Client.rotationManager.getRotationYaw();
            event.pitch = Client.rotationManager.getRotationPitch();
            rotTick++;
        } else if (rotTick != 0) rotTick = 0;
    }
}
