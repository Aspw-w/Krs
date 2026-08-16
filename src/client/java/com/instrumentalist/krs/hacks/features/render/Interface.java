package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.KeyboardEvent;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.exploit.disabler.DisablerModule;
import com.instrumentalist.krs.hacks.features.player.LookTP;
import com.instrumentalist.krs.hacks.features.player.MurdererDetector;
import com.instrumentalist.krs.hacks.features.player.Scaffold;
import com.instrumentalist.krs.hacks.features.exploit.disabler.features.HypixelDisabler;
import com.instrumentalist.krs.hacks.features.level.Breaker;
import com.instrumentalist.krs.hacks.features.level.CivBreak;
import com.instrumentalist.krs.hacks.features.level.Nuker;
import com.instrumentalist.krs.hacks.features.player.Stalker;
import com.instrumentalist.krs.utils.entity.StreamConverter;
import com.instrumentalist.krs.utils.math.Interpolation;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.nanovg.MaterialIcon;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.nanovg.NanoVGTextFormatter;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.packet.BlinkUtil;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import com.instrumentalist.krs.utils.render.RenderUtil;
import com.instrumentalist.krs.utils.value.*;
import com.instrumentalist.krs.utils.math.Tuple;
import com.instrumentalist.mixin.injector.ChatComponentAccessor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.Border;

import java.awt.Color;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.util.*;

