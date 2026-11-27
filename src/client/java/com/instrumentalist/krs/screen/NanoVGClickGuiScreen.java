package com.instrumentalist.krs.screen;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.configs.ConfigManager;
import com.instrumentalist.krs.utils.NotificationManager;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.InventoryMove;
import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.utils.nanovg.MaterialIcon;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.network.FileUtil;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import com.instrumentalist.krs.utils.render.Shader2DRenderer;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.ColorValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.KeyBindValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.krs.utils.value.SettingValue;
import com.instrumentalist.krs.utils.value.TextValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.instrumentalist.krs.utils.input.Keys;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.Border;
import org.nvgu.util.NVGFont;

import java.awt.Color;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.function.Consumer;

public class NanoVGClickGuiScreen extends Screen {
    private static final float LAYOUT = 1.35f;
    private static final float TEXT_SHADOW_SCALE = 0.5f;
    private static final float BACKGROUND_BLUR_RADIUS = 12f;
    private static final int BACKGROUND_DIM_ALPHA = 64;
    private static final Rect EMPTY_RECT = new Rect(-100000f, -100000f, 0f, 0f);
    private static final float COLUMN_TOP = u(30f);
    private static final float COLUMN_MARGIN = u(14f);
    private static final float COLUMN_GAP = u(8f);
    private static final float COLUMN_PADDING_X = u(7f);
    private static final float COLUMN_ROW_PADDING_Y = u(4.25f);
    private static final float COLUMN_FONT = font(11f);
    private static final float COLUMN_HEADER_FONT = font(14f);
    private static final float COLUMN_MIN_WIDTH = u(104f);
    private static final float COLUMN_BOTTOM_PADDING = u(4f);
    private static final int COLUMN_PANEL_ALPHA_OFFSET = 55;
    private static final float TYPE_SEARCH_HEIGHT = u(24f);
    private static final float MODULE_PANEL_WIDTH = u(204f);
    private static final int MODULE_PANEL_ALPHA_OFFSET = 80;
    private static final float MODULE_PANEL_MAX_HEIGHT = u(280f);
    private static final float SET_FONT = font(10f);
    private static final float SET_LINE = u(13f);
    private static final float SET_ROW = u(19f);
    private static final float SET_GAP = u(2f);
    private static final float SET_PAD = u(6f);
    private static final float SET_BAR_SPACE = u(15f);
    private static final float SET_SWITCH_WIDTH = u(24f);
    private static final float SET_SWITCH_HEIGHT = u(12f);
    private static final int WRAP_CACHE_LIMIT = 4096;
    private static final Map<WrapKey, List<String>> WRAP_CACHE = new HashMap<>();
    private static final DateTimeFormatter CONFIG_LIST_UPDATED_FORMAT = DateTimeFormatter
            .ofPattern("MM/dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private static NanoVGClickGuiScreen detachedClosingScreen;
    private static Module rememberedSettingsPanelModule;
    private static ConfigTab rememberedConfigTab = ConfigTab.MODULE;
    private static Module rememberedOpenedListModule;
    private static ListValue rememberedOpenedListValue;
    private static float rememberedSettingsPanelScroll;
    private static float rememberedTargetSettingsPanelScroll;

    private final List<ModuleRowBounds> moduleRows = new ArrayList<>();
    private final List<ControlBounds> controls = new ArrayList<>();
    private final List<Rect> inputClips = new ArrayList<>();
    private final List<TextFieldLayout> textFields = new ArrayList<>();
    private final List<PendingControlGlass> pendingControlGlass = new ArrayList<>();
    private final List<Runnable> pendingControlForegrounds = new ArrayList<>();
    private boolean deferControlChrome;
    private final Map<Module, List<SettingValue<?>>> moduleSettingsCache = new IdentityHashMap<>();
    private final Map<Module, Float> expandedSettingsHeightCache = new IdentityHashMap<>();
    private final Map<Module, Boolean> renderableSettingsCache = new IdentityHashMap<>();
    private final Map<Module, Float> enabledAnimations = new IdentityHashMap<>();
    private final Map<ListValue, Float> listDropdownAnimations = new IdentityHashMap<>();
    private final Map<SettingValue<?>, Float> settingVisibilityAnimations = new IdentityHashMap<>();
    private final Map<Object, Float> switchAnimations = new IdentityHashMap<>();
    private final Map<Module, Long> enabledAnimationFrames = new IdentityHashMap<>();
    private final Map<ListValue, Long> listDropdownAnimationFrames = new IdentityHashMap<>();
    private final Map<SettingValue<?>, Long> settingVisibilityAnimationFrames = new IdentityHashMap<>();
    private final Map<Object, Long> switchAnimationFrames = new IdentityHashMap<>();
    private final Map<String, Float> animations = new HashMap<>();
    private final Consumer<NVGU> nanoVgRenderer = this::render;
    private final Screen returnScreen;

    private ConfigTab selectedConfigTab = ConfigTab.MODULE;
    private String searchQuery = "";
    private String newConfigName = "";
    private String renameConfigName = "";
    private ConfigEntry renamingConfig;
    private float settingsPanelScroll;
    private float targetSettingsPanelScroll;
    private float settingsPanelScrollVelocity;
    private float maxSettingsPanelScroll;
    private float screenMouseX;
    private float screenMouseY;
    private float scaledMouseX;
    private float scaledMouseY;
    private float openProgress;
    private float frameDelta = 1f;
    private long lastFrameNanos;
    private long animationFrame;
    private boolean closing;
    private Rect searchRect = new Rect(0f, 0f, 0f, 0f);
    private Rect settingsPanelRect = new Rect(0f, 0f, 0f, 0f);
    private Rect settingsPanelViewport = new Rect(0f, 0f, 0f, 0f);
    private Rect settingsPanelScrollbarTrackRect = new Rect(0f, 0f, 0f, 0f);
    private Rect settingsPanelScrollbarThumbRect = new Rect(0f, 0f, 0f, 0f);
    private TextFocus textFocus = TextFocus.NONE;
    private boolean textInputActive;
    private TextValue focusedTextValue;
    private Module focusedTextModule;
    private SettingValue<?> focusedNumberValue;
    private String numberInput = "";
    private int textCaret;
    private int textAnchor;
    private boolean draggingTextSelection;
    private Module bindingModule;
    private KeyBindValue bindingValue;
    private Module openedListModule;
    private ListValue openedListValue;
    private SliderDrag activeSlider;
    private ControlBounds pressedControl;
    private Module pressedModule;
    private ScrollbarDrag activeSettingsPanelScrollbar;
    private Module settingsPanelModule;
    private Module settingsPanelRenderModule;
    private int settingsPanelControlStartIndex;
    private Module hoveredModule;
    private Module tooltipHoverModule;
    private long hoveredModuleStartNanos;
    private String tooltipText = "";
    private float settingsDockReveal;
    private Module pendingSettingsPanelModule;
    private final List<GuiColumn> columns = new ArrayList<>();
    private boolean typeSearchOpen;
    private Rect configManagerRect = EMPTY_RECT;
    private Rect configListViewport = EMPTY_RECT;
    private float configListScroll;
    private float targetConfigListScroll;
    private float maxConfigListScroll;
    private float configListScrollVelocity;
    private Rect configScrollbarTrackRect = EMPTY_RECT;
    private Rect configScrollbarThumbRect = EMPTY_RECT;
    private ScrollbarDrag activeConfigScrollbar;
    private static final float CONFIG_MANAGER_MAX_LIST_HEIGHT = u(150f);
    private static final float CONFIG_MANAGER_DATE_FONT = font(9f);
    private static final long CONFIG_DELETE_CONFIRM_DELAY_NANOS = 300_000_000L;
    private static final long CONFIG_DELETE_CONFIRM_TIMEOUT_NANOS = 3_000_000_000L;
    private static final String CONFIG_DELETE_CONFIRM_LABEL = "Delete";
    private ConfigEntry armedDeleteConfig;
    private long armedDeleteNanos;
    private float panelAnchorY = Float.NaN;
    private float panelAnchorLeft = Float.NaN;
    private float panelAnchorRight = Float.NaN;

    public NanoVGClickGuiScreen() {
        this(null);
    }

    public NanoVGClickGuiScreen(Screen returnScreen) {
        super(Component.literal("NanoVG Click GUI"));
        this.returnScreen = returnScreen instanceof NanoVGClickGuiScreen ? null : returnScreen;
        detachedClosingScreen = null;
        restoreUiState();
    }

    public static boolean isShowing() {
        Minecraft minecraft = Minecraft.getInstance();
        return detachedClosingScreen != null
                || minecraft != null && minecraft.gui.screen() instanceof NanoVGClickGuiScreen;
    }

    public static void renderDetachedIfNeeded() {
        if (detachedClosingScreen == null || Client.nanoVgManager == null)
            return;

        NanoVGClickGuiScreen screen = detachedClosingScreen;
        if (!screen.closing) {
            detachedClosingScreen = null;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.mouseHandler != null) {
            screen.screenMouseX = NanoVGManager.toScaledMouseX(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()));
            screen.screenMouseY = NanoVGManager.toScaledMouseY(minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));
            screen.updateScaledMouse();
        }

        Client.nanoVgManager.load(screen.nanoVgRenderer);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Screen backgroundScreen = getBackgroundScreen();
        if (backgroundScreen != null)
            backgroundScreen.extractRenderStateWithTooltipAndSubtitles(context, 0, 0, delta);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (Client.nanoVgManager == null) return;

