package xyz.breadloaf.imguimc.customwindow;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.nulling.PluginsDetector;
import com.instrumentalist.krs.hacks.features.exploit.ServerCrasher;
import com.instrumentalist.krs.hacks.features.player.ChatCommands;
import com.instrumentalist.krs.hacks.features.render.ImGui;
import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.network.FileUtil;
import com.instrumentalist.krs.utils.network.ServerLookup;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.pathfinder.MainPathFinder;
import com.instrumentalist.krs.utils.value.*;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.EntityDataAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import xyz.breadloaf.imguimc.config.WindowConfigManager;
import xyz.breadloaf.imguimc.config.WindowState;
import xyz.breadloaf.imguimc.interfaces.Renderable;
import xyz.breadloaf.imguimc.interfaces.Theme;
import xyz.breadloaf.imguimc.theme.ImGuiClassicTheme;
import xyz.breadloaf.imguimc.theme.ImGuiDarkTheme;
import xyz.breadloaf.imguimc.theme.ImGuiLightTheme;

import java.awt.Color;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ModuleRenderable implements Renderable, IMinecraft {

    private static final int MAX_COMMAND_LOGS = 50;
    private static final int MAX_COMMAND_LOG_LENGTH = 4_096;
    private static final Theme LIGHT_THEME = new ImGuiLightTheme();
    private static final Theme DARK_THEME = new ImGuiDarkTheme();
    private static final Theme CLASSIC_THEME = new ImGuiClassicTheme();
    private static ImString searchQuery = new ImString(256);
    public static final List<String> commandLogs = new CopyOnWriteArrayList<>();
    private final ImString commandInput = new ImString(256);
    private final ImString newModuleConfigName = new ImString(256);
    private final ImString newBindConfigName = new ImString(256);
    private final Map<String, ImString> inputBuffers = new HashMap<>();
    private final Map<Module, List<SettingValue<?>>> moduleSettingsCache = new IdentityHashMap<>();
    private final List<Path> moduleConfigScratch = new ArrayList<>();
    private final List<Path> bindConfigScratch = new ArrayList<>();
    private boolean loadedConfig = false;

    @FunctionalInterface
    private interface WindowContent {
        void render();
    }

    @Override
    public String getName() {
        return "Module Renderable";
    }

    @Override
    public Theme getTheme() {
        return switch (ImGui.theme.get().toLowerCase()) {
            case "light" -> LIGHT_THEME;
            case "dark" -> DARK_THEME;
            default -> CLASSIC_THEME;
        };
    }

    @Override
    public void render() {
        renderWindow("Modules", this::renderModulesWindow);
        renderWindow("Commands", this::renderCommandWindow);
        renderWindow("Configs", this::renderConfigsWindow);
        loadedConfig = true;
    }

    private void renderWindow(String windowName, WindowContent content) {
        WindowState state = Objects.requireNonNull(WindowConfigManager.getWindowState(windowName));
        if (!loadedConfig) {
            imgui.ImGui.setNextWindowSize(state.width, state.height, ImGuiCond.Once);
            imgui.ImGui.setNextWindowPos(state.x, state.y, ImGuiCond.Once);
        }

        boolean visible = imgui.ImGui.begin(windowName);
        try {
            rememberWindowState(state);
            if (visible)
                content.render();
        } finally {
            imgui.ImGui.end();
        }
    }

    private static void rememberWindowState(WindowState state) {
        state.x = imgui.ImGui.getWindowPosX();
        state.y = imgui.ImGui.getWindowPosY();
        state.width = imgui.ImGui.getWindowWidth();
        state.height = imgui.ImGui.getWindowHeight();
    }

    private void renderModulesWindow() {
        if (imgui.ImGui.beginTabBar("Module Categories")) {
            try {
                for (ModuleCategory category : ModuleCategory.values()) {
                    if (category == null) continue;

                    if (imgui.ImGui.beginTabItem(category.name())) {
                        try {
                            renderCategoryModules(category);
                        } finally {
                            imgui.ImGui.endTabItem();
                        }
                    }
                }

                if (imgui.ImGui.beginTabItem("Search")) {
                    try {
                        renderSearchModules();
                    } finally {
                        imgui.ImGui.endTabItem();
                    }
                }
            } finally {
                imgui.ImGui.endTabBar();
            }
        }
    }

    private void renderCategoryModules(ModuleCategory category) {
        if (imgui.ImGui.beginChild("ModuleList##" + category.name(), 0, imgui.ImGui.getContentRegionAvailY(), false)) {
            for (Module module : ModuleManager.allModules) {
                if (module.moduleCategory != category) continue;
                renderModuleEntry(module);
            }
        }
        imgui.ImGui.endChild();
    }

    private void renderSearchModules() {
        if (imgui.ImGui.beginChild("SearchPanel", 0, imgui.ImGui.getContentRegionAvailY(), false)) {
            imgui.ImGui.text("Search Modules:");
            imgui.ImGui.inputText("Search", searchQuery);

            String query = searchQuery.get().toLowerCase().replace(" ", "");
            List<Module> searchResults = new ArrayList<>();
            for (Module module : ModuleManager.allModules) {
                if (module.moduleCategory == null || module.moduleCategory == ModuleCategory.Dev) continue;
                if (query.isEmpty() || module.moduleName.replace(" ", "").toLowerCase().contains(query) || module.moduleCategory.name().toLowerCase().contains(query))
                    searchResults.add(module);
            }

            imgui.ImGui.spacing();
            imgui.ImGui.text("Matches: " + searchResults.size());
            imgui.ImGui.spacing();

            if (imgui.ImGui.beginChild("SearchResults", 0, imgui.ImGui.getContentRegionAvailY(), true)) {
                for (Module module : searchResults)
                    renderModuleEntry(module);
            }
            imgui.ImGui.endChild();
        }
        imgui.ImGui.endChild();
    }

    private void renderModuleEntry(Module module) {
        imgui.ImGui.pushID(module.moduleName);
        try {
            boolean lightTheme = ImGui.theme.get().equalsIgnoreCase("light");
            if (module.tempEnabled && lightTheme) {
                imgui.ImGui.pushStyleColor(ImGuiCol.Header, 219, 207, 255, 255);
                imgui.ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 205, 188, 250, 255);
                imgui.ImGui.pushStyleColor(ImGuiCol.HeaderActive, 190, 172, 242, 255);
                imgui.ImGui.pushStyleColor(ImGuiCol.Text, 36, 28, 54, 255);
            } else if (module.tempEnabled) {
                imgui.ImGui.pushStyleColor(ImGuiCol.Header, 138, 88, 255, 92);
                imgui.ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 170, 112, 255, 132);
                imgui.ImGui.pushStyleColor(ImGuiCol.HeaderActive, 232, 95, 205, 150);
                imgui.ImGui.pushStyleColor(ImGuiCol.Text, 250, 246, 255, 255);
            } else if (lightTheme) {
                imgui.ImGui.pushStyleColor(ImGuiCol.Header, 243, 239, 252, 255);
                imgui.ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 230, 222, 247, 255);
                imgui.ImGui.pushStyleColor(ImGuiCol.HeaderActive, 216, 205, 242, 255);
                imgui.ImGui.pushStyleColor(ImGuiCol.Text, 64, 55, 82, 255);
            } else {
                imgui.ImGui.pushStyleColor(ImGuiCol.Header, 48, 44, 62, 150);
                imgui.ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 70, 62, 88, 190);
                imgui.ImGui.pushStyleColor(ImGuiCol.HeaderActive, 92, 78, 115, 215);
                imgui.ImGui.pushStyleColor(ImGuiCol.Text, 224, 216, 238, 255);
            }

            if (imgui.ImGui.collapsingHeader(module.moduleName + "##module-header"))
                renderModuleSettings(module);
        } finally {
            imgui.ImGui.popStyleColor(4);
            imgui.ImGui.popID();
        }
    }

    private void renderCommandWindow() {
        if (imgui.ImGui.beginTabBar("Command Categories")) {
            try {
                if (imgui.ImGui.beginTabItem("Command")) {
                    try {
                        renderCommandTab();
                    } finally {
                        imgui.ImGui.endTabItem();
                    }
                }

                if (imgui.ImGui.beginTabItem("Multiplay")) {
                    try {
                        renderMultiPlayTab();
                    } finally {
                        imgui.ImGui.endTabItem();
                    }
                }
            } finally {
                imgui.ImGui.endTabBar();
            }
        }
    }

    private void renderCommandTab() {
        if (imgui.ImGui.beginChild("CommandPanel", 0, imgui.ImGui.getContentRegionAvailY(), false)) {
            imgui.ImGui.text("Command Execution");

            if (commandLogs.isEmpty()) {
                addCommandLog("Type commands here");
            }

            imgui.ImGui.spacing();
            imgui.ImGui.text("Enter a command:");

            if (imgui.ImGui.inputText("Input here", commandInput, ImGuiInputTextFlags.EnterReturnsTrue)) {
                executeCommand(commandInput.get(), false);
                commandInput.set("");
                imgui.ImGui.setKeyboardFocusHere(-1);
            }

            imgui.ImGui.spacing();
            imgui.ImGui.separator();
            imgui.ImGui.spacing();

            if (imgui.ImGui.beginChild("LogWindow", 0, imgui.ImGui.getContentRegionAvailY(), true)) {
                for (String log : commandLogs)
                    imgui.ImGui.textWrapped(log);

                if (imgui.ImGui.getScrollY() >= imgui.ImGui.getScrollMaxY())
                    imgui.ImGui.setScrollHereY(1.0f);
            }
            imgui.ImGui.endChild();
        }
        imgui.ImGui.endChild();
    }

    private static void renderMultiPlayTab() {
        if (imgui.ImGui.beginChild("MultiPlayPanel", 0, imgui.ImGui.getContentRegionAvailY(), false)) {
            if (imgui.ImGui.button("Force disconnect from server")) {
                if (mc.level != null)
                    mc.level.disconnect(Component.literal("Disconnected"));
            }

                imgui.ImGui.text("Player List");
            if (imgui.ImGui.beginChild("PlayerList", 0, imgui.ImGui.getContentRegionAvailY(), true)) {
                if (mc.level != null && mc.getConnection() != null) {
                    List<AbstractClientPlayer> sortedPlayerEntities = new ArrayList<>(mc.level.players());
                    sortedPlayerEntities.sort(Comparator.comparing(player -> player.getName().getString(), String.CASE_INSENSITIVE_ORDER));

                    for (AbstractClientPlayer playerEntity : sortedPlayerEntities) {
                        PlayerInfo entry = mc.getConnection().getPlayerInfo(playerEntity.getUUID());
                        String playerName = playerEntity.getName().getString();
                        String showString = entry != null && playerEntity instanceof LocalPlayer ? "[You] Name: " + playerName + ", Ping: " + entry.getLatency() : entry != null ? "Name: " + playerName + ", Ping: " + entry.getLatency() : "Name: " + playerName + ", Ping: null";
                        imgui.ImGui.pushID(playerName);
                        try {
                            if (imgui.ImGui.collapsingHeader(showString)) {
                                imgui.ImGui.separator();
                                imgui.ImGui.indent();

                                if (imgui.ImGui.button("Kill"))
                                    mc.getConnection().sendCommand("kill " + playerName);
                                if (imgui.ImGui.button("Teleport"))
                                    mc.getConnection().sendCommand("tp " + playerName);
                                if (imgui.ImGui.button("Crash"))
                                    mc.getConnection().sendCommand("execute at " + playerName + " run particle minecraft:explosion ~ ~ ~ 0.1 0.1 0.1 0.01 100000000 force");

                                imgui.ImGui.unindent();
                                imgui.ImGui.separator();
                                imgui.ImGui.spacing();
                            }
                        } finally {
                            imgui.ImGui.popID();
                        }
                    }
                }
            }
            imgui.ImGui.endChild();
        }
        imgui.ImGui.endChild();
    }

    private void renderConfigsWindow() {
        if (imgui.ImGui.beginTabBar("Config Categories")) {
            try {
                if (imgui.ImGui.beginTabItem("Module")) {
                    try {
                        renderModuleConfigsTab();
                    } finally {
                        imgui.ImGui.endTabItem();
                    }
                }

                if (imgui.ImGui.beginTabItem("Bind")) {
                    try {
                        renderBindConfigsTab();
                    } finally {
                        imgui.ImGui.endTabItem();
                    }
                }

                if (imgui.ImGui.beginTabItem("Online")) {
                    try {
                        renderOnlineConfigsTab();
                    } finally {
                        imgui.ImGui.endTabItem();
                    }
                }
            } finally {
                imgui.ImGui.endTabBar();
            }
        }
    }

    private void renderModuleConfigsTab() {
        if (imgui.ImGui.beginChild("ModuleConfigsPanel", 0, imgui.ImGui.getContentRegionAvailY(), false)) {
            imgui.ImGui.text("Create a new Module Config:");
            if (imgui.ImGui.inputText("New Config Name", newModuleConfigName, ImGuiInputTextFlags.EnterReturnsTrue)) {
                String configName = newModuleConfigName.get();
                if (!configName.isEmpty()) {
                    Client.configManager.saveConfigFile(Client.configManager.configCurrent, false);
                    Client.configManager.saveConfigFile(configName, true);
                    newModuleConfigName.set("");
                }
            }

            imgui.ImGui.spacing();
            imgui.ImGui.separator();
            imgui.ImGui.spacing();

            fillSortedConfigs(moduleConfigScratch, FileUtil.INSTANCE.getModuleFiles());
            if (!moduleConfigScratch.isEmpty()) {
                imgui.ImGui.text("Available Module Configs:");
                if (imgui.ImGui.beginChild("ModuleConfigsList", 0, imgui.ImGui.getContentRegionAvailY(), true)) {
                    for (Path config : moduleConfigScratch) {
                        String configName = configName(config);
                        String showConfigName = configName.equalsIgnoreCase(Client.configManager.configCurrent) ? configName + " <- Current" : configName;

                        if (imgui.ImGui.selectable(showConfigName)) {
                            saveCurrentModuleConfig();
                            Client.configManager.loadConfig(configName, false);
                            saveLoadedModuleConfig(configName);
                            inputBuffers.clear();
                        }
                    }
                }
                imgui.ImGui.endChild();
            } else {
                imgui.ImGui.text("No module configs found.");
            }
        }
        imgui.ImGui.endChild();
    }

    private void renderBindConfigsTab() {
        if (imgui.ImGui.beginChild("BindConfigsPanel", 0, imgui.ImGui.getContentRegionAvailY(), false)) {
            imgui.ImGui.text("Create a new Bind Config:");
            if (imgui.ImGui.inputText("New Config Name", newBindConfigName, ImGuiInputTextFlags.EnterReturnsTrue)) {
                String configName = newBindConfigName.get();
                if (!configName.isEmpty()) {
                    Client.configManager.saveBindFile(Client.configManager.bindCurrent, false);
                    Client.configManager.saveBindFile(configName, true);
                    newBindConfigName.set("");
                }
            }

            imgui.ImGui.spacing();
            imgui.ImGui.separator();
            imgui.ImGui.spacing();

            fillSortedConfigs(bindConfigScratch, FileUtil.INSTANCE.getBindFiles());
            if (!bindConfigScratch.isEmpty()) {
                imgui.ImGui.text("Available Bind Configs:");
                if (imgui.ImGui.beginChild("BindConfigsList", 0, imgui.ImGui.getContentRegionAvailY(), true)) {
                    for (Path config : bindConfigScratch) {
                        String configName = configName(config);
                        String showConfigName = configName.equalsIgnoreCase(Client.configManager.bindCurrent) ? configName + " <- Current" : configName;

                        if (imgui.ImGui.selectable(showConfigName)) {
                            saveCurrentBindConfig();
                            Client.configManager.loadBind(configName);
                            saveLoadedBindConfig(configName);
                            inputBuffers.clear();
                        }
                    }
                }
                imgui.ImGui.endChild();
            } else {
                imgui.ImGui.text("No bind configs found.");
            }
        }
        imgui.ImGui.endChild();
    }

    private void renderOnlineConfigsTab() {
        if (imgui.ImGui.beginChild("OnlineConfigsPanel", 0, imgui.ImGui.getContentRegionAvailY(), false)) {
            List<String> moduleConfigs = FileUtil.INSTANCE.getOnlineCfgs();
            if (!moduleConfigs.isEmpty()) {
                imgui.ImGui.text("Available Online Configs:");
                if (imgui.ImGui.beginChild("OnlineConfigsList", 0, imgui.ImGui.getContentRegionAvailY(), true)) {
                    for (String config : moduleConfigs) {
                        String configName = config.replace(".json", "");

                        if (imgui.ImGui.selectable(configName)) {
                            saveCurrentModuleConfig();
                            Client.configManager.loadConfig(configName, true);
                            inputBuffers.clear();
                        }
                    }
                }
                imgui.ImGui.endChild();
            } else {
                imgui.ImGui.text("No online configs found.");
            }
        }
        imgui.ImGui.endChild();
    }

    private static void fillSortedConfigs(List<Path> target, List<Path> configs) {
        target.clear();

        for (Path config : configs) {
            if (config != null)
                target.add(config);
        }

        target.sort(Comparator.comparing(ModuleRenderable::configName, String.CASE_INSENSITIVE_ORDER));
    }

    private static String configName(Path config) {
        return config.getFileName().toString().replace(".json", "");
    }

    private static void saveCurrentModuleConfig() {
        File prevBase = new File(Client.configManager.BASE_DIR, "module_configs");
        File prevModuleFile = new File(prevBase, Client.configManager.configCurrent + ".json");
        if (prevBase.exists() && prevModuleFile.exists())
            Client.configManager.saveConfigFile(Client.configManager.configCurrent, false);
    }

    private static void saveLoadedModuleConfig(String configName) {
        File base = new File(Client.configManager.BASE_DIR, "module_configs");
        File moduleFile = new File(base, configName + ".json");
        if (base.exists() && moduleFile.exists())
            Client.configManager.saveConfigFile(configName, true);
    }

    private static void saveCurrentBindConfig() {
        File prevBase = new File(Client.configManager.BASE_DIR, "bind_configs");
        File prevBindFile = new File(prevBase, Client.configManager.bindCurrent + ".json");
        if (prevBase.exists() && prevBindFile.exists())
            Client.configManager.saveBindFile(Client.configManager.bindCurrent, false);
    }

    private static void saveLoadedBindConfig(String configName) {
        File base = new File(Client.configManager.BASE_DIR, "bind_configs");
        File bindFile = new File(base, configName + ".json");
        if (base.exists() && bindFile.exists())
            Client.configManager.saveBindFile(configName, true);
    }

    private static void showLog(Boolean chat, String message) {
        if (chat)
            ChatUtil.printChat(message);
        else log(message);
    }

    public static void executeCommand(String command, Boolean chatMode) {
        if (command.isBlank()) {
            if (chatMode)
                showLog(true, ChatCommands.prefix.get() + "help to show every commands");
            return;
        }

        if (command.equalsIgnoreCase("help") || command.equalsIgnoreCase("commands")) {
            showLog(chatMode, "help/commands -> show every commands");
            showLog(chatMode, "say <message> -> force send messages");
            showLog(chatMode, "t/toggle <module> -> toggle module");
            showLog(chatMode, "ign/name -> copy your current username");
            showLog(chatMode, "bind <module> <key> -> bind module");
            showLog(chatMode, "crash -> crash the server");
            showLog(chatMode, "copynbt -> copy your holding item nbt");
            showLog(chatMode, "pl/plugins -> detect server plugins");
            showLog(chatMode, "transaction -> debug transaction packets (x10)");
            showLog(chatMode, "session -> show your current session id");
            showLog(chatMode, "vclip <height> -> teleport up from current position");
            showLog(chatMode, "tp <x> <y> <z> or <player> -> teleport to everywhere");
            showLog(chatMode, "notify <title> <message> -> show custom notification");
            showLog(chatMode, "server <address> -> dump raw IP, vulns, linked servers, and all lookup data");
        } else if (command.equalsIgnoreCase("server") || command.toLowerCase(Locale.ROOT).startsWith("server ")) {
            String target = command.length() > 7 ? command.substring(7).trim() : "";
            String error = ServerLookup.start(target, lines -> mc.execute(() -> {
                for (String line : lines)
                    showLog(chatMode, line);
            }));
            showLog(chatMode, Objects.requireNonNullElse(error, "Looking up server..."));
        } else if (command.toLowerCase().startsWith("t " ) || command.toLowerCase().startsWith("toggle " )) {
            String moduleName = "";

            if (command.toLowerCase().startsWith("t " ))
                moduleName = command.toLowerCase().replace("t ", "").replace(" ", "");
            if (command.toLowerCase().startsWith("toggle " ))
                moduleName = command.toLowerCase().replace("toggle ", "").replace(" ", "");

            for (Module module : ModuleManager.modules) {
                if (moduleName.equals(module.moduleName.toLowerCase().replace(" ", ""))) {
                    module.toggle();
                    Client.notificationManager.addNotification("Toggle", module.tempEnabled ? "Enabled " + module.moduleName : "Disabled " + module.moduleName);
                    return;
                }
            }

            showLog(chatMode, "Module " + moduleName + " was not found");
        } else if (command.equalsIgnoreCase("crash")) {
            showLog(chatMode, "Crashing server...");
            for (Module module : ModuleManager.modules) {
                if (module instanceof ServerCrasher && !module.tempEnabled)
                    module.toggle();
            }
        } else if (command.equalsIgnoreCase("transaction")) {
            showLog(chatMode, "Logging transactions...");
            ModuleManager.gettingTransactions = true;
        } else if (command.equalsIgnoreCase("ign") || command.equalsIgnoreCase("name")) {
            showLog(chatMode, "Copied username to your clipboard!");
            Client.copyToClipboard(mc.getGameProfile().name());
        } else if (command.equalsIgnoreCase("copynbt")) {
            if (mc.player != null) {
                MutableComponent text = Component.empty();

                try {
                    DataAccessor dataCommandObject = new EntityDataAccessor(mc.player);
                    NbtPathArgument.NbtPath handPath = NbtPathArgument.NbtPath.of("SelectedItem");
                    List<Tag> nbtElement = handPath.get(dataCommandObject.getData());
                    if (!nbtElement.isEmpty()) {
                        text.append(" ").append(NbtUtils.toPrettyComponent(nbtElement.getFirst()));
                    }
                    showLog(chatMode, "Copied nbt to your clipboard!");
                } catch (CommandSyntaxException ignored) {
                    text.append(" ").append("{}");
                    showLog(chatMode, "No NBTs found");
                }

                ChatUtil.printModifiedChat(text);
                Client.copyToClipboard(text.getString().substring(1));
            } else showLog(chatMode, "player is null");
        } else if (command.equalsIgnoreCase("pl") || command.equalsIgnoreCase("plugins")) {
            showLog(chatMode, "Detecting plugins...");
            for (Module module : ModuleManager.modules) {
                if (module instanceof PluginsDetector && !module.tempEnabled)
                    module.toggle();
            }
        } else if (command.startsWith("say ")) {
            String message = command.toLowerCase().replace("say ", "");
            if (mc.player != null) {
                if (message.startsWith("/"))
                    mc.player.connection.sendCommand(message.substring(1));
                else mc.player.connection.sendChat(message);
            }
        } else if (command.startsWith("bind ")) {
            String moduleName;
            moduleName = command.toLowerCase().replace("bind ", "");

            String[] parts = moduleName.split(" ");

            if (parts.length == 2) {
                for (Module module : ModuleManager.allModules) {
                    if (Objects.equals(module.moduleName.toLowerCase().replace(" ", ""), parts[0])) {
                        try {
                            int newKey = InputConstants.getKey("key.keyboard." + parts[1].toLowerCase()).getValue();
                            module.key = newKey;
                            showLog(chatMode, "Bound " + keyName(newKey) + " key to " + module.moduleName + " module");
                        } catch (NumberFormatException ignored) {
                            module.key = GLFW.GLFW_KEY_UNKNOWN;
                            showLog(chatMode, "Unbound " + module.moduleName + " module");
                        }
                        return;
                    }
                }
            }

            showLog(chatMode, "Module " + moduleName + " was not found");
        } else if (command.startsWith("tp ")) {
            String tpStr;
            tpStr = command.toLowerCase().replace("tp ", "");

            String[] parts = tpStr.split(" ");

            if (mc.player != null && mc.level != null) {
                if (parts.length == 1) {
                    boolean found = false;

                    for (Player playerEntity : mc.level.players()) {
                        if (playerEntity.getName().getString().equalsIgnoreCase(parts[0])) {
                            found = true;

                            ArrayList<Vec3> paths;
                            paths = MainPathFinder.computePath(mc.player.position(), playerEntity.position());

                            if (paths == null || paths.isEmpty()) return;

                            for (Vec3 path : paths) {
                                PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(path.x, path.y, path.z, true, mc.player.horizontalCollision));
                            }

                            mc.player.setPos(playerEntity.position());

                            showLog(chatMode, "teleported");
                            Client.notificationManager.addNotification("Success", "Teleported!");

                            break;
                        }
                    }

                    if (found) return;
                } else if (parts.length == 3) {
                    try {
                        String realX = parts[0];
                        boolean addXFix = false;
                        if (realX.contains("~")) {
                            realX = realX.replace("~", "");
                            if (realX.isBlank())
                                realX = "0";
                            addXFix = true;
                        }
                        double xPos = Double.parseDouble(realX);
                        if (addXFix) xPos += mc.player.position().x;

                        String realY = parts[1];
                        boolean addYFix = false;
                        if (realY.contains("~")) {
                            realY = realY.replace("~", "");
                            if (realY.isBlank())
                                realY = "0";
                            addYFix = true;
                        }
                        double yPos = Double.parseDouble(realY);
                        if (addYFix) yPos += mc.player.position().y;

                        String realZ = parts[2];
                        boolean addZFix = false;
                        if (realZ.contains("~")) {
                            realZ = realZ.replace("~", "");
                            if (realZ.isBlank())
                                realZ = "0";
                            addZFix = true;
                        }
                        double zPos = Double.parseDouble(realZ);
                        if (addZFix) zPos += mc.player.position().z;

                        ArrayList<Vec3> paths;
                        paths = MainPathFinder.computePath(mc.player.position(), new Vec3(xPos, yPos, zPos));

                        if (paths == null || paths.isEmpty()) return;

                        for (Vec3 path : paths) {
                            PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(path.x, path.y, path.z, true, mc.player.horizontalCollision));
                        }

                        mc.player.setPos(paths.getLast());

                        showLog(chatMode, "teleported");
                        Client.notificationManager.addNotification("Success", "Teleported!");
                        return;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            showLog(chatMode, "Failed to teleport");
            Client.notificationManager.addNotification("Failed", "Failed to teleport");
        } else if (command.startsWith("vclip ")) {
            String distance;
            distance = command.toLowerCase().replace("vclip ", "");

            try {
                double dist = Double.parseDouble(distance);
                if (mc.player != null) {
                    if (dist > 10 || dist < -10) {
                        for (int i = 0; i <= 9; i++) {
                            PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(mc.player.position().x, mc.player.position().y, mc.player.position().z, true, mc.player.horizontalCollision));
                        }
                    }
                    mc.player.setPos(mc.player.position().x, mc.player.position().y + dist, mc.player.position().z);
                    showLog(chatMode, "Vertical clipped");
                    Client.notificationManager.addNotification("Success", "Vertical clipped");
                }
            } catch (NumberFormatException ignored) {
                showLog(chatMode, "Failed to vclip");
                Client.notificationManager.addNotification("Failed", "Failed to vclip");
            }
        } else if (command.equalsIgnoreCase("session")) {
            if (mc.getConnection() != null)
                showLog(chatMode, String.valueOf(mc.getConnection().getLocalGameProfile().id()));
            else showLog(chatMode, "User is null");
        } else if (command.startsWith("notify ")) {
            String notify;
            notify = command.toLowerCase().replace("notify ", "");

            String[] parts = notify.split(" ");

            if (parts.length == 2) {
                showLog(chatMode, "Notified");
                Client.notificationManager.addNotification(parts[0], parts[1]);
                return;
            }

            showLog(chatMode, "Failed to notify");
        } else {
            showLog(chatMode, "Unknown command: " + command);
        }
    }

    private static void log(String log) {
        addCommandLog(log);
    }

    public static void addCommandLog(String log) {
        String safeLog = log == null ? "" : log;
        if (safeLog.length() > MAX_COMMAND_LOG_LENGTH)
            safeLog = safeLog.substring(0, MAX_COMMAND_LOG_LENGTH);

        synchronized (commandLogs) {
            while (commandLogs.size() >= MAX_COMMAND_LOGS)
                commandLogs.removeFirst();
            commandLogs.add(safeLog);
        }
    }

    private void renderModuleSettings(Module module) {
        if (imgui.ImGui.checkbox("Toggle Module##" + module.moduleName, module.tempEnabled)) {
            module.toggle();
        }

        if (imgui.ImGui.checkbox("Show on array##" + module.moduleName, module.showOnArray)) {
            module.showOnArray = !module.showOnArray;
            Interface.reloadSortedModules();
        }

        if (module.description() != null)
            imgui.ImGui.textColored(255, 205, 92, 255, module.description());

        String shownKey = keyName(module.key);
        ImString currentKey = inputBuffer("module-key:" + module.moduleName, shownKey, 64);
        imgui.ImGui.inputText("Keybind ##" + module.moduleName, currentKey);
        if (imgui.ImGui.isItemDeactivatedAfterEdit()) {
            module.key = parseKey(currentKey.get());
            currentKey.set(keyName(module.key));
        }

        List<SettingValue<?>> settings = collectSettings(module);

        if (!settings.isEmpty()) {
            imgui.ImGui.separator();
            imgui.ImGui.indent();
        }

        for (SettingValue<?> setting : settings) {
            if (setting.canDisplay.canDisplay())
                renderSetting(setting, module.moduleName);
        }

        if (!settings.isEmpty()) {
            imgui.ImGui.unindent();
            imgui.ImGui.separator();
            imgui.ImGui.spacing();
        }
    }

    private List<SettingValue<?>> collectSettings(Module module) {
        if (module == null)
            return Collections.emptyList();

        return moduleSettingsCache.computeIfAbsent(module, this::readSettings);
    }

    private List<SettingValue<?>> readSettings(Module module) {
        List<SettingValue<?>> values = new ArrayList<>();
        for (Field field : ModuleManager.getSettings(module)) {
            try {
                Object setting = field.get(module);
                if (setting instanceof SettingValue<?> value)
                    values.add(value);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return values.isEmpty() ? Collections.emptyList() : List.copyOf(values);
    }

    private void renderSetting(SettingValue<?> settingValue, String moduleName) {
        switch (settingValue) {
            case BooleanValue booleanValue -> {
                if (imgui.ImGui.checkbox(booleanValue.name + "##" + moduleName, booleanValue.get())) {
                    booleanValue.set(!booleanValue.get());
                }
            }
            case TextValue textValue -> {
                ImString currentText = inputBuffer("text:" + moduleName + ":" + textValue.name, textValue.get(), 256);
                if (imgui.ImGui.inputText(textValue.name + "##" + moduleName, currentText)) {
                    textValue.set(currentText.get());
                }
            }
            case ListValue listValue -> {
                ImInt selectedIndex = new ImInt(listValue.getCurrentIndex());
                if (imgui.ImGui.combo(listValue.name + "##" + moduleName, selectedIndex, listValue.values)) {
                    listValue.setByIndex(selectedIndex.get());
                }
            }
            case FloatValue floatValue -> {
                float[] currentFloat = {normalizeNegativeZero(floatValue.value)};
                if (imgui.ImGui.sliderFloat(floatValue.name + "##" + moduleName, currentFloat, floatValue.minimum, floatValue.maximum, "%.1f")) {
                    floatValue.set(normalizeNegativeZero(currentFloat[0]));
                }
            }
            case IntValue intValue -> {
                int[] currentInt = {intValue.value};
                if (imgui.ImGui.sliderInt(intValue.name + "##" + moduleName, currentInt, intValue.minimum, intValue.maximum)) {
                    intValue.set(currentInt[0]);
                }
            }
            case ColorValue colorValue -> {
                float[] currentColor = {
                        colorValue.value.getRed() / 255f,
                        colorValue.value.getGreen() / 255f,
                        colorValue.value.getBlue() / 255f
                };
                imgui.ImGui.setNextItemWidth(150);
                if (imgui.ImGui.colorEdit3(colorValue.name + "##" + moduleName, currentColor)) {
                    Color newColor = new Color(
                            (int) (currentColor[0] * 255),
                            (int) (currentColor[1] * 255),
                            (int) (currentColor[2] * 255)
                    );
                    colorValue.set(newColor);
                }
            }
            case KeyBindValue keyBindValue -> {
                String shownKey = keyName(keyBindValue.get());
                ImString currentKey = inputBuffer("setting-key:" + moduleName + ":" + keyBindValue.name, shownKey, 64);
                imgui.ImGui.inputText(keyBindValue.name + " (" + shownKey.toUpperCase() + ")##" + moduleName, currentKey);
                if (imgui.ImGui.isItemDeactivatedAfterEdit()) {
                    keyBindValue.set(parseKey(currentKey.get()));
                    currentKey.set(keyName(keyBindValue.get()));
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + settingValue);
        }
    }

    private ImString inputBuffer(String id, String value, int capacity) {
        return inputBuffers.computeIfAbsent(id, key -> new ImString(value, capacity));
    }

    private static float normalizeNegativeZero(float value) {
        return Math.abs(value) < 0.05f ? 0.0f : value;
    }

    public static String keyName(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN)
            return "NONE";

        String name = GLFW.glfwGetKeyName(key, GLFW.glfwGetKeyScancode(key));
        if (name != null)
            return name.toUpperCase();

        return fallbackKeyName(key).toUpperCase();
    }

    private static String fallbackKeyName(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_UNKNOWN, GLFW.GLFW_KEY_SLASH, GLFW.GLFW_KEY_DELETE -> "NONE";
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_ESCAPE -> "Escape";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_PAGE_UP -> "Page Up";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "Page Down";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps Lock";
            case GLFW.GLFW_KEY_SCROLL_LOCK -> "Scroll Lock";
            case GLFW.GLFW_KEY_NUM_LOCK -> "Num Lock";
            case GLFW.GLFW_KEY_PRINT_SCREEN -> "Print Screen";
            case GLFW.GLFW_KEY_PAUSE -> "Pause";
            case GLFW.GLFW_KEY_F1 -> "F1";
            case GLFW.GLFW_KEY_F2 -> "F2";
            case GLFW.GLFW_KEY_F3 -> "F3";
            case GLFW.GLFW_KEY_F4 -> "F4";
            case GLFW.GLFW_KEY_F5 -> "F5";
            case GLFW.GLFW_KEY_F6 -> "F6";
            case GLFW.GLFW_KEY_F7 -> "F7";
            case GLFW.GLFW_KEY_F8 -> "F8";
            case GLFW.GLFW_KEY_F9 -> "F9";
            case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11";
            case GLFW.GLFW_KEY_F12 -> "F12";
            case GLFW.GLFW_KEY_F13 -> "F13";
            case GLFW.GLFW_KEY_F14 -> "F14";
            case GLFW.GLFW_KEY_F15 -> "F15";
            case GLFW.GLFW_KEY_F16 -> "F16";
            case GLFW.GLFW_KEY_F17 -> "F17";
            case GLFW.GLFW_KEY_F18 -> "F18";
            case GLFW.GLFW_KEY_F19 -> "F19";
            case GLFW.GLFW_KEY_F20 -> "F20";
            case GLFW.GLFW_KEY_F21 -> "F21";
            case GLFW.GLFW_KEY_F22 -> "F22";
            case GLFW.GLFW_KEY_F23 -> "F23";
            case GLFW.GLFW_KEY_F24 -> "F24";
            case GLFW.GLFW_KEY_F25 -> "F25";
            case GLFW.GLFW_KEY_KP_0 -> "Keypad 0";
            case GLFW.GLFW_KEY_KP_1 -> "Keypad 1";
            case GLFW.GLFW_KEY_KP_2 -> "Keypad 2";
            case GLFW.GLFW_KEY_KP_3 -> "Keypad 3";
            case GLFW.GLFW_KEY_KP_4 -> "Keypad 4";
            case GLFW.GLFW_KEY_KP_5 -> "Keypad 5";
            case GLFW.GLFW_KEY_KP_6 -> "Keypad 6";
            case GLFW.GLFW_KEY_KP_7 -> "Keypad 7";
            case GLFW.GLFW_KEY_KP_8 -> "Keypad 8";
            case GLFW.GLFW_KEY_KP_9 -> "Keypad 9";
            case GLFW.GLFW_KEY_KP_DECIMAL -> "Keypad .";
            case GLFW.GLFW_KEY_KP_DIVIDE -> "Keypad /";
            case GLFW.GLFW_KEY_KP_MULTIPLY -> "Keypad *";
            case GLFW.GLFW_KEY_KP_SUBTRACT -> "Keypad -";
            case GLFW.GLFW_KEY_KP_ADD -> "Keypad +";
            case GLFW.GLFW_KEY_KP_ENTER -> "Keypad Enter";
            case GLFW.GLFW_KEY_KP_EQUAL -> "Keypad =";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "Left Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "Left Control";
            case GLFW.GLFW_KEY_LEFT_ALT -> "Left Alt";
            case GLFW.GLFW_KEY_LEFT_SUPER -> "Left Super";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "Right Shift";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "Right Control";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "Right Alt";
            case GLFW.GLFW_KEY_RIGHT_SUPER -> "Right Super";
            case GLFW.GLFW_KEY_MENU -> "Menu";
            default -> String.valueOf(key);
        };
    }

    private static int parseKey(String value) {
        String key = value.trim();
        if (key.isEmpty() || key.equalsIgnoreCase("none"))
            return GLFW.GLFW_KEY_UNKNOWN;

        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException ignored) {
        }

        try {
            return InputConstants.getKey("key.keyboard." + key.toLowerCase().replace(" ", ".")).getValue();
        } catch (RuntimeException ignored) {
            return GLFW.GLFW_KEY_UNKNOWN;
        }
    }

}