public class Interface extends Module {
    private static final int MAX_HUD_CLIP_REGIONS = 64;
    private static final int MINIMAP_VISIBLE_RADIUS_BLOCKS = 24;
    private static final int MINIMAP_TEXTURE_MARGIN_BLOCKS = 10;
    private static final int MINIMAP_REBUILD_DRIFT_BLOCKS = 8;
    private static final int MINIMAP_TERRAIN_SAMPLES_PER_FRAME = 192;
    private static final long MINIMAP_TERRAIN_REFRESH_NANOS = 5_000_000_000L;
    private static final long PLAYER_LIST_REFRESH_NANOS = 250_000_000L;
    private static final ThreadLocal<DecimalFormat> ONE_DECIMAL_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("0.0"));
    private static final Color MODULE_LIST_BACKGROUND_COLOR = NanoVGTheme.COMPACT_BACKGROUND;
    private static final Color MODULE_LIST_TAG_COLOR = new Color(128, 128, 128, 255);
    private static long cachedTimeSecond = -1L;
    private static String cachedTimeText = "00:00:00";

    public Interface() {
        super("Interface", ModuleCategory.Render, GLFW.GLFW_KEY_UNKNOWN, true, false);
    }

    private static Color alphaColor(int red, int green, int blue, int alpha) {
        return new Color(red, green, blue, Math.clamp(alpha, 0, 255));
    }

    private static int minimapTerrainTextureRangeBlocks() {
        return MINIMAP_VISIBLE_RADIUS_BLOCKS + MINIMAP_TEXTURE_MARGIN_BLOCKS;
    }

    private static int minimapTerrainResolution() {
        int range = minimapTerrainTextureRangeBlocks();
        return range * 2 + 1;
    }

    private static int minimapTerrainTextureBytes() {
        int resolution = minimapTerrainResolution();
        return resolution * resolution * 4;
    }

    @Setting
    private static final BooleanValue waterMark = new BooleanValue(
            "Watermark",
            true
    );

    @Setting
    private final TextValue waterMarkText = new TextValue(
            "Watermark Component",
            "Krs",
            waterMark::get
    );

    @Setting
    private static final ListValue waterMarkMode = new ListValue(
            "Watermark Mode",
            new String[]{"Simple", "Bullshit"},
            "Simple",
            waterMark::get
    );

    @Setting
    private final BooleanValue moduleList = new BooleanValue(
            "Module List",
            true
    );

    @Setting
    private static final BooleanValue tabGui = new BooleanValue(
            "TabGui",
            false
    );

    @Setting
    private final BooleanValue notifications = new BooleanValue(
            "Notifications",
            true
    );

    @Setting
    private final BooleanValue targetHud = new BooleanValue(
            "Target Hud",
            true
    );

    @Setting
    private static final BooleanValue miniMap = new BooleanValue(
            "Minimap",
            true
    );

    @Setting
    private static final BooleanValue playerList = new BooleanValue(
            "Players",
            false
    );

    @Setting
    private final BooleanValue moduleInformation = new BooleanValue(
            "Module Information",
            true
    );

    @Setting
    public static final BooleanValue someInformation = new BooleanValue(
            "Some Information",
            true
    );

    public static List<Module> sortedModules;

    private static boolean sortedModulesDirty = true;
    private static final int INFO_HYPIXEL_DISABLER = 0;
    private static final int INFO_LOOK_TP = 1;
    private static final int INFO_BREAKING = 2;
    private static final int INFO_BLINKING = 3;
    private static final int INFO_SCAFFOLD_BLOCKS = 4;
    private static final int INFO_MURDERER_COUNT = 5;
    private static final float[] moduleInformationFadeProgress = new float[6];
    private static long hudFrameCounter;
    private static long playerListCacheFrame = -1L;
    private static long playerListNextRefreshNanos;
    private static boolean playerListCacheInitialized;
    private static List<PlayerInfo> cachedPlayerListEntries = Collections.emptyList();
    private static final ArrayList<PlayerInfo> playerListEntryBuffer = new ArrayList<>();
    private static final ArrayList<PlayerListRow> playerListRowBuffer = new ArrayList<>(10);
    private static float cachedPlayerListHeight;
    private long lastRenderNanos;

    private final List<TargetHudData> data = new ArrayList<>();
    private final List<TargetHudRenderEntry> targetHudEntryBuffer = new ArrayList<>(4);
    private final ArrayList<TargetHudRenderEntry> targetHudFrontToBackBuffer = new ArrayList<>(4);
    private final ArrayList<HudClipRect> targetHudOccluderBuffer = new ArrayList<>(4);
    private final ArrayList<TargetHudRenderEntry> targetHudVisibleEntryBuffer = new ArrayList<>(4);
    private final List<ModuleListEntry> moduleListEntryBuffer = new ArrayList<>();
    private final Map<Module, ModuleListEntry> moduleListEntryCache = new HashMap<>();
    private final List<ModuleListRenderEntry> moduleListRenderEntryBuffer = new ArrayList<>();
    private final List<ModuleListRenderEntry> moduleListRenderEntryPool = new ArrayList<>();
    private float[] moduleListBackgroundGeometry = new float[32 * 8];
    private final List<Entity> targetBuffer = new ArrayList<>(4);
    private final Set<UUID> targetUuidBuffer = new HashSet<>();
    private final Map<UUID, MinimapPlayerDotState> minimapPlayerDots = new HashMap<>();
    private final Set<UUID> minimapPlayerUuidBuffer = new HashSet<>();
    private final ByteBuffer minimapTerrainBuffer = BufferUtils.createByteBuffer(minimapTerrainTextureBytes());
    private int[] minimapTerrainColors = new int[minimapTerrainResolution() * minimapTerrainResolution()];
    private int[] minimapTerrainBuildColors = new int[minimapTerrainResolution() * minimapTerrainResolution()];
    private final int[] minimapTerrainDirtyIndices = new int[minimapTerrainResolution() * minimapTerrainResolution()];
    private final BlockPos.MutableBlockPos minimapTerrainSamplePos = new BlockPos.MutableBlockPos();
    private int minimapTerrainCenterBlockX = Integer.MIN_VALUE;
    private int minimapTerrainCenterBlockZ = Integer.MIN_VALUE;
    private int minimapTerrainPlayerBlockY = Integer.MIN_VALUE;
    private long minimapTerrainLastUpdateNanos;
    private boolean minimapTerrainDirty = true;
    private boolean minimapTerrainDataReady;
    private boolean minimapTerrainTextureReady;
    private boolean minimapTerrainBuildInProgress;
    private int minimapTerrainBuildCenterBlockX;
    private int minimapTerrainBuildCenterBlockZ;
    private int minimapTerrainBuildPlayerBlockY;
    private int minimapTerrainDirtyCount;
    private int minimapTerrainDirtyCursor;
    private boolean minimapViewInitialized;
    private double minimapViewX;
    private double minimapViewZ;
    private final List<StyledTextRenderEntry> moduleInformationEntryBuffer = new ArrayList<>();
    private final List<ModuleCategory> tabGuiCategoryBuffer = new ArrayList<>();
    private final List<Module> tabGuiModuleBuffer = new ArrayList<>();
    private final ArrayList<InfoHudEntry> informationEntryBuffer = new ArrayList<>(2);
    private final ArrayList<PotionHudEntry> potionEntryBuffer = new ArrayList<>();
    private final ArrayList<InfoHudEntry> connectionEntryBuffer = new ArrayList<>(3);
    private final SomeInformationRenderState someInformationRenderState = new SomeInformationRenderState(
            informationEntryBuffer, potionEntryBuffer, connectionEntryBuffer
    );
    private int tabGuiCategoryIndex;
    private int tabGuiModuleIndex;
    private boolean tabGuiExpanded;
    private float tabGuiExpandProgress;
    private float tabGuiCategorySelectionY = Float.NaN;
    private float tabGuiModuleSelectionY = Float.NaN;

    private static class ModuleListEntry {
        String text;
        String tagText;
        float progress;
        float textWidth;
        float fullWidth;

        boolean update(String text, String tagText, float progress, float fontSize) {
            boolean widthChanged = false;
            if (!Objects.equals(this.text, text) || !Objects.equals(this.tagText, tagText)) {
                float textWidth = NVGFonts.INTER.getWidth(text, fontSize);
                float tagWidth = tagText != null ? NVGFonts.INTER.getWidth(tagText, fontSize) : 0f;
                float fullWidth = textWidth + tagWidth;
                widthChanged = this.text != null && Float.compare(this.fullWidth, fullWidth) != 0;
                this.textWidth = textWidth;
                this.fullWidth = fullWidth;
            }
            this.text = text;
            this.tagText = tagText;
            this.progress = progress;
            return widthChanged;
        }
    }

    private interface ConnectedHudRect {
        float x();

        float y();

        float width();

        float height();

        default float right() {
            return x() + width();
        }
    }

    private static class ConnectedHudCornerRadii {
        final float topLeft;
        final float topRight;
        final float bottomRight;
        final float bottomLeft;

        ConnectedHudCornerRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
            this.topLeft = topLeft;
            this.topRight = topRight;
            this.bottomRight = bottomRight;
            this.bottomLeft = bottomLeft;
        }
    }

    private static class ModuleListRenderEntry implements ConnectedHudRect {
        ModuleListEntry entry;
        float x;
        float y;
        float width;
        float height;
        float textX;
        float textY;
        int alpha;
        int baseAlpha;
        Color textColor;
        float topLeftRadius;
        float topRightRadius;
        float bottomRightRadius;
        float bottomLeftRadius;

        void update(ModuleListEntry entry, float x, float y, float width, float height,
                    float textX, float textY, int alpha, int baseAlpha, Color textColor) {
            this.entry = entry;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.textX = textX;
            this.textY = textY;
            this.alpha = alpha;
            this.baseAlpha = baseAlpha;
            this.textColor = textColor;
        }

        void updateCornerRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
            this.topLeftRadius = topLeft;
            this.topRightRadius = topRight;
            this.bottomRightRadius = bottomRight;
            this.bottomLeftRadius = bottomLeft;
        }

        public float x() {
            return x;
        }

        public float y() {
            return y;
        }

        public float width() {
            return width;
        }

        public float height() {
            return height;
        }
    }

    private static class StyledTextRenderEntry {
        final String text;
        final float x;
        final float y;
        final float alpha;

        StyledTextRenderEntry(String text, float x, float y, float alpha) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.alpha = alpha;
        }
    }

    private static class SomeInformationRenderState {
        final List<InfoHudEntry> informationEntries;
        final List<PotionHudEntry> potionEntries;
        final List<InfoHudEntry> connectionEntries;

        SomeInformationRenderState(List<InfoHudEntry> informationEntries, List<PotionHudEntry> potionEntries, List<InfoHudEntry> connectionEntries) {
            this.informationEntries = informationEntries;
            this.potionEntries = potionEntries;
            this.connectionEntries = connectionEntries;
        }
    }

    private static class PotionHudEntry implements ConnectedHudRect {
        final String text;
        final float rectX;
        final float rectY;
        final float width;
        final float height;
        final float textX;
        final float textY;

        PotionHudEntry(String text, float rectX, float rectY, float width, float height, float textX, float textY) {
            this.text = text;
            this.rectX = rectX;
            this.rectY = rectY;
            this.width = width;
            this.height = height;
            this.textX = textX;
            this.textY = textY;
        }

        public float x() {
            return rectX;
        }

        public float y() {
            return rectY;
        }

        public float width() {
            return width;
        }

        public float height() {
            return height;
        }
    }

    private static class InfoHudEntry implements ConnectedHudRect {
        final String text;
        final String icon;
        final float iconWidth;
        final float width;
        final float height;
        float x;
        float y;

        InfoHudEntry(String text, String icon) {
            this.text = text;
            this.icon = icon;
            this.iconWidth = NVGFonts.ICON.getWidth(icon, 16f);
            this.width = NVGFonts.INTER.getWidth(text, 16f) + iconWidth + 10f;
            this.height = NVGFonts.INTER.getHeight(16f) + 6f;
        }

        public float right() {
            return x + width;
        }

        public float x() {
            return x;
        }

        public float y() {
            return y;
        }

        public float width() {
            return width;
        }

        public float height() {
            return height;
        }
    }

    private static class MinimapPlayerDotState {
        double x;
        double z;

        MinimapPlayerDotState(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    private static class ModuleSortEntry {
        final Module module;
        final float width;

        ModuleSortEntry(Module module, float width) {
            this.module = module;
            this.width = width;
        }
    }

    public static Color getFadedColor(int index, int totalModules) {
        return getFadedColor(index, totalModules, 255, System.currentTimeMillis());
    }

    private static Color getFadedColor(int index, int totalModules, int alpha, long currentTimeMillis) {
        totalModules = Math.max(1, totalModules);
        float cycleDuration = 4000f / 3f;
        float progress = ((currentTimeMillis % (long) cycleDuration) / cycleDuration + (float) index / totalModules) % 1.0f;
        progress = 0.5f - 0.5f * (float) Math.cos(progress * 2 * Math.PI);

        int red = (int) (255f * progress);
        int green = (int) (255f * (1.0f - progress));
        return new Color(red, green, 255, Math.clamp(alpha, 0, 255));
    }

    private static String getCachedTimeText() {
        long second = System.currentTimeMillis() / 1000L;
        if (second != cachedTimeSecond) {
            cachedTimeSecond = second;
            cachedTimeText = formatTime(LocalTime.now());
        }
        return cachedTimeText;
    }

    private static String formatTime(LocalTime time) {
        char[] text = new char[8];
        twoDigits(text, 0, time.getHour());
        text[2] = ':';
        twoDigits(text, 3, time.getMinute());
        text[5] = ':';
        twoDigits(text, 6, time.getSecond());
        return new String(text);
    }

    private static void twoDigits(char[] text, int offset, int value) {
        text[offset] = (char) ('0' + value / 10);
        text[offset + 1] = (char) ('0' + value % 10);
    }

    private static String formatOneDecimal(DecimalFormat formatter, double value) {
        return formatter.format(value);
    }

    private static String formatDurationTicks(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return "(" + minutes + ":" + (seconds < 10 ? "0" : "") + seconds + ")";
    }

    private static String formatPositionText(double x, double y, double z, double netherX, double netherZ, String direction) {
        DecimalFormat formatter = ONE_DECIMAL_FORMAT.get();
        return formatOneDecimal(formatter, x) + ", " + formatOneDecimal(formatter, y) + ", " + formatOneDecimal(formatter, z)
                + " | " + formatOneDecimal(formatter, netherX) + ", " + formatOneDecimal(formatter, netherZ)
                + " (" + direction + ")";
    }

    private static float getLeftHudTopY() {
        if (tabGui.get()) {
            float tabGuiHeight = getTabGuiCategoryPanelHeight();
            if (tabGuiHeight > 0f)
                return getTabGuiBaseY() + tabGuiHeight + 12f;
        }

        if (!waterMark.get())
            return 20f;

        return getTabGuiBaseY();
    }

    private static float getTabGuiBaseY() {
        if (!waterMark.get())
            return 16f;

        return waterMarkMode.get().equalsIgnoreCase("bullshit") ? 100f : 70f;
    }

    private static float getLeftHudX() {
        return 16f;
    }

    private static float getTabGuiCategoryPanelHeight() {
        int categoryCount = getTabGuiCategoryCount();
        return categoryCount <= 0 ? 0f : getTabGuiPanelHeight(categoryCount);
    }

    private static float getTabGuiPanelHeight(int rowCount) {
        return 25f + rowCount * 22f + 6f;
    }

    private static int getTabGuiCategoryCount() {
        int count = 0;
        for (ModuleCategory category : ModuleCategory.values()) {
            if (category != ModuleCategory.Dev && hasTabGuiModules(category))
                count++;
        }
        return count;
    }

    private static boolean hasTabGuiModules(ModuleCategory category) {
        for (Module module : ModuleManager.modules) {
            if (module.moduleCategory == category)
                return true;
        }
        return false;
    }

    private static List<PlayerInfo> getPlayerListEntries() {
        if (playerListCacheFrame == hudFrameCounter)
            return cachedPlayerListEntries;

        playerListCacheFrame = hudFrameCounter;

        if (!playerList.get() || mc.getConnection() == null)
            return clearPlayerListEntries();

        long now = System.nanoTime();
        if (playerListCacheInitialized && now < playerListNextRefreshNanos)
            return cachedPlayerListEntries;

        playerListCacheInitialized = true;
        playerListNextRefreshNanos = now + PLAYER_LIST_REFRESH_NANOS;
        cachedPlayerListHeight = 0f;

        ArrayList<PlayerInfo> players = playerListEntryBuffer;
        players.clear();
        players.addAll(mc.getConnection().getListedOnlinePlayers());
        players.sort((first, second) -> {
            boolean firstSelf = isSelfPlayerListEntry(first);
            boolean secondSelf = isSelfPlayerListEntry(second);
            if (firstSelf != secondSelf)
                return firstSelf ? -1 : 1;

            return getPlayerListName(first).compareToIgnoreCase(getPlayerListName(second));
        });
        cachedPlayerListEntries = players;
        preparePlayerListRows(players);
        return cachedPlayerListEntries;
    }

    private static final class PlayerListRow {
        final String pingText;
        final Color pingColor;
        final Color nameColor;
        final List<String> nameLines;
        final float rowHeight;

        private PlayerListRow(String pingText, Color pingColor, Color nameColor,
                              List<String> nameLines, float rowHeight) {
            this.pingText = pingText;
            this.pingColor = pingColor;
            this.nameColor = nameColor;
            this.nameLines = nameLines;
            this.rowHeight = rowHeight;
        }
    }

    private static List<PlayerInfo> clearPlayerListEntries() {
        playerListEntryBuffer.clear();
        playerListRowBuffer.clear();
        cachedPlayerListEntries = Collections.emptyList();
        playerListCacheInitialized = false;
        playerListNextRefreshNanos = 0L;
        cachedPlayerListHeight = 0f;
        return cachedPlayerListEntries;
    }

    private static float getPlayerListHeight() {
        getPlayerListEntries();
        if (playerListRowBuffer.isEmpty())
            return 0f;
        return cachedPlayerListHeight;
    }

    private static void preparePlayerListRows(List<PlayerInfo> players) {
        playerListRowBuffer.clear();
        float height = 23f + 6f;
        int visiblePlayers = Math.min(players.size(), 10);
        for (int i = 0; i < visiblePlayers; i++) {
            PlayerInfo entry = players.get(i);
            String pingText = getPlayerListPingText(entry.getLatency());
            Color pingColor = getPlayerListPingColor(entry.getLatency());
            float pingWidth = NVGFonts.INTER.getWidth(pingText, 12f);
            List<String> nameLines = List.copyOf(wrapTextToWidth(
                    getColoredPlayerListName(entry),
                    240f - pingWidth - 34f,
                    13f
            ));
            float rowHeight = Math.max(17f, 3f * 2f + nameLines.size() * 14f);
            Color nameColor = isSelfPlayerListEntry(entry)
                    ? new Color(0, 255, 255)
                    : new Color(255, 255, 255, 225);
            playerListRowBuffer.add(new PlayerListRow(
                    pingText,
                    pingColor,
                    nameColor,
                    nameLines,
                    rowHeight
            ));
            height += rowHeight;
        }
        cachedPlayerListHeight = height;
    }

    private static void beginHudFrame() {
        hudFrameCounter++;
    }

    private static float getMinimapY() {
        float y = getLeftHudTopY() + 5f;
        float playerListHeight = getPlayerListHeight();

        if (playerListHeight > 0f)
            y += playerListHeight + 8f;

        return y;
    }

    private static float getInformationBaseY() {
        float y = getLeftHudTopY();
        float playerListHeight = getPlayerListHeight();

        if (playerListHeight > 0f)
            y += playerListHeight + 8f;

        if (miniMap.get())
            y += 150f + 20f;
        else
            y += 6f;

        return y;
    }

    private SomeInformationRenderState prepareSomeInformation() {
        float baseY = getInformationBaseY();
        float infoLeftX = 20f;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        boolean inNether = mc.level.dimension() == Level.NETHER;
        double netherX = inNether ? x * 8 : x / 8;
        double netherZ = inNether ? z * 8 : z / 8;
        String positionText = formatPositionText(x, y, z, netherX, netherZ, mc.player.getDirection().getSerializedName().toUpperCase(Locale.ROOT));
        List<InfoHudEntry> informationEntries = informationEntryBuffer;
        informationEntries.clear();
        informationEntries.add(new InfoHudEntry(getCachedTimeText(), MaterialIcon.CLOCK));
        informationEntries.add(new InfoHudEntry(positionText, MaterialIcon.CORDS));
        layoutConnectedInfoHudChips(informationEntries, infoLeftX, baseY + informationEntries.get(0).height);

        Map<Holder<MobEffect>, MobEffectInstance> activeEffects = mc.player.getActiveEffectsMap();
        List<PotionHudEntry> potionEntries = potionEntryBuffer;
        potionEntries.clear();
        potionEntryBuffer.ensureCapacity(activeEffects.size());

        if (!activeEffects.isEmpty()) {
            float screenWidth = NanoVGManager.getScaledScreenWidth();
            float yOffset = NanoVGManager.getScaledScreenHeight() - 20f;

            for (Map.Entry<Holder<MobEffect>, MobEffectInstance> entry : activeEffects.entrySet()) {
                MobEffect effect = entry.getKey().value();
                MobEffectInstance instance = entry.getValue();
                var effectKey = BuiltInRegistries.MOB_EFFECT.getKey(effect);
                if (effectKey == null) continue;
                String effectName = StreamConverter.formatNameByPath(effectKey.getPath());

                String durationText = instance.isInfiniteDuration() ? "(\u221e)" : formatDurationTicks(instance.getDuration());
                String effectText = effectName + " " + getRomanNumeral(instance.getAmplifier() + 1) + " " + durationText;

                float textWidth = NVGFonts.INTER.getWidth(effectText, 16f) + 6f;
                float textHeight = NVGFonts.INTER.getHeight(16f) + 6f;
                float rectYOffset = yOffset + 7f;
                float textYOffset = yOffset + 4.5f;

                potionEntries.add(new PotionHudEntry(effectText, screenWidth - 10f - textWidth, rectYOffset - textHeight, textWidth, textHeight, screenWidth - 12f, textYOffset));

                yOffset -= 22f;
            }

            if (!potionEntries.isEmpty()) {
                potionEntries.sort(Comparator.comparingDouble(PotionHudEntry::y));
            }
        }

        String connectionType;
        int ping = -1;
        String serverIp = "Unknown";

        if (mc.isLocalServer()) {
            connectionType = "Singleplayer";
        } else if (mc.getCurrentServer() != null) {
            connectionType = "Multiplayer";
            serverIp = mc.getCurrentServer().ip;
        } else {
            connectionType = "Integrated Server";
        }

        if (mc.getConnection() != null && mc.player != null) {
            PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (entry != null) {
                ping = entry.getLatency();
            }
        }

        float leftX = 10f;
        float windowHeight = NanoVGManager.getScaledScreenHeight();
        float startY = windowHeight - 30f - getChatScreenOffset();
        List<InfoHudEntry> connectionEntries = connectionEntryBuffer;
        connectionEntries.clear();

        connectionEntries.add(new InfoHudEntry(connectionType, MaterialIcon.PUBLIC));

        if (!connectionType.equals("Singleplayer")) {
            String pingText = (ping >= 0) ? ping + "ms" : "Unknown";
            connectionEntries.add(new InfoHudEntry(serverIp, MaterialIcon.LAN));
            connectionEntries.add(new InfoHudEntry(pingText, MaterialIcon.SIGNAL));
        }

        layoutConnectedInfoHudChips(connectionEntries, leftX, startY);

        return someInformationRenderState;
    }

    private void renderSomeInformation(NVGU vg) {
        SomeInformationRenderState state = prepareSomeInformation();
        vg.beginEffectBatch();
        renderSomeInformationEffects(vg, state);
        vg.flushEffectBatch();
        renderSomeInformationBody(vg, state);
    }

    private static void renderSomeInformationEffects(NVGU vg, SomeInformationRenderState state) {
        renderConnectedInfoHudChipEffects(vg, state.informationEntries, 1f);
        renderPotionHudEffects(vg, state.potionEntries);
        renderConnectedInfoHudChipEffects(vg, state.connectionEntries, 1f);
    }

    private static void renderSomeInformationBody(NVGU vg, SomeInformationRenderState state) {
        renderConnectedInfoHudChipBodies(vg, state.informationEntries, 1f);
        renderPotionHudBodies(vg, state.potionEntries);
        renderConnectedInfoHudChipBodies(vg, state.connectionEntries, 1f);
    }

    private static void renderConnectedSecondStyledTextWithIcons(NVGU vg, List<InfoHudEntry> entries, float x, float bottomY, float alpha) {
        if (entries.isEmpty()) return;

        layoutConnectedInfoHudChips(entries, x, bottomY);
        vg.beginEffectBatch();
        renderConnectedInfoHudChipEffects(vg, entries, alpha);
        vg.flushEffectBatch();
        renderConnectedInfoHudChipBodies(vg, entries, alpha);
    }

    private static void layoutConnectedInfoHudChips(List<InfoHudEntry> entries, float x, float bottomY) {
        if (entries.isEmpty()) return;

        float rowHeight = entries.get(0).height;
        float topY = bottomY - rowHeight * (entries.size() - 1);
        for (int i = 0, n = entries.size(); i < n; i++) {
            InfoHudEntry entry = entries.get(i);
            entry.x = x;
            entry.y = topY + rowHeight * i;
        }
    }

    private static void renderConnectedInfoHudChipEffects(NVGU vg, List<InfoHudEntry> entries, float alpha) {
        for (InfoHudEntry entry : entries) {
            renderInfoHudChipEffects(vg, entry, alpha);
        }
    }

    private static void renderConnectedInfoHudChipBodies(NVGU vg, List<InfoHudEntry> entries, float alpha) {
        for (int i = 0, n = entries.size(); i < n; i++) {
            renderConnectedInfoHudChipBackground(vg, entries, i, alpha);
        }

        for (InfoHudEntry entry : entries) {
            renderInfoHudChipText(entry);
        }
    }

    private static void renderPotionHudEffects(NVGU vg, List<PotionHudEntry> potionEntries) {
        for (PotionHudEntry entry : potionEntries) {
            NanoVGTheme.renderCompactEffects(vg, entry.rectX, entry.rectY, entry.width, entry.height, 5f, 1f);
        }
    }

    private static void renderPotionHudBodies(NVGU vg, List<PotionHudEntry> potionEntries) {
        for (int i = 0, n = potionEntries.size(); i < n; i++) {
            PotionHudEntry entry = potionEntries.get(i);
            ConnectedHudCornerRadii radii = getConnectedHudCornerRadii(potionEntries, i, 5f);
            NanoVGTheme.renderCompact(
                    vg,
                    entry.rectX, entry.rectY, entry.width, entry.height,
                    radii.topLeft, radii.topRight, radii.bottomRight, radii.bottomLeft,
                    1f
            );
            NVGFonts.INTER.drawText(entry.text, entry.textX, entry.textY, 16f, new Color(218, 228, 234), Alignment.RIGHT_BOTTOM, false);
        }
    }

    private static void renderInfoHudChipEffects(NVGU vg, InfoHudEntry entry, float alpha) {
        NanoVGTheme.renderCompactEffects(vg, entry.x, entry.y, entry.width, entry.height, 5f, alpha);
    }

    private static void renderConnectedInfoHudChipBackground(NVGU vg, List<InfoHudEntry> entries, int index, float alpha) {
        InfoHudEntry entry = entries.get(index);
        ConnectedHudCornerRadii radii = getConnectedHudCornerRadii(entries, index, 5f);

        NanoVGTheme.renderCompact(
                vg,
                entry.x, entry.y, entry.width, entry.height,
                radii.topLeft, radii.topRight, radii.bottomRight, radii.bottomLeft,
                alpha
        );
    }

    private static ConnectedHudCornerRadii getConnectedHudCornerRadii(List<? extends ConnectedHudRect> entries, int index, float radius) {
        ConnectedHudRect entry = entries.get(index);
        ConnectedHudRect previous = index > 0 ? entries.get(index - 1) : null;
        ConnectedHudRect next = index + 1 < entries.size() ? entries.get(index + 1) : null;

        float topLeft = coversHudCorner(previous, entry.x()) ? 0f : radius;
        float topRight = coversHudCorner(previous, entry.right()) ? 0f : radius;
        float bottomRight = coversHudCorner(next, entry.right()) ? 0f : radius;
        float bottomLeft = coversHudCorner(next, entry.x()) ? 0f : radius;

        return new ConnectedHudCornerRadii(topLeft, topRight, bottomRight, bottomLeft);
    }

    private static boolean coversHudCorner(@Nullable ConnectedHudRect neighbor, float cornerX) {
        return neighbor != null && cornerX >= neighbor.x() - 0.5f && cornerX <= neighbor.right() + 0.5f;
    }

    private static float getExposedHudCornerRadius(@Nullable ConnectedHudRect neighbor, float cornerX, float maxRadius) {
        if (neighbor == null)
            return maxRadius;
        if (cornerX >= neighbor.x() - 0.5f && cornerX <= neighbor.right() + 0.5f)
            return 0f;

        float exposedWidth = cornerX < neighbor.x()
                ? neighbor.x() - cornerX
                : cornerX - neighbor.right();
        return Math.min(maxRadius, Math.max(0f, exposedWidth));
    }

    private static void renderInfoHudChipText(InfoHudEntry entry) {
        NVGFonts.ICON.drawText(entry.icon, entry.x + 2.5f, entry.y + 2f, 16f, new Color(0, 255, 255), Alignment.LEFT_TOP, false);
        NVGFonts.INTER.drawText(entry.text, entry.x + 5f + entry.iconWidth, entry.y + 4f, 16f, new Color(218, 228, 234), Alignment.LEFT_TOP, false);
    }

    private static float getChatScreenOffset() {
        if (!(mc.gui.screen() instanceof ChatScreen))
            return 0f;

        ChatComponent chat = mc.gui.hud.getChat();
        ChatComponentAccessor chatAccessor = (ChatComponentAccessor) chat;
        int visibleLines = Math.max(0, Math.min(
                chat.getLinesPerPage(),
                chatAccessor.krs$getTrimmedMessages().size() - Math.max(0, chatAccessor.krs$getChatScrollbarPos())
        ));
        double lineSpacing = (Double) mc.options.chatLineSpacing().get();
        float chatLineHeight = (int) (9.0D * (lineSpacing + 1.0D));
        float chatScale = ((Double) mc.options.chatScale().get()).floatValue();
        float guiToNanoVgScale = NanoVGManager.getScaledScreenHeight() / Math.max(1f, mc.getWindow().getGuiScaledHeight());
        float visibleChatHeight = visibleLines * chatLineHeight * chatScale;

        if (visibleLines == 0f) {
            return 438f;
        }

        return (40f + visibleChatHeight) * guiToNanoVgScale - 30f + (16f + 12f);
    }


    private static class TargetHudState {
        Entity lastTargetEntity;
        InputStream cachedPlayerTexture;
        boolean playerTextureRequestPending;
        boolean playerTextureReady;
        boolean playerTextureFailed;
        long playerTextureRequestGeneration;
        float targetHudAlpha = 0f;
        float previousHealthRatio = 0.5f;
        float previousRegenRatio = 0.5f;
        float previousTargetWidth = 0f;

        void resetPlayerTexture() {
            playerTextureRequestGeneration++;
            playerTextureRequestPending = false;
            playerTextureReady = false;
            playerTextureFailed = false;
            closeInputStream(cachedPlayerTexture);
            cachedPlayerTexture = null;
        }
    }

    private static void requestPlayerTexture(TargetHudState state, Player player) {
        String identifier = player.getStringUUID().toLowerCase(Locale.ROOT);
        NVGU vg = NVGU.INSTANCE;
        if (vg != null && vg.isCreated() && vg.hasTexture(identifier)) {
            state.playerTextureReady = true;
            state.playerTextureFailed = false;
            return;
        }

        if (state.playerTextureReady)
            state.playerTextureReady = false;
        if (state.playerTextureRequestPending || state.playerTextureFailed || state.cachedPlayerTexture != null)
            return;

        state.playerTextureRequestPending = true;
        long requestGeneration = ++state.playerTextureRequestGeneration;
        Entity requestedEntity = state.lastTargetEntity;
        StreamConverter.getPlayerFaceAsInputStream(player, inputStream -> {
            if (state.playerTextureRequestGeneration != requestGeneration || state.lastTargetEntity != requestedEntity) {
                closeInputStream(inputStream);
                return;
            }

            state.playerTextureRequestPending = false;
            if (inputStream == null) {
                state.playerTextureFailed = true;
                return;
            }
            state.cachedPlayerTexture = inputStream;
        });
    }

    private static void closeInputStream(InputStream inputStream) {
        if (inputStream == null)
            return;

        try {
            inputStream.close();
        } catch (java.io.IOException ignored) {
        }
    }

    private static void clearTargetHudTracking() {
        for (TargetHudState state : hudStates.values())
            state.resetPlayerTexture();
        hudStates.clear();
        actualTargetEntitiesData.clear();
    }

    private static class TargetHudData {
        final UUID id;
        @Nullable
        final Entity entity;
        final float hudX;
        final float hudY;
        final double depthSquared;

        TargetHudData(UUID id, @Nullable Entity entity, float hudX, float hudY, double depthSquared) {
            this.id = id;
            this.entity = entity;
            this.hudX = hudX;
            this.hudY = hudY;
            this.depthSquared = depthSquared;
        }
    }

    private static class HudClipRect {
        final float x;
        final float y;
        final float width;
        final float height;

        HudClipRect(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        float right() {
            return x + width;
        }

        float bottom() {
            return y + height;
        }
    }

    private static class TargetHudRenderEntry implements ConnectedHudRect {
        final TargetHudState state;
        final Entity targetEntity;
        final float hudX;
        final float hudY;
        final float hudWidth;
        final float iconFix;
        final float healthBarX;
        final float healthBarY;
        final float regenBarY;
        final float barHeight;
        final float textX;
        final String entityName;
        final String posStr;
        final String healthPctStr;
        final String regenPctStr;
        final int alphaValue;
        final int barAlpha;
        final double depthSquared;
        final HudClipRect bounds;
        List<HudClipRect> visibleRects = List.of();

        TargetHudRenderEntry(TargetHudState state, Entity targetEntity, float hudX, float hudY, float hudWidth,
                             float iconFix, float healthBarX, float healthBarY, float regenBarY, float barHeight,
                             float textX, String entityName, String posStr, String healthPctStr, String regenPctStr,
                             int alphaValue, int barAlpha, double depthSquared) {
            this.state = state;
            this.targetEntity = targetEntity;
            this.hudX = hudX;
            this.hudY = hudY;
            this.hudWidth = hudWidth;
            this.iconFix = iconFix;
            this.healthBarX = healthBarX;
            this.healthBarY = healthBarY;
            this.regenBarY = regenBarY;
            this.barHeight = barHeight;
            this.textX = textX;
            this.entityName = entityName;
            this.posStr = posStr;
            this.healthPctStr = healthPctStr;
            this.regenPctStr = regenPctStr;
            this.alphaValue = alphaValue;
            this.barAlpha = barAlpha;
            this.depthSquared = depthSquared;
            this.bounds = new HudClipRect(hudX, hudY, hudWidth, 95f);
        }

        HudClipRect bounds() {
            return bounds;
        }

        public float x() {
            return hudX;
        }

        public float y() {
            return hudY;
        }

        public float width() {
            return hudWidth;
        }

        public float height() {
            return 95f;
        }
    }

    private static final Map<UUID, TargetHudState> hudStates = new HashMap<>();
    private static final List<Tuple<@Nullable Entity, UUID>> actualTargetEntitiesData = new ArrayList<>();

    public static void renderTargetHud(UUID id, @Nullable Entity entity, NVGU vg, float deltaSpeed, float hudX, float hudY) {
        if (!RenderUtil.shouldRenderWorldHudOverlays())
            return;

        TargetHudRenderEntry entry = prepareTargetHud(id, entity, deltaSpeed, hudX, hudY, 0.0);
        if (entry == null)
            return;

        entry.visibleRects = List.of(entry.bounds());
        drawTargetHudEffects(vg, entry);
        renderTargetHudVisibleRegions(vg, entry, () -> drawTargetHudBody(vg, entry));
    }

    private static TargetHudRenderEntry prepareTargetHud(UUID id, @Nullable Entity entity, float deltaSpeed, float hudX, float hudY, double depthSquared) {
        TargetHudState state = hudStates.computeIfAbsent(id, k -> new TargetHudState());

        if (entity != null) {
            if (state.lastTargetEntity != entity) {
                state.resetPlayerTexture();
                state.lastTargetEntity = entity;
            }
        }

        boolean activeTarget = BehaviorUtils.isTarget(entity);
        float animationDelta = Float.isFinite(deltaSpeed) ? Math.clamp(deltaSpeed, 0.01f, 1.0f) : 1.0f;
        float targetAlpha = activeTarget ? 1.0f : 0.0f;
        state.targetHudAlpha = Math.clamp(
                Interpolation.INSTANCE.lerp(state.targetHudAlpha, targetAlpha, animationDelta),
                0.0f,
                1.0f
        );

        // A newly acquired target starts below this threshold at high frame rates.
        // Only retire a state after the target is actually inactive and has faded out.
        if (!activeTarget && state.targetHudAlpha < 0.1f) {
            state.previousHealthRatio = 0.5f;
            state.previousRegenRatio = 0.5f;
            state.previousTargetWidth = 0f;
            Iterator<Tuple<Entity, UUID>> iterator = actualTargetEntitiesData.iterator();
            while (iterator.hasNext()) {
                Tuple<Entity, UUID> pair = iterator.next();
                if (pair.getSecond().equals(id)) {
                    state.lastTargetEntity = null;
                    iterator.remove();
                }
            }
            state.resetPlayerTexture();
            hudStates.remove(id, state);
            return null;
        }

        if (state.lastTargetEntity == null) return null;

        if (state.lastTargetEntity instanceof Player player)
            requestPlayerTexture(state, player);

        String entityName = state.lastTargetEntity instanceof Player || state.lastTargetEntity.hasCustomName() ? state.lastTargetEntity.getName().getString() : StreamConverter.formatNameByPath(BuiltInRegistries.ENTITY_TYPE.getKey(state.lastTargetEntity.getType()).getPath());

        float health = state.lastTargetEntity instanceof LivingEntity ? ((LivingEntity) state.lastTargetEntity).getHealth() : -1;
        float maxHealth = state.lastTargetEntity instanceof LivingEntity ? ((LivingEntity) state.lastTargetEntity).getMaxHealth() : 1;
        float timeUntilRegen = state.lastTargetEntity instanceof LivingEntity ? ((LivingEntity) state.lastTargetEntity).hurtTime * 2 : 1;
        if (!Float.isFinite(health))
            health = 0f;
        if (!Float.isFinite(maxHealth) || maxHealth <= 0f)
            maxHealth = 1f;
        double posX = state.lastTargetEntity.getX();
        double posY = state.lastTargetEntity.getY();
        double posZ = state.lastTargetEntity.getZ();

        DecimalFormat formatter = ONE_DECIMAL_FORMAT.get();
        String posStr = formatOneDecimal(formatter, posX) + ", " + formatOneDecimal(formatter, posY) + ", " + formatOneDecimal(formatter, posZ);
        String healthPctStr = formatOneDecimal(formatter, (health / maxHealth) * 100) + "%";
        String regenPctStr = formatOneDecimal(formatter, (timeUntilRegen / 20f) * 100) + "%";

        float width = NVGFonts.INTER.getWidth(posStr, 16f);
        float textWidth = Math.max(NVGFonts.INTER.getWidth(healthPctStr, 14f), NVGFonts.INTER.getWidth(regenPctStr, 14f));

        float entityNameWidth = NVGFonts.INTER.getWidth(entityName, 16f);
        if (entityNameWidth > width)
            width = entityNameWidth;

        float barHeight = 10f;
        float healthBarX = hudX + 10f;
        float healthBarY = hudY + 55f;
        float regenBarY = healthBarY + barHeight + 5f;
        float healthRatioTarget = Math.max(0, Math.min(health / maxHealth, 1));
        float regenRatioTarget = Math.max(0, Math.min(timeUntilRegen / 20f, 1));
        float widthTarget = (entity != null) ? width : 0f;
        state.previousTargetWidth = Interpolation.INSTANCE.lerp(state.previousTargetWidth, widthTarget, animationDelta);
        state.previousHealthRatio = Interpolation.INSTANCE.lerp(state.previousHealthRatio, healthRatioTarget, animationDelta);
        state.previousRegenRatio = Interpolation.INSTANCE.lerp(state.previousRegenRatio, regenRatioTarget, animationDelta);
        float textX = healthBarX + state.previousTargetWidth + 5f;

        int alphaValue = Math.min(255, (int) (state.targetHudAlpha * 250));
        int barAlpha = Math.min(255, (int) (state.targetHudAlpha * 220));

        float iconFix = state.cachedPlayerTexture != null || state.playerTextureReady ? 81f : 0f;
        float hudWidth = state.previousTargetWidth + textWidth + 40f + iconFix;

        return new TargetHudRenderEntry(
                state,
                state.lastTargetEntity,
                hudX,
                hudY,
                hudWidth,
                iconFix,
                healthBarX,
                healthBarY,
                regenBarY,
                barHeight,
                textX,
                entityName,
                posStr,
                healthPctStr,
                regenPctStr,
                alphaValue,
                barAlpha,
                depthSquared
        );
    }

    private List<TargetHudRenderEntry> buildVisibleTargetHudEntries(List<TargetHudRenderEntry> entries) {
        List<TargetHudRenderEntry> frontToBack = targetHudFrontToBackBuffer;
        frontToBack.clear();
        frontToBack.addAll(entries);
        frontToBack.sort(Comparator.comparingDouble(entry -> entry.depthSquared));

        List<HudClipRect> occluders = targetHudOccluderBuffer;
        List<TargetHudRenderEntry> visibleEntries = targetHudVisibleEntryBuffer;
        occluders.clear();
        visibleEntries.clear();

        for (TargetHudRenderEntry entry : frontToBack) {
            List<HudClipRect> visibleRects = subtractHudOccluders(entry.bounds(), occluders);
            if (!visibleRects.isEmpty()) {
                entry.visibleRects = visibleRects;
                visibleEntries.add(entry);
            }

            occluders.add(entry.bounds());
        }

        Collections.reverse(visibleEntries);
        return visibleEntries;
    }

    private static List<HudClipRect> subtractHudOccluders(HudClipRect source, List<HudClipRect> occluders) {
        List<HudClipRect> visibleRects = List.of(source);

        for (HudClipRect occluder : occluders) {
            if (visibleRects.isEmpty())
                break;

            List<HudClipRect> nextRects = new ArrayList<>(Math.min(MAX_HUD_CLIP_REGIONS, visibleRects.size() * 4));
            for (HudClipRect rect : visibleRects) {
                subtractHudRect(rect, occluder, nextRects);
            }
            visibleRects = nextRects;
        }

        return visibleRects;
    }

    private static void subtractHudRect(HudClipRect source, HudClipRect occluder, List<HudClipRect> output) {
        float left = Math.max(source.x, occluder.x);
        float top = Math.max(source.y, occluder.y);
        float right = Math.min(source.right(), occluder.right());
        float bottom = Math.min(source.bottom(), occluder.bottom());

        if (left >= right || top >= bottom) {
            output.add(source);
            return;
        }

        addHudClipRect(output, source.x, source.y, source.width, top - source.y);
        addHudClipRect(output, source.x, bottom, source.width, source.bottom() - bottom);
        addHudClipRect(output, source.x, top, left - source.x, bottom - top);
        addHudClipRect(output, right, top, source.right() - right, bottom - top);
    }

    private static void addHudClipRect(List<HudClipRect> output, float x, float y, float width, float height) {
        if (output.size() < MAX_HUD_CLIP_REGIONS && width > 0.5f && height > 0.5f) {
            output.add(new HudClipRect(x, y, width, height));
        }
    }

    private static void renderTargetHudVisibleRegions(NVGU vg, TargetHudRenderEntry entry, Runnable render) {
        for (HudClipRect rect : entry.visibleRects) {
            vg.pushScissor(rect.x, rect.y, rect.width, rect.height);
            try {
                render.run();
            } finally {
                vg.popScissor();
            }
        }
    }

    private static void drawTargetHudEffects(NVGU vg, TargetHudRenderEntry entry) {
        NanoVGTheme.renderPanelEffects(
                vg,
                entry.hudX, entry.hudY, entry.hudWidth, 95f,
                10f,
                entry.state.targetHudAlpha
        );
    }

    private static void drawTargetHudBody(NVGU vg, TargetHudRenderEntry entry) {
        TargetHudState state = entry.state;

        NanoVGTheme.renderPanel(
                vg,
                entry.hudX, entry.hudY, entry.hudWidth, 95f,
                10f,
                state.targetHudAlpha
        );

        if (entry.targetEntity instanceof Player) {
            String identifier = entry.targetEntity.getStringUUID().toLowerCase(Locale.ROOT);
            InputStream playerTexture = state.cachedPlayerTexture;
            if (playerTexture != null) {
                state.cachedPlayerTexture = null;
                try {
                    vg.createTexture(identifier, playerTexture);
                    state.playerTextureReady = vg.hasTexture(identifier);
                    state.playerTextureFailed = !state.playerTextureReady;
                } catch (RuntimeException ignored) {
                    state.playerTextureReady = false;
                    state.playerTextureFailed = true;
                } finally {
                    closeInputStream(playerTexture);
                }
            }

            if (state.playerTextureReady && vg.hasTexture(identifier)) {
                vg.texturedRoundedRectangle(entry.hudX - 35f - ((state.targetHudAlpha * 75f) / 2f) + entry.iconFix, entry.hudY + 47f - ((state.targetHudAlpha * 75f) / 2f), state.targetHudAlpha * 75f, state.targetHudAlpha * 75f, 120f - (state.targetHudAlpha * 115f), identifier);
            } else if (state.playerTextureReady) {
                state.playerTextureReady = false;
                state.playerTextureFailed = false;
            }
        }

        NVGFonts.INTER.drawText(entry.entityName, entry.hudX + 10f + entry.iconFix, entry.hudY + 10f, 16f, alphaColor(255, 255, 255, entry.alphaValue), Alignment.LEFT_TOP, true);
        NVGFonts.INTER.drawText(entry.posStr, entry.hudX + 10f + entry.iconFix, entry.hudY + 30f, 16f, alphaColor(100, 255, 100, entry.alphaValue), Alignment.LEFT_TOP, true);

        vg.roundedRectangle(entry.healthBarX + entry.iconFix, entry.healthBarY, state.previousTargetWidth, entry.barHeight, 5f, alphaColor(50, 50, 50, entry.alphaValue));
        if (state.previousHealthRatio >= 0.01f)
            vg.roundedRectangle(entry.healthBarX + entry.iconFix, entry.healthBarY, state.previousTargetWidth * state.previousHealthRatio, entry.barHeight, 5f, alphaColor(255, 50, 50, entry.barAlpha));
        NVGFonts.ICON.drawText(MaterialIcon.HEART, entry.textX - 0.5f + entry.iconFix, entry.healthBarY - 3f, 14f, alphaColor(255, 100, 100, entry.barAlpha), Alignment.LEFT_TOP, true);
        NVGFonts.INTER.drawText(entry.healthPctStr, entry.textX + 2f + NVGFonts.ICON.getWidth(MaterialIcon.HEART, 14f) + entry.iconFix, entry.healthBarY - 2f, 14f, alphaColor(255, 100, 100, entry.barAlpha), Alignment.LEFT_TOP, true);

        vg.roundedRectangle(entry.healthBarX + entry.iconFix, entry.regenBarY, state.previousTargetWidth, entry.barHeight, 5f, alphaColor(50, 50, 50, entry.alphaValue));
        if (state.previousRegenRatio >= 0.01f)
            vg.roundedRectangle(entry.healthBarX + entry.iconFix, entry.regenBarY, state.previousTargetWidth * state.previousRegenRatio, entry.barHeight, 5f, alphaColor(255, 254, 59, entry.barAlpha));
        NVGFonts.ICON.drawText(MaterialIcon.BROKEN_HEART, entry.textX - 0.5f + entry.iconFix, entry.regenBarY - 3f, 14f, alphaColor(255, 254, 59, entry.barAlpha), Alignment.LEFT_TOP, true);
        NVGFonts.INTER.drawText(entry.regenPctStr, entry.textX + 2f + entry.iconFix + NVGFonts.ICON.getWidth(MaterialIcon.BROKEN_HEART, 14f), entry.regenBarY - 2f, 14f, alphaColor(255, 254, 59, entry.barAlpha), Alignment.LEFT_TOP, true);
    }

    public static void renderModuleInformation(NVGU vg, float startY, float deltaSpeed) {
        List<StyledTextRenderEntry> entries = new ArrayList<>();
        prepareModuleInformationEntries(entries, startY, deltaSpeed);

        vg.beginEffectBatch();
        renderStyledTextEffects(vg, entries);
        vg.flushEffectBatch();
        renderStyledTextBodies(vg, entries);
    }

    private static void prepareModuleInformationEntries(List<StyledTextRenderEntry> entries, float startY, float deltaSpeed) {
        entries.clear();
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float centerX = screenWidth * 0.5f;

        boolean hypixelDisablerActive = ModuleManager.getModuleState(DisablerModule.class) && HypixelDisabler.stuckOnAir && HypixelDisabler.airTicks >= 9 && !HypixelDisabler.watchDogDisabled;
        float progress = advanceModuleInformationFade(INFO_HYPIXEL_DISABLER, hypixelDisablerActive, deltaSpeed);
        if (progress > 0f)
            entries.add(new StyledTextRenderEntry("Disabling... " + HypixelDisabler.airStuckTicks + "/25", centerX, startY, progress));
        if (hypixelDisablerActive) startY -= 40f;

        LookTP lookTP = ModuleManager.getModule(LookTP.class);
        boolean lookTpTeleporting = lookTP != null && lookTP.isClientsideTeleporting();
        progress = advanceModuleInformationFade(INFO_LOOK_TP, lookTpTeleporting, deltaSpeed);
        if (progress > 0f)
            entries.add(new StyledTextRenderEntry("Look TP: " + lookTP.getTeleportProgressPercent() + "%", centerX, startY, progress));
        if (lookTpTeleporting) startY -= 40f;

        boolean breakingActive = mc.gameMode != null && (
                ModuleManager.getModuleState(Breaker.class) && Breaker.getWasBreaking()
                        || ModuleManager.getModuleState(CivBreak.class) && CivBreak.getWasBreaking()
                        || ModuleManager.getModuleState(Nuker.class) && Nuker.getWasBreaking());
        progress = advanceModuleInformationFade(INFO_BREAKING, breakingActive, deltaSpeed);
        if (progress > 0f)
            entries.add(new StyledTextRenderEntry("Breaking...", centerX, startY, progress));
        if (breakingActive) startY -= 40f;

        boolean blinking = BlinkUtil.INSTANCE.getBlinking();
        progress = advanceModuleInformationFade(INFO_BLINKING, blinking, deltaSpeed);
        if (progress > 0f)
            entries.add(new StyledTextRenderEntry("Blinking... (x" + BlinkUtil.INSTANCE.getPacketCount() + ")", centerX, startY, progress));
        if (blinking) startY -= 40f;

        boolean scaffoldActive = ModuleManager.getModuleState(Scaffold.class) && mc.player != null;
        progress = advanceModuleInformationFade(INFO_SCAFFOLD_BLOCKS, scaffoldActive, deltaSpeed);
        if (progress > 0f) {
            int scaffoldBlocks = mc.player != null ? Scaffold.countUsableHotbarBlocks(mc.player.getInventory()) : 0;
            entries.add(new StyledTextRenderEntry("Amount: " + scaffoldBlocks, centerX, startY, progress));
        }
        if (scaffoldActive) startY -= 40f;

        List<Player> murdererList = MurdererDetector.murderers;
        if (!murdererList.isEmpty()) {
            for (Player murderer : murdererList) {
                String distance = mc.player != null ? Interpolation.INSTANCE.getDecimalFormat().format(mc.player.distanceTo(murderer)) : Interpolation.INSTANCE.getDecimalFormat().format(0f);
                entries.add(new StyledTextRenderEntry("> " + murderer.getName().getString() + " (" + distance + "m)", centerX, startY, 1f));
                startY -= 27f;
            }
        }
        boolean murdererDetectorActive = ModuleManager.getModuleState(MurdererDetector.class);
        progress = advanceModuleInformationFade(INFO_MURDERER_COUNT, murdererDetectorActive, deltaSpeed);
        if (progress > 0f)
            entries.add(new StyledTextRenderEntry("Murderers: " + murdererList.size(), centerX, startY, progress));
    }

    public void renderPlayerList(NVGU vg) {
        List<PlayerInfo> players = getPlayerListEntries();
        if (players.isEmpty() || playerListRowBuffer.isEmpty()) return;

        float x = getLeftHudX();
        float y = getLeftHudTopY();
        float height = getPlayerListHeight();
        int totalPlayers = players.size();
        int visiblePlayers = playerListRowBuffer.size();
        String countText = totalPlayers > visiblePlayers ? visiblePlayers + "/" + totalPlayers : String.valueOf(totalPlayers);

        NanoVGTheme.renderPanel(vg, x, y, 240f, height, 8f, 1f);
        vg.rectangle(x + 8f, y + 23f - 2f, 240f - 16f, 1f, new Color(255, 255, 255, 38));

        NVGFonts.ICON.drawText(MaterialIcon.PERSON, x + 8f, y + 3f, 14f, new Color(0, 255, 255), Alignment.LEFT_TOP, true);
        NVGFonts.INTER.drawText("Players (" + countText + ")", x + 26f, y + 4f, 13f, new Color(255, 255, 255, 225), Alignment.LEFT_TOP, true);

        float rowY = y + 23f;
        for (int i = 0; i < visiblePlayers; i++) {
            PlayerListRow row = playerListRowBuffer.get(i);

            if (i % 2 == 0)
                vg.rectangle(x + 6f, rowY + 1f, 240f - 12f, row.rowHeight - 2f, new Color(255, 255, 255, 18));

            vg.circle(x + 11f, rowY + row.rowHeight / 2f, 2.2f, row.pingColor);
            for (int line = 0; line < row.nameLines.size(); line++) {
                NVGFonts.INTER.drawText(row.nameLines.get(line), x + 17f, rowY + 3f + line * 14f, 13f, row.nameColor, Alignment.LEFT_TOP, false);
            }
            NVGFonts.INTER.drawText(row.pingText, x + 240f - 8f, rowY + 3f, 12f, row.pingColor, Alignment.RIGHT_TOP, false);

            rowY += row.rowHeight;
        }
    }

    private static void renderPlayerListEffects(NVGU vg) {
        List<PlayerInfo> players = getPlayerListEntries();
        if (players.isEmpty()) return;

        float x = getLeftHudX();
        float y = getLeftHudTopY();
        float height = getPlayerListHeight();

        NanoVGTheme.renderPanelEffects(vg, x, y, 240f, height, 8f, 1f);
    }

    private static boolean isSelfPlayerListEntry(PlayerInfo entry) {
        return mc.player != null && Objects.equals(entry.getProfile().id(), mc.player.getUUID());
    }

    private static String getPlayerListName(PlayerInfo entry) {
        String name = getPlayerListDisplayName(entry).getString();
        name = stripPlayerListRankPrefix(name);
        return name == null || name.isBlank() ? "Unknown" : name;
    }

    private static String getColoredPlayerListName(PlayerInfo entry) {
        Component displayName = getPlayerListDisplayName(entry);
        String plainName = displayName.getString();
        String strippedName = stripPlayerListRankPrefix(plainName);
        if (strippedName == null || strippedName.isBlank())
            return "Unknown";

        int skippedCharacters = plainName.length() - strippedName.length();
        return NanoVGTextFormatter.formatColors(displayName, skippedCharacters);
    }

    private static Component getPlayerListDisplayName(PlayerInfo entry) {
        return mc.gui.hud.getTabList().getNameForDisplay(entry);
    }

    private static String stripPlayerListRankPrefix(String name) {
        if (name == null)
            return name;

        int separatorIndex = name.indexOf('|');
        if (separatorIndex >= 0)
            return name.substring(separatorIndex + 1).stripLeading();

        if (!name.startsWith("["))
            return name;

        int closingBracketIndex = name.indexOf(']');
        if (closingBracketIndex <= 0)
            return name;

        return name.substring(closingBracketIndex + 1).stripLeading();
    }

    private static String getPlayerListPingText(int latency) {
        return latency >= 0 ? latency + "ms" : "--";
    }

    private static Color getPlayerListPingColor(int latency) {
        if (latency < 0)
            return new Color(200, 200, 200, 170);
        if (latency <= 80)
            return new Color(90, 255, 120, 220);
        if (latency <= 180)
            return new Color(255, 210, 70, 220);

        return new Color(255, 95, 95, 220);
    }

    private static List<String> wrapTextToWidth(String text, float maxWidth, float fontSize) {
        List<String> lines = new ArrayList<>();
        if (maxWidth <= 0f)
            return Collections.singletonList("");
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        if (NVGFonts.INTER.getWidth(text, fontSize) <= maxWidth)
            return Collections.singletonList(text);

        int start = 0;
        while (start < text.length()) {
            int end = findWrapEnd(text, start, maxWidth, fontSize);
            lines.add(text.substring(start, end).strip());
            start = end;

            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }

        return lines.isEmpty() ? Collections.singletonList("") : lines;
    }

    private static int findWrapEnd(String text, int start, float maxWidth, float fontSize) {
        int bestFit = start + 1;
        int lastWhitespace = -1;

        for (int end = start + 1; end <= text.length(); end++) {
            String candidate = text.substring(start, end);
            if (NVGFonts.INTER.getWidth(candidate, fontSize) > maxWidth)
                break;

            bestFit = end;
            if (Character.isWhitespace(text.charAt(end - 1)))
                lastWhitespace = end;
        }

        if (bestFit >= text.length())
            return text.length();
        if (lastWhitespace > start)
            return lastWhitespace;

        return bestFit;
    }

    public void renderMinimap(NVGU vg, long deltaTime) {
        if (mc.player == null || mc.level == null) return;

        ClientLevel level = mc.level;
        BlockPos playerBlockPos = mc.player.blockPosition();
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();
        updateMinimapView(playerX, playerZ, deltaTime);

        updateMinimapTerrainTexture(vg, level, (int) Math.floor(minimapViewX), (int) Math.floor(minimapViewZ), playerBlockPos.getY());
        drawMinimap(vg, minimapViewX, minimapViewZ, deltaTime);
    }

    private void drawMinimap(NVGU vg, double viewX, double viewZ, long deltaTime) {
        float mapX = getLeftHudX();
        float mapY = getMinimapY();
        float halfSize = 150f / 2f;
        float centerX = mapX + halfSize;
        float centerY = mapY + halfSize;

        NanoVGTheme.renderPanel(vg, mapX, mapY, 150f, 150f, 20f, 1f);
        float blockScale = (150f - 12f) / (MINIMAP_VISIBLE_RADIUS_BLOCKS * 2f);
        renderMinimapTerrain(vg, mapX, mapY, centerX, centerY, blockScale, viewX, viewZ);
        vg.line(centerX, mapY + 7f, centerX, mapY + 150f - 7f, 1f, new Color(255, 255, 255, 28));
        vg.line(mapX + 7f, centerY, mapX + 150f - 7f, centerY, 1f, new Color(255, 255, 255, 28));

        renderMinimapPlayerDots(vg, centerX, centerY, blockScale, viewX, viewZ, deltaTime);

        float yawRadians = (float) Math.toRadians(mc.player.getYRot() - 180f);
        float dirX = (float) Math.sin(yawRadians);
        float dirZ = (float) -Math.cos(yawRadians);
        vg.line(centerX, centerY, centerX + dirX * 12f, centerY + dirZ * 12f, 1.5f, new Color(255, 255, 0, 220));

        float selfX = centerX - 4f / 2f;
        float selfY = centerY - 4f / 2f;
        vg.roundedRectangle(selfX, selfY, 4f, 4f, 2f, new Color(0, 255, 255, 240));

    }

    private static void renderMinimapEffects(NVGU vg) {
        if (mc.player == null || mc.level == null) return;

        float mapX = getLeftHudX();
        float mapY = getMinimapY();

        NanoVGTheme.renderPanelEffects(vg, mapX, mapY, 150f, 150f, 20f, 1f);
    }

    private void renderMinimapTerrain(NVGU vg, float mapX, float mapY, float centerX, float centerY, float blockScale, double viewX, double viewZ) {
        if (!minimapTerrainTextureReady || !vg.hasTexture("krs_minimap_terrain")) {
            minimapTerrainDirty = true;
            return;
        }
        float inset = 6f;
        float terrainX = mapX + inset;
        float terrainY = mapY + inset;
        float terrainSize = 150f - inset * 2f;
        int terrainStartX = minimapTerrainCenterBlockX - minimapTerrainTextureRangeBlocks();
        int terrainStartZ = minimapTerrainCenterBlockZ - minimapTerrainTextureRangeBlocks();
        float textureX = centerX + (float) (terrainStartX - viewX) * blockScale;
        float textureY = centerY + (float) (terrainStartZ - viewZ) * blockScale;
        float textureSize = minimapTerrainResolution() * blockScale;
        float clipX = Math.max(terrainX, textureX);
        float clipY = Math.max(terrainY, textureY);
        float clipRight = Math.min(terrainX + terrainSize, textureX + textureSize);
        float clipBottom = Math.min(terrainY + terrainSize, textureY + textureSize);
        if (clipRight <= clipX || clipBottom <= clipY)
            return;

        // Never sample outside the cached image. NanoVG otherwise clamps the last
        // texel and stretches it into a band while the view moves near an edge.
        vg.scissor(clipX, clipY, clipRight - clipX, clipBottom - clipY, () ->
                vg.roundedRectangle(
                        terrainX,
                        terrainY,
                        terrainSize,
                        terrainSize,
                        Math.max(0f, 20f - inset),
                        vg.texture("krs_minimap_terrain", textureX, textureY, textureSize, textureSize)
                )
        );
    }

    private void renderMinimapPlayerDots(NVGU vg, float centerX, float centerY, float blockScale, double viewX, double viewZ, long deltaTime) {
        double radius = MINIMAP_VISIBLE_RADIUS_BLOCKS + 1.5;
        double radiusSquared = radius * radius;
        minimapPlayerUuidBuffer.clear();

        for (AbstractClientPlayer otherPlayer : mc.level.players()) {
            if (otherPlayer == mc.player) continue;

            UUID uuid = otherPlayer.getUUID();
            minimapPlayerUuidBuffer.add(uuid);
            MinimapPlayerDotState dotState = minimapPlayerDots.get(uuid);

            if (dotState == null) {
                dotState = new MinimapPlayerDotState(otherPlayer.getX(), otherPlayer.getZ());
                minimapPlayerDots.put(uuid, dotState);
            } else {
                smoothMinimapDot(dotState, otherPlayer.getX(), otherPlayer.getZ(), deltaTime);
            }

            double dx = dotState.x - viewX;
            double dz = dotState.z - viewZ;
            if (dx * dx + dz * dz > radiusSquared) continue;

            float drawX = centerX + (float) dx * blockScale - 3f / 2f;
            float drawY = centerY + (float) dz * blockScale - 3f / 2f;
            vg.rectangle(drawX, drawY, 3f, 3f, new Color(0, 255, 0, 230));
        }

        minimapPlayerDots.keySet().removeIf(uuid -> !minimapPlayerUuidBuffer.contains(uuid));
        minimapPlayerUuidBuffer.clear();
    }

    private void updateMinimapView(double playerX, double playerZ, long deltaTime) {
        if (!minimapViewInitialized || !Double.isFinite(minimapViewX) || !Double.isFinite(minimapViewZ)) {
            minimapViewX = playerX;
            minimapViewZ = playerZ;
            minimapViewInitialized = true;
            return;
        }

        double dx = playerX - minimapViewX;
        double dz = playerZ - minimapViewZ;
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (!Double.isFinite(distance) || distance > (MINIMAP_VISIBLE_RADIUS_BLOCKS * 6.0)) {
            minimapViewX = playerX;
            minimapViewZ = playerZ;
            return;
        }

        double factor = getMinimapSmoothFactor(deltaTime, 14.0, 0.012, distance);
        minimapViewX += dx * factor;
        minimapViewZ += dz * factor;
    }

    private static void smoothMinimapDot(MinimapPlayerDotState dotState, double targetX, double targetZ, long deltaTime) {
        double dx = targetX - dotState.x;
        double dz = targetZ - dotState.z;
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (!Double.isFinite(distance) || distance > (MINIMAP_VISIBLE_RADIUS_BLOCKS * 4.0)) {
            dotState.x = targetX;
            dotState.z = targetZ;
            return;
        }

        double factor = getMinimapSmoothFactor(deltaTime, 18.0, 0.018, distance);
        dotState.x += dx * factor;
        dotState.z += dz * factor;
    }

    private static double getMinimapSmoothFactor(long deltaTime, double responsiveness, double distanceBoost, double distance) {
        long clampedDelta = Math.clamp(deltaTime, 0L, 100L);
        double baseFactor = 1.0 - Math.exp(-responsiveness * (clampedDelta / 1000.0));
        return Math.clamp(baseFactor + distance * distanceBoost, baseFactor, 0.92);
    }

    private void updateMinimapTerrainTexture(NVGU vg, ClientLevel level, int centerBlockX, int centerBlockZ, int playerBlockY) {
        boolean hasTexture = vg.hasTexture("krs_minimap_terrain");
        if (!hasTexture) {
            minimapTerrainTextureReady = false;
            if (minimapTerrainDataReady && !minimapTerrainBuildInProgress) {
                uploadMinimapTerrainTexture(vg, minimapTerrainColors);
                return;
            }
            minimapTerrainDirty = true;
        }

        long now = System.nanoTime();
        boolean hasCachedCenter = minimapTerrainDataReady
                && minimapTerrainCenterBlockX != Integer.MIN_VALUE
                && minimapTerrainCenterBlockZ != Integer.MIN_VALUE;
        long centerDriftX = hasCachedCenter ? Math.abs((long) centerBlockX - minimapTerrainCenterBlockX) : Long.MAX_VALUE;
        long centerDriftZ = hasCachedCenter ? Math.abs((long) centerBlockZ - minimapTerrainCenterBlockZ) : Long.MAX_VALUE;
        boolean centerMovedFar = Math.max(centerDriftX, centerDriftZ) >= MINIMAP_REBUILD_DRIFT_BLOCKS;
        boolean verticalMovedFar = hasCachedCenter
                && Math.abs((long) playerBlockY - minimapTerrainPlayerBlockY) >= 16L;
        boolean refreshDue = hasCachedCenter
                && now - minimapTerrainLastUpdateNanos >= MINIMAP_TERRAIN_REFRESH_NANOS;

        if (minimapTerrainBuildInProgress) {
            long buildDriftX = Math.abs((long) centerBlockX - minimapTerrainBuildCenterBlockX);
            long buildDriftZ = Math.abs((long) centerBlockZ - minimapTerrainBuildCenterBlockZ);
            if (Math.max(buildDriftX, buildDriftZ) > minimapTerrainTextureRangeBlocks())
                startMinimapTerrainBuild(centerBlockX, centerBlockZ, playerBlockY, false);
        } else if (minimapTerrainDirty || !hasCachedCenter || centerMovedFar || verticalMovedFar || refreshDue) {
            boolean reuseCachedTerrain = hasCachedCenter && centerMovedFar
                    && !verticalMovedFar && !refreshDue && !minimapTerrainDirty;
            startMinimapTerrainBuild(centerBlockX, centerBlockZ, playerBlockY, reuseCachedTerrain);
        }

        if (!minimapTerrainBuildInProgress)
            return;

        int sampleBudget = minimapTerrainTextureReady
                ? MINIMAP_TERRAIN_SAMPLES_PER_FRAME
                : MINIMAP_TERRAIN_SAMPLES_PER_FRAME * 2;
        processMinimapTerrainBuild(level, sampleBudget);
        if (minimapTerrainDirtyCursor < minimapTerrainDirtyCount)
            return;

        int[] previousColors = minimapTerrainColors;
        minimapTerrainColors = minimapTerrainBuildColors;
        minimapTerrainBuildColors = previousColors;
        minimapTerrainCenterBlockX = minimapTerrainBuildCenterBlockX;
        minimapTerrainCenterBlockZ = minimapTerrainBuildCenterBlockZ;
        minimapTerrainPlayerBlockY = minimapTerrainBuildPlayerBlockY;
        minimapTerrainLastUpdateNanos = now;
        minimapTerrainBuildInProgress = false;
        minimapTerrainDataReady = true;
        minimapTerrainDirty = false;
        uploadMinimapTerrainTexture(vg, minimapTerrainColors);
    }

    private void startMinimapTerrainBuild(int centerBlockX, int centerBlockZ, int playerBlockY, boolean reuseCachedTerrain) {
        minimapTerrainBuildCenterBlockX = centerBlockX;
        minimapTerrainBuildCenterBlockZ = centerBlockZ;
        minimapTerrainBuildPlayerBlockY = playerBlockY;
        minimapTerrainDirtyCount = 0;
        minimapTerrainDirtyCursor = 0;
        minimapTerrainBuildInProgress = true;

        int resolution = minimapTerrainResolution();
        int range = minimapTerrainTextureRangeBlocks();
        int buildStartX = centerBlockX - range;
        int buildStartZ = centerBlockZ - range;
        int cachedStartX = minimapTerrainCenterBlockX - range;
        int cachedStartZ = minimapTerrainCenterBlockZ - range;

        for (int pixelZ = 0; pixelZ < resolution; pixelZ++) {
            int sampleZ = buildStartZ + pixelZ;
            int cachedPixelZ = sampleZ - cachedStartZ;
            for (int pixelX = 0; pixelX < resolution; pixelX++) {
                int destinationIndex = pixelZ * resolution + pixelX;
                int sampleX = buildStartX + pixelX;
                int cachedPixelX = sampleX - cachedStartX;
                if (reuseCachedTerrain
                        && cachedPixelX >= 0 && cachedPixelX < resolution
                        && cachedPixelZ >= 0 && cachedPixelZ < resolution) {
                    minimapTerrainBuildColors[destinationIndex] = minimapTerrainColors[cachedPixelZ * resolution + cachedPixelX];
                } else {
                    minimapTerrainDirtyIndices[minimapTerrainDirtyCount++] = destinationIndex;
                }
            }
        }
    }

    private void processMinimapTerrainBuild(ClientLevel level, int sampleBudget) {
        int resolution = minimapTerrainResolution();
        int range = minimapTerrainTextureRangeBlocks();
        int startX = minimapTerrainBuildCenterBlockX - range;
        int startZ = minimapTerrainBuildCenterBlockZ - range;
        int end = Math.min(minimapTerrainDirtyCount, minimapTerrainDirtyCursor + Math.max(1, sampleBudget));

        while (minimapTerrainDirtyCursor < end) {
            int destinationIndex = minimapTerrainDirtyIndices[minimapTerrainDirtyCursor++];
            int pixelX = destinationIndex % resolution;
            int pixelZ = destinationIndex / resolution;
            int sampleX = startX + pixelX;
            int sampleZ = startZ + pixelZ;
            int rgba = 0;

            if (level.hasChunk(sampleX >> 4, sampleZ >> 4)) {
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, sampleX, sampleZ) - 1;
                if (surfaceY >= level.getMinY()) {
                    minimapTerrainSamplePos.set(sampleX, surfaceY, sampleZ);
                    rgba = getMinimapTerrainColor(
                            level,
                            minimapTerrainSamplePos,
                            surfaceY,
                            minimapTerrainBuildPlayerBlockY
                    );
                }
            }
            minimapTerrainBuildColors[destinationIndex] = rgba;
        }
    }

    private void uploadMinimapTerrainTexture(NVGU vg, int[] colors) {
        minimapTerrainBuffer.clear();
        for (int color : colors)
            putRgba(minimapTerrainBuffer, color);
        minimapTerrainBuffer.flip();
        vg.createOrUpdateTextureRGBA(
                "krs_minimap_terrain",
                minimapTerrainResolution(),
                minimapTerrainResolution(),
                minimapTerrainBuffer
        );
        minimapTerrainTextureReady = vg.hasTexture("krs_minimap_terrain");
    }

    private static int getMinimapTerrainColor(ClientLevel level, BlockPos.MutableBlockPos pos, int surfaceY, int playerBlockY) {
        int playerTopY = Math.min(surfaceY, playerBlockY + 16);
        int playerBottomY = Math.max(level.getMinY(), playerBlockY - 96);
        int color = scanMinimapTerrainColumn(level, pos, playerTopY, playerBottomY, playerBlockY);

        if (color != 0)
            return color;

        int fallbackBottomY = Math.max(level.getMinY(), surfaceY - 256);
        return scanMinimapTerrainColumn(level, pos, surfaceY, fallbackBottomY, playerBlockY);
    }

    private static int scanMinimapTerrainColumn(ClientLevel level, BlockPos.MutableBlockPos pos, int topY, int bottomY, int playerBlockY) {
        if (topY < bottomY)
            return 0;

        for (int y = topY; y >= bottomY; y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);

            if (state.isAir() || shouldHideMinimapTerrainBlock(state))
                continue;

            int color = getMinimapTerrainColor(level, state, pos, y, playerBlockY);
            if (color != 0)
                return color;
        }

        return 0;
    }

    private static int getMinimapTerrainColor(ClientLevel level, BlockState state, BlockPos pos, int surfaceY, int playerBlockY) {
        MapColor mapColor = state.getMapColor(level, pos);
        if (mapColor == MapColor.NONE)
            return 0;

        int argb = mapColor.calculateARGBColor(MapColor.Brightness.NORMAL);
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        float heightShade = Math.clamp(0.92f + (surfaceY - playerBlockY) * 0.018f, 0.68f, 1.18f);

        red = Math.clamp((int) (red * heightShade), 0, 255);
        green = Math.clamp((int) (green * heightShade), 0, 255);
        blue = Math.clamp((int) (blue * heightShade), 0, 255);

        int alpha;
        if (!state.getFluidState().isEmpty())
            alpha = 135;
        else if (state.canOcclude())
            alpha = 170;
        else
            alpha = 115;

        return red << 24 | green << 16 | blue << 8 | alpha;
    }

    private static boolean shouldHideMinimapTerrainBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.BARRIER
                || block == Blocks.GLASS_PANE
                || block instanceof TransparentBlock
                || block instanceof StainedGlassPaneBlock;
    }

    private static void putRgba(ByteBuffer buffer, int rgba) {
        buffer.put((byte) ((rgba >> 24) & 0xFF));
        buffer.put((byte) ((rgba >> 16) & 0xFF));
        buffer.put((byte) ((rgba >> 8) & 0xFF));
        buffer.put((byte) (rgba & 0xFF));
    }

    private void resetMinimapTerrainCache() {
        minimapTerrainCenterBlockX = Integer.MIN_VALUE;
        minimapTerrainCenterBlockZ = Integer.MIN_VALUE;
        minimapTerrainPlayerBlockY = Integer.MIN_VALUE;
        minimapTerrainLastUpdateNanos = 0L;
        minimapTerrainDirty = true;
        minimapTerrainDataReady = false;
        minimapTerrainTextureReady = false;
        minimapTerrainBuildInProgress = false;
        minimapTerrainDirtyCount = 0;
        minimapTerrainDirtyCursor = 0;
        minimapViewInitialized = false;
        minimapViewX = 0.0;
        minimapViewZ = 0.0;
        minimapPlayerDots.clear();
        minimapPlayerUuidBuffer.clear();
        minimapTerrainBuffer.clear();
    }

    private static float advanceModuleInformationFade(int slot, boolean active, float deltaSpeed) {
        float progress = moduleInformationFadeProgress[slot] + (active ? deltaSpeed : -deltaSpeed);
        progress = Math.max(0f, Math.min(1f, progress));
        moduleInformationFadeProgress[slot] = progress;
        return progress;
    }

    private static void renderStyledText(NVGU vg, String text, float x, float y, float alpha) {
        StyledTextRenderEntry entry = new StyledTextRenderEntry(text, x, y, alpha);
        vg.beginEffectBatch();
        renderStyledTextEffects(vg, List.of(entry));
        vg.flushEffectBatch();
        renderStyledTextBody(vg, entry);
    }

    private static void renderStyledTextEffects(NVGU vg, List<StyledTextRenderEntry> entries) {
        for (StyledTextRenderEntry entry : entries) {
            renderStyledTextEffect(vg, entry);
        }
    }

    private static void renderStyledTextBodies(NVGU vg, List<StyledTextRenderEntry> entries) {
        for (StyledTextRenderEntry entry : entries) {
            renderStyledTextBody(vg, entry);
        }
    }

    private static void renderStyledTextEffect(NVGU vg, StyledTextRenderEntry entry) {
        float textWidth = NVGFonts.INTER.getWidth(entry.text, 16f) + 10f;
        float textHeight = NVGFonts.INTER.getHeight(16f) + 6f;

        NanoVGTheme.renderCompactEffects(
                vg,
                entry.x - textWidth / 2f, entry.y, textWidth, textHeight,
                5f,
                entry.alpha
        );
    }

    private static void renderStyledTextBody(NVGU vg, StyledTextRenderEntry entry) {
        String text = entry.text;
        float x = entry.x;
        float y = entry.y;
        float alpha = entry.alpha;
        float textWidth = NVGFonts.INTER.getWidth(text, 16f) + 10f;
        float textHeight = NVGFonts.INTER.getHeight(16f) + 6f;
        Color textColor = alphaColor(255, 255, 255, (int) (220 * alpha));

        Color accent = getFadedColor(0, 1).darker();
        NanoVGTheme.renderCompact(vg, x - textWidth / 2f, y, textWidth, textHeight, 5f, alpha);
        vg.roundedRectangle(x + 4f - textWidth / 2f, y + textHeight, textWidth - 8f, 2f, 2f, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), textColor.getAlpha()));
        NVGFonts.INTER.drawText(text, x, y + 3f, 16f, textColor, Alignment.CENTER_TOP, true);
    }

    private List<ModuleCategory> prepareTabGuiCategories() {
        tabGuiCategoryBuffer.clear();
        for (ModuleCategory category : ModuleCategory.values()) {
            if (category != ModuleCategory.Dev && hasTabGuiModules(category))
                tabGuiCategoryBuffer.add(category);
        }

        tabGuiCategoryIndex = clampTabGuiIndex(tabGuiCategoryIndex, tabGuiCategoryBuffer.size());
        if (tabGuiCategoryBuffer.isEmpty()) {
            tabGuiModuleIndex = 0;
            tabGuiExpanded = false;
        }
        return tabGuiCategoryBuffer;
    }

    private List<Module> prepareTabGuiModules(@Nullable ModuleCategory category) {
        tabGuiModuleBuffer.clear();
        if (category == null) {
            tabGuiModuleIndex = 0;
            return tabGuiModuleBuffer;
        }

        for (Module module : ModuleManager.modules) {
            if (module.moduleCategory == category)
                tabGuiModuleBuffer.add(module);
        }

        tabGuiModuleIndex = clampTabGuiIndex(tabGuiModuleIndex, tabGuiModuleBuffer.size());
        return tabGuiModuleBuffer;
    }

    @Nullable
    private ModuleCategory selectedTabGuiCategory(List<ModuleCategory> categories) {
        if (categories.isEmpty())
            return null;

        tabGuiCategoryIndex = clampTabGuiIndex(tabGuiCategoryIndex, categories.size());
        return categories.get(tabGuiCategoryIndex);
    }

    private int clampTabGuiIndex(int index, int size) {
        if (size <= 0)
            return 0;

        return Math.max(0, Math.min(index, size - 1));
    }

    private int moveTabGuiIndex(int index, int amount, int size) {
        if (size <= 0)
            return 0;

        int next = (index + amount) % size;
        return next < 0 ? next + size : next;
    }

    public boolean isHandlingTabGuiKey(int key, int action) {
        return tempEnabled
                && tabGui.get()
                && !ModuleManager.getModuleState(Stalker.class)
                && mc.gui.screen() == null
                && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)
                && isTabGuiArrowKey(key);
    }

    private static boolean isTabGuiArrowKey(int key) {
        return key == GLFW.GLFW_KEY_UP
                || key == GLFW.GLFW_KEY_DOWN
                || key == GLFW.GLFW_KEY_LEFT
                || key == GLFW.GLFW_KEY_RIGHT;
    }

    private void updateTabGuiAnimations(float deltaSpeed, List<ModuleCategory> categories, List<Module> modules) {
        tabGuiExpandProgress = animateTabGuiValue(tabGuiExpandProgress, tabGuiExpanded && !modules.isEmpty() ? 1f : 0f, deltaSpeed);

        if (categories.isEmpty()) {
            tabGuiCategorySelectionY = Float.NaN;
            tabGuiModuleSelectionY = Float.NaN;
            return;
        }

        float y = getTabGuiBaseY();
        float targetCategorySelectionY = y + 25f + tabGuiCategoryIndex * 22f;
        tabGuiCategorySelectionY = Float.isNaN(tabGuiCategorySelectionY)
                ? targetCategorySelectionY
                : animateTabGuiValue(tabGuiCategorySelectionY, targetCategorySelectionY, deltaSpeed);

        if (modules.isEmpty()) {
            tabGuiModuleSelectionY = Float.NaN;
            return;
        }

        float moduleHeight = getTabGuiVisiblePanelHeight(modules.size(), y);
        int firstModule = getFirstVisibleTabGuiModuleIndex(modules.size(), moduleHeight);
        float targetModuleSelectionY = y + 25f + (tabGuiModuleIndex - firstModule) * 22f;
        tabGuiModuleSelectionY = Float.isNaN(tabGuiModuleSelectionY)
                ? targetModuleSelectionY
                : animateTabGuiValue(tabGuiModuleSelectionY, targetModuleSelectionY, deltaSpeed);
    }

    private float animateTabGuiValue(float current, float target, float deltaSpeed) {
        float amount = Math.clamp(deltaSpeed * 0.9f, 0.08f, 1f);
        return current + (target - current) * amount;
    }

    private static float easeTabGuiProgress(float progress) {
        progress = Math.clamp(progress, 0f, 1f);
        return progress * progress * (3f - 2f * progress);
    }

    private void renderTabGuiEffects(NVGU vg, List<ModuleCategory> categories, List<Module> modules) {
        if (categories.isEmpty())
            return;

        float x = getLeftHudX();
        float y = getTabGuiBaseY();
        float categoryWidth = getTabGuiCategoryPanelWidth(categories);
        float categoryHeight = getTabGuiPanelHeight(categories.size());
        float expandProgress = easeTabGuiProgress(tabGuiExpandProgress);

        NanoVGTheme.renderPanelEffects(vg, x, y, categoryWidth, categoryHeight, 8f, 1f);

        if (expandProgress > 0.01f && !modules.isEmpty()) {
            ModuleCategory category = selectedTabGuiCategory(categories);
            float moduleX = x + categoryWidth + 6f;
            float moduleWidth = getTabGuiModulePanelWidth(category, modules) * expandProgress;
            float moduleHeight = getTabGuiVisiblePanelHeight(modules.size(), y);

            NanoVGTheme.renderPanelEffects(vg, moduleX, y, moduleWidth, moduleHeight, 8f, expandProgress);
        }
    }

    private void renderTabGuiBody(NVGU vg, List<ModuleCategory> categories, List<Module> modules) {
        if (categories.isEmpty())
            return;

        float x = getLeftHudX();
        float y = getTabGuiBaseY();
        float categoryWidth = getTabGuiCategoryPanelWidth(categories);
        float categoryHeight = getTabGuiPanelHeight(categories.size());
        float expandProgress = easeTabGuiProgress(tabGuiExpandProgress);

        NanoVGTheme.renderPanel(vg, x, y, categoryWidth, categoryHeight, 8f, 1f);
        vg.rectangle(x + 8f, y + 24f, categoryWidth - 16f, 1f, new Color(255, 255, 255, 38));

        NVGFonts.ICON.drawText(MaterialIcon.MENU, x + 8f, y + 4f, 14f, new Color(0, 255, 255), Alignment.LEFT_TOP, true);
        NVGFonts.INTER_MEDIUM.drawText("Modules", x + 26f, y + 5f, 13f, new Color(255, 255, 255, 225), Alignment.LEFT_TOP, true);

        if (!Float.isNaN(tabGuiCategorySelectionY)) {
            vg.roundedRectangle(x + 5f, tabGuiCategorySelectionY + 2f, categoryWidth - 10f, 18f, 4f, new Color(0, 255, 255, 32));
            vg.roundedRectangle(x + 5f, tabGuiCategorySelectionY + 4f, 2f, 14f, 1f, new Color(0, 255, 255, 170));
        }

        for (int i = 0, n = categories.size(); i < n; i++) {
            ModuleCategory category = categories.get(i);
            float rowY = y + 25f + i * 22f;
            boolean selected = i == tabGuiCategoryIndex;

            Color textColor = selected ? new Color(0, 255, 255) : new Color(255, 255, 255, 215);
            NVGFonts.ICON.drawText(tabGuiCategoryIcon(category), x + 11f, rowY + 4f, 12f, textColor, Alignment.LEFT_TOP, true);
            NVGFonts.INTER.drawText(category.name(), x + 28f, rowY + 4f, 14f, textColor, Alignment.LEFT_TOP, true);

            if (selected && expandProgress > 0.01f) {
                NVGFonts.ICON.drawText(MaterialIcon.RIGHT_ARROW, x + categoryWidth - 8f, rowY + 3f, 14f, new Color(0, 255, 255, (int) (255 * expandProgress)), Alignment.RIGHT_TOP, true);
            }
        }

        if (expandProgress > 0.01f && !modules.isEmpty()) {
            ModuleCategory category = selectedTabGuiCategory(categories);
            renderTabGuiModulePanel(vg, category, modules, x + categoryWidth + 6f, y);
        }
    }

    private void renderTabGuiModulePanel(NVGU vg, @Nullable ModuleCategory category, List<Module> modules, float x, float y) {
        float width = getTabGuiModulePanelWidth(category, modules);
        float height = getTabGuiVisiblePanelHeight(modules.size(), y);
        int visibleRows = getTabGuiVisibleRows(modules.size(), height);
        int firstModule = getFirstVisibleTabGuiModuleIndex(modules.size(), height);
        float expandProgress = easeTabGuiProgress(tabGuiExpandProgress);
        float visibleWidth = width * expandProgress;

        vg.pushScissor(x, y, visibleWidth, height);
        vg.globalAlpha(expandProgress, () -> {
            NanoVGTheme.renderPanel(vg, x, y, width, height, 8f, 1f);
            vg.rectangle(x + 8f, y + 24f, width - 16f, 1f, new Color(255, 255, 255, 38));

            NVGFonts.ICON.drawText(MaterialIcon.TUNE, x + 8f, y + 4f, 14f, new Color(0, 255, 255), Alignment.LEFT_TOP, true);
            NVGFonts.INTER_MEDIUM.drawText(category != null ? category.name() : "Modules", x + 26f, y + 5f, 13f, new Color(255, 255, 255, 225), Alignment.LEFT_TOP, true);

            if (!Float.isNaN(tabGuiModuleSelectionY)) {
                vg.roundedRectangle(x + 5f, tabGuiModuleSelectionY + 2f, width - 10f, 18f, 4f, new Color(0, 255, 255, 32));
                vg.roundedRectangle(x + 5f, tabGuiModuleSelectionY + 4f, 2f, 14f, 1f, new Color(0, 255, 255, 170));
            }

            int lastModule = Math.min(modules.size(), firstModule + visibleRows);
            for (int i = firstModule; i < lastModule; i++) {
                Module module = modules.get(i);
                float rowY = y + 25f + (i - firstModule) * 22f;
                boolean selected = i == tabGuiModuleIndex;
                boolean enabled = module.tempEnabled;

                Color nameColor = enabled ? new Color(0, 255, 255) : selected ? new Color(255, 255, 255, 235) : new Color(255, 255, 255, 205);
                Color toggleColor = enabled ? new Color(0, 255, 255) : new Color(160, 160, 160, 200);
                NVGFonts.INTER.drawText(module.moduleName, x + 11f, rowY + 4f, 14f, nameColor, Alignment.LEFT_TOP, true);
                NVGFonts.ICON.drawText(enabled ? MaterialIcon.TOGGLE_ON : MaterialIcon.TOGGLE_OFF, x + width - 8f, rowY + 1.5f, 17f, toggleColor, Alignment.RIGHT_TOP, true);
            }

            if (modules.size() > visibleRows) {
                float trackY = y + 29f;
                float trackHeight = height - 36f;
                float thumbHeight = Math.max(12f, trackHeight * visibleRows / modules.size());
                float thumbY = trackY + (trackHeight - thumbHeight) * firstModule / (modules.size() - visibleRows);
                vg.roundedRectangle(x + width - 4f, thumbY, 2f, thumbHeight, 1f, new Color(0, 255, 255, 150));
            }
        });
        vg.popScissor();
    }

    private float getTabGuiCategoryPanelWidth(List<ModuleCategory> categories) {
        float width = NVGFonts.INTER_MEDIUM.getWidth("Modules", 13f) + 40f;
        for (ModuleCategory category : categories) {
            width = Math.max(width, NVGFonts.INTER.getWidth(category.name(), 14f) + 58f);
        }
        return Math.max(110f, width);
    }

    private static String tabGuiCategoryIcon(ModuleCategory category) {
        return switch (category) {
            case Combat -> MaterialIcon.BOLT;
            case Movement -> MaterialIcon.AUTO_RENEW;
            case Player -> MaterialIcon.PERSON;
            case Level -> MaterialIcon.PUBLIC;
            case Exploit -> MaterialIcon.WARNING;
            case Render -> MaterialIcon.READER;
            case Dev -> MaterialIcon.DEBUG;
        };
    }

    private float getTabGuiModulePanelWidth(@Nullable ModuleCategory category, List<Module> modules) {
        float width = NVGFonts.INTER_MEDIUM.getWidth(category != null ? category.name() : "Modules", 13f) + 40f;
        for (Module module : modules) {
            width = Math.max(width, NVGFonts.INTER.getWidth(module.moduleName, 14f) + 43f);
        }
        return Math.max(145f, width);
    }

    private static float getTabGuiVisiblePanelHeight(int rowCount, float y) {
        if (rowCount <= 0)
            return 0f;

        float panelHeight = getTabGuiPanelHeight(rowCount);
        float availableHeight = NanoVGManager.getScaledScreenHeight() - y - 12f;
        return Math.min(panelHeight, Math.max(getTabGuiPanelHeight(1), availableHeight));
    }

    private static int getTabGuiVisibleRows(int rowCount, float panelHeight) {
        int visibleRows = (int) ((panelHeight - 31f) / 22f);
        return Math.max(1, Math.min(rowCount, visibleRows));
    }

    private int getFirstVisibleTabGuiModuleIndex(int moduleCount, float panelHeight) {
        int visibleRows = getTabGuiVisibleRows(moduleCount, panelHeight);
        if (moduleCount <= visibleRows)
            return 0;

        int first = tabGuiModuleIndex - visibleRows / 2;
        return Math.max(0, Math.min(first, moduleCount - visibleRows));
    }

    private List<ModuleListEntry> prepareModuleListEntries(long deltaTime) {
        boolean sortOrderRefreshed = sortedModulesDirty || sortedModules == null;
        if (sortOrderRefreshed)
            refreshSortedModules();

        List<Module> sourceModules = sortedModules != null ? sortedModules : Collections.emptyList();
        List<ModuleListEntry> renderEntries = moduleListEntryBuffer;
        renderEntries.clear();
        float fontSize = 17f;
        float speed = 0.14f * (deltaTime / 16f);
        boolean displayedWidthChanged = false;

        for (Module m : sourceModules) {
            if (!m.showOnArray) continue;

            boolean isActive = m.tempEnabled;
            ModuleListEntry entry = moduleListEntryCache.get(m);
            if (!isActive && (entry == null || entry.progress <= 0.0f)) continue;
            if (entry == null) {
                entry = new ModuleListEntry();
                moduleListEntryCache.put(m, entry);
            }

            float progress = entry.progress;
            progress = isActive ? Math.min(1.0f, progress + speed) : Math.max(0.0f, progress - speed);

            if (progress <= 0.0f && !isActive) {
                entry.progress = 0.0f;
                continue;
            }

            String text = m.moduleName;
            String tagText = getModuleListTagText(m);
            displayedWidthChanged |= entry.update(text, tagText, progress, fontSize);
            renderEntries.add(entry);
        }

        if (displayedWidthChanged && !sortOrderRefreshed) {
            refreshSortedModules();
            rebuildModuleListEntriesInSortOrder(renderEntries);
        }

        return renderEntries;
    }

    private void rebuildModuleListEntriesInSortOrder(List<ModuleListEntry> renderEntries) {
        renderEntries.clear();
        for (Module module : sortedModules) {
            ModuleListEntry entry = moduleListEntryCache.get(module);
            if (entry != null && entry.progress > 0.0f)
                renderEntries.add(entry);
        }
    }

    private List<ModuleListRenderEntry> prepareModuleListRenderEntries(List<ModuleListEntry> entries) {
        List<ModuleListRenderEntry> renderEntries = moduleListRenderEntryBuffer;
        renderEntries.clear();
        if (entries.isEmpty())
            return renderEntries;

        float xPadding = -5f;
        float listY = 4f;
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float padding = 3f;
        float fontSize = 17f;
        float fontHeight = NVGFonts.INTER.getHeight(fontSize);
        float rowHeight = fontHeight + 5f;
        float defaultTopPadding = 3f;
        float defaultBottomPadding = rowHeight - fontHeight - defaultTopPadding;
        float rightEdge = screenWidth + xPadding - 1f;
        int visibleCount = countVisibleModuleListEntries(entries, rowHeight);
        long colorTimeMillis = System.currentTimeMillis();

        if (visibleCount == 0)
            return renderEntries;

        int visibleIndex = 0;
        for (ModuleListEntry entry : entries) {
            if (rowHeight * entry.progress <= 0.5f)
                continue;

            float textX = rightEdge - entry.fullWidth - 2;
            int alpha = Math.clamp((int) (entry.progress * 255f), 0, 255);
            int baseAlpha = alpha * MODULE_LIST_BACKGROUND_COLOR.getAlpha() / 255;
            float rightX = rightEdge + padding;
            float horizontalPadding = rightX - (textX + entry.fullWidth) + 1;
            float leftX = textX - horizontalPadding;
            float topPadding = visibleIndex == 0 ? horizontalPadding - 2 : defaultTopPadding;
            float bottomPadding = visibleIndex == visibleCount - 1 ? horizontalPadding - 2 : defaultBottomPadding;
            float effectiveHeight = (fontHeight + topPadding + bottomPadding) * entry.progress;
            float textY = listY + topPadding;
            Color textColor = getFadedColor(visibleIndex, visibleCount, alpha, colorTimeMillis);

            while (moduleListRenderEntryPool.size() <= visibleIndex)
                moduleListRenderEntryPool.add(new ModuleListRenderEntry());
            ModuleListRenderEntry renderEntry = moduleListRenderEntryPool.get(visibleIndex);
            renderEntry.update(
                    entry,
                    leftX,
                    listY,
                    rightX - leftX,
                    effectiveHeight,
                    textX,
                    textY,
                    alpha,
                    baseAlpha,
                    textColor
            );
            renderEntries.add(renderEntry);
            listY += effectiveHeight;
            visibleIndex++;
        }

        for (int i = 0, n = renderEntries.size(); i < n; i++) {
            ModuleListRenderEntry entry = renderEntries.get(i);
            float radius = Math.min(6f, entry.height / 2f);
            ConnectedHudRect previous = i > 0 ? renderEntries.get(i - 1) : null;
            ConnectedHudRect next = i + 1 < n ? renderEntries.get(i + 1) : null;
            entry.updateCornerRadii(
                    getExposedHudCornerRadius(previous, entry.x, radius),
                    getExposedHudCornerRadius(previous, entry.right(), radius),
                    getExposedHudCornerRadius(next, entry.right(), radius),
                    getExposedHudCornerRadius(next, entry.x, radius)
            );
        }

        return renderEntries;
    }

    private static void renderModuleListEffects(NVGU vg, List<ModuleListRenderEntry> renderEntries) {
        if (renderEntries.isEmpty()) return;

        for (ModuleListRenderEntry entry : renderEntries) {
            float radius = Math.min(6f, entry.height / 2f);
            float effectAlpha = entry.alpha / 255f;
            NanoVGTheme.renderCompactEffects(vg, entry.x, entry.y, entry.width, entry.height, radius, effectAlpha);
        }
    }

    private void renderModuleListBody(NVGU vg, List<ModuleListRenderEntry> renderEntries) {
        if (renderEntries.isEmpty()) return;

        int stableBackgroundCount = 0;
        ensureModuleListBackgroundGeometryCapacity(renderEntries.size());
        for (ModuleListRenderEntry entry : renderEntries) {
            if (entry.alpha != 255)
                continue;

            int offset = stableBackgroundCount * 8;
            moduleListBackgroundGeometry[offset] = entry.x;
            moduleListBackgroundGeometry[offset + 1] = entry.y;
            moduleListBackgroundGeometry[offset + 2] = entry.width;
            moduleListBackgroundGeometry[offset + 3] = entry.height;
            moduleListBackgroundGeometry[offset + 4] = entry.topLeftRadius;
            moduleListBackgroundGeometry[offset + 5] = entry.topRightRadius;
            moduleListBackgroundGeometry[offset + 6] = entry.bottomRightRadius;
            moduleListBackgroundGeometry[offset + 7] = entry.bottomLeftRadius;
            stableBackgroundCount++;
        }

        vg.roundedRectangles(moduleListBackgroundGeometry, stableBackgroundCount, MODULE_LIST_BACKGROUND_COLOR);
        for (ModuleListRenderEntry entry : renderEntries) {
            if (entry.alpha == 255)
                continue;

            vg.roundedRectangle(
                    entry.x,
                    entry.y,
                    entry.width,
                    entry.height,
                    entry.topLeftRadius,
                    entry.topRightRadius,
                    entry.bottomRightRadius,
                    entry.bottomLeftRadius,
                    alphaColor(
                            MODULE_LIST_BACKGROUND_COLOR.getRed(),
                            MODULE_LIST_BACKGROUND_COLOR.getGreen(),
                            MODULE_LIST_BACKGROUND_COLOR.getBlue(),
                            entry.baseAlpha
                    )
            );
        }

        float fontSize = 17f;
        for (ModuleListRenderEntry entry : renderEntries) {
            boolean clipAnimatedEntry = entry.alpha != 255;
            if (clipAnimatedEntry)
                vg.pushScissor(entry.x, entry.y, entry.width, entry.height);
            try {
                boolean drawTextShadow = entry.baseAlpha < 96;
                NVGFonts.INTER.drawText(entry.entry.text, entry.textX, entry.textY, fontSize,
                        entry.textColor, Alignment.LEFT_TOP, drawTextShadow);
                if (entry.entry.tagText != null) {
                    NVGFonts.INTER.drawText(
                            entry.entry.tagText,
                            entry.textX + entry.entry.textWidth,
                            entry.textY,
                            fontSize,
                            entry.alpha == 255 ? MODULE_LIST_TAG_COLOR : alphaColor(128, 128, 128, entry.alpha),
                            Alignment.LEFT_TOP,
                            drawTextShadow
                    );
                }
            } finally {
                if (clipAnimatedEntry)
                    vg.popScissor();
            }
        }
    }

    private void ensureModuleListBackgroundGeometryCapacity(int rectangleCount) {
        int requiredFloats = rectangleCount * 8;
        if (requiredFloats <= moduleListBackgroundGeometry.length)
            return;

        int newLength = Math.max(requiredFloats, moduleListBackgroundGeometry.length * 2);
        moduleListBackgroundGeometry = Arrays.copyOf(moduleListBackgroundGeometry, newLength);
    }

    private static int countVisibleModuleListEntries(List<ModuleListEntry> renderEntries, float rowHeight) {
        int visibleCount = 0;
        for (ModuleListEntry entry : renderEntries) {
            if (rowHeight * entry.progress > 0.5f)
                visibleCount++;
        }
        return visibleCount;
    }

    private static String getModuleListTagText(Module module) {
        String tag = module.tag();
        return tag != null && !tag.isEmpty() ? " " + tag : null;
    }

    private static float getModuleListWidth(Module module, float fontSize) {
        String tagText = getModuleListTagText(module);
        return NVGFonts.INTER.getWidth(module.moduleName, fontSize) + (tagText != null ? NVGFonts.INTER.getWidth(tagText, fontSize) : 0f);
    }

    private void renderWatermark(NVGU vg) {
        String waterText;
        String first;
        String after;

        switch (waterMarkMode.get().toLowerCase(Locale.ROOT)) {
            case "simple":
                waterText = waterMarkText.get() + " " + Client.clientVersion + " | " + mc.getFps() + " fps";
                first = waterText.substring(0, 1);
                after = waterText.substring(1);
                float boxWidth = NVGFonts.INTER.getWidth(waterText, 20f) + 21f;
                float boxHeight = NVGFonts.INTER.getHeight(20f) + 13f;
                NanoVGTheme.renderPanel(vg, 16f, 16f, boxWidth, boxHeight, 8f, 1f);
                vg.rectangle(22f, 16f + boxHeight, boxWidth - 11f, 2f, new Color(0, 255, 255));
                NVGFonts.INTER.drawText(first, 26f, 22f, 20f, new Color(0, 255, 255), Alignment.LEFT_TOP, true);
                NVGFonts.INTER.drawText(after, NVGFonts.INTER.getWidth(first, 20f) + 26f, 22f, 20f, new Color(255, 255, 255, 255), Alignment.LEFT_TOP, true);
                break;

            case "bullshit":
                float x = 16f;
                float y = 16f;
                float padding = 12f;
                waterText = waterMarkText.get() + " " + Client.clientVersion;
                String fpsText = mc.getFps() + " fps";
                float titleWidth = NVGFonts.INTER.getWidth(waterText, 20f);
                float fpsWidth = NVGFonts.INTER.getWidth(fpsText, 18f);
                float width = Math.max(titleWidth, fpsWidth) + padding * 2;
                float height = NVGFonts.INTER.getHeight(20f) + NVGFonts.INTER.getHeight(18f) + padding * 3;
                NanoVGTheme.renderPanel(vg, x, y, width, height - 6f, 10f, 1f);
                vg.rectangle(x + 6f, y + height - 9f, width - 12f, 2f, new Color(0, 255, 255, 180));
                first = waterText.substring(0, 1);
                after = waterText.substring(1);
                NVGFonts.INTER.drawText(first, x + padding, y + padding, 20f, new Color(0, 255, 255), Alignment.LEFT_TOP, true);
                NVGFonts.INTER.drawText(after, x + padding + NVGFonts.INTER.getWidth(first, 20f), y + padding, 20f, new Color(255, 255, 255, 255), Alignment.LEFT_TOP, true);
                NVGFonts.INTER.drawText(fpsText, x + padding, y + padding + NVGFonts.INTER.getHeight(20f) + 6f, 18f, new Color(200, 200, 200, 180), Alignment.LEFT_TOP, true);
                break;
        }
    }

    private void renderWatermarkEffects(NVGU vg) {
        String waterText;

        switch (waterMarkMode.get().toLowerCase(Locale.ROOT)) {
            case "simple":
                waterText = waterMarkText.get() + " " + Client.clientVersion + " | " + mc.getFps() + " fps";
                float boxWidth = NVGFonts.INTER.getWidth(waterText, 20f) + 21f;
                float boxHeight = NVGFonts.INTER.getHeight(20f) + 13f;
                NanoVGTheme.renderPanelEffects(vg, 16f, 16f, boxWidth, boxHeight, 8f, 1f);
                break;

            case "bullshit":
                float x = 16f;
                float y = 16f;
                float padding = 12f;
                waterText = waterMarkText.get() + " " + Client.clientVersion;
                String fpsText = mc.getFps() + " fps";
                float titleWidth = NVGFonts.INTER.getWidth(waterText, 20f);
                float fpsWidth = NVGFonts.INTER.getWidth(fpsText, 18f);
                float width = Math.max(titleWidth, fpsWidth) + padding * 2;
                float height = NVGFonts.INTER.getHeight(20f) + NVGFonts.INTER.getHeight(18f) + padding * 3;
                NanoVGTheme.renderPanelEffects(vg, x, y, width, height - 6f, 10f, 1f);
                break;
        }
    }

    private static String getRomanNumeral(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(num);
        };
    }

    public static void reloadSortedModules() {
        sortedModulesDirty = true;
    }

    private static void refreshSortedModules() {
        final float fontSize = 17f;
        List<ModuleSortEntry> entries = new ArrayList<>();
        for (Module module : ModuleManager.allModules) {
            if (module.showOnArray)
                entries.add(new ModuleSortEntry(module, getModuleListWidth(module, fontSize)));
        }

        entries.sort((first, second) -> {
            int widthComparison = Float.compare(second.width, first.width);
            if (widthComparison != 0)
                return widthComparison;

            return String.CASE_INSENSITIVE_ORDER.compare(first.module.moduleName, second.module.moduleName);
        });

        List<Module> modules = new ArrayList<>(entries.size());
        for (int i = 0, n = entries.size(); i < n; i++) {
            modules.add(entries.get(i).module);
        }
        sortedModules = List.copyOf(modules);
        sortedModulesDirty = false;
    }

    @Override
    public void onKey(KeyboardEvent event) {
        if (!isHandlingTabGuiKey(event.key, event.action))
            return;

        List<ModuleCategory> categories = prepareTabGuiCategories();
        if (categories.isEmpty())
            return;

        ModuleCategory selectedCategory = selectedTabGuiCategory(categories);
        List<Module> modules = prepareTabGuiModules(selectedCategory);

        switch (event.key) {
            case GLFW.GLFW_KEY_UP -> {
                if (tabGuiExpanded && !modules.isEmpty()) {
                    tabGuiModuleIndex = moveTabGuiIndex(tabGuiModuleIndex, -1, modules.size());
                } else {
                    tabGuiCategoryIndex = moveTabGuiIndex(tabGuiCategoryIndex, -1, categories.size());
                    tabGuiModuleIndex = 0;
                }
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (tabGuiExpanded && !modules.isEmpty()) {
                    tabGuiModuleIndex = moveTabGuiIndex(tabGuiModuleIndex, 1, modules.size());
                } else {
                    tabGuiCategoryIndex = moveTabGuiIndex(tabGuiCategoryIndex, 1, categories.size());
                    tabGuiModuleIndex = 0;
                }
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (!tabGuiExpanded) {
                    tabGuiExpanded = true;
                    tabGuiModuleIndex = clampTabGuiIndex(tabGuiModuleIndex, modules.size());
                } else if (event.action == GLFW.GLFW_PRESS && !modules.isEmpty()) {
                    modules.get(tabGuiModuleIndex).toggle();
                }
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (tabGuiExpanded)
                    tabGuiExpanded = false;
            }
        }
    }

    @Override
    public void onDisable() {
        data.clear();
        targetHudEntryBuffer.clear();
        targetHudFrontToBackBuffer.clear();
        targetHudOccluderBuffer.clear();
        targetHudVisibleEntryBuffer.clear();
        moduleListEntryBuffer.clear();
        moduleListEntryCache.clear();
        moduleListRenderEntryBuffer.clear();
        moduleListRenderEntryPool.clear();
        moduleInformationEntryBuffer.clear();
        tabGuiCategoryBuffer.clear();
        tabGuiModuleBuffer.clear();
        informationEntryBuffer.clear();
        potionEntryBuffer.clear();
        connectionEntryBuffer.clear();
        clearPlayerListEntries();
        tabGuiExpanded = false;
        tabGuiExpandProgress = 0f;
        tabGuiCategorySelectionY = Float.NaN;
        tabGuiModuleSelectionY = Float.NaN;
        lastRenderNanos = 0L;
        clearTargetHudTracking();
        resetMinimapTerrainCache();
    }

    @Override
    public void onEnable() {
        lastRenderNanos = 0L;
        resetMinimapTerrainCache();
    }

    @Override
    public void onWorld(WorldEvent event) {
        data.clear();
        clearTargetHudTracking();
        targetHudEntryBuffer.clear();
        targetHudFrontToBackBuffer.clear();
        targetHudOccluderBuffer.clear();
        targetHudVisibleEntryBuffer.clear();
        informationEntryBuffer.clear();
        potionEntryBuffer.clear();
        connectionEntryBuffer.clear();
        clearPlayerListEntries();
        lastRenderNanos = 0L;
        resetMinimapTerrainCache();
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;

        Client.nanoVgManager.load(vg -> {
            beginHudFrame();
            long now = System.nanoTime();
            long deltaNanos = lastRenderNanos == 0L ? 16_666_667L : now - lastRenderNanos;
            lastRenderNanos = now;
            long deltaTime = Math.clamp(deltaNanos / 1_000_000L, 1L, 100L);

            float deltaSpeed = 0.16f * (deltaTime / 16f);
            deltaSpeed = Math.min(deltaSpeed, 1.0f);
            float interfaceAlpha = NanoVGManager.shouldRenderBelowDebugOverlay() ? 0.2f : 1f;
            float finalDeltaSpeed = deltaSpeed;
            boolean renderWorldHudOverlays = RenderUtil.shouldRenderWorldHudOverlays();

            vg.globalAlpha(interfaceAlpha, () -> {
                List<TargetHudRenderEntry> visibleTargetHudEntries = Collections.emptyList();
                if (targetHud.get() && renderWorldHudOverlays) {
                    targetHudEntryBuffer.clear();
                    for (int i = 0, n = data.size(); i < n; i++) {
                        TargetHudData hudData = data.get(i);
                        TargetHudRenderEntry entry = prepareTargetHud(hudData.id, hudData.entity, finalDeltaSpeed, hudData.hudX, hudData.hudY, hudData.depthSquared);
                        if (entry != null)
                            targetHudEntryBuffer.add(entry);
                    }

                    if (!targetHudEntryBuffer.isEmpty()) {
                        visibleTargetHudEntries = buildVisibleTargetHudEntries(targetHudEntryBuffer);
                    }
                } else {
                    targetHudEntryBuffer.clear();
                }

                List<ModuleListEntry> moduleListEntries = moduleList.get() ? prepareModuleListEntries(deltaTime) : Collections.emptyList();
                List<ModuleListRenderEntry> moduleListRenderEntries = moduleList.get()
                        ? prepareModuleListRenderEntries(moduleListEntries)
                        : Collections.emptyList();
                SomeInformationRenderState someInformationState = someInformation.get() ? prepareSomeInformation() : null;
                if (moduleInformation.get())
                    prepareModuleInformationEntries(moduleInformationEntryBuffer, NanoVGManager.getScaledScreenHeight() / 2f - 300f, finalDeltaSpeed);
                else
                    moduleInformationEntryBuffer.clear();
                List<ModuleCategory> tabGuiCategories = tabGui.get() ? prepareTabGuiCategories() : Collections.emptyList();
                List<Module> tabGuiModules = Collections.emptyList();
                if (tabGui.get()) {
                    ModuleCategory selectedTabGuiCategory = selectedTabGuiCategory(tabGuiCategories);
                    if (selectedTabGuiCategory != null && (tabGuiExpanded || tabGuiExpandProgress > 0.01f))
                        tabGuiModules = prepareTabGuiModules(selectedTabGuiCategory);
                    updateTabGuiAnimations(finalDeltaSpeed, tabGuiCategories, tabGuiModules);
                } else {
                    tabGuiExpandProgress = 0f;
                    tabGuiCategorySelectionY = Float.NaN;
                    tabGuiModuleSelectionY = Float.NaN;
                }
                if (notifications.get())
                    Client.notificationManager.prepareNotifications();

                vg.beginEffectBatch();
                for (TargetHudRenderEntry entry : visibleTargetHudEntries) {
                    drawTargetHudEffects(vg, entry);
                }
                if (waterMark.get())
                    renderWatermarkEffects(vg);
                if (moduleList.get())
                    renderModuleListEffects(vg, moduleListRenderEntries);
                if (playerList.get())
                    renderPlayerListEffects(vg);
                if (miniMap.get())
                    renderMinimapEffects(vg);
                if (someInformationState != null)
                    renderSomeInformationEffects(vg, someInformationState);
                if (moduleInformation.get())
                    renderStyledTextEffects(vg, moduleInformationEntryBuffer);
                if (notifications.get())
                    Client.notificationManager.renderNotificationEffects(vg);
                if (tabGui.get())
                    renderTabGuiEffects(vg, tabGuiCategories, tabGuiModules);
                vg.flushEffectBatch();

                for (TargetHudRenderEntry entry : visibleTargetHudEntries) {
                    renderTargetHudVisibleRegions(vg, entry, () -> drawTargetHudBody(vg, entry));
                }

                if (waterMark.get())
                    renderWatermark(vg);

                if (moduleList.get())
                    renderModuleListBody(vg, moduleListRenderEntries);

                if (playerList.get())
                    renderPlayerList(vg);

                if (miniMap.get())
                    renderMinimap(vg, deltaTime);

                if (someInformationState != null)
                    renderSomeInformationBody(vg, someInformationState);

                if (moduleInformation.get())
                    renderStyledTextBodies(vg, moduleInformationEntryBuffer);

                if (notifications.get())
                    Client.notificationManager.renderNotificationBodies(vg);

                if (tabGui.get())
                    renderTabGuiBody(vg, tabGuiCategories, tabGuiModules);
            });
        });
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null || !targetHud.get()) {
            data.clear();
            clearTargetHudTracking();
            return;
        }
        if (!RenderUtil.shouldRenderWorldHudOverlays()) {
            data.clear();
            return;
        }

        float partialTicks = event.partialTicks;
        data.clear();

        BehaviorUtils.fillTargetList(targetBuffer);

        if (!targetBuffer.isEmpty()) {
            int existingSize = actualTargetEntitiesData.size();
            targetUuidBuffer.clear();
            for (int i = 0; i < existingSize; i++) {
                targetUuidBuffer.add(actualTargetEntitiesData.get(i).getSecond());
            }
            for (int i = 0, n = targetBuffer.size(); i < n; i++) {
                Entity target = targetBuffer.get(i);
                UUID uuid = target.getUUID();
                if (!targetUuidBuffer.contains(uuid)) {
                    actualTargetEntitiesData.add(new Tuple<>(target, uuid));
                }
            }
        }

        if (actualTargetEntitiesData.isEmpty()) return;

        Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
        Vec3 forward = mc.player.getViewVector(partialTicks);
        Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize().scale(0.7);

        for (int i = 0; i < actualTargetEntitiesData.size();) {
            Tuple<Entity, UUID> pair = actualTargetEntitiesData.get(i);
            Entity entity = pair.getFirst();
            UUID targetId = pair.getSecond();
            Vec3 lerpedPos = RenderUtil.INSTANCE.getLerpedPos(entity, partialTicks).add(right);
            Vec3 targetHudWorldPos = new Vec3(lerpedPos.x, lerpedPos.y + entity.getBbHeight(), lerpedPos.z);
            Vec3 screenPos = RenderUtil.INSTANCE.isEntityInCameraFrustum(entity, partialTicks)
                    ? RenderUtil.INSTANCE.worldToScreen(targetHudWorldPos)
                    : null;
            if (screenPos != null) {
                float hudX = NanoVGManager.fromFramebufferX(screenPos.x);
                float hudY = NanoVGManager.fromFramebufferY(screenPos.y);
                double depthSquared = targetHudWorldPos.distanceToSqr(cameraPos);
                data.add(new TargetHudData(targetId, entity, hudX, hudY, depthSquared));
            } else {
                boolean activeTarget = BehaviorUtils.isTarget(entity);
                if (activeTarget) {
                    i++;
                    continue;
                }

                actualTargetEntitiesData.remove(i);
                TargetHudState removedState = hudStates.remove(targetId);
                if (removedState != null)
                    removedState.resetPlayerTexture();
                continue;
            }
            i++;
        }
    }
}