        screenMouseX = NanoVGManager.toScaledMouseX(mouseX);
        screenMouseY = NanoVGManager.toScaledMouseY(mouseY);
        updateScaledMouse();
        context.requestCursor(resolveHoverCursor());
        syncTextInput();
        Client.nanoVgManager.load(nanoVgRenderer);
    }

    private void syncTextInput() {
        boolean wantsTextInput = textFocus != TextFocus.NONE;
        if (wantsTextInput == textInputActive || minecraft == null)
            return;

        textInputActive = wantsTextInput;
        minecraft.onTextInputFocusChange(this, wantsTextInput);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing)
            return true;

        float mouseX = NanoVGManager.toScaledMouseX(event.x());
        float mouseY = NanoVGManager.toScaledMouseY(event.y());
        int button = event.button();

        if ((bindingModule != null || bindingValue != null) && button != Keys.MOUSE_BUTTON_LEFT) {
            if (bindingModule != null)
                bindingModule.key = Keys.mouseKey(button);
            else
                bindingValue.set(Keys.mouseKey(button));
            bindingModule = null;
            bindingValue = null;
            return true;
        }

        if (isSettingsDockOpen()) {
            if (button == Keys.MOUSE_BUTTON_LEFT && maxSettingsPanelScroll > 0f
                    && settingsPanelScrollbarTrackRect.contains(mouseX, mouseY)) {
                closeListDropdown();
                clearTextFocus();
                float offsetY = settingsPanelScrollbarThumbRect.contains(mouseX, mouseY)
                        ? mouseY - settingsPanelScrollbarThumbRect.y
                        : settingsPanelScrollbarThumbRect.height / 2f;
                activeSettingsPanelScrollbar = new ScrollbarDrag(offsetY);
                updateSettingsPanelScrollbarDrag(mouseY);
                return true;
            }

            if (settingsPanelRect.contains(mouseX, mouseY)
                    && (button == Keys.MOUSE_BUTTON_LEFT
                    || button == Keys.MOUSE_BUTTON_RIGHT
                    || button == Keys.MOUSE_BUTTON_MIDDLE)) {
                for (int i = settingsPanelControlStartIndex; i < controls.size(); i++) {
                    ControlBounds control = controls.get(i);
                    if (control.rect.contains(mouseX, mouseY) && handleControlClick(control, mouseX, button, doubleClick))
                        return true;
                }

                clearTextFocus();
                closeListDropdown();
                return true;
            }
        }

        if (button == Keys.MOUSE_BUTTON_LEFT && maxConfigListScroll > 0f
                && configScrollbarTrackRect.contains(mouseX, mouseY)) {
            clearTextFocus();
            float offsetY = configScrollbarThumbRect.contains(mouseX, mouseY)
                    ? mouseY - configScrollbarThumbRect.y
                    : configScrollbarThumbRect.height / 2f;
            activeConfigScrollbar = new ScrollbarDrag(offsetY);
            updateConfigScrollbarDrag(mouseY);
            return true;
        }

        if (button == Keys.MOUSE_BUTTON_LEFT
                || button == Keys.MOUSE_BUTTON_RIGHT
                || button == Keys.MOUSE_BUTTON_MIDDLE) {
            for (ControlBounds control : controls) {
                if (control.rect.contains(mouseX, mouseY) && handleControlClick(control, mouseX, button, doubleClick))
                    return true;
            }
        }

        if (button == Keys.MOUSE_BUTTON_LEFT && searchRect.contains(mouseX, mouseY)) {
            focusSearch();
            beginTextSelectionOrSelectAll(mouseX, doubleClick);
            return true;
        }

        for (ModuleRowBounds row : moduleRows) {
            if (!row.rect.contains(mouseX, mouseY)) continue;

            if (button == Keys.MOUSE_BUTTON_LEFT) {
                closeListDropdown();
                clearTextFocus();
                pressedModule = row.module;
                row.module.toggle();
                return true;
            }

            if (button == Keys.MOUSE_BUTTON_RIGHT) {
                closeListDropdown();
                toggleSettingsPanel(row.module);
                return true;
            }
        }

        if (button == Keys.MOUSE_BUTTON_RIGHT && openedListValue != null) {
            closeListDropdown();
            return true;
        }

        if (button == Keys.MOUSE_BUTTON_LEFT) {
            clearTextFocus();
            closeListDropdown();
        }

        if (settingsPanelModule != null && button == Keys.MOUSE_BUTTON_RIGHT) {
            closeSettingsPanel();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingTextSelection) {
            updateTextSelectionDrag(NanoVGManager.toScaledMouseX(event.x()));
            return true;
        }

        if (activeSettingsPanelScrollbar != null) {
            updateSettingsPanelScrollbarDrag(NanoVGManager.toScaledMouseY(event.y()));
            return true;
        }

        if (activeConfigScrollbar != null) {
            updateConfigScrollbarDrag(NanoVGManager.toScaledMouseY(event.y()));
            return true;
        }

        if (activeSlider == null) return false;

        updateSlider(activeSlider, NanoVGManager.toScaledMouseX(event.x()));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        activeSlider = null;
        pressedControl = null;
        pressedModule = null;
        activeSettingsPanelScrollbar = null;
        activeConfigScrollbar = null;
        draggingTextSelection = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        float scaledX = NanoVGManager.toScaledMouseX(mouseX);
        float scaledY = NanoVGManager.toScaledMouseY(mouseY);

        if (nudgeHoveredColorSlider(scaledX, scaledY, vertical))
            return true;

        if (isSettingsDockOpen()
                && (settingsPanelViewport.contains(scaledX, scaledY)
                || settingsPanelScrollbarTrackRect.contains(scaledX, scaledY))) {
            float previousTarget = targetSettingsPanelScroll;
            float scrollStep = Math.clamp(settingsPanelViewport.height * 0.11f, u(36f), u(64f));
            targetSettingsPanelScroll = Math.clamp(
                    targetSettingsPanelScroll - (float) vertical * scrollStep,
                    0f,
                    maxSettingsPanelScroll
            );
            settingsPanelScrollVelocity = Math.clamp(
                    settingsPanelScrollVelocity + (targetSettingsPanelScroll - previousTarget) * 0.08f,
                    -22f,
                    22f
            );
            return true;
        }

        if ((configListViewport.contains(scaledX, scaledY) || configScrollbarTrackRect.contains(scaledX, scaledY))) {
            if (maxConfigListScroll > 0f) {
                float previousTarget = targetConfigListScroll;
                float scrollStep = Math.clamp(configListViewport.height * 0.11f, u(36f), u(64f));
                targetConfigListScroll = Math.clamp(targetConfigListScroll - (float) vertical * scrollStep, 0f, maxConfigListScroll);
                configListScrollVelocity = Math.clamp(
                        configListScrollVelocity + (targetConfigListScroll - previousTarget) * 0.08f,
                        -22f,
                        22f
                );
            }
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closing)
            return true;

        int key = event.key();

        if (bindingModule != null) {
            if (key != Keys.KEY_ESCAPE)
                bindingModule.key = normalizeKey(key);
            bindingModule = null;
            return true;
        }

        if (bindingValue != null) {
            if (key != Keys.KEY_ESCAPE)
                bindingValue.set(normalizeKey(key));
            bindingValue = null;
            return true;
        }

        boolean commandModifier = (event.modifiers() & (Keys.MOD_CONTROL | Keys.MOD_SUPER)) != 0;
        if (key == Keys.KEY_F && commandModifier) {
            typeSearchOpen = true;
            focusSearch();
            syncTextInput();
            return true;
        }

        if (textFocus != TextFocus.NONE) {
            if (key == Keys.KEY_ESCAPE && (textFocus == TextFocus.NUMBER || textFocus == TextFocus.CONFIG_RENAME)) {
                if (textFocus == TextFocus.NUMBER)
                    cancelNumberInput();
                else
                    cancelConfigRename();
                return true;
            }

            if (key == Keys.KEY_ENTER || key == Keys.KEY_KP_ENTER) {
                if (textFocus == TextFocus.CONFIG_NAME)
                    createConfigFromInput();
                else if (textFocus == TextFocus.CONFIG_RENAME) {
                    if (commitConfigRename())
                        resetTextFocus();
                } else if (textFocus == TextFocus.NUMBER) {
                    if (commitNumberInput())
                        resetTextFocus();
                }
                else
                    clearTextFocus();
                return true;
            }

            if (key == Keys.KEY_A && commandModifier) {
                selectAllFocusedText();
                return true;
            }

            if (key == Keys.KEY_C && commandModifier) {
                copyFocusedTextToClipboard();
                return true;
            }

            if (key == Keys.KEY_V && commandModifier) {
                pasteClipboardIntoFocusedText();
                return true;
            }

            if (key == Keys.KEY_BACKSPACE) {
                removeLastFocusedCharacter();
                return true;
            }

            if (key == Keys.KEY_DELETE) {
                deleteForwardFocusedCharacter();
                return true;
            }

            boolean shift = (event.modifiers() & Keys.MOD_SHIFT) != 0;
            if (key == Keys.KEY_LEFT) {
                moveFocusedCaret(-1, shift);
                return true;
            }
            if (key == Keys.KEY_RIGHT) {
                moveFocusedCaret(1, shift);
                return true;
            }
        }

        if (key == Keys.KEY_ESCAPE) {
            if (textFocus == TextFocus.CONFIG_NAME) {
                newConfigName = "";
                resetTextFocus();
                return true;
            }
            if (isTypeSearchVisible()) {
                typeSearchOpen = false;
                clearSearch();
                resetTextFocus();
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (textFocus == TextFocus.NONE || !event.isAllowedChatCharacter())
            return false;

        String input = event.codepointAsString();
        if (input != null && !input.isEmpty()) {
            if (textFocus == TextFocus.NUMBER)
                appendNumberInput(input);
            else
                appendFocusedText(input);
        }

        return true;
    }

    @Override
    public boolean isPauseScreen() {
        Screen backgroundScreen = getBackgroundScreen();
        return backgroundScreen != null && backgroundScreen.isPauseScreen();
    }

    @Override
    public void tick() {
        super.tick();

        Screen backgroundScreen = getBackgroundScreen();
        if (backgroundScreen != null && minecraft != null && minecraft.gui.screen() == this)
            backgroundScreen.tick();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);

        Screen backgroundScreen = getBackgroundScreen();
        if (backgroundScreen != null)
            backgroundScreen.resize(width, height);
    }

    public boolean shouldBlockGameMovement() {
        return textFocus != TextFocus.NONE || bindingModule != null || bindingValue != null || shouldBlockMovementForBackgroundScreen();
    }

    public boolean shouldSyncGameMovementKeys() {
        return isInGame() && !closing && !shouldBlockGameMovement();
    }

    @Override
    public void removed() {
        rememberUiState();
        activeSlider = null;
        pressedControl = null;
        pressedModule = null;
        activeSettingsPanelScrollbar = null;
        bindingModule = null;
        bindingValue = null;
        clearTextFocus();
        syncTextInput();
        resetHoverCursor();
        super.removed();
    }

    @Override
    public void onClose() {
        if (closing)
            return;

        rememberUiState();
        activeSlider = null;
        pressedControl = null;
        pressedModule = null;
        activeSettingsPanelScrollbar = null;
        bindingModule = null;
        bindingValue = null;
        clearTextFocus();
        resetHoverCursor();
        closing = true;

        if (isInGame() && minecraft != null && minecraft.gui.screen() == this) {
            detachedClosingScreen = this;
            minecraft.gui.setScreen(returnScreen);
        }
    }

    private void restoreUiState() {
        settingsPanelModule = rememberedSettingsPanelModule;
        settingsPanelRenderModule = rememberedSettingsPanelModule;
        selectedConfigTab = rememberedConfigTab == null ? ConfigTab.MODULE : rememberedConfigTab;
        openedListModule = rememberedOpenedListModule;
        openedListValue = rememberedOpenedListValue;
        settingsPanelScroll = rememberedSettingsPanelScroll;
        targetSettingsPanelScroll = rememberedTargetSettingsPanelScroll;
        restoreSettingsPanelAnimations();
    }

    private void restoreSettingsPanelAnimations() {
        if (isSettingsDockOpen()) {
            animations.put("settings-panel-open", 1f);
            settingsDockReveal = 1f;
        }

        if (settingsPanelModule != null) {
            for (SettingValue<?> setting : collectSettings(settingsPanelModule)) {
                if (setting.canDisplay.canDisplay())
                    settingVisibilityAnimations.put(setting, 1f);
                else
                    settingVisibilityAnimations.remove(setting);
            }
        }

        if (openedListModule != null && openedListValue != null)
            listDropdownAnimations.put(openedListValue, 1f);
    }

    private void rememberUiState() {
        rememberedSettingsPanelModule = pendingSettingsPanelModule != null ? pendingSettingsPanelModule : settingsPanelModule;
        rememberedConfigTab = selectedConfigTab;
        rememberedOpenedListModule = openedListModule;
        rememberedOpenedListValue = openedListValue;
        rememberedSettingsPanelScroll = settingsPanelScroll;
        rememberedTargetSettingsPanelScroll = targetSettingsPanelScroll;
    }

    private void render(NVGU vg) {
        float previousShadowScale = vg.getTextShadowScale();
        vg.setTextShadowScale(TEXT_SHADOW_SCALE);
        try {
            renderFrame(vg);
        } finally {
            vg.setTextShadowScale(previousShadowScale);
        }
    }

    private void renderFrame(NVGU vg) {
        float delta = updateDelta();
        frameDelta = delta;
        animationFrame++;
        clearFrameLayoutCaches();
        openProgress = stepTowards(openProgress, closing ? 0f : 1f, 0.08f * delta);
        updateSettingsPanelSmoothScroll(delta);

        if (closing && openProgress <= 0.001f) {
            finishClose();
            renderNotificationsOnTop(vg);
            return;
        }

        moduleRows.clear();
        controls.clear();
        textFields.clear();
        pendingControlGlass.clear();
        pendingControlForegrounds.clear();
        deferControlChrome = false;
        hoveredModule = null;
        tooltipText = "";
        if (pendingSettingsPanelModule != null && !isSettingsDockOpen()
                && animations.getOrDefault("settings-panel-open", 0f) <= 0f) {
            Module pending = pendingSettingsPanelModule;
            pendingSettingsPanelModule = null;
            openSettingsPanel(pending);
        }
        if (settingsPanelModule != null)
            settingsPanelRenderModule = settingsPanelModule;
        settingsDockReveal = settingsPanelReveal();
        if (settingsDockReveal <= 0f)
            settingsPanelRenderModule = null;

        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        updateScaledMouse();
        boolean staticFallbackBackdrop = !isInGame() && !hasBackgroundScreen();

        if (staticFallbackBackdrop)
            renderTitleBackdrop(vg, screenWidth, screenHeight);
        else if (hasBackgroundScreen())
            Shader2DRenderer.INSTANCE.beginFrame();

        float backgroundProgress = easeOut(openProgress);
        if (backgroundProgress > 0.001f) {
            vg.beginEffectBatch();
            Shader2DRenderer.INSTANCE.beginFrame();
            vg.blurRoundedRectangle(0f, 0f, screenWidth, screenHeight, 0f, BACKGROUND_BLUR_RADIUS, backgroundProgress);
            vg.flushEffectBatch();
            vg.rectangle(0f, 0f, screenWidth, screenHeight, new Color(0, 0, 0, Math.round(BACKGROUND_DIM_ALPHA * backgroundProgress)));
        }
        Shader2DRenderer.INSTANCE.beginFrame();

        float zoom = 0.5f + 0.5f * (openProgress * openProgress * (3f - 2f * openProgress));
        float alpha = openProgress * openProgress * openProgress;
        renderZoomed(vg, screenWidth, screenHeight, zoom, alpha, () -> renderConfigManager(vg, screenWidth, screenHeight));
        renderNotificationsOnTop(vg);
        renderZoomed(vg, screenWidth, screenHeight, zoom, alpha, () -> {
            renderColumnView(vg, screenWidth, screenHeight);
            renderFloatingModulePanel(vg, screenWidth, screenHeight);
            renderHoverTooltip(vg);
        });
        applyHoverCursor();
    }

    private void renderZoomed(NVGU vg, float screenWidth, float screenHeight, float zoom, float alpha, Runnable renderer) {
        vg.scale(screenWidth / 2f, screenHeight / 2f, zoom);
        try {
            vg.globalAlpha(alpha, renderer);
        } finally {
            vg.scale(screenWidth / 2f, screenHeight / 2f, 1f / zoom);
        }
    }

    private void renderNotificationsOnTop(NVGU vg) {
        if (isInGame())
            Interface.renderNotificationsOverlay(vg);
        else
            Client.notificationManager.renderNotifications(vg);
    }

    private static float u(float value) {
        return value * LAYOUT;
    }

    private static float font(float size) {
        return Math.round(size * LAYOUT);
    }

    private void updateScaledMouse() {
        scaledMouseX = screenMouseX;
        scaledMouseY = screenMouseY;
    }

    private void finishClose() {
        clearInteractionState();
        if (detachedClosingScreen == this)
            detachedClosingScreen = null;
        if (minecraft != null && minecraft.gui.screen() == this)
            minecraft.gui.setScreen(returnScreen);
    }

    private void clearFrameLayoutCaches() {
        expandedSettingsHeightCache.clear();
        renderableSettingsCache.clear();
    }

    private float updateDelta() {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1f : Math.clamp((now - lastFrameNanos) / 16_666_666f, 0.25f, 3f);
        lastFrameNanos = now;
        return delta;
    }

    private void updateSettingsPanelSmoothScroll(float delta) {
        if (!isSettingsDockOpen() || settingsPanelViewport.height <= 0f)
            return;

        targetSettingsPanelScroll = Math.clamp(targetSettingsPanelScroll, 0f, maxSettingsPanelScroll);

        if (activeSettingsPanelScrollbar != null) {
            settingsPanelScrollVelocity = 0f;
            settingsPanelScroll = targetSettingsPanelScroll;
            return;
        }

        if (maxSettingsPanelScroll <= 0f) {
            targetSettingsPanelScroll = 0f;
            settingsPanelScroll = 0f;
            settingsPanelScrollVelocity = 0f;
            return;
        }

        float distance = targetSettingsPanelScroll - settingsPanelScroll;
        settingsPanelScrollVelocity += distance * 0.045f * delta;
        settingsPanelScrollVelocity *= (float) Math.pow(0.74f, delta);
        settingsPanelScrollVelocity = Math.clamp(settingsPanelScrollVelocity, -42f, 42f);

        settingsPanelScroll += settingsPanelScrollVelocity * delta;
        settingsPanelScroll = approach(
                settingsPanelScroll,
                targetSettingsPanelScroll,
                1f - (float) Math.pow(0.91f, delta)
        );

        if (settingsPanelScroll < 0f || settingsPanelScroll > maxSettingsPanelScroll) {
            settingsPanelScroll = Math.clamp(settingsPanelScroll, 0f, maxSettingsPanelScroll);
            settingsPanelScrollVelocity = 0f;
        }

        if (Math.abs(targetSettingsPanelScroll - settingsPanelScroll) < 0.06f
                && Math.abs(settingsPanelScrollVelocity) < 0.06f) {
            settingsPanelScroll = targetSettingsPanelScroll;
            settingsPanelScrollVelocity = 0f;
        }
    }

    private Screen getBackgroundScreen() {
        return returnScreen != null && returnScreen != this ? returnScreen : null;
    }

    private boolean hasBackgroundScreen() {
        return getBackgroundScreen() != null;
    }

    private boolean shouldBlockMovementForBackgroundScreen() {
        Screen backgroundScreen = getBackgroundScreen();
        return backgroundScreen != null && !InventoryMove.canMoveFreely(backgroundScreen);
    }

    private void renderTitleBackdrop(NVGU vg, float width, float height) {
        if (ensureTexture(vg, "title_screen", "assets/krs/title.png")) {
            CustomTitleScreen.stepBackgroundParallax(screenMouseX, screenMouseY, Math.clamp(0.03f * frameDelta, 0f, 1f));

            vg.texturedRectangle(0f, 0f, width * 2f, height * 2f, "title_screen");
            vg.save();
            try {
                vg.translate(CustomTitleScreen.bgOffsetX, CustomTitleScreen.bgOffsetY);
                vg.texturedRectangle(-width, -height, width * 2f, height * 2f, "title_screen");
            } finally {
                vg.restore();
            }
        }
    }

    private boolean isInGame() {
        return minecraft != null && minecraft.level != null && minecraft.player != null;
    }

    private static boolean ensureTexture(NVGU vg, String identifier, String path) {
        if (vg.hasTexture(identifier))
            return true;

        try (InputStream texture = NanoVGClickGuiScreen.class.getClassLoader().getResourceAsStream(path)) {
            if (texture == null)
                return false;

            vg.createTexture(identifier, texture);
            return vg.hasTexture(identifier);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void renderColumnView(NVGU vg, float screenWidth, float screenHeight) {
        if (typeSearchOpen && textFocus != TextFocus.SEARCH && searchQuery.isEmpty())
            typeSearchOpen = false;

        float searchCurrent = animations.getOrDefault("type-search", 0f);
        float searchRaw = isTypeSearchVisible()
                ? approach(searchCurrent, 1f, 0.2f * frameDelta)
                : stepTowards(searchCurrent, 0f, 0.2f * frameDelta);
        animations.put("type-search", searchRaw);
        float searchProgress = easeOut(searchRaw);
        Rect searchBar = typeSearchRect(screenWidth, searchProgress);
        float searchLayout = easeOut(animate("type-search-layout", isTypeSearchVisible(), 0.2f));
        float top = COLUMN_TOP + (TYPE_SEARCH_HEIGHT + u(10f)) * searchLayout;
        layoutColumns(screenWidth, screenHeight, top);

        vg.beginEffectBatch();
        for (GuiColumn column : columns)
            NanoVGTheme.renderPanelEffects(vg, column.bounds.x, column.bounds.y, column.bounds.width, column.bounds.height, NanoVGTheme.RADIUS_PANEL, 1f);
        if (searchProgress > 0.01f)
            NanoVGTheme.renderPanelEffects(vg, searchBar.x, searchBar.y, searchBar.width, searchBar.height, NanoVGTheme.RADIUS_HUD, searchProgress);
        vg.flushEffectBatch();

        for (GuiColumn column : columns) {
            NanoVGTheme.renderPanel(vg, column.bounds.x, column.bounds.y, column.bounds.width, column.bounds.height,
                    NanoVGTheme.RADIUS_PANEL, 1f, COLUMN_PANEL_ALPHA_OFFSET);
            renderColumnContent(vg, column);
        }

        renderTypeSearch(vg, searchBar, searchProgress);
    }

    private boolean isTypeSearchVisible() {
        return typeSearchOpen || textFocus == TextFocus.SEARCH || !searchQuery.isEmpty();
    }

    private Rect typeSearchRect(float screenWidth, float progress) {
        float fontSize = font(11f);
        String shown = searchQuery.isEmpty() ? "Search" : searchQuery;
        float width = Math.clamp(NVGFonts.INTER.getWidth(shown, fontSize) + u(46f), u(140f), Math.max(u(140f), screenWidth - COLUMN_MARGIN * 2f));
        float y = COLUMN_TOP - u(6f) * (1f - progress);
        return new Rect((screenWidth - width) / 2f, y, width, TYPE_SEARCH_HEIGHT);
    }

    private void renderTypeSearch(NVGU vg, Rect rect, float progress) {
        if (progress <= 0.01f) {
            searchRect = EMPTY_RECT;
            return;
        }

        searchRect = rect;
        boolean focused = textFocus == TextFocus.SEARCH;
        float fontSize = font(11f);
        vg.globalAlpha(progress, () -> {
            NanoVGTheme.renderPanel(vg, rect.x, rect.y, rect.width, rect.height, NanoVGTheme.RADIUS_HUD, 1f);
            NVGFonts.ICON.drawText(MaterialIcon.SEARCH, rect.x + u(8f), rect.centerY(), font(12f),
                    focused ? NanoVGTheme.TEXT : NanoVGTheme.iconIdle(220), Alignment.LEFT_MIDDLE, true);
            String text = inputText(searchQuery, focused, "Search");
            Color color = searchQuery.isEmpty() ? NanoVGTheme.muted(200) : NanoVGTheme.TEXT;
            float textY = rect.centerY() - fontSize / 2f;
            renderInputText(vg, TextFocus.SEARCH, null, focused, text, fontSize, rect.x + u(25f), textY, textY, rect.width - u(34f), rect, color);
        });
    }

    private void layoutColumns(float screenWidth, float screenHeight, float top) {
        columns.clear();
        String query = normalize(searchQuery);
        for (ModuleCategory category : ModuleCategory.values()) {
            if (category == ModuleCategory.Dev && (!query.isBlank() || !ConfigManager.isDeveloperFeaturesEnabled()))
                continue;

            GuiColumn column = new GuiColumn(category.name());
            for (Module module : ModuleManager.allModules) {
                if (module.moduleCategory != category)
                    continue;
                if (!query.isBlank() && !matchesModuleSearch(module, query))
                    continue;
                column.rows.add(moduleColumnRow(module));
            }
            if (column.rows.isEmpty() && !query.isBlank())
                continue;

            column.rows.sort(Comparator.comparing(ColumnRow::label, String.CASE_INSENSITIVE_ORDER));
            columns.add(column);
        }

        float rowHeight = columnRowHeight();
        float headerHeight = columnHeaderHeight();
        float available = screenWidth - COLUMN_MARGIN * 2f;
        for (GuiColumn column : columns) {
            column.width = Math.max(COLUMN_MIN_WIDTH, NVGFonts.INTER_SEMIBOLD.getWidth(column.title, COLUMN_HEADER_FONT) + COLUMN_PADDING_X * 2f);
            for (ColumnRow row : column.rows)
                column.width = Math.max(column.width, row.width() + u(6f));
        }

        float lineTop = top;
        int start = 0;
        while (start < columns.size()) {
            int end = start;
            float lineWidth = 0f;
            while (end < columns.size()) {
                float next = lineWidth + (end > start ? COLUMN_GAP : 0f) + columns.get(end).width;
                if (end > start && next > available)
                    break;
                lineWidth = next;
                end++;
            }

            float x = (screenWidth - lineWidth) / 2f;
            float lineHeight = 0f;
            for (int i = start; i < end; i++) {
                GuiColumn column = columns.get(i);
                column.x = x;
                column.y = lineTop;
                column.rowsTop = column.y + headerHeight;
                column.bounds = new Rect(column.x, column.y, column.width, headerHeight + column.rows.size() * rowHeight + COLUMN_BOTTOM_PADDING);
                lineHeight = Math.max(lineHeight, column.bounds.height);
                x += column.width + COLUMN_GAP;
            }

            lineTop += lineHeight + COLUMN_GAP;
            start = end;
        }
    }

    private static float columnRowHeight() {
        return NVGFonts.INTER.getHeight(COLUMN_FONT) + COLUMN_ROW_PADDING_Y * 2f;
    }

    private static float columnHeaderHeight() {
        return NVGFonts.INTER.getHeight(COLUMN_HEADER_FONT) + u(13f);
    }

    private static ColumnRow moduleColumnRow(Module module) {
        float labelWidth = NVGFonts.INTER_MEDIUM.getWidth(module.moduleName, COLUMN_FONT);
        if (module.isLocked())
            labelWidth += NVGFonts.ICON.getWidth(MaterialIcon.LOCK, COLUMN_FONT) + u(4f);
        return new ColumnRow(module, module.moduleName, labelWidth + COLUMN_PADDING_X * 2f);
    }

    private void renderColumnContent(NVGU vg, GuiColumn column) {
        float rowHeight = columnRowHeight();
        float headerHeight = columnHeaderHeight();
        float headerTextY = column.y + (headerHeight - NVGFonts.INTER.getHeight(COLUMN_HEADER_FONT)) / 2f;
        NVGFonts.INTER_SEMIBOLD.drawText(column.title, column.x + column.width / 2f, headerTextY, COLUMN_HEADER_FONT, NanoVGTheme.TEXT, Alignment.CENTER_TOP, true);
        renderColumnRows(vg, column, rowHeight);
    }

    private void renderColumnRows(NVGU vg, GuiColumn column, float rowHeight) {
        float textOffset = (rowHeight - NVGFonts.INTER.getHeight(COLUMN_FONT)) / 2f;
        boolean overPanel = isSettingsDockOpen() && settingsPanelRect.contains(scaledMouseX, scaledMouseY)
                || configManagerRect.contains(scaledMouseX, scaledMouseY);
        for (int rowIndex = 0; rowIndex < column.rows.size(); rowIndex++) {
            ColumnRow row = column.rows.get(rowIndex);
            Rect strip = new Rect(column.x + u(3f), column.rowsTop + rowIndex * rowHeight, column.width - u(6f), rowHeight);
            Rect hit = new Rect(column.x, strip.y, column.width, rowHeight - 0.001f);
            boolean hovered = !overPanel && isHovered(hit);
            float textX = strip.x + u(6f);
            float textY = strip.y + textOffset;
            Module module = row.module();
            addModuleRow(module, hit);
            if (hovered)
                rememberHoveredModule(module);

            boolean selected = settingsPanelModule == module;
            boolean marked = selected || pendingSettingsPanelModule == module;
            if (selected) {
                panelAnchorY = strip.y;
                panelAnchorLeft = column.x;
                panelAnchorRight = column.x + column.width;
            }

            float mark = animate("module-mark:" + System.identityHashCode(module), marked, 0.18f);
            if (mark > 0.01f)
                vg.roundedRectangle(strip.x + u(1f), strip.y + u(4.5f), u(2f), Math.max(u(4f), strip.height - u(9f)), u(1f), alpha(255, 255, 255, (int) (230 * mark)));

            float enabled = animateIdentity(enabledAnimations, enabledAnimationFrames, module, module.tempEnabled, 0.12f);
            Color color = mix(NanoVGTheme.MUTED, NanoVGTheme.TEXT, enabled);
            NVGFonts.INTER_MEDIUM.drawText(row.label(), textX, textY, COLUMN_FONT, color, Alignment.LEFT_TOP, true);
            if (module.isLocked()) {
                NVGFonts.ICON.drawText(MaterialIcon.LOCK, strip.x + strip.width - u(4f), strip.y + strip.height / 2f, COLUMN_FONT, NanoVGTheme.muted(200), Alignment.RIGHT_MIDDLE, true);
            }
        }
    }

    private void renderConfigManager(NVGU vg, float screenWidth, float screenHeight) {
        List<ConfigEntry> configs = configEntries("");
        boolean creatable = selectedConfigTab != ConfigTab.ONLINE;
        float rowHeight = columnRowHeight();
        float tabFont = COLUMN_FONT;
        float tabGap = u(12f);
        float tabsWidth = 0f;
        ConfigTab[] tabs = ConfigTab.values();
        for (ConfigTab tab : tabs)
            tabsWidth += NVGFonts.INTER_MEDIUM.getWidth(configTabLabel(tab), tabFont);
        tabsWidth += tabGap * (tabs.length - 1);

        float width = Math.max(COLUMN_MIN_WIDTH, tabsWidth + COLUMN_PADDING_X * 2f);
        for (ConfigEntry config : configs) {
            String updated = configListUpdatedLabel(config);
            float trailing = NVGFonts.INTER_MEDIUM.getWidth(CONFIG_DELETE_CONFIRM_LABEL, CONFIG_MANAGER_DATE_FONT) + u(22f)
                    + (updated.isBlank() ? 0f : NVGFonts.INTER.getWidth(updated, CONFIG_MANAGER_DATE_FONT) + u(8f));
            width = Math.max(width, NVGFonts.INTER_MEDIUM.getWidth(config.name(), COLUMN_FONT) + COLUMN_PADDING_X * 2f + trailing);
        }

        float configsContentHeight = configs.size() * rowHeight;
        float maxListHeight = Math.min(CONFIG_MANAGER_MAX_LIST_HEIGHT, Math.max(rowHeight, screenHeight * 0.4f));
        float configsHeight = Math.min(configsContentHeight, maxListHeight);
        maxConfigListScroll = Math.max(0f, configsContentHeight - configsHeight);
        boolean scrollable = maxConfigListScroll > 0f;
        if (scrollable)
            width += u(8f);
        updateConfigListSmoothScroll(frameDelta);
        float listHeight = configsHeight + (creatable ? rowHeight : 0f);
        float tabsHeight = rowHeight + u(4f);
        float height = u(4f) + listHeight + tabsHeight;
        float x = screenWidth - COLUMN_MARGIN - width;
        float y = screenHeight - COLUMN_MARGIN - height;
        Rect panel = new Rect(x, y, width, height);
        configManagerRect = panel;

        String currentName = null;
        Rect currentRect = null;
        float currentLabelFont = font(9f);
        String currentLabel = "Current";
        if (Client.configManager != null) {
            boolean bind = selectedConfigTab == ConfigTab.BIND;
            String name = bind ? Client.configManager.bindCurrent : Client.configManager.configCurrent;
            if (currentConfigExists(name, bind)) {
                currentName = name;
                float contentWidth = NVGFonts.INTER_MEDIUM.getWidth(currentLabel, currentLabelFont) + u(5f)
                        + NVGFonts.INTER_MEDIUM.getWidth(name, COLUMN_FONT);
                float currentWidth = Math.min(screenWidth - COLUMN_MARGIN * 2f, contentWidth + COLUMN_PADDING_X * 2f);
                currentRect = new Rect(panel.x + panel.width - currentWidth, panel.y - u(6f) - rowHeight, currentWidth, rowHeight);
            }
        }

        NotificationManager.avoidAbove((currentRect != null ? currentRect.y : panel.y) - u(8f));

        vg.beginEffectBatch();
        NanoVGTheme.renderPanelEffects(vg, panel.x, panel.y, panel.width, panel.height, NanoVGTheme.RADIUS_PANEL, 1f);
        if (currentRect != null)
            NanoVGTheme.renderPanelEffects(vg, currentRect.x, currentRect.y, currentRect.width, currentRect.height, NanoVGTheme.RADIUS_PANEL, 1f);
        vg.flushEffectBatch();
        NanoVGTheme.renderPanel(vg, panel.x, panel.y, panel.width, panel.height, NanoVGTheme.RADIUS_PANEL, 1f, COLUMN_PANEL_ALPHA_OFFSET);

        float textOffset = (rowHeight - NVGFonts.INTER.getHeight(COLUMN_FONT)) / 2f;
        if (currentRect != null) {
            NanoVGTheme.renderPanel(vg, currentRect.x, currentRect.y, currentRect.width, currentRect.height, NanoVGTheme.RADIUS_PANEL, 1f, COLUMN_PANEL_ALPHA_OFFSET);
            float nameRight = currentRect.x + currentRect.width - COLUMN_PADDING_X;
            float nameY = currentRect.y + textOffset;
            float nameWidth = NVGFonts.INTER_MEDIUM.getWidth(currentName, COLUMN_FONT);
            NVGFonts.INTER_MEDIUM.drawText(currentName, nameRight, nameY, COLUMN_FONT, NanoVGTheme.TEXT, Alignment.RIGHT_TOP, true);
            NVGFonts.INTER_MEDIUM.drawText(currentLabel, nameRight - nameWidth - u(5f), currentRect.centerY(), currentLabelFont,
                    NanoVGTheme.muted(220), Alignment.RIGHT_MIDDLE, true);
        }
        float listTop = panel.y + u(4f);
        float rowsWidth = panel.width - u(6f) - (scrollable ? u(8f) : 0f);
        Rect viewport = new Rect(panel.x, listTop, panel.width - (scrollable ? u(8f) : 0f), configsHeight);
        configListViewport = viewport;
        boolean underSettings = isSettingsDockOpen() && settingsPanelRect.contains(scaledMouseX, scaledMouseY);
        withInputClip(viewport, () -> vg.scissor(viewport.x, viewport.y, viewport.width, viewport.height, () -> {
            float configY = listTop - configListScroll;
            for (ConfigEntry config : configs) {
                if (configY + rowHeight > viewport.y && configY < viewport.y + viewport.height) {
                    Rect row = new Rect(panel.x + u(3f), configY, rowsWidth, rowHeight - 0.001f);
                    renderConfigManagerRow(vg, config, row, textOffset, underSettings);
                }
                configY += rowHeight;
            }
        }));
        renderConfigScrollbar(vg, viewport, configsContentHeight);
        float rowY = listTop + configsHeight;

        if (creatable) {
            Rect row = new Rect(panel.x + u(3f), rowY, panel.width - u(6f), rowHeight - 0.001f);
            addControl(ControlType.CONFIG_NAME, row, null, null, 0);
            boolean active = textFocus == TextFocus.CONFIG_NAME;
            String text = inputText(newConfigName, active, "New config");
            Color color = newConfigName.isEmpty() && !active ? NanoVGTheme.muted(170) : NanoVGTheme.TEXT;
            NVGFonts.ICON.drawText(MaterialIcon.ADD, row.x + u(6f), row.centerY(), COLUMN_FONT, NanoVGTheme.muted(active ? 255 : 190), Alignment.LEFT_MIDDLE, true);
            float textX = row.x + u(6f) + NVGFonts.ICON.getWidth(MaterialIcon.ADD, COLUMN_FONT) + u(4f);
            float textY = row.y + textOffset;
            renderInputText(vg, TextFocus.CONFIG_NAME, null, active, text, COLUMN_FONT, textX, textY, textY, row.x + row.width - u(6f) - textX, row, color);
            rowY += rowHeight;
        }

        float tabsY = panel.y + panel.height - tabsHeight + u(1f);
        float tabX = panel.x + (panel.width - tabsWidth) / 2f;
        for (ConfigTab tab : tabs) {
            String label = configTabLabel(tab);
            float labelWidth = NVGFonts.INTER_MEDIUM.getWidth(label, tabFont);
            Rect hit = new Rect(tabX - tabGap / 2f, tabsY, labelWidth + tabGap, rowHeight);
            addControl(ControlType.CONFIG_TAB, hit, tab, null, 0);
            float selected = animate("config-tab:" + tab.name(), selectedConfigTab == tab, 0.2f);
            NVGFonts.INTER_MEDIUM.drawText(label, tabX, tabsY + textOffset, tabFont, mix(NanoVGTheme.MUTED, NanoVGTheme.TEXT, selected), Alignment.LEFT_TOP, true);
            tabX += labelWidth + tabGap;
        }
    }

    private void renderConfigManagerRow(NVGU vg, ConfigEntry config, Rect row, float textOffset, boolean underSettings) {
        boolean renaming = isRenamingConfig(config);
        float textX = row.x + u(6f);
        float textY = row.y + textOffset;
        if (renaming) {
            addControl(ControlType.CONFIG_RENAME, row, config, null, 0);
            String text = inputText(renameConfigName, true, "");
            renderInputText(vg, TextFocus.CONFIG_RENAME, config, true, text, COLUMN_FONT, textX, textY, textY, row.width - u(12f), row, NanoVGTheme.TEXT);
            return;
        }

        boolean deletable = config.type() != ConfigTab.ONLINE && !config.current();
        boolean hovered = !underSettings && row.contains(scaledMouseX, scaledMouseY)
                && configListViewport.contains(scaledMouseX, scaledMouseY);
        boolean armed = isDeleteArmed(config);
        if (armed && (!hovered || System.nanoTime() - armedDeleteNanos > CONFIG_DELETE_CONFIRM_TIMEOUT_NANOS)) {
            armedDeleteConfig = null;
            armed = false;
        }
        boolean showDelete = deletable && hovered;
        String updated = configListUpdatedLabel(config);
        float dateWidth = updated.isBlank() ? 0f : NVGFonts.INTER.getWidth(updated, CONFIG_MANAGER_DATE_FONT) + u(8f);
        float trailingRight = row.x + row.width - dateWidth;
        String deleteKey = config.type().name() + ":" + config.name();
        float armedFade = animate("config-delete-armed:" + deleteKey, showDelete && armed, 0.2f);
        float iconFade = animate("config-delete-icon:" + deleteKey, showDelete && !armed, 0.2f);
        if (armedFade > 0.01f) {
            float labelWidth = NVGFonts.INTER_MEDIUM.getWidth(CONFIG_DELETE_CONFIRM_LABEL, CONFIG_MANAGER_DATE_FONT);
            float width = labelWidth + u(12f);
            Rect delete = new Rect(trailingRight - width - u(4f), row.y + u(2f), width, row.height - u(4f));
            if (showDelete && armed)
                addControl(ControlType.CONFIG_DELETE, delete, config, null, 0);
            boolean deleteHovered = delete.contains(scaledMouseX, scaledMouseY);
            boolean ready = armed && System.nanoTime() - armedDeleteNanos >= CONFIG_DELETE_CONFIRM_DELAY_NANOS;
            int fillAlpha = ready ? (deleteHovered ? 235 : 190) : 110;
            vg.globalAlpha(armedFade, () -> {
                vg.roundedRectangle(delete.x, delete.y, delete.width, delete.height, delete.height / 2f,
                        alpha(NanoVGTheme.DANGER.getRed(), NanoVGTheme.DANGER.getGreen(), NanoVGTheme.DANGER.getBlue(), fillAlpha));
                NVGFonts.INTER_MEDIUM.drawText(CONFIG_DELETE_CONFIRM_LABEL, delete.centerX(), delete.centerY(), CONFIG_MANAGER_DATE_FONT,
                        NanoVGTheme.TEXT, Alignment.CENTER_MIDDLE, true);
            });
        }
        if (iconFade > 0.01f) {
            Rect delete = new Rect(trailingRight - u(20f), row.y, u(18f), row.height);
            if (showDelete && !armed)
                addControl(ControlType.CONFIG_DELETE, delete, config, null, 0);
            boolean deleteHovered = showDelete && delete.contains(scaledMouseX, scaledMouseY);
            float deleteHover = animate("config-delete-hover:" + deleteKey, deleteHovered, 0.25f);
            vg.globalAlpha(iconFade, () -> NVGFonts.ICON.drawText(MaterialIcon.DELETE, delete.centerX(), delete.centerY(), COLUMN_FONT,
                    mix(NanoVGTheme.muted(200), NanoVGTheme.DANGER, deleteHover), Alignment.CENTER_MIDDLE, true));
        }
        addControl(ControlType.CONFIG_LOAD, row, config, null, 0);

        String currentKey = "config-current:" + config.type().name() + ":" + config.name();
        animations.putIfAbsent(currentKey, config.current() ? 1f : 0f);
        float current = animate(currentKey, config.current(), 0.12f);
        NVGFonts.INTER_MEDIUM.drawText(config.name(), textX, textY, COLUMN_FONT, mix(NanoVGTheme.MUTED, NanoVGTheme.TEXT, current), Alignment.LEFT_TOP, true);

        if (!updated.isBlank())
            NVGFonts.INTER.drawText(updated, row.x + row.width - u(6f), row.centerY(), CONFIG_MANAGER_DATE_FONT,
                    NanoVGTheme.muted(200), Alignment.RIGHT_MIDDLE, true);
    }

    private void updateConfigListSmoothScroll(float delta) {
        targetConfigListScroll = Math.clamp(targetConfigListScroll, 0f, maxConfigListScroll);

        if (activeConfigScrollbar != null) {
            configListScrollVelocity = 0f;
            configListScroll = targetConfigListScroll;
            return;
        }

        if (maxConfigListScroll <= 0f) {
            targetConfigListScroll = 0f;
            configListScroll = 0f;
            configListScrollVelocity = 0f;
            return;
        }

        float distance = targetConfigListScroll - configListScroll;
        configListScrollVelocity += distance * 0.045f * delta;
        configListScrollVelocity *= (float) Math.pow(0.74f, delta);
        configListScrollVelocity = Math.clamp(configListScrollVelocity, -42f, 42f);

        configListScroll += configListScrollVelocity * delta;
        configListScroll = approach(configListScroll, targetConfigListScroll, 1f - (float) Math.pow(0.91f, delta));

        if (configListScroll < 0f || configListScroll > maxConfigListScroll) {
            configListScroll = Math.clamp(configListScroll, 0f, maxConfigListScroll);
            configListScrollVelocity = 0f;
        }

        if (Math.abs(targetConfigListScroll - configListScroll) < 0.06f && Math.abs(configListScrollVelocity) < 0.06f) {
            configListScroll = targetConfigListScroll;
            configListScrollVelocity = 0f;
        }
    }

    private void renderConfigScrollbar(NVGU vg, Rect viewport, float contentHeight) {
        if (contentHeight <= viewport.height + 1f) {
            configScrollbarTrackRect = EMPTY_RECT;
            configScrollbarThumbRect = EMPTY_RECT;
            return;
        }

        float thumbHeight = Math.max(u(22f), viewport.height * viewport.height / contentHeight);
        float travel = viewport.height - thumbHeight;
        float thumbY = viewport.y + travel * (configListScroll / Math.max(1f, contentHeight - viewport.height));
        float trackX = viewport.x + viewport.width + u(2f);
        configScrollbarTrackRect = new Rect(trackX - u(5f), viewport.y, u(12f), viewport.height);
        configScrollbarThumbRect = new Rect(trackX - u(1f), thumbY, u(5f), thumbHeight);

        boolean hovered = configScrollbarTrackRect.contains(scaledMouseX, scaledMouseY) || activeConfigScrollbar != null;
        float progress = animate("config-scrollbar", hovered, 0.18f);
        vg.roundedRectangle(trackX + u(1f), viewport.y + u(4f), u(2f), viewport.height - u(8f), u(1f),
                alpha(255, 255, 255, (int) (18 + 18 * progress)));
        vg.roundedRectangle(configScrollbarThumbRect.x, configScrollbarThumbRect.y, configScrollbarThumbRect.width,
                configScrollbarThumbRect.height, u(2.5f), NanoVGTheme.scrollbarThumb((int) (112 + 86 * progress)));
    }

    private void updateConfigScrollbarDrag(float mouseY) {
        if (activeConfigScrollbar == null || maxConfigListScroll <= 0f
                || configScrollbarTrackRect.height <= 0f || configScrollbarThumbRect.height <= 0f)
            return;

        float travel = Math.max(1f, configScrollbarTrackRect.height - configScrollbarThumbRect.height);
        float thumbY = Math.clamp(mouseY - activeConfigScrollbar.offsetY, configScrollbarTrackRect.y, configScrollbarTrackRect.y + travel);
        float progress = (thumbY - configScrollbarTrackRect.y) / travel;
        targetConfigListScroll = Math.clamp(progress * maxConfigListScroll, 0f, maxConfigListScroll);
        configListScroll = targetConfigListScroll;
        configListScrollVelocity = 0f;
    }

    private boolean isDeleteArmed(ConfigEntry config) {
        return armedDeleteConfig != null && config != null
                && armedDeleteConfig.type() == config.type()
                && armedDeleteConfig.name().equalsIgnoreCase(config.name());
    }

    private static String configTabLabel(ConfigTab tab) {
        return switch (tab) {
            case MODULE -> "Module";
            case BIND -> "Bind";
            case ONLINE -> "Online";
        };
    }

    private void renderFloatingModulePanel(NVGU vg, float screenWidth, float screenHeight) {
        settingsPanelControlStartIndex = controls.size();

        Module module = settingsPanelRenderModule;
        float reveal = settingsDockReveal;
        if (module == null || reveal <= 0f) {
            settingsPanelRect = EMPTY_RECT;
            settingsPanelViewport = EMPTY_RECT;
            settingsPanelScrollbarTrackRect = EMPTY_RECT;
            settingsPanelScrollbarThumbRect = EMPTY_RECT;
            maxSettingsPanelScroll = 0f;
            return;
        }

        float width = Math.min(MODULE_PANEL_WIDTH, screenWidth - COLUMN_MARGIN * 2f);
        float padding = u(8f);
        float titleFont = font(12f);
        float titleWidth = width - padding * 2f - (module.isLocked() ? 0f : SET_SWITCH_WIDTH + u(8f));
        float headerHeight = u(26f) + (wrappedLineCount(module.moduleName, NVGFonts.INTER_SEMIBOLD, titleFont, titleWidth) - 1) * u(15f);
        float viewportWidth = width - u(15f);
        settingsPanelViewport = new Rect(0f, 0f, viewportWidth, 0f);
        float contentHeight = expandedSettingsHeight(module);
        float height = Math.min(Math.min(MODULE_PANEL_MAX_HEIGHT, screenHeight - COLUMN_MARGIN * 2f), headerHeight + contentHeight + u(5f));

        float x;
        float y;
        if (Float.isNaN(panelAnchorY)) {
            x = (screenWidth - width) / 2f;
            y = (screenHeight - height) / 2f;
        } else {
            x = panelAnchorRight + u(6f);
            if (x + width > screenWidth - COLUMN_MARGIN)
                x = panelAnchorLeft - u(6f) - width;
            y = panelAnchorY - u(3f);
        }
        x = Math.clamp(x, COLUMN_MARGIN, Math.max(COLUMN_MARGIN, screenWidth - COLUMN_MARGIN - width));
        y = Math.clamp(y, COLUMN_MARGIN, Math.max(COLUMN_MARGIN, screenHeight - COLUMN_MARGIN - height));
        y += (1f - reveal) * u(6f);

        Rect panel = new Rect(x, y, width, height);
        settingsPanelRect = panel;
        settingsPanelViewport = new Rect(x + u(4f), y + headerHeight, viewportWidth, Math.max(u(10f), height - headerHeight - u(5f)));
        maxSettingsPanelScroll = Math.max(0f, contentHeight - settingsPanelViewport.height);
        targetSettingsPanelScroll = Math.clamp(targetSettingsPanelScroll, 0f, maxSettingsPanelScroll);
        settingsPanelScroll = Math.clamp(settingsPanelScroll, 0f, maxSettingsPanelScroll);
        if (maxSettingsPanelScroll <= 0f)
            settingsPanelScrollVelocity = 0f;

        Rect viewport = settingsPanelViewport;
        vg.globalAlpha(reveal, () -> {
            vg.beginEffectBatch();
            NanoVGTheme.renderPanelEffects(vg, panel.x, panel.y, panel.width, panel.height, NanoVGTheme.RADIUS_PANEL, 1f);
            vg.flushEffectBatch();
            NanoVGTheme.renderPanel(vg, panel.x, panel.y, panel.width, panel.height, NanoVGTheme.RADIUS_PANEL, 1f, MODULE_PANEL_ALPHA_OFFSET);

            deferControlChrome = true;
            try {
                drawWrappedText(NVGFonts.INTER_SEMIBOLD, module.moduleName, panel.x + padding, panel.y + u(6f), titleFont,
                        titleWidth, u(15f), NanoVGTheme.TEXT, Alignment.LEFT_TOP);
                Rect enableRect = new Rect(panel.x + panel.width - padding - SET_SWITCH_WIDTH, panel.y + u(7f), SET_SWITCH_WIDTH, SET_SWITCH_HEIGHT);
                if (!module.isLocked()) {
                    if (settingsPanelModule != null)
                        addControl(ControlType.MODULE_ENABLED, enableRect.expand(u(4f), u(6f)), module, null, 0);
                    drawSwitch(
                            vg,
                            enableRect.x,
                            enableRect.y,
                            enableRect.width,
                            enableRect.height,
                            animateIdentity(enabledAnimations, enabledAnimationFrames, module, module.tempEnabled, 0.12f),
                            "enable:" + System.identityHashCode(module),
                            isPressed(ControlType.MODULE_ENABLED, module)
                    );
                }

                withInputClip(viewport, () -> vg.scissor(viewport.x, viewport.y, viewport.width, viewport.height,
                        () -> renderExpandedSettings(vg, module, viewport.x, viewport.y - settingsPanelScroll, viewport.width)));
                flushPendingControlGlass(vg);
                drawPendingControlForegrounds(vg);
                renderSettingsPanelScrollbar(vg, contentHeight);
            } finally {
                deferControlChrome = false;
                pendingControlGlass.clear();
                pendingControlForegrounds.clear();
            }
        });

        if (!isSettingsDockOpen()) {
            while (controls.size() > settingsPanelControlStartIndex)
                controls.remove(controls.size() - 1);
        }
    }

    private float settingsPanelReveal() {
        boolean open = isSettingsDockOpen();
        float current = animations.getOrDefault("settings-panel-open", 0f);
        float raw = open
                ? approach(current, 1f, 0.14f * frameDelta)
                : stepTowards(current, 0f, 0.17f * frameDelta);
        animations.put("settings-panel-open", raw);
        if (!open && raw <= 0.02f) {
            animations.put("settings-panel-open", 0f);
            return 0f;
        }

        float reveal = open ? easeOut(raw) : easeIn(raw);
        return reveal <= 0.0005f ? 0f : reveal;
    }

    private boolean isRenamingConfig(ConfigEntry config) {
        return textFocus == TextFocus.CONFIG_RENAME && isSameConfig(renamingConfig, config);
    }

    private void renderExpandedSettings(NVGU vg, Module module, float x, float y, float width) {
        float rowY = y + u(1f);
        rowY = renderBaseSettingRows(vg, module, x + u(2f), rowY, width - u(4f));
        List<SettingValue<?>> settings = collectSettings(module);

        if (!hasRenderableSettings(module)) {
            NVGFonts.INTER.drawText("No settings", x + SET_PAD, rowY + u(4f), SET_FONT, NanoVGTheme.muted(230), Alignment.LEFT_TOP, true);
            return;
        }

        for (SettingValue<?> setting : settings) {
            float progress = settingVisibilityProgress(setting);
            if (progress <= 0.001f)
                continue;

            float settingHeight = (settingRowHeight(setting, width - u(4f)) + settingRowGap() + listDropdownHeight(module, setting)) * easeOut(progress);
            float finalRowY = rowY;
            Rect settingClip = new Rect(x + u(2f), rowY, width - u(4f), settingHeight);
            vg.scissor(settingClip.x, settingClip.y, settingClip.width, settingClip.height, () ->
                    withInputClip(settingClip, () ->
                            vg.globalAlpha(easeOut(progress), () -> renderSetting(vg, module, setting, x + u(2f), finalRowY, width - u(4f)))));
            rowY += settingHeight;
        }
    }

    private String configListUpdatedLabel(ConfigEntry config) {
        Long updated = configUpdatedMillis(config);
        if (updated == null || updated <= 0L)
            return "";

        return CONFIG_LIST_UPDATED_FORMAT.format(Instant.ofEpochMilli(updated));
    }

    private Long configUpdatedMillis(ConfigEntry config) {
        if (config == null)
            return null;
        if (config.type() == ConfigTab.ONLINE)
            return FileUtil.INSTANCE.cachedOnlineConfigUpdatedAt(config.name());
        if (Client.configManager == null)
            return null;

        return Client.configManager.configUpdatedAt(config.type() == ConfigTab.BIND, config.name());
    }

    private float renderBaseSettingRows(NVGU vg, Module module, float x, float y, float width) {
        float rowY = renderModuleNote(vg, module, x, y, width);
        if (!module.isLocked()) {
            float arrayHeight = wrappedSettingRowHeight("Show on array", booleanLabelMaxWidth(width));
            Rect array = new Rect(x, rowY, width, arrayHeight);
            addControl(ControlType.SHOW_ON_ARRAY, settingSwitchRect(array).expand(u(3f), u(3f)), module, null, 0);
            float shownOnArray = animateIdentity(switchAnimations, switchAnimationFrames, module, module.showOnArray, 0.12f);
            renderBooleanRow(vg, array, "Show on array", "array", module, module.showOnArray, shownOnArray, "array:" + System.identityHashCode(module), isPressed(ControlType.SHOW_ON_ARRAY, module));
            rowY += arrayHeight + settingRowGap();
        }

        if (!module.isBindable())
            return rowY;

        Rect key = new Rect(x, rowY, width, SET_ROW);
        addControl(ControlType.MODULE_KEY, key, module, null, 0);
        renderKeybindRow(vg, key, "Keybind", bindingModule == module ? "Press key..." : keyName(module.key));

        return rowY + SET_ROW + settingRowGap();
    }


    private float renderModuleNote(NVGU vg, Module module, float x, float y, float width) {
        String note = moduleNote(module);
        if (note.isEmpty())
            return y;

        List<String> lines = wrapNoteLines(note, width);
        float textY = y + u(2f);
        for (int i = 0; i < lines.size(); i++)
            NVGFonts.INTER.drawText(lines.get(i), x + SET_PAD, textY + i * SET_LINE, SET_FONT, NanoVGTheme.muted(240), Alignment.LEFT_TOP, true);
        return y + noteBlockHeight(lines) + SET_GAP;
    }


    private float renderSetting(NVGU vg, Module module, SettingValue<?> setting, float x, float y, float width) {
        Rect row = new Rect(x, y, width, settingRowHeight(setting, width));

        switch (setting) {
            case BooleanValue value -> {
                float enabled = animateIdentity(switchAnimations, switchAnimationFrames, value, value.get(), 0.12f);
                addControl(ControlType.BOOLEAN, row, value, null, 0);
                renderBooleanRow(vg, row, value.name, "bool", value, value.get(), enabled, "bool:" + System.identityHashCode(value), isPressed(ControlType.BOOLEAN, value));
            }
            case ListValue value -> renderListSetting(vg, row, module, value);
            case FloatValue value -> renderFloatSetting(vg, row, value);
            case IntValue value -> renderIntSetting(vg, row, value);
            case ColorValue value -> renderColorSetting(vg, row, value);
            case TextValue value -> renderTextSetting(vg, row, module, value);
            case KeyBindValue value -> renderKeyBindSetting(vg, row, value);
            default -> renderSettingRow(vg, row, setting, "Unsupported", 0f);
        }

        return row.height + settingRowGap() + listDropdownHeight(module, setting);
    }

    private static void renderSettingWash(NVGU vg, Rect row, float alphaScale) {
        vg.roundedRectangle(row.x, row.y, row.width, row.height, u(4f), alpha(255, 255, 255, (int) (16 * Math.clamp(alphaScale, 0f, 1f))));
    }

    private static float settingTextOffset() {
        return (SET_ROW - NVGFonts.INTER.getHeight(SET_FONT)) / 2f;
    }

    private static Color settingValueColor() {
        return NanoVGTheme.text(215);
    }


    private void renderBooleanRow(NVGU vg, Rect row, String label, String washScope, Object washKey, boolean washActive, float enabled, String grabId, boolean grabbed) {
        drawWrappedSettingLabel(label, row.x + SET_PAD, row.y + settingTextOffset(), booleanLabelMaxWidth(row.width));
        Rect switchRect = settingSwitchRect(row);
        drawSwitch(vg, switchRect.x, switchRect.y, switchRect.width, switchRect.height, enabled, grabId, grabbed);
    }

    private static Rect settingSwitchRect(Rect row) {
        return new Rect(row.x + row.width - SET_PAD - SET_SWITCH_WIDTH, row.y + (SET_ROW - SET_SWITCH_HEIGHT) / 2f, SET_SWITCH_WIDTH, SET_SWITCH_HEIGHT);
    }


    private void renderSettingRow(NVGU vg, Rect row, SettingValue<?> setting, String value, float enabled) {
        if (enabled > 0.01f)
            renderSettingWash(vg, row, enabled);
        drawWrappedSettingLabel(setting.name, row.x + SET_PAD, row.y + settingTextOffset(), settingLabelMaxWidth(setting, row.width));
        if (value != null)
            NVGFonts.INTER_MEDIUM.drawText(value, row.x + row.width - SET_PAD, row.y + settingTextOffset(), SET_FONT, settingValueColor(), Alignment.RIGHT_TOP, true);
    }


    private void renderKeybindRow(NVGU vg, Rect row, String label, String value) {
        drawWrappedSettingLabel(label, row.x + SET_PAD, row.y + settingTextOffset(), keybindLabelMaxWidth(row.width));
        if (value != null)
            NVGFonts.INTER_MEDIUM.drawText(value, row.x + row.width - SET_PAD, row.y + settingTextOffset(), SET_FONT, settingValueColor(), Alignment.RIGHT_TOP, true);
    }


    private void renderListSetting(NVGU vg, Rect row, Module module, ListValue value) {
        boolean open = openedListModule == module && openedListValue == value;
        float dropdownProgress = listDropdownProgress(module, value);
        float easedDropdown = easeOut(dropdownProgress);
        float optionWidth = listOptionWidth(row.width);

        if (dropdownProgress > 0.01f) {
            float[] heights = listOptionHeights(value, optionWidth);
            float openHeight = row.height + (u(2f) + listOptionsTotalHeight(heights)) * easedDropdown;
            vg.roundedRectangle(row.x + u(1f), row.y + u(3f), u(2f), Math.max(u(4f), openHeight - u(6f)), u(1f), NanoVGTheme.text((int) (220 * easedDropdown)));
        }

        drawWrappedSettingLabel(value.name, row.x + SET_PAD, row.y + settingTextOffset(), listLabelMaxWidth(value, row.width));
        if (value.get() != null)
            drawWrappedText(NVGFonts.INTER_MEDIUM, value.get(), row.x + row.width - SET_PAD, row.y + settingTextOffset(), SET_FONT,
                    listCurrentValueWidth(row.width), SET_LINE, settingValueColor(), Alignment.RIGHT_TOP);
        addControl(ControlType.LIST_VALUE, row, value, module, 0);
        if (dropdownProgress > 0.01f)
            renderListOptions(vg, value, row.x, row.y + row.height, optionWidth, easedDropdown, open);
    }

    private void renderListOptions(NVGU vg, ListValue value, float x, float y, float width, float progress, boolean interactive) {
        float[] heights = listOptionHeights(value, width);
        float listHeight = listOptionsTotalHeight(heights);
        float visibleHeight = Math.max(u(20f), listHeight * progress);
        Rect listClip = new Rect(x, y, width, visibleHeight);

        withInputClip(listClip, () -> {
            if (!(interactive && progress > 0.62f))
                return;

            float optionY = y;
            for (int i = 0; i < value.values.length; i++) {
                float optionProgress = Math.clamp((progress - i * listOptionStagger(value)) / 0.78f, 0f, 1f);
                if (optionProgress > 0.01f)
                    addControl(ControlType.LIST_OPTION, new Rect(x, optionY, width, heights[i]), value, null, i);
                optionY += heights[i];
            }
        });

        emitControlForeground(vg, () -> vg.scissor(listClip.x, listClip.y, listClip.width, listClip.height, () -> {
            float optionY = y;
            for (int i = 0; i < value.values.length; i++) {
                String option = value.values[i];
                float optionHeight = heights[i];
                float optionProgress = Math.clamp((progress - i * listOptionStagger(value)) / 0.78f, 0f, 1f);
                if (optionProgress > 0.01f) {
                    Rect optionRect = new Rect(x, optionY, width, optionHeight);
                    boolean selected = i == value.getCurrentIndex();
                    float optionGrab = knobGrab("list-option:" + System.identityHashCode(value) + ":" + i, isListOptionPressed(value, i));
                    Rect optionChip = squashedRect(optionRect, optionGrab, 0.04f, 0.16f);
                    float textOffset = u(3f) * (1f - optionProgress);
                    float finalOptionProgress = optionProgress;
                    vg.globalAlpha(finalOptionProgress, () -> {
                        drawWrappedListOption(option, optionChip.x + optionChip.width - SET_PAD, optionChip.y + u(2f) + textOffset, optionChip.width - SET_PAD * 2f, selected);
                    });
                }
                optionY += optionHeight;
            }
        }));
    }

    private static float listOptionStagger(ListValue value) {
        return Math.min(0.035f, 0.22f / Math.max(1, value.values.length));
    }


    private void renderFloatSetting(NVGU vg, Rect row, FloatValue value) {
        boolean active = textFocus == TextFocus.NUMBER && focusedNumberValue == value;
        addControl(ControlType.FLOAT_INPUT, row, value, null, 0);
        renderSettingRow(vg, row, value, active ? null : value.format() + (value.suffix == null ? "" : value.suffix), 0f);
        if (active)
            renderNumberInput(vg, row, value, value.suffix);

        Rect track = settingBarTrack(row);
        addControl(ControlType.FLOAT_SLIDER, track.expand(u(6f), u(5f)), value, null, 0);
        drawSlider(vg, track, normalize(value.get(), value.minimum, value.maximum), value, 0);
    }


    private void renderIntSetting(NVGU vg, Rect row, IntValue value) {
        boolean active = textFocus == TextFocus.NUMBER && focusedNumberValue == value;
        addControl(ControlType.INT_INPUT, row, value, null, 0);
        renderSettingRow(vg, row, value, active ? null : value.get() + (value.suffix == null ? "" : value.suffix), 0f);
        if (active)
            renderNumberInput(vg, row, value, value.suffix);

        Rect track = settingBarTrack(row);
        addControl(ControlType.INT_SLIDER, track.expand(u(6f), u(5f)), value, null, 0);
        drawSlider(vg, track, normalize(value.get(), value.minimum, value.maximum), value, 0);
    }

    private static Rect settingBarTrack(Rect row) {
        return new Rect(row.x + SET_PAD + u(4f), row.y + row.height - u(11f), row.width - SET_PAD * 2f - u(8f), u(6f));
    }


    private void renderNumberInput(NVGU vg, Rect row, SettingValue<?> value, String suffix) {
        float inputWidth = Math.clamp(row.width * 0.36f, u(56f), u(90f));
        Rect input = new Rect(row.x + row.width - inputWidth - u(3f), row.y + u(2f), inputWidth, SET_ROW - u(4f));
        boolean valid = parseNumberInput() != null;
        Color borderColor = valid ? NanoVGTheme.inputFocus(140) : alpha(255, 88, 88, 160);
        vg.roundedRectangle(input.x, input.y, input.width, input.height, u(4f), alpha(0, 0, 0, 90));
        vg.roundedRectangleBorder(input.x, input.y, input.width, input.height, u(4f), u(1f), borderColor, Border.INSIDE);

        String shownSuffix = suffix == null ? "" : suffix;
        float inputFont = font(9.5f);
        float suffixWidth = shownSuffix.isEmpty() ? 0f : NVGFonts.INTER.getWidth(shownSuffix, inputFont) + u(6f);
        String text = inputText(numberInput, true, "");
        Color numberColor = valid ? NanoVGTheme.TEXT : alpha(255, 175, 175, 240);
        float textY = input.centerY() - NVGFonts.INTER.getHeight(inputFont) / 2f;
        renderInputText(vg, TextFocus.NUMBER, value, true, text, inputFont, input.x + u(4f), textY, textY, input.width - suffixWidth - u(8f), input, numberColor);
        if (!shownSuffix.isEmpty())
            NVGFonts.INTER.drawText(shownSuffix, input.x + input.width - u(4f), textY, inputFont, NanoVGTheme.muted(230), Alignment.RIGHT_TOP, true);
    }


    private void renderColorSetting(NVGU vg, Rect row, ColorValue value) {
        renderSettingRow(vg, row, value, null, 0f);
        Color color = value.get();
        float swatch = u(10f);
        float swatchX = row.x + row.width - SET_PAD - swatch;
        float swatchY = row.y + (SET_ROW - swatch) / 2f;
        NVGFonts.INTER_MEDIUM.drawText(value.toHex().substring(0, 7), swatchX - u(5f), row.y + settingTextOffset(), SET_FONT, settingValueColor(), Alignment.RIGHT_TOP, true);
        vg.roundedRectangle(swatchX, swatchY, swatch, swatch, u(3f), color);
        vg.roundedRectangleBorder(swatchX, swatchY, swatch, swatch, u(3f), u(1f), alpha(255, 255, 255, 90), Border.INSIDE);

        Rect bars = settingBarTrack(row);
        float gap = u(6f);
        float gapWidth = gap + u(4f);
        float trackWidth = (bars.width - gapWidth * 2f) / 3f;
        drawColorTrack(vg, value, bars.x, bars.y, trackWidth, color.getRed(), 0, alpha(255, 80, 80, 230));
        drawColorTrack(vg, value, bars.x + trackWidth + gapWidth, bars.y, trackWidth, color.getGreen(), 1, alpha(80, 255, 120, 230));
        drawColorTrack(vg, value, bars.x + (trackWidth + gapWidth) * 2f, bars.y, trackWidth, color.getBlue(), 2, alpha(80, 175, 255, 230));
    }


    private void drawColorTrack(NVGU vg, ColorValue value, float x, float y, float width, int component, int index, Color accent) {
        Rect track = new Rect(x, y, width, u(6f));
        addControl(ControlType.COLOR_SLIDER, track.expand(u(3f), u(5f)), value, null, index);
        float radius = track.height / 2f;
        emitControlGlass(vg, track.x, track.y, track.width, track.height, radius);
        float grab = knobGrab("slider:" + System.identityHashCode(value) + ":" + index, isSliderPressed(value, index));
        Rect knob = sliderKnob(track, component / 255f, grab);
        emitControlGlass(vg, knob.x, knob.y, knob.width, knob.height, Math.min(knob.width, knob.height) / 2f);
        Color fill = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 220);
        emitControlForeground(vg, () -> {
            vg.roundedRectangle(track.x, track.y, track.width, track.height, radius, NanoVGTheme.CONTROL_TRACK);
            float filled = track.width * component / 255f;
            if (filled > 0.5f)
                vg.roundedRectangle(track.x, track.y, filled, track.height, radius, fill);
            drawKnobFill(vg, knob);
        });
    }


    private void renderTextSetting(NVGU vg, Rect row, Module module, TextValue value) {
        boolean active = textFocus == TextFocus.SETTING && focusedTextValue == value;
        renderSettingRow(vg, row, value, null, 0f);
        Rect input = new Rect(row.x + SET_PAD, row.y + row.height - u(19f), row.width - SET_PAD * 2f, u(16f));
        addControl(ControlType.TEXT_VALUE, input, value, module, 0);
        vg.roundedRectangle(input.x, input.y, input.width, input.height, u(4f), alpha(0, 0, 0, active ? 100 : 70));
        vg.roundedRectangleBorder(input.x, input.y, input.width, input.height, u(4f), u(1f), active ? NanoVGTheme.inputFocus(130) : alpha(255, 255, 255, 34), Border.INSIDE);
        String text = inputText(value.get(), active, "");
        float inputFont = font(9.5f);
        Color textColor = value.get().isEmpty() && !active ? NanoVGTheme.muted(200) : NanoVGTheme.TEXT;
        float textY = input.centerY() - NVGFonts.INTER.getHeight(inputFont) / 2f;
        renderInputText(vg, TextFocus.SETTING, value, active, text, inputFont, input.x + u(5f), textY, textY, input.width - u(10f), input, textColor);
    }

    private void renderKeyBindSetting(NVGU vg, Rect row, KeyBindValue value) {
        boolean active = bindingValue == value;
        renderKeybindRow(vg, row, value.name, active ? "Press key..." : keyName(value.get()));
        addControl(ControlType.KEY_VALUE, row, value, null, 0);
    }

    private void withInputClip(Rect clip, Runnable runnable) {
        Rect clipped = clipToCurrentInputClip(clip);
        inputClips.add(clipped != null ? clipped : new Rect(0f, 0f, 0f, 0f));
        try {
            runnable.run();
        } finally {
            inputClips.remove(inputClips.size() - 1);
        }
    }

    private void addControl(ControlType type, Rect rect, Object target, Object owner, int index) {
        Rect clipped = clipToCurrentInputClip(rect);
        if (clipped != null)
            controls.add(new ControlBounds(type, clipped, target, owner, index));
    }

    private void addModuleRow(Module module, Rect rect) {
        Rect clipped = clipToCurrentInputClip(rect);
        if (clipped != null)
            moduleRows.add(new ModuleRowBounds(module, clipped));
    }

    private boolean isHovered(Rect rect) {
        return rect.contains(scaledMouseX, scaledMouseY) && isInsideCurrentInputClip(scaledMouseX, scaledMouseY);
    }

    private boolean isInsideCurrentInputClip(float x, float y) {
        Rect clip = currentInputClip();
        return clip == null || clip.contains(x, y);
    }

    private Rect clipToCurrentInputClip(Rect rect) {
        Rect clip = currentInputClip();
        return clip == null ? rect : rect.intersect(clip);
    }

    private Rect currentInputClip() {
        return inputClips.isEmpty() ? null : inputClips.get(inputClips.size() - 1);
    }

    private boolean handleControlClick(ControlBounds control, float mouseX, int button, boolean doubleClick) {
        if (control.type == ControlType.FLOAT_INPUT || control.type == ControlType.INT_INPUT) {
            if (button == Keys.MOUSE_BUTTON_MIDDLE) {
                focusNumberInput((SettingValue<?>) control.target);
                return true;
            }

            if (button == Keys.MOUSE_BUTTON_LEFT
                    && textFocus == TextFocus.NUMBER
                    && focusedNumberValue == control.target) {
                beginTextSelectionOrSelectAll(mouseX, doubleClick);
                return true;
            }

            return false;
        }

        if (control.type == ControlType.CONFIG_LOAD || control.type == ControlType.CONFIG_RENAME) {
            ConfigEntry entry = (ConfigEntry) control.target;
            if (button == Keys.MOUSE_BUTTON_MIDDLE)
                return beginConfigRename(entry);

            if (button == Keys.MOUSE_BUTTON_LEFT
                    && textFocus == TextFocus.CONFIG_RENAME
                    && isSameConfig(renamingConfig, entry)) {
                beginTextSelectionOrSelectAll(mouseX, doubleClick);
                return true;
            }

            if (button == Keys.MOUSE_BUTTON_RIGHT
                    && textFocus == TextFocus.CONFIG_RENAME
                    && isSameConfig(renamingConfig, entry)) {
                cancelConfigRename();
                return true;
            }
        }

        if (button == Keys.MOUSE_BUTTON_MIDDLE)
            return false;

        clearTextFocus();
        if (control.type != ControlType.MODULE_KEY) bindingModule = null;
        if (control.type != ControlType.KEY_VALUE) bindingValue = null;

        if (button == Keys.MOUSE_BUTTON_RIGHT) {
            if (control.type == ControlType.CONFIG_LOAD || control.type == ControlType.CONFIG_RENAME)
                return true;

            if (control.type == ControlType.LIST_VALUE) {
                toggleListDropdown((Module) control.owner, (ListValue) control.target);
                pressedControl = control;
                return true;
            }

            if (control.type == ControlType.MODULE_KEY || control.type == ControlType.KEY_VALUE)
                return true;

            if (openedListValue != null) {
                closeListDropdown();
                return true;
            }

            return false;
        }

        if (control.type != ControlType.LIST_VALUE && control.type != ControlType.LIST_OPTION)
            closeListDropdown();

        switch (control.type) {
            case CONFIG_TAB -> {
                selectedConfigTab = (ConfigTab) control.target;
                newConfigName = "";
                configListScroll = 0f;
                targetConfigListScroll = 0f;
                configListScrollVelocity = 0f;
            }
            case CONFIG_NAME -> {
                textFocus = TextFocus.CONFIG_NAME;
                beginTextSelectionOrSelectAll(mouseX, doubleClick);
            }
            case CONFIG_LOAD -> loadConfigEntry((ConfigEntry) control.target);
            case CONFIG_RENAME -> {
            }
            case CONFIG_DELETE -> {
                ConfigEntry target = (ConfigEntry) control.target;
                long now = System.nanoTime();
                if (isDeleteArmed(target)) {
                    if (now - armedDeleteNanos >= CONFIG_DELETE_CONFIRM_DELAY_NANOS) {
                        armedDeleteConfig = null;
                        deleteConfigEntry(target);
                    }
                } else {
                    armedDeleteConfig = target;
                    armedDeleteNanos = now;
                }
            }
            case SHOW_ON_ARRAY -> {
                Module module = (Module) control.target;
                module.showOnArray = !module.showOnArray;
                Interface.reloadSortedModules();
                pressedControl = control;
            }
            case MODULE_ENABLED -> {
                ((Module) control.target).toggle();
                pressedControl = control;
            }
            case MODULE_KEY -> bindingModule = (Module) control.target;
            case BOOLEAN -> {
                BooleanValue value = (BooleanValue) control.target;
                value.set(!value.get());
                pressedControl = control;
            }
            case LIST_VALUE -> {
                toggleListDropdown((Module) control.owner, (ListValue) control.target);
                pressedControl = control;
            }
            case LIST_OPTION -> {
                ((ListValue) control.target).setByIndex(control.index);
                pressedControl = control;
                closeListDropdown();
            }
            case FLOAT_SLIDER, INT_SLIDER, COLOR_SLIDER -> {
                activeSlider = new SliderDrag(control);
                pressedControl = control;
                updateSlider(activeSlider, mouseX);
            }
            case FLOAT_INPUT, INT_INPUT -> {
            }
            case TEXT_VALUE -> {
                textFocus = TextFocus.SETTING;
                focusedTextValue = (TextValue) control.target;
                focusedTextModule = (Module) control.owner;
                beginTextSelectionOrSelectAll(mouseX, doubleClick);
            }
            case KEY_VALUE -> bindingValue = (KeyBindValue) control.target;
        }

        return true;
    }

    private void updateSlider(SliderDrag drag, float mouseX) {
        ControlBounds control = drag.control;
        Rect track = sliderTrack(control);
        float pct = Math.clamp((mouseX - track.x) / Math.max(1f, track.width), 0f, 1f);

        switch (control.type) {
            case FLOAT_SLIDER -> {
                FloatValue value = (FloatValue) control.target;
                float next = value.minimum + (value.maximum - value.minimum) * pct;
                value.set(Math.round(next * 100f) / 100f);
            }
            case INT_SLIDER -> {
                IntValue value = (IntValue) control.target;
                int next = Math.round(value.minimum + (value.maximum - value.minimum) * pct);
                value.set(next);
            }
            case COLOR_SLIDER -> {
                ColorValue value = (ColorValue) control.target;
                Color color = value.get();
                int component = Math.clamp(Math.round(pct * 255f), 0, 255);
                int red = control.index == 0 ? component : color.getRed();
                int green = control.index == 1 ? component : color.getGreen();
                int blue = control.index == 2 ? component : color.getBlue();
                value.set(new Color(red, green, blue, color.getAlpha()));
            }
        }
    }

    private static void renderListRowMark(NVGU vg, Rect row) {
        renderListRowMark(vg, row, 1f);
    }

    private static void renderListRowMark(NVGU vg, Rect row, float alphaScale) {
        int markAlpha = Math.clamp(Math.round(220f * Math.clamp(alphaScale, 0f, 1f)), 0, 255);
        if (markAlpha <= 0)
            return;
        vg.roundedRectangle(row.x + u(6f), row.y + u(7f), u(2f), Math.max(u(8f), row.height - u(14f)), u(1f), alpha(255, 255, 255, markAlpha));
    }

    private void flushPendingControlGlass(NVGU vg) {
        if (pendingControlGlass.isEmpty())
            return;

        flushPendingControlGlass(vg, false);
        flushPendingControlGlass(vg, true);
    }

    private void flushPendingControlGlass(NVGU vg, boolean dark) {
        float current = Math.max(vg.currentAlpha(), 0.001f);
        boolean any = false;
        for (PendingControlGlass glass : pendingControlGlass) {
            if (glass.dark == dark && glass.alphaAtQueue / current > 0.001f) {
                any = true;
                break;
            }
        }
        if (!any)
            return;

        vg.beginEffectBatch();
        for (PendingControlGlass glass : pendingControlGlass) {
            if (glass.dark != dark)
                continue;
            float relative = Math.clamp(glass.alphaAtQueue / current, 0f, 1f);
            if (relative <= 0.001f)
                continue;
            drawControlGlass(vg, glass.x, glass.y, glass.width, glass.height, glass.radius, relative, glass.dark, glass.brightness);
        }
        vg.flushEffectBatch();
    }

    private void drawPendingControlForegrounds(NVGU vg) {
        for (int i = 0, n = pendingControlForegrounds.size(); i < n; i++)
            pendingControlForegrounds.get(i).run();
    }

    private void emitControlGlass(NVGU vg, float x, float y, float width, float height, float radius) {
        emitControlGlass(vg, x, y, width, height, radius, false, 0f);
    }

    private void emitControlGlass(NVGU vg, float x, float y, float width, float height, float radius, boolean dark, float brightness) {
        Rect rect = new Rect(x, y, width, height);
        Rect clip = currentInputClip();
        if (clip != null) {
            rect = rect.intersect(clip);
            if (rect == null)
                return;
        }
        if (rect.width < 1f || rect.height < 1f)
            return;

        float alphaAtQueue = vg.currentAlpha();
        if (alphaAtQueue <= 0.001f)
            return;

        if (deferControlChrome) {
            pendingControlGlass.add(new PendingControlGlass(rect.x, rect.y, rect.width, rect.height, radius, alphaAtQueue, dark, brightness));
            return;
        }

        Rect drawn = rect;
        vg.effectBatch(() -> drawControlGlass(vg, drawn.x, drawn.y, drawn.width, drawn.height, radius, 1f, dark, brightness));
    }

    private void emitControlForeground(NVGU vg, Runnable draw) {
        if (!deferControlChrome) {
            draw.run();
            return;
        }

        Rect clip = currentInputClip();
        float alphaAtQueue = vg.currentAlpha();
        pendingControlForegrounds.add(() -> {
            float relative = Math.clamp(alphaAtQueue / Math.max(vg.currentAlpha(), 0.001f), 0f, 1f);
            if (relative <= 0.001f)
                return;
            vg.globalAlpha(relative, () -> {
                if (clip == null)
                    draw.run();
                else
                    vg.scissor(clip.x, clip.y, clip.width, clip.height, draw);
            });
        });
    }

    private static void drawControlGlass(NVGU vg, float x, float y, float width, float height, float radius, float alpha, boolean dark, float brightness) {
        if (dark)
            NanoVGTheme.renderControlDarkEffects(vg, x, y, width, height, radius, alpha);
        else
            NanoVGTheme.renderControlEffects(vg, x, y, width, height, radius, alpha, brightness);
    }

    private void drawSwitch(NVGU vg, float x, float y, float width, float height, float progress, String grabId, boolean grabbed) {
        float knobProgress = Math.clamp(progress, 0f, 1f);
        float grab = knobGrab(grabId, grabbed);
        Rect knob = switchKnob(x, y, width, height, knobProgress, grab);
        emitControlGlass(vg, x, y, width, height, height / 2f, false, 0.04f * knobProgress);
        emitControlGlass(vg, knob.x, knob.y, knob.width, knob.height, Math.min(knob.width, knob.height) / 2f);
        emitControlForeground(vg, () -> {
            vg.roundedRectangle(x, y, width, height, height / 2f, mix(NanoVGTheme.CONTROL_TRACK, NanoVGTheme.switchOn(240), knobProgress));
            vg.roundedRectangle(knob.x, knob.y, knob.width, knob.height, Math.min(knob.width, knob.height) / 2f, mix(NanoVGTheme.KNOB_FILL, NanoVGTheme.KNOB_ON_FILL, knobProgress));
        });
    }

    private void drawSlider(NVGU vg, Rect track, float progress, Object target, int index) {
        float fill = Math.clamp(progress, 0f, 1f);
        float radius = track.height / 2f;
        float grab = knobGrab("slider:" + System.identityHashCode(target) + ":" + index, isSliderPressed(target, index));
        Rect knob = sliderKnob(track, fill, grab);
        emitControlGlass(vg, track.x, track.y, track.width, track.height, radius);
        emitControlGlass(vg, knob.x, knob.y, knob.width, knob.height, Math.min(knob.width, knob.height) / 2f);
        emitControlForeground(vg, () -> {
            vg.roundedRectangle(track.x, track.y, track.width, track.height, radius, NanoVGTheme.CONTROL_TRACK);
            if (fill > 0.001f)
                vg.roundedRectangle(track.x, track.y, track.width * fill, track.height, radius, NanoVGTheme.sliderFill(190));
            drawKnobFill(vg, knob);
        });
    }

    private static void drawKnobFill(NVGU vg, Rect knob) {
        vg.roundedRectangle(knob.x, knob.y, knob.width, knob.height, Math.min(knob.width, knob.height) / 2f, NanoVGTheme.KNOB_FILL);
    }

    private static Rect switchKnob(float x, float y, float width, float height, float progress, float grab) {
        float size = height * 0.74f;
        float knobWidth = size * (1f + 0.38f * grab);
        float knobHeight = size * (1f - 0.28f * grab);
        float centerX = x + height / 2f + (width - height) * progress;
        float centerY = y + height / 2f;
        return new Rect(centerX - knobWidth / 2f, centerY - knobHeight / 2f, knobWidth, knobHeight);
    }

    private boolean isPressed(ControlType type, Object target) {
        return pressedControl != null && pressedControl.type == type && pressedControl.target == target;
    }

    private boolean isListOptionPressed(ListValue value, int index) {
        return pressedControl != null
                && pressedControl.type == ControlType.LIST_OPTION
                && pressedControl.target == value
                && pressedControl.index == index;
    }

    private static Rect sliderKnob(Rect track, float progress, float grab) {
        float knobWidth = u(9f) * (1f + 0.46f * grab);
        float knobHeight = u(13f) * (1f - 0.30f * grab);
        float centerX = track.x + track.width * progress;
        float centerY = track.centerY();
        return new Rect(centerX - knobWidth / 2f, centerY - knobHeight / 2f, knobWidth, knobHeight);
    }

    private static Rect squashedRect(Rect rect, float grab, float widthScale, float heightScale) {
        float width = rect.width * (1f + widthScale * grab);
        float height = rect.height * (1f - heightScale * grab);
        return new Rect(rect.centerX() - width / 2f, rect.centerY() - height / 2f, width, height);
    }

    private boolean isSliderPressed(Object target, int index) {
        return activeSlider != null && activeSlider.control.target == target && activeSlider.control.index == index;
    }

    private float knobGrab(String id, boolean grabbed) {
        return animate("knob-grab:" + id, grabbed, grabbed ? 0.38f : 0.11f);
    }

    private void renderSettingsPanelScrollbar(NVGU vg, float contentHeight) {
        if (contentHeight <= settingsPanelViewport.height + 1f) {
            settingsPanelScrollbarTrackRect = new Rect(0f, 0f, 0f, 0f);
            settingsPanelScrollbarThumbRect = new Rect(0f, 0f, 0f, 0f);
            return;
        }

        float thumbHeight = Math.max(
                u(22f),
                settingsPanelViewport.height * settingsPanelViewport.height / contentHeight
        );
        float travel = settingsPanelViewport.height - thumbHeight;
        float thumbY = settingsPanelViewport.y + travel
                * (settingsPanelScroll / Math.max(1f, contentHeight - settingsPanelViewport.height));
        float trackX = settingsPanelViewport.x + settingsPanelViewport.width + u(2f);
        settingsPanelScrollbarTrackRect = new Rect(
                trackX - u(5f),
                settingsPanelViewport.y,
                u(12f),
                settingsPanelViewport.height
        );
        settingsPanelScrollbarThumbRect = new Rect(trackX - u(1f), thumbY, u(5f), thumbHeight);

        boolean hovered = settingsPanelScrollbarTrackRect.contains(scaledMouseX, scaledMouseY)
                || activeSettingsPanelScrollbar != null;
        float progress = animate("settings-panel-scrollbar", hovered, 0.18f);
        vg.roundedRectangle(
                trackX + u(1f),
                settingsPanelViewport.y + u(4f),
                u(2f),
                settingsPanelViewport.height - u(8f),
                u(1f),
                alpha(255, 255, 255, (int) (18 + 18 * progress))
        );
        vg.roundedRectangle(
                settingsPanelScrollbarThumbRect.x,
                settingsPanelScrollbarThumbRect.y,
                settingsPanelScrollbarThumbRect.width,
                settingsPanelScrollbarThumbRect.height,
                u(2.5f),
                NanoVGTheme.scrollbarThumb((int) (112 + 86 * progress))
        );
    }

    private void updateSettingsPanelScrollbarDrag(float mouseY) {
        if (activeSettingsPanelScrollbar == null || maxSettingsPanelScroll <= 0f
                || settingsPanelScrollbarTrackRect.height <= 0f
                || settingsPanelScrollbarThumbRect.height <= 0f)
            return;

        float travel = Math.max(
                1f,
                settingsPanelScrollbarTrackRect.height - settingsPanelScrollbarThumbRect.height
        );
        float thumbY = Math.clamp(
                mouseY - activeSettingsPanelScrollbar.offsetY,
                settingsPanelScrollbarTrackRect.y,
                settingsPanelScrollbarTrackRect.y + travel
        );
        float progress = (thumbY - settingsPanelScrollbarTrackRect.y) / travel;
        targetSettingsPanelScroll = Math.clamp(progress * maxSettingsPanelScroll, 0f, maxSettingsPanelScroll);
        settingsPanelScroll = targetSettingsPanelScroll;
        settingsPanelScrollVelocity = 0f;
    }

    private static boolean matchesModuleSearch(Module module, String query) {
        return normalize(module.moduleName).contains(query)
                || normalize(module.moduleCategory.name()).contains(query)
                || (module.tag() != null && normalize(module.tag()).contains(query))
                || (module.description() != null && normalize(module.description()).contains(query));
    }

    private List<ConfigEntry> configEntries(String query) {
        List<ConfigEntry> entries = new ArrayList<>();

        switch (selectedConfigTab) {
            case MODULE -> {
                for (Path path : FileUtil.INSTANCE.getModuleFiles()) {
                    String name = configName(path);
                    if (matchesConfigSearch(name, query))
                        entries.add(new ConfigEntry(name, ConfigTab.MODULE, name.equalsIgnoreCase(Client.configManager.configCurrent)));
                }
            }
            case BIND -> {
                for (Path path : FileUtil.INSTANCE.getBindFiles()) {
                    String name = configName(path);
                    if (matchesConfigSearch(name, query))
                        entries.add(new ConfigEntry(name, ConfigTab.BIND, name.equalsIgnoreCase(Client.configManager.bindCurrent)));
                }
            }
            case ONLINE -> {
                for (String config : FileUtil.INSTANCE.getOnlineCfgs()) {
                    String name = stripJsonExtension(config);
                    if (!name.isBlank() && matchesConfigSearch(name, query))
                        entries.add(new ConfigEntry(name, ConfigTab.ONLINE, ConfigManager.onlineConfigClientName(name).equalsIgnoreCase(Client.configManager.configCurrent)));
                }
            }
        }

        entries.sort(Comparator.comparing(ConfigEntry::name, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private boolean currentConfigExists(String currentName, boolean bind) {
        if (currentName == null || currentName.isBlank())
            return false;

        for (Path path : bind ? FileUtil.INSTANCE.getBindFiles() : FileUtil.INSTANCE.getModuleFiles()) {
            if (configName(path).equalsIgnoreCase(currentName))
                return true;
        }
        return false;
    }

    private boolean matchesConfigSearch(String name, String query) {
        return query.isBlank() || normalize(name).contains(query);
    }

    private void createConfigFromInput() {
        if (selectedConfigTab == ConfigTab.ONLINE)
            return;

        String configName = cleanConfigName(newConfigName);
        if (configName.isBlank())
            return;

        if (selectedConfigTab == ConfigTab.MODULE) {
            Client.configManager.saveConfigFile(Client.configManager.configCurrent, false);
            Client.configManager.saveConfigFile(configName, true);
        } else {
            Client.configManager.saveBindFile(Client.configManager.bindCurrent, false);
            Client.configManager.saveBindFile(configName, true);
        }

        newConfigName = "";
        clearTextFocus();
    }

    private void loadConfigEntry(ConfigEntry entry) {
        Map<Module, Boolean> previousModuleStates = snapshotModuleStates();

        switch (entry.type) {
            case MODULE -> {
                saveCurrentModuleConfig();
                Client.configManager.loadConfig(entry.name, false);
                saveLoadedModuleConfig(entry.name);
            }
            case BIND -> {
                saveCurrentBindConfig();
                Client.configManager.loadBind(entry.name);
                saveLoadedBindConfig(entry.name);
            }
            case ONLINE -> {
                saveCurrentModuleConfig();
                Client.configManager.loadConfig(entry.name, true);
            }
        }

        if (entry.type != ConfigTab.BIND) {
            prepareConfigToggleAnimations(previousModuleStates);
            Interface.reloadSortedModules();
        }

        closeListDropdown();
    }

    private void deleteConfigEntry(ConfigEntry entry) {
        if (entry == null || entry.type == ConfigTab.ONLINE || entry.current)
            return;

        String configName = cleanConfigName(entry.name);
        if (configName.isBlank())
            return;

        if (entry.type == ConfigTab.MODULE && configName.equalsIgnoreCase(Client.configManager.configCurrent))
            return;

        if (entry.type == ConfigTab.BIND && configName.equalsIgnoreCase(Client.configManager.bindCurrent))
            return;

        File base = new File(Client.configManager.BASE_DIR, entry.type == ConfigTab.MODULE ? "module_configs" : "bind_configs");
        File configFile = new File(base, configName + ".json");
        if (!base.exists() || !configFile.isFile() || !configFile.delete())
            return;

        FileUtil.INSTANCE.invalidateLocalConfigCache();
        Client.configManager.forgetConfigUpdated(entry.type == ConfigTab.BIND, configName);
        clearTextFocus();
        closeListDropdown();
    }

    private Map<Module, Boolean> snapshotModuleStates() {
        Map<Module, Boolean> states = new IdentityHashMap<>();
        for (Module module : ModuleManager.allModules) {
            if (module != null)
                states.put(module, module.tempEnabled);
        }
        return states;
    }

    private void prepareConfigToggleAnimations(Map<Module, Boolean> previousStates) {
        for (Module module : ModuleManager.allModules) {
            if (module == null)
                continue;

            Boolean previous = previousStates.get(module);
            if (previous == null || previous == module.tempEnabled)
                continue;

            enabledAnimations.put(module, previous ? 1f : 0f);
            enabledAnimationFrames.remove(module);
        }
    }

    private static String configName(Path config) {
        if (config == null || config.getFileName() == null)
            return "";
        return stripJsonExtension(config.getFileName().toString());
    }

    private static String stripJsonExtension(String config) {
        if (config == null)
            return "";

        String name = config.trim();
        return name.toLowerCase(Locale.ROOT).endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    private static String cleanConfigName(String input) {
        String stripped = stripJsonExtension(input);
        if (stripped.isBlank())
            return "";

        StringBuilder builder = new StringBuilder(stripped.length());
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (Character.isISOControl(c) || c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|')
                continue;
            builder.append(c);
        }

        return builder.toString().trim();
    }

    private static void saveCurrentModuleConfig() {
        File base = new File(Client.configManager.BASE_DIR, "module_configs");
        File configFile = new File(base, Client.configManager.configCurrent + ".json");
        if (base.exists() && configFile.exists())
            Client.configManager.saveConfigFile(Client.configManager.configCurrent, false);
    }

    private static void saveLoadedModuleConfig(String configName) {
        File base = new File(Client.configManager.BASE_DIR, "module_configs");
        File configFile = new File(base, configName + ".json");
        if (base.exists() && configFile.exists())
            Client.configManager.saveConfigFile(configName, true, Client.configManager.configUpdatedAt(false, configName));
    }

    private static void saveCurrentBindConfig() {
        File base = new File(Client.configManager.BASE_DIR, "bind_configs");
        File bindFile = new File(base, Client.configManager.bindCurrent + ".json");
        if (base.exists() && bindFile.exists())
            Client.configManager.saveBindFile(Client.configManager.bindCurrent, false);
    }

    private static void saveLoadedBindConfig(String configName) {
        File base = new File(Client.configManager.BASE_DIR, "bind_configs");
        File bindFile = new File(base, configName + ".json");
        if (base.exists() && bindFile.exists())
            Client.configManager.saveBindFile(configName, true);
    }

    private float expandedSettingsHeight(Module module) {
        Float cached = expandedSettingsHeightCache.get(module);
        if (cached != null)
            return cached;

        List<SettingValue<?>> settings = collectSettings(module);
        float rowWidth = Math.max(u(40f), settingsPanelViewport.width - u(4f));
        float arrayHeight = wrappedSettingRowHeight("Show on array", booleanLabelMaxWidth(rowWidth));
        float height = u(1f) + moduleNoteHeight(module)
                + (module.isLocked() ? 0f : arrayHeight + settingRowGap())
                + (module.isBindable() ? SET_ROW + settingRowGap() : 0f);

        if (settings.isEmpty()) {
            height += u(30f);
            expandedSettingsHeightCache.put(module, height);
            return height;
        }

        for (SettingValue<?> setting : settings) {
            height += (settingRowHeight(setting, rowWidth) + settingRowGap() + listDropdownHeight(module, setting)) * easeOut(settingVisibilityProgress(setting));
        }

        if (!hasRenderableSettings(module))
            height += u(30f);

        expandedSettingsHeightCache.put(module, height);
        return height;
    }

    private float listDropdownHeight(Module module, SettingValue<?> setting) {
        return openedListModule == module && openedListValue == setting && setting instanceof ListValue listValue
                ? listDropdownBaseHeight(listValue) * easeOut(listDropdownProgress(module, listValue))
                : setting instanceof ListValue listValue && listDropdownAnimations.containsKey(listValue)
                ? listDropdownBaseHeight(listValue) * easeOut(listDropdownProgress(module, listValue))
                : 0f;
    }


    private float listDropdownBaseHeight(ListValue value) {
        if (value == null || value.values.length <= 1)
            return 0f;
        float rowWidth = Math.max(u(40f), settingsPanelViewport.width - u(4f));
        float[] heights = listOptionHeights(value, listOptionWidth(rowWidth));
        return u(2f) + listOptionsTotalHeight(heights);
    }

    private float listDropdownProgress(Module module, ListValue value) {
        return animateIdentity(listDropdownAnimations, listDropdownAnimationFrames, value, openedListModule == module && openedListValue == value, 0.08f);
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

    private boolean hasRenderableSettings(Module module) {
        Boolean cached = renderableSettingsCache.get(module);
        if (cached != null)
            return cached;

        for (SettingValue<?> setting : collectSettings(module)) {
            if (setting.canDisplay.canDisplay()) {
                renderableSettingsCache.put(module, true);
                return true;
            }

            Float progress = settingVisibilityAnimations.get(setting);
            if (progress != null && progress > 0.001f) {
                renderableSettingsCache.put(module, true);
                return true;
            }
        }

        renderableSettingsCache.put(module, false);
        return false;
    }

    private float moduleNoteHeight(Module module) {
        String note = moduleNote(module);
        if (note.isEmpty())
            return 0f;

        return noteBlockHeight(wrapNoteLines(note, Math.max(u(40f), settingsPanelViewport.width - u(4f)))) + SET_GAP;
    }


    private static float noteBlockHeight(List<String> lines) {
        return u(5f) + Math.max(1, lines.size()) * SET_LINE;
    }


    private static List<String> wrapNoteLines(String note, float rowWidth) {
        return wrapTextToWidth(note, NVGFonts.INTER, SET_FONT, Math.max(u(20f), rowWidth - SET_PAD * 2f));
    }

    private String moduleNote(Module module) {
        String description = module.description();
        return description == null ? "" : description.trim();
    }

    private float settingVisibilityProgress(SettingValue<?> setting) {
        return animateIdentity(settingVisibilityAnimations, settingVisibilityAnimationFrames, setting, setting.canDisplay.canDisplay(), 0.08f);
    }

    private void toggleSettingsPanel(Module module) {
        if (module == null)
            return;
        if (settingsPanelModule == module || pendingSettingsPanelModule == module)
            closeSettingsPanel();
        else
            openSettingsPanel(module);
    }

    private void openSettingsPanel(Module module) {
        boolean alreadyOpen = isSettingsDockOpen();
        if (pendingSettingsPanelModule != null
                || alreadyOpen && settingsPanelModule != module && settingsPanelRenderModule != null) {
            clearInteractionState();
            pendingSettingsPanelModule = module;
            settingsPanelModule = null;
            activeSettingsPanelScrollbar = null;
            return;
        }

        clearInteractionState();
        settingsPanelModule = module;
        settingsPanelRenderModule = module;
        settingsPanelScroll = 0f;
        targetSettingsPanelScroll = 0f;
        settingsPanelScrollVelocity = 0f;
        activeSettingsPanelScrollbar = null;
        if (!alreadyOpen)
            animations.remove("settings-panel-open");
    }

    private void closeSettingsPanel() {
        clearInteractionState();
        pendingSettingsPanelModule = null;
        settingsPanelModule = null;
        settingsPanelScroll = 0f;
        targetSettingsPanelScroll = 0f;
        settingsPanelScrollVelocity = 0f;
        maxSettingsPanelScroll = 0f;
        activeSettingsPanelScrollbar = null;
    }

    private boolean isSettingsDockOpen() {
        return settingsPanelModule != null;
    }

    private static boolean isSameConfig(ConfigEntry first, ConfigEntry second) {
        return first != null && second != null && first.type() == second.type() && first.name().equalsIgnoreCase(second.name());
    }

    private void focusSearch() {
        clearInteractionState();
        textFocus = TextFocus.SEARCH;
        textCaret = searchQuery.length();
        textAnchor = textCaret;
    }

    private void focusNumberInput(SettingValue<?> value) {
        if (!(value instanceof FloatValue) && !(value instanceof IntValue))
            return;

        clearTextFocus();
        bindingModule = null;
        bindingValue = null;
        activeSlider = null;
        pressedControl = null;
        pressedModule = null;
        closeListDropdown();

        focusedNumberValue = value;
        numberInput = value instanceof FloatValue floatValue
                ? Float.toString(floatValue.get())
                : Integer.toString(((IntValue) value).get());
        textFocus = TextFocus.NUMBER;
        selectAllFocusedText();
    }

    private boolean beginConfigRename(ConfigEntry entry) {
        if (entry == null || entry.type == ConfigTab.ONLINE)
            return false;

        if (textFocus == TextFocus.CONFIG_RENAME && isSameConfig(renamingConfig, entry)) {
            selectAllFocusedText();
            return true;
        }

        clearTextFocus();
        bindingModule = null;
        bindingValue = null;
        activeSlider = null;
        pressedControl = null;
        pressedModule = null;
        closeListDropdown();

        renamingConfig = entry;
        renameConfigName = entry.name == null ? "" : entry.name;
        textFocus = TextFocus.CONFIG_RENAME;
        selectAllFocusedText();
        return true;
    }

    private void cancelConfigRename() {
        resetTextFocus();
    }

    private boolean commitConfigRename() {
        if (renamingConfig == null || renamingConfig.type == ConfigTab.ONLINE)
            return false;

        String cleaned = cleanConfigName(renameConfigName);
        if (cleaned.isBlank())
            return false;

        boolean bind = renamingConfig.type == ConfigTab.BIND;
        String renamed = Client.configManager.renameConfig(bind, renamingConfig.name, cleaned);
        return renamed != null;
    }

    private void clearSearch() {
        searchQuery = "";
        textCaret = 0;
        textAnchor = 0;
        closeListDropdown();
    }

    private void clearInteractionState() {
        clearTextFocus();
        bindingModule = null;
        bindingValue = null;
        activeSlider = null;
        pressedControl = null;
        pressedModule = null;
        activeSettingsPanelScrollbar = null;
        closeListDropdown();
    }

    private void closeListDropdown() {
        openedListModule = null;
        openedListValue = null;
    }

    private void toggleListDropdown(Module module, ListValue value) {
        if (module == null || value == null)
            return;
        if (openedListModule == module && openedListValue == value)
            closeListDropdown();
        else {
            openedListModule = module;
            openedListValue = value;
        }
    }

    private void rememberHoveredModule(Module module) {
        hoveredModule = module;
        if (tooltipHoverModule != module) {
            tooltipHoverModule = module;
            hoveredModuleStartNanos = System.nanoTime();
        }
        String note = moduleNote(module);
        if (note.isEmpty())
            return;
        tooltipText = note;
    }

    private void renderHoverTooltip(NVGU vg) {
        boolean ready = hoveredModule != null
                && !tooltipText.isEmpty()
                && settingsPanelModule != hoveredModule
                && System.nanoTime() - hoveredModuleStartNanos >= 280_000_000L;
        if (!ready)
            tooltipHoverModule = hoveredModule;
        float progress = animate("module-tooltip", ready, 0.22f);
        if (progress <= 0.01f || tooltipText.isEmpty())
            return;

        float paddingX = u(8f);
        float maxWidth = u(280f);
        float tooltipFont = font(12f);
        float lineHeight = u(15f);
        List<String> lines = wrapTextToWidth(tooltipText, NVGFonts.INTER, tooltipFont, maxWidth);
        float textWidth = 0f;
        for (String line : lines)
            textWidth = Math.max(textWidth, NVGFonts.INTER.getWidth(line, tooltipFont));
        float width = textWidth + paddingX * 2f;
        float height = u(24f) + (lines.size() - 1) * lineHeight;
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        float preferredX = scaledMouseX + u(10f);
        float drawX = preferredX + width > screenWidth - u(8f)
                ? Math.max(u(8f), screenWidth - u(8f) - width)
                : Math.max(u(8f), preferredX);
        float drawY = scaledMouseY + u(16f) + height > screenHeight - u(8f)
                ? scaledMouseY - height - u(8f)
                : scaledMouseY + u(16f);

        vg.globalAlpha(easeOut(progress), () -> {
            vg.beginEffectBatch();
            NanoVGTheme.renderPanelEffects(vg, drawX, drawY, width, height, u(NanoVGTheme.RADIUS_HUD), 1f);
            vg.flushEffectBatch();
            NanoVGTheme.renderPanel(vg, drawX, drawY, width, height, u(NanoVGTheme.RADIUS_HUD), 1f);
            for (int i = 0; i < lines.size(); i++)
                NVGFonts.INTER.drawText(lines.get(i), drawX + paddingX, drawY + u(6f) + i * lineHeight, tooltipFont, NanoVGTheme.TEXT, Alignment.LEFT_TOP, true);
        });
    }

    private boolean nudgeHoveredColorSlider(float mouseX, float mouseY, double vertical) {
        if (Math.abs(vertical) < 0.01d)
            return false;

        for (ControlBounds control : controls) {
            if (control.type != ControlType.COLOR_SLIDER || !control.rect.contains(mouseX, mouseY))
                continue;

            ColorValue value = (ColorValue) control.target;
            Color color = value.get();
            int delta = (int) Math.round(vertical * 5d);
            int red = control.index == 0 ? Math.clamp(color.getRed() + delta, 0, 255) : color.getRed();
            int green = control.index == 1 ? Math.clamp(color.getGreen() + delta, 0, 255) : color.getGreen();
            int blue = control.index == 2 ? Math.clamp(color.getBlue() + delta, 0, 255) : color.getBlue();
            value.set(new Color(red, green, blue, color.getAlpha()));
            return true;
        }
        return false;
    }

    private void copyFocusedTextToClipboard() {
        if (minecraft == null || textFocus == TextFocus.NONE)
            return;

        String text = getFocusedText();
        if (text == null || text.isEmpty())
            return;

        if (hasTextSelection()) {
            int start = Math.clamp(textSelectionStart(), 0, text.length());
            int end = Math.clamp(textSelectionEnd(), start, text.length());
            if (start >= end)
                return;
            text = text.substring(start, end);
        }

        if (text.isEmpty())
            return;

        minecraft.keyboardHandler.setClipboard(text);
    }

    private void pasteClipboardIntoFocusedText() {
        if (minecraft == null || textFocus == TextFocus.NONE)
            return;

        String clipboard = minecraft.keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.isEmpty())
            return;

        String sanitized = clipboard.replace('\n', ' ').replace('\r', ' ');
        if (textFocus == TextFocus.NUMBER) {
            appendNumberInput(sanitized.trim());
            return;
        }
        if (textFocus != TextFocus.SETTING)
            sanitized = sanitized.replace('\t', ' ');
        if (sanitized.isEmpty())
            return;

        appendFocusedText(sanitized);
    }

    private void clearTextFocus() {
        if (textFocus == TextFocus.NUMBER)
            commitNumberInput();
        if (textFocus == TextFocus.CONFIG_RENAME)
            commitConfigRename();
        resetTextFocus();
    }

    private void resetTextFocus() {
        textFocus = TextFocus.NONE;
        focusedTextValue = null;
        focusedTextModule = null;
        focusedNumberValue = null;
        renamingConfig = null;
        renameConfigName = "";
        numberInput = "";
        textCaret = 0;
        textAnchor = 0;
        draggingTextSelection = false;
    }

    private void cancelNumberInput() {
        resetTextFocus();
    }

    private boolean commitNumberInput() {
        Number parsed = parseNumberInput();
        if (parsed == null)
            return false;

        if (focusedNumberValue instanceof FloatValue value) {
            value.set(Math.clamp(parsed.floatValue(), value.minimum, value.maximum));
            return true;
        }

        if (focusedNumberValue instanceof IntValue value) {
            long parsedValue = parsed.longValue();
            long clamped = Math.max(value.minimum, Math.min(value.maximum, parsedValue));
            value.set((int) clamped);
            return true;
        }

        return false;
    }

    private Number parseNumberInput() {
        String input = numberInput.trim();
        if (input.isEmpty())
            return null;

        try {
            if (focusedNumberValue instanceof FloatValue) {
                float parsed = Float.parseFloat(input);
                return Float.isFinite(parsed) ? parsed : null;
            }
            if (focusedNumberValue instanceof IntValue)
                return Long.parseLong(input);
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private void appendNumberInput(String input) {
        if (textFocus != TextFocus.NUMBER || input == null || input.isEmpty())
            return;

        String text = numberInput == null ? "" : numberInput;
        int start = Math.clamp(textSelectionStart(), 0, text.length());
        int end = Math.clamp(textSelectionEnd(), start, text.length());
        String candidate = text.substring(0, start) + input + text.substring(end);
        if (candidate.length() > 64 || !isPotentialNumberInput(candidate))
            return;

        numberInput = candidate;
        textCaret = start + input.length();
        textAnchor = textCaret;
    }

    private boolean isPotentialNumberInput(String input) {
        if (focusedNumberValue instanceof FloatValue)
            return input.matches("[+-]?(?:\\d*(?:\\.\\d*)?)(?:[eE][+-]?\\d*)?");
        if (focusedNumberValue instanceof IntValue)
            return input.matches("[+-]?\\d*");
        return false;
    }

    private String getFocusedText() {
        if (textFocus == TextFocus.SEARCH) return searchQuery;
        if (textFocus == TextFocus.CONFIG_NAME) return newConfigName;
        if (textFocus == TextFocus.CONFIG_RENAME) return renameConfigName;
        if (textFocus == TextFocus.SETTING && focusedTextValue != null) return focusedTextValue.get();
        if (textFocus == TextFocus.NUMBER) return numberInput;
        return "";
    }

    private void setFocusedText(String text) {
        if (textFocus == TextFocus.SEARCH) {
            searchQuery = text == null ? "" : text;
            return;
        }

        if (textFocus == TextFocus.CONFIG_NAME) {
            newConfigName = text == null ? "" : text;
            return;
        }

        if (textFocus == TextFocus.CONFIG_RENAME) {
            String next = text == null ? "" : text;
            if (next.length() > ConfigManager.MAX_CONFIG_NAME_LENGTH)
                next = next.substring(0, ConfigManager.MAX_CONFIG_NAME_LENGTH);
            renameConfigName = next;
            return;
        }

        if (textFocus == TextFocus.SETTING && focusedTextValue != null) {
            focusedTextValue.set(text == null ? "" : text);
            return;
        }

        if (textFocus == TextFocus.NUMBER) {
            numberInput = text == null ? "" : text;
        }
    }

    private void appendFocusedText(String input) {
        if (textFocus == TextFocus.NONE || input == null || input.isEmpty())
            return;

        replaceFocusedSelection(input);
    }

    private void removeLastFocusedCharacter() {
        if (hasTextSelection()) {
            replaceFocusedSelection("");
            return;
        }

        String text = getFocusedText();
        if (text.isEmpty() || textCaret <= 0)
            return;

        int caret = Math.clamp(textCaret, 0, text.length());
        int previous = text.offsetByCodePoints(caret, -1);
        setFocusedText(text.substring(0, previous) + text.substring(caret));
        textCaret = previous;
        textAnchor = previous;
    }

    private void deleteForwardFocusedCharacter() {
        if (hasTextSelection()) {
            replaceFocusedSelection("");
            return;
        }

        String text = getFocusedText();
        if (text.isEmpty() || textCaret >= text.length())
            return;

        int caret = Math.clamp(textCaret, 0, text.length());
        int next = text.offsetByCodePoints(caret, 1);
        setFocusedText(text.substring(0, caret) + text.substring(next));
        textCaret = caret;
        textAnchor = caret;
    }

    private void replaceFocusedSelection(String insertion) {
        String text = getFocusedText();
        int start = Math.clamp(textSelectionStart(), 0, text.length());
        int end = Math.clamp(textSelectionEnd(), start, text.length());
        setFocusedText(text.substring(0, start) + insertion + text.substring(end));
        textCaret = start + insertion.length();
        textAnchor = textCaret;
    }

    private void rememberTextField(TextFocus focus, Object target, float textX, float fontSize, float maxWidth) {
        textFields.add(new TextFieldLayout(focus, target, textX, fontSize, maxWidth));
    }

    private boolean hasTextSelection() {
        return textCaret != textAnchor;
    }

    private int textSelectionStart() {
        return Math.min(textCaret, textAnchor);
    }

    private int textSelectionEnd() {
        return Math.max(textCaret, textAnchor);
    }

    private void selectAllFocusedText() {
        textAnchor = 0;
        textCaret = getFocusedText().length();
        draggingTextSelection = false;
    }

    private void moveFocusedCaret(int direction, boolean extendSelection) {
        String text = getFocusedText();
        if (text.isEmpty()) {
            textCaret = 0;
            textAnchor = 0;
            return;
        }

        if (!extendSelection && hasTextSelection()) {
            textCaret = direction < 0 ? textSelectionStart() : textSelectionEnd();
            textAnchor = textCaret;
            return;
        }

        int caret = Math.clamp(textCaret, 0, text.length());
        if (direction < 0 && caret > 0)
            caret = text.offsetByCodePoints(caret, -1);
        else if (direction > 0 && caret < text.length())
            caret = text.offsetByCodePoints(caret, 1);
        textCaret = caret;
        if (!extendSelection)
            textAnchor = textCaret;
    }

    private void beginTextSelectionOrSelectAll(float mouseX, boolean doubleClick) {
        if (doubleClick) {
            selectAllFocusedText();
            return;
        }
        beginTextSelection(mouseX);
    }

    private void beginTextSelection(float mouseX) {
        int index = caretIndexAt(mouseX);
        textAnchor = index;
        textCaret = index;
        draggingTextSelection = true;
    }

    private void updateTextSelectionDrag(float mouseX) {
        if (textFocus == TextFocus.NONE)
            return;
        textCaret = caretIndexAt(mouseX);
    }

    private int caretIndexAt(float mouseX) {
        String text = getFocusedText();
        TextFieldLayout layout = findFocusedTextField();
        if (text.isEmpty())
            return 0;
        if (layout == null)
            return text.length();

        float local = mouseX - layout.textX;
        if (local <= 0f)
            return 0;

        int index = 0;
        while (index < text.length()) {
            int next = text.offsetByCodePoints(index, 1);
            float left = NVGFonts.INTER.getWidth(text.substring(0, index), layout.fontSize);
            float right = NVGFonts.INTER.getWidth(text.substring(0, next), layout.fontSize);
            if (local < (left + right) / 2f)
                return index;
            index = next;
        }
        return text.length();
    }

    private TextFieldLayout findFocusedTextField() {
        for (int i = 0, n = textFields.size(); i < n; i++) {
            TextFieldLayout layout = textFields.get(i);
            if (layout.focus != textFocus)
                continue;
            if (textFocus == TextFocus.SETTING && layout.target != focusedTextValue)
                continue;
            if (textFocus == TextFocus.NUMBER && layout.target != focusedNumberValue)
                continue;
            if (textFocus == TextFocus.CONFIG_RENAME) {
                if (!(layout.target instanceof ConfigEntry layoutConfig) || !isSameConfig(layoutConfig, renamingConfig))
                    continue;
            }
            return layout;
        }
        return null;
    }

    private void applyHoverCursor() {
        if (minecraft == null)
            return;
        minecraft.getWindow().selectCursor(resolveHoverCursor());
    }

    private void resetHoverCursor() {
        if (minecraft == null)
            return;
        minecraft.getWindow().selectCursor(CursorType.DEFAULT);
    }

    private CursorType resolveHoverCursor() {
        if (closing)
            return CursorType.DEFAULT;

        if (hitRect(settingsPanelScrollbarThumbRect) || hitRect(settingsPanelScrollbarTrackRect))
            return CursorTypes.POINTING_HAND;
        if (hitRect(configScrollbarThumbRect) || hitRect(configScrollbarTrackRect))
            return CursorTypes.POINTING_HAND;

        if (isSettingsDockOpen() && hitRect(settingsPanelRect)) {
            CursorType settingsCursor = cursorForControls(settingsPanelControlStartIndex, controls.size());
            return settingsCursor == null ? CursorType.DEFAULT : settingsCursor;
        }

        CursorType controlCursor = cursorForControls(0, settingsPanelControlStartIndex > 0 ? settingsPanelControlStartIndex : controls.size());
        if (controlCursor != null)
            return controlCursor;

        if (hitRect(searchRect))
            return CursorTypes.IBEAM;

        for (int i = 0, n = moduleRows.size(); i < n; i++) {
            if (moduleRows.get(i).rect.contains(scaledMouseX, scaledMouseY))
                return CursorTypes.POINTING_HAND;
        }
        return CursorType.DEFAULT;
    }

    private CursorType cursorForControls(int start, int end) {
        for (int i = end - 1; i >= start; i--) {
            ControlBounds control = controls.get(i);
            if (!control.rect.contains(scaledMouseX, scaledMouseY))
                continue;
            return cursorForControl(control);
        }
        return null;
    }

    private CursorType cursorForControl(ControlBounds control) {
        return switch (control.type) {
            case TEXT_VALUE, CONFIG_NAME, CONFIG_RENAME -> CursorTypes.IBEAM;
            case FLOAT_SLIDER, INT_SLIDER, COLOR_SLIDER -> CursorTypes.POINTING_HAND;
            case FLOAT_INPUT, INT_INPUT ->
                    textFocus == TextFocus.NUMBER && focusedNumberValue == control.target
                            ? CursorTypes.IBEAM
                            : CursorTypes.POINTING_HAND;
            default -> CursorTypes.POINTING_HAND;
        };
    }

    private boolean hitRect(Rect rect) {
        return rect != null && rect.width() > 0f && rect.height() > 0f && rect.contains(scaledMouseX, scaledMouseY);
    }

    private static Rect sliderTrack(ControlBounds control) {
        return switch (control.type) {
            case FLOAT_SLIDER, INT_SLIDER -> control.rect.contract(u(6f), u(5f));
            case COLOR_SLIDER -> control.rect.contract(u(3f), u(5f));
            default -> control.rect;
        };
    }

    private float animate(String key, boolean active, float speed) {
        return animations.compute(key, (ignored, current) -> approach(current == null ? 0f : current, active ? 1f : 0f, speed * frameDelta));
    }

    private <T> float animateIdentity(Map<T, Float> states, Map<T, Long> frames, T key, boolean active, float speed) {
        Float current = states.get(key);
        if (current == null) {
            float initial = active ? 1f : 0f;
            states.put(key, initial);
            frames.put(key, animationFrame);
            return initial;
        }

        Long frame = frames.get(key);
        if (frame != null && frame == animationFrame)
            return current;

        float progress = stepTowards(current, active ? 1f : 0f, speed * frameDelta);
        states.put(key, progress);
        frames.put(key, animationFrame);
        return progress;
    }

    private static float approach(float value, float target, float speed) {
        return value + (target - value) * Math.clamp(speed, 0f, 1f);
    }

    private static float stepTowards(float value, float target, float step) {
        if (value < target)
            return Math.min(target, value + Math.max(0f, step));
        if (value > target)
            return Math.max(target, value - Math.max(0f, step));
        return target;
    }

    private static float easeOut(float progress) {
        progress = Math.clamp(progress, 0f, 1f);
        return 1f - (1f - progress) * (1f - progress);
    }

    private static float easeIn(float progress) {
        progress = Math.clamp(progress, 0f, 1f);
        return progress * progress;
    }

    private static float normalize(float value, float min, float max) {
        if (Math.abs(max - min) <= 0.0001f) return 0f;
        return Math.clamp((value - min) / (max - min), 0f, 1f);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private String inputText(String value, boolean active, String placeholder) {
        String text = value == null ? "" : value;
        if (active) {
            if (hasTextSelection() || !caretVisible())
                return text;
            int caret = Math.clamp(textCaret, 0, text.length());
            return caret >= text.length() ? text + "_" : text;
        }
        return text.isBlank() ? (placeholder == null ? "" : placeholder) : text;
    }

    private void renderInlineCaret(NVGU vg, boolean active, NVGFont font, float fontSize, float textX, float textY, Color color) {
        if (!active || hasTextSelection() || !caretVisible())
            return;

        String text = getFocusedText();
        int caret = Math.clamp(textCaret, 0, text.length());
        if (caret >= text.length())
            return;

        float caretX = textX + font.getWidth(text.substring(0, caret), fontSize);
        font.drawText("|", caretX, textY, fontSize, color, Alignment.CENTER_TOP, true);
    }

    private void renderSelectionHighlight(
            NVGU vg,
            boolean active,
            NVGFont font,
            float fontSize,
            float textX,
            float textY,
            float maxWidth
    ) {
        if (!active || !hasTextSelection())
            return;

        String text = getFocusedText();
        if (text == null || text.isEmpty())
            return;

        int start = Math.clamp(textSelectionStart(), 0, text.length());
        int end = Math.clamp(textSelectionEnd(), start, text.length());
        if (start >= end)
            return;

        float startX = textX + font.getWidth(text.substring(0, start), fontSize);
        float endX = textX + font.getWidth(text.substring(0, end), fontSize);
        float left = Math.max(textX, startX);
        float right = Math.min(textX + Math.max(0f, maxWidth), endX);
        if (right <= left)
            return;

        vg.roundedRectangle(
                left - u(1f),
                textY - u(1f),
                right - left + u(2f),
                fontSize + u(3f),
                u(2f),
                NanoVGTheme.inputFocus(72)
        );
    }

    private boolean caretVisible() {
        return (System.currentTimeMillis() / 480L) % 2L == 0L;
    }

    private static Color alpha(int red, int green, int blue, int alpha) {
        return new Color(red, green, blue, Math.clamp(alpha, 0, 255));
    }

    private static Color mix(Color start, Color end, float progress) {
        progress = Math.clamp(progress, 0f, 1f);
        return new Color(
                Math.clamp(Math.round(start.getRed() + (end.getRed() - start.getRed()) * progress), 0, 255),
                Math.clamp(Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * progress), 0, 255),
                Math.clamp(Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * progress), 0, 255),
                Math.clamp(Math.round(start.getAlpha() + (end.getAlpha() - start.getAlpha()) * progress), 0, 255)
        );
    }


    private float settingRowGap() {
        return SET_GAP;
    }


    private float settingRowHeight(SettingValue<?> setting, float rowWidth) {
        if (setting instanceof BooleanValue value)
            return wrappedSettingRowHeight(value.name, booleanLabelMaxWidth(rowWidth));
        if (setting instanceof ListValue value) {
            float labelHeight = wrappedSettingRowHeight(value.name, listLabelMaxWidth(value, rowWidth));
            if (value.get() == null)
                return labelHeight;
            int valueLines = wrappedLineCount(value.get(), NVGFonts.INTER_MEDIUM, SET_FONT, listCurrentValueWidth(rowWidth));
            return Math.max(labelHeight, SET_ROW + (valueLines - 1) * SET_LINE);
        }
        if (setting instanceof KeyBindValue)
            return wrappedSettingRowHeight(setting.name, keybindLabelMaxWidth(rowWidth));
        float labelHeight = wrappedSettingRowHeight(setting.name, settingLabelMaxWidth(setting, rowWidth));
        return switch (setting) {
            case FloatValue ignored -> labelHeight + SET_BAR_SPACE;
            case IntValue ignored -> labelHeight + SET_BAR_SPACE;
            case ColorValue ignored -> labelHeight + SET_BAR_SPACE;
            case TextValue ignored -> labelHeight + u(19f);
            default -> labelHeight;
        };
    }


    private float settingLabelMaxWidth(SettingValue<?> setting, float rowWidth) {
        float reserve = switch (setting) {
            case FloatValue ignored -> u(56f);
            case IntValue ignored -> u(48f);
            case ColorValue ignored -> u(74f);
            case TextValue ignored -> 0f;
            default -> NVGFonts.INTER.getWidth("Unsupported", SET_FONT) + u(6f);
        };
        return Math.max(u(36f), rowWidth - reserve - SET_PAD * 2f);
    }


    private float keybindLabelMaxWidth(float rowWidth) {
        float valueWidth = NVGFonts.INTER_MEDIUM.getWidth("Press key...", SET_FONT);
        return Math.max(u(36f), rowWidth - valueWidth - SET_PAD * 2f - u(6f));
    }


    private float listCurrentValueWidth(float rowWidth) {
        return Math.max(u(20f), rowWidth * 0.5f - SET_PAD);
    }


    private float booleanLabelMaxWidth(float rowWidth) {
        return Math.max(u(36f), rowWidth - SET_SWITCH_WIDTH - SET_PAD * 2f - u(6f));
    }


    private float listOptionWidth(float rowWidth) {
        return Math.max(u(40f), rowWidth);
    }


    private float listLabelMaxWidth(ListValue value, float rowWidth) {
        float valueWidth = value.get() == null ? 0f
                : Math.min(NVGFonts.INTER_MEDIUM.getWidth(value.get(), SET_FONT), listCurrentValueWidth(rowWidth));
        return Math.max(u(36f), rowWidth - valueWidth - SET_PAD * 2f - u(10f));
    }

    private float[] listOptionHeights(ListValue value, float optionWidth) {
        float[] heights = new float[value.values.length];
        for (int i = 0; i < value.values.length; i++)
            heights[i] = listOptionRowHeight(value.values[i], optionWidth);
        return heights;
    }

    private float listOptionsTotalHeight(float[] heights) {
        float total = 0f;
        for (float height : heights)
            total += height;
        return total;
    }


    private float listOptionRowHeight(String option, float optionWidth) {
        int lines = wrapListOption(option, optionWidth - SET_PAD * 2f).size();
        return Math.max(u(17f), u(4f) + lines * SET_LINE);
    }


    private void drawWrappedListOption(String option, float x, float y, float maxWidth, boolean selected) {
        NVGFont optionFont = selected ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
        Color color = selected ? NanoVGTheme.TEXT : NanoVGTheme.muted(240);
        List<String> lines = wrapListOption(option, maxWidth);
        for (int i = 0; i < lines.size(); i++)
            optionFont.drawText(lines.get(i), x, y + i * SET_LINE, SET_FONT, color, Alignment.RIGHT_TOP, true);
    }


    private static List<String> wrapListOption(String option, float maxWidth) {
        return wrapTextToWidth(option == null ? "" : option, NVGFonts.INTER_MEDIUM, SET_FONT, Math.max(u(20f), maxWidth));
    }


    private float wrappedSettingRowHeight(String label, float maxWidth) {
        int lines = wrapSettingLabel(label, maxWidth).size();
        return SET_ROW + (lines - 1) * SET_LINE;
    }


    private void drawWrappedSettingLabel(String label, float x, float y, float maxWidth) {
        List<String> lines = wrapSettingLabel(label, maxWidth);
        for (int i = 0; i < lines.size(); i++)
            NVGFonts.INTER.drawText(lines.get(i), x, y + i * SET_LINE, SET_FONT, NanoVGTheme.TEXT, Alignment.LEFT_TOP, true);
    }


    private static List<String> wrapSettingLabel(String label, float maxWidth) {
        return wrapTextToWidth(label == null ? "" : label, NVGFonts.INTER, SET_FONT, Math.max(u(20f), maxWidth));
    }

    private static List<String> wrapTextToWidth(String text, NVGFont font, float size, float maxWidth) {
        WrapKey key = new WrapKey(text, font, size, maxWidth);
        List<String> cached = WRAP_CACHE.get(key);
        if (cached != null)
            return cached;

        List<String> lines = Collections.unmodifiableList(computeWrappedLines(text, font, size, maxWidth));
        if (WRAP_CACHE.size() >= WRAP_CACHE_LIMIT)
            WRAP_CACHE.clear();
        WRAP_CACHE.put(key, lines);
        return lines;
    }

    private static List<String> computeWrappedLines(String text, NVGFont font, float size, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        int index = 0;
        while (index <= normalized.length()) {
            int lineBreak = normalized.indexOf('\n', index);
            if (lineBreak < 0)
                lineBreak = normalized.length();
            wrapParagraph(normalized.substring(index, lineBreak), font, size, maxWidth, lines);
            if (lineBreak >= normalized.length())
                break;
            index = lineBreak + 1;
        }

        return lines.isEmpty() ? Collections.singletonList("") : lines;
    }

    private static void wrapParagraph(String paragraph, NVGFont font, float size, float maxWidth, List<String> lines) {
        if (paragraph.isEmpty()) {
            lines.add("");
            return;
        }
        if (font.getWidth(paragraph, size) <= maxWidth) {
            lines.add(paragraph);
            return;
        }

        int start = 0;
        while (start < paragraph.length()) {
            int end = findWrapEnd(paragraph, start, font, size, maxWidth);
            lines.add(paragraph.substring(start, end).strip());
            start = end;
            while (start < paragraph.length() && Character.isWhitespace(paragraph.charAt(start)))
                start++;
        }
    }

    private static int findWrapEnd(String text, int start, NVGFont font, float size, float maxWidth) {
        int bestFit = start + 1;
        int lastWhitespace = -1;

        for (int end = start + 1; end <= text.length(); end++) {
            if (font.getWidth(text.substring(start, end), size) > maxWidth)
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

    private static float drawWrappedText(NVGFont font, String text, float x, float y, float size, float maxWidth, float lineHeight, Color color, Alignment alignment) {
        List<String> lines = wrapTextToWidth(text == null ? "" : text, font, size, Math.max(u(20f), maxWidth));
        for (int i = 0; i < lines.size(); i++)
            font.drawText(lines.get(i), x, y + i * lineHeight, size, color, alignment, true);
        return lines.size() * lineHeight;
    }

    private static int wrappedLineCount(String text, NVGFont font, float size, float maxWidth) {
        return wrapTextToWidth(text == null ? "" : text, font, size, Math.max(u(20f), maxWidth)).size();
    }

    private void renderInputText(NVGU vg, TextFocus focus, Object target, boolean active, String text, float fontSize, float textX, float textY, float selectionY, float maxWidth, Rect box, Color color) {
        float scroll = inputScroll(text, active, fontSize, maxWidth);
        float shiftedX = textX - scroll;
        rememberTextField(focus, target, shiftedX, fontSize, maxWidth + scroll);
        vg.scissor(textX - u(2f), box.y, maxWidth + u(4f), box.height, () -> {
            renderSelectionHighlight(vg, active, NVGFonts.INTER, fontSize, shiftedX, selectionY, maxWidth + scroll);
            NVGFonts.INTER.drawText(text, shiftedX, textY, fontSize, color, Alignment.LEFT_TOP, true);
            renderInlineCaret(vg, active, NVGFonts.INTER, fontSize, shiftedX, textY, color);
        });
    }

    private float inputScroll(String text, boolean active, float fontSize, float maxWidth) {
        float total = NVGFonts.INTER.getWidth(text, fontSize);
        if (!active || total <= maxWidth)
            return 0f;

        String focused = getFocusedText();
        int caret = Math.clamp(textCaret, 0, focused.length());
        float caretX = NVGFonts.INTER.getWidth(focused.substring(0, caret), fontSize) + u(6f);
        return Math.clamp(caretX - maxWidth, 0f, total - maxWidth);
    }

    private static int normalizeKey(int key) {
        return key == Keys.KEY_ESCAPE || key == Keys.KEY_BACKSPACE || key == Keys.KEY_DELETE
                ? Keys.KEY_UNKNOWN
                : key;
    }

    private static String keyName(int key) {
        if (key == Keys.KEY_UNKNOWN)
            return "NONE";
        if (Keys.isMouseKey(key))
            return Keys.mouseKeyName(key);

        String name = Keys.printableKeyName(key);
        if (name != null)
            return name.toUpperCase(Locale.ROOT);

        return switch (key) {
            case Keys.KEY_SPACE -> "SPACE";
            case Keys.KEY_ESCAPE -> "ESCAPE";
            case Keys.KEY_ENTER -> "ENTER";
            case Keys.KEY_TAB -> "TAB";
            case Keys.KEY_BACKSPACE -> "BACKSPACE";
            case Keys.KEY_INSERT -> "INSERT";
            case Keys.KEY_DELETE -> "DELETE";
            case Keys.KEY_RIGHT -> "RIGHT";
            case Keys.KEY_LEFT -> "LEFT";
            case Keys.KEY_DOWN -> "DOWN";
            case Keys.KEY_UP -> "UP";
            case Keys.KEY_PAGE_UP -> "PAGE UP";
            case Keys.KEY_PAGE_DOWN -> "PAGE DOWN";
            case Keys.KEY_HOME -> "HOME";
            case Keys.KEY_END -> "END";
            case Keys.KEY_LEFT_SHIFT -> "LEFT SHIFT";
            case Keys.KEY_LEFT_CONTROL -> "LEFT CTRL";
            case Keys.KEY_LEFT_ALT -> "LEFT ALT";
            case Keys.KEY_RIGHT_SHIFT -> "RIGHT SHIFT";
            case Keys.KEY_RIGHT_CONTROL -> "RIGHT CTRL";
            case Keys.KEY_RIGHT_ALT -> "RIGHT ALT";
            case Keys.KEY_F1 -> "F1";
            case Keys.KEY_F2 -> "F2";
            case Keys.KEY_F3 -> "F3";
            case Keys.KEY_F4 -> "F4";
            case Keys.KEY_F5 -> "F5";
            case Keys.KEY_F6 -> "F6";
            case Keys.KEY_F7 -> "F7";
            case Keys.KEY_F8 -> "F8";
            case Keys.KEY_F9 -> "F9";
            case Keys.KEY_F10 -> "F10";
            case Keys.KEY_F11 -> "F11";
            case Keys.KEY_F12 -> "F12";
            default -> String.valueOf(key);
        };
    }

    private enum ConfigTab {
        MODULE,
        BIND,
        ONLINE
    }

    private enum TextFocus {
        NONE,
        SEARCH,
        SETTING,
        NUMBER,
        CONFIG_NAME,
        CONFIG_RENAME
    }

    private enum ControlType {
        CONFIG_TAB,
        CONFIG_NAME,
        CONFIG_LOAD,
        CONFIG_RENAME,
        CONFIG_DELETE,
        SHOW_ON_ARRAY,
        MODULE_ENABLED,
        MODULE_KEY,
        BOOLEAN,
        LIST_VALUE,
        LIST_OPTION,
        FLOAT_INPUT,
        INT_INPUT,
        FLOAT_SLIDER,
        INT_SLIDER,
        COLOR_SLIDER,
        TEXT_VALUE,
        KEY_VALUE
    }

    private record ColumnRow(Module module, String label, float width) {
    }

    private static final class GuiColumn {
        private final String title;
        private final List<ColumnRow> rows = new ArrayList<>();
        private float x;
        private float y;
        private float width;
        private float rowsTop;
        private Rect bounds;

        private GuiColumn(String title) {
            this.title = title;
        }
    }

    private record ModuleRowBounds(Module module, Rect rect) {
    }

    private record ConfigEntry(String name, ConfigTab type, boolean current) {
    }

    private record ControlBounds(ControlType type, Rect rect, Object target, Object owner, int index) {
    }

    private record SliderDrag(ControlBounds control) {
    }

    private record ScrollbarDrag(float offsetY) {
    }

    private record PendingControlGlass(float x, float y, float width, float height, float radius, float alphaAtQueue, boolean dark, float brightness) {
    }

    private record WrapKey(String text, NVGFont font, float size, float maxWidth) {
    }

    private record TextFieldLayout(TextFocus focus, Object target, float textX, float fontSize, float maxWidth) {
    }

    private record Rect(float x, float y, float width, float height) {
        boolean contains(float px, float py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }

        float centerX() {
            return x + width / 2f;
        }

        float centerY() {
            return y + height / 2f;
        }

        Rect expand(float horizontal, float vertical) {
            return new Rect(x - horizontal, y - vertical, width + horizontal * 2f, height + vertical * 2f);
        }

        Rect contract(float horizontal, float vertical) {
            return new Rect(x + horizontal, y + vertical, Math.max(1f, width - horizontal * 2f), Math.max(1f, height - vertical * 2f));
        }

        Rect intersect(Rect other) {
            float left = Math.max(x, other.x);
            float top = Math.max(y, other.y);
            float right = Math.min(x + width, other.x + other.width);
            float bottom = Math.min(y + height, other.y + other.height);
            if (right <= left || bottom <= top)
                return null;
            return new Rect(left, top, right - left, bottom - top);
        }
    }
}
