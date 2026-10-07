package com.instrumentalist.krs.screen;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.configs.ConfigManager;
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
import org.lwjgl.glfw.GLFW;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.Border;
import org.nvgu.util.NVGFont;

import java.awt.Color;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.function.Consumer;

public class NanoVGClickGuiScreen extends Screen {
    private static final float LAYOUT = 1.35f;
    private static final float PANEL_HEADER_HEIGHT = u(42f);
    private static final float PANEL_FOOTER_HEIGHT = u(22f);
    private static final float PANEL_PADDING = u(10f);
    private static final float CATEGORY_SIDEBAR_WIDTH = u(126f);
    private static final float CATEGORY_CONTENT_GAP = u(10f);
    private static final float SETTINGS_PANEL_HEADER_HEIGHT = u(44f);
    private static final float SETTINGS_DOCK_GAP = u(8f);
    private static final float ROW_HEIGHT = u(32f);
    private static final float SETTING_ROW_HEIGHT = u(27f);
    private static final float SETTING_ROW_STEP = u(34f);
    private static final float SWITCH_WIDTH = u(31f);
    private static final float SWITCH_HEIGHT = u(15f);
    private static final int ROOT_PANEL_BACKGROUND_ALPHA_OFFSET = 70;
    private static final int COMPACT_BACKGROUND_ALPHA_OFFSET = 35;

    private static NanoVGClickGuiScreen detachedClosingScreen;
    private static Module rememberedSettingsPanelModule;
    private static ModuleCategory rememberedSelectedCategory = ModuleCategory.Combat;
    private static boolean rememberedConfigView;
    private static ConfigTab rememberedConfigTab = ConfigTab.MODULE;
    private static Module rememberedOpenedListModule;
    private static ListValue rememberedOpenedListValue;
    private static float rememberedListScroll;
    private static float rememberedTargetListScroll;
    private static float rememberedSettingsPanelScroll;
    private static float rememberedTargetSettingsPanelScroll;
    private static final Map<ModuleCategory, ScrollState> rememberedCategoryScrolls = new EnumMap<>(ModuleCategory.class);

    private final List<TabBounds> tabBounds = new ArrayList<>();
    private final List<ModuleRowBounds> moduleRows = new ArrayList<>();
    private final List<ControlBounds> controls = new ArrayList<>();
    private final List<Rect> inputClips = new ArrayList<>();
    private final List<TextFieldLayout> textFields = new ArrayList<>();
    private final List<PendingControlGlass> pendingControlGlass = new ArrayList<>();
    private final List<Runnable> pendingControlForegrounds = new ArrayList<>();
    private boolean deferControlChrome;
    private final Map<ModuleCategory, ScrollState> categoryScrolls = new EnumMap<>(ModuleCategory.class);
    private final Map<Module, List<SettingValue<?>>> moduleSettingsCache = new IdentityHashMap<>();
    private final Map<Module, Float> expandedSettingsHeightCache = new IdentityHashMap<>();
    private final Map<Module, Boolean> renderableSettingsCache = new IdentityHashMap<>();
    private final Map<Module, Float> enabledAnimations = new IdentityHashMap<>();
    private final Map<Module, Float> expansionAnimations = new IdentityHashMap<>();
    private final Map<ListValue, Float> listDropdownAnimations = new IdentityHashMap<>();
    private final Map<SettingValue<?>, Float> settingVisibilityAnimations = new IdentityHashMap<>();
    private final Map<Object, Float> switchAnimations = new IdentityHashMap<>();
    private final Map<Module, Long> enabledAnimationFrames = new IdentityHashMap<>();
    private final Map<Module, Long> expansionAnimationFrames = new IdentityHashMap<>();
    private final Map<ListValue, Long> listDropdownAnimationFrames = new IdentityHashMap<>();
    private final Map<SettingValue<?>, Long> settingVisibilityAnimationFrames = new IdentityHashMap<>();
    private final Map<Object, Long> switchAnimationFrames = new IdentityHashMap<>();
    private final Map<String, Float> animations = new HashMap<>();
    private final Consumer<NVGU> nanoVgRenderer = this::render;
    private final Screen returnScreen;

    private ModuleCategory selectedCategory = ModuleCategory.Combat;
    private boolean configView;
    private ConfigTab selectedConfigTab = ConfigTab.MODULE;
    private String searchQuery = "";
    private String newConfigName = "";
    private float listScroll;
    private float targetListScroll;
    private float scrollVelocity;
    private float maxListScroll;
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
    private Rect closeRect = new Rect(0f, 0f, 0f, 0f);
    private Rect searchRect = new Rect(0f, 0f, 0f, 0f);
    private Rect searchClearRect = new Rect(0f, 0f, 0f, 0f);
    private Rect listViewport = new Rect(0f, 0f, 0f, 0f);
    private Rect scrollbarTrackRect = new Rect(0f, 0f, 0f, 0f);
    private Rect scrollbarThumbRect = new Rect(0f, 0f, 0f, 0f);
    private Rect settingsPanelRect = new Rect(0f, 0f, 0f, 0f);
    private Rect settingsPanelCloseRect = new Rect(0f, 0f, 0f, 0f);
    private Rect settingsPanelViewport = new Rect(0f, 0f, 0f, 0f);
    private Rect settingsPanelScrollbarTrackRect = new Rect(0f, 0f, 0f, 0f);
    private Rect settingsPanelScrollbarThumbRect = new Rect(0f, 0f, 0f, 0f);
    private TextFocus textFocus = TextFocus.NONE;
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
    private ScrollbarDrag activeScrollbar;
    private ScrollbarDrag activeSettingsPanelScrollbar;
    private Module settingsPanelModule;
    private Module settingsPanelRenderModule;
    private int settingsPanelControlStartIndex;
    private Module hoveredModule;
    private Module tooltipHoverModule;
    private long hoveredModuleStartNanos;
    private String tooltipText = "";
    private float settingsDockReveal;
    private float tabMarkY = Float.NaN;

    public NanoVGClickGuiScreen() {
        this(null);
    }

    public NanoVGClickGuiScreen(Screen returnScreen) {
        super(Component.literal("NanoVG Click GUI"));
        this.returnScreen = returnScreen instanceof NanoVGClickGuiScreen ? null : returnScreen;
        detachedClosingScreen = null;
        restoreUiState();
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
        Client.nanoVgManager.load(nanoVgRenderer);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing)
            return true;

        float mouseX = NanoVGManager.toScaledMouseX(event.x());
        float mouseY = NanoVGManager.toScaledMouseY(event.y());
        int button = event.button();

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && closeRect.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }

        if (settingsPanelModule != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && settingsPanelCloseRect.contains(mouseX, mouseY)) {
                closeSettingsPanel();
                return true;
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && maxSettingsPanelScroll > 0f
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
                    && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                    || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE)) {
                for (int i = settingsPanelControlStartIndex; i < controls.size(); i++) {
                    ControlBounds control = controls.get(i);
                    if (control.rect.contains(mouseX, mouseY) && handleControlClick(control, mouseX, button))
                        return true;
                }

                clearTextFocus();
                closeListDropdown();
                return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !searchQuery.isBlank() && searchClearRect.contains(mouseX, mouseY)) {
            clearSearch();
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && maxListScroll > 0f && scrollbarTrackRect.contains(mouseX, mouseY)) {
            closeListDropdown();
            clearTextFocus();
            float offsetY = scrollbarThumbRect.contains(mouseX, mouseY) ? mouseY - scrollbarThumbRect.y : scrollbarThumbRect.height / 2f;
            activeScrollbar = new ScrollbarDrag(offsetY);
            updateScrollbarDrag(mouseY);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            for (ControlBounds control : controls) {
                if (control.rect.contains(mouseX, mouseY) && handleControlClick(control, mouseX, button))
                    return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && searchRect.contains(mouseX, mouseY)) {
            focusSearch();
            beginTextSelection(mouseX);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (TabBounds tab : tabBounds) {
                if (tab.rect.contains(mouseX, mouseY)) {
                    storeCurrentCategoryScroll();
                    if (tab.isConfig()) {
                        configView = true;
                        selectedConfigTab = tab.configTab();
                        searchQuery = "";
                        resetListScroll();
                    } else {
                        selectedCategory = tab.category();
                        configView = false;
                        searchQuery = "";
                        restoreCategoryScroll(selectedCategory);
                    }
                    clearInteractionState();
                    return true;
                }
            }
        }

        for (ModuleRowBounds row : moduleRows) {
            if (!row.rect.contains(mouseX, mouseY)) continue;

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                closeListDropdown();
                pressedModule = row.module;
                row.module.toggle();
                return true;
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                closeListDropdown();
                toggleSettingsPanel(row.module);
                return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && openedListValue != null) {
            closeListDropdown();
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            clearTextFocus();
            closeListDropdown();
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

        if (activeScrollbar != null) {
            updateScrollbarDrag(NanoVGManager.toScaledMouseY(event.y()));
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
        activeScrollbar = null;
        activeSettingsPanelScrollbar = null;
        draggingTextSelection = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        float scaledX = NanoVGManager.toScaledMouseX(mouseX);
        float scaledY = NanoVGManager.toScaledMouseY(mouseY);

        if (nudgeHoveredColorSlider(scaledX, scaledY, vertical))
            return true;

        if (settingsPanelModule != null
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

        if (listViewport.contains(scaledX, scaledY) || scrollbarTrackRect.contains(scaledX, scaledY)) {
            float previousTarget = targetListScroll;
            float scrollStep = Math.clamp(listViewport.height * 0.11f, u(36f), u(64f));
            targetListScroll = Math.clamp(targetListScroll - (float) vertical * scrollStep, 0f, maxListScroll);
            scrollVelocity = Math.clamp(scrollVelocity + (targetListScroll - previousTarget) * 0.08f, -22f, 22f);
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
            if (key != GLFW.GLFW_KEY_ESCAPE)
                bindingModule.key = normalizeKey(key);
            bindingModule = null;
            return true;
        }

        if (bindingValue != null) {
            if (key != GLFW.GLFW_KEY_ESCAPE)
                bindingValue.set(normalizeKey(key));
            bindingValue = null;
            return true;
        }

        boolean commandModifier = (event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (key == GLFW.GLFW_KEY_F && commandModifier) {
            focusSearch();
            return true;
        }

        if (textFocus != TextFocus.NONE) {
            if (key == GLFW.GLFW_KEY_ESCAPE && textFocus == TextFocus.NUMBER) {
                cancelNumberInput();
                return true;
            }

            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (textFocus == TextFocus.CONFIG_NAME)
                    createConfigFromInput();
                else if (textFocus == TextFocus.NUMBER) {
                    if (commitNumberInput())
                        resetTextFocus();
                }
                else
                    clearTextFocus();
                return true;
            }

            if (key == GLFW.GLFW_KEY_A && commandModifier) {
                selectAllFocusedText();
                return true;
            }

            if (key == GLFW.GLFW_KEY_C && commandModifier) {
                copyFocusedTextToClipboard();
                return true;
            }

            if (key == GLFW.GLFW_KEY_V && commandModifier) {
                pasteClipboardIntoFocusedText();
                return true;
            }

            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                removeLastFocusedCharacter();
                return true;
            }

            if (key == GLFW.GLFW_KEY_DELETE) {
                deleteForwardFocusedCharacter();
                return true;
            }

            boolean shift = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            if (key == GLFW.GLFW_KEY_LEFT) {
                moveFocusedCaret(-1, shift);
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT) {
                moveFocusedCaret(1, shift);
                return true;
            }
        }

        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            cycleCategory(key == GLFW.GLFW_KEY_DOWN ? 1 : -1);
            return true;
        }

        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN
                || key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
            scrollWithKeyboard(key);
            return true;
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
        return false;
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
        // Server-driven screen changes bypass onClose(). Persist the current layout
        // here as well so reopening ClickGui restores the exact same view state.
        rememberUiState();
        activeSlider = null;
        pressedControl = null;
        pressedModule = null;
        activeScrollbar = null;
        activeSettingsPanelScrollbar = null;
        bindingModule = null;
        bindingValue = null;
        clearTextFocus();
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
        activeScrollbar = null;
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
        categoryScrolls.clear();
        categoryScrolls.putAll(rememberedCategoryScrolls);
        settingsPanelModule = rememberedSettingsPanelModule;
        settingsPanelRenderModule = rememberedSettingsPanelModule;
        selectedCategory = rememberedSelectedCategory == null ? ModuleCategory.Combat : rememberedSelectedCategory;
        configView = rememberedConfigView;
        selectedConfigTab = rememberedConfigTab == null ? ConfigTab.MODULE : rememberedConfigTab;
        openedListModule = rememberedOpenedListModule;
        openedListValue = rememberedOpenedListValue;
        ScrollState selectedScroll = !configView && selectedCategory != null ? categoryScrolls.get(selectedCategory) : null;
        if (selectedScroll != null) {
            listScroll = selectedScroll.listScroll();
            targetListScroll = selectedScroll.targetListScroll();
        } else {
            targetListScroll = rememberedTargetListScroll;
            listScroll = rememberedListScroll;
            if (!configView && selectedCategory != null)
                categoryScrolls.put(selectedCategory, new ScrollState(listScroll, targetListScroll));
        }
        settingsPanelScroll = rememberedSettingsPanelScroll;
        targetSettingsPanelScroll = rememberedTargetSettingsPanelScroll;
        restoreSettingsPanelAnimations();
    }

    private void restoreSettingsPanelAnimations() {
        if (settingsPanelModule != null) {
            animations.put("settings-panel-open", 1f);
            settingsDockReveal = 1f;
            expansionAnimations.put(settingsPanelModule, 1f);

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
        storeCurrentCategoryScroll();
        rememberedSettingsPanelModule = settingsPanelModule;
        rememberedSelectedCategory = selectedCategory;
        rememberedConfigView = configView;
        rememberedConfigTab = selectedConfigTab;
        rememberedOpenedListModule = openedListModule;
        rememberedOpenedListValue = openedListValue;
        rememberedListScroll = listScroll;
        rememberedTargetListScroll = targetListScroll;
        rememberedSettingsPanelScroll = settingsPanelScroll;
        rememberedTargetSettingsPanelScroll = targetSettingsPanelScroll;
        rememberedCategoryScrolls.clear();
        rememberedCategoryScrolls.putAll(categoryScrolls);
    }

    private void render(NVGU vg) {
        float delta = updateDelta();
        frameDelta = delta;
        animationFrame++;
        clearFrameLayoutCaches();
        openProgress = stepTowards(openProgress, closing ? 0f : 1f, 0.08f * delta);
        updateSmoothScroll(delta);
        updateSettingsPanelSmoothScroll(delta);

        if (closing && openProgress <= 0.001f) {
            finishClose();
            return;
        }

        tabBounds.clear();
        moduleRows.clear();
        controls.clear();
        textFields.clear();
        pendingControlGlass.clear();
        pendingControlForegrounds.clear();
        deferControlChrome = false;
        hoveredModule = null;
        tooltipText = "";
        if (settingsPanelModule != null)
            settingsPanelRenderModule = settingsPanelModule;
        settingsDockReveal = settingsPanelReveal();
        if (settingsDockReveal <= 0f)
            settingsPanelRenderModule = null;

        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        Rect panel = panelBounds(screenWidth, screenHeight);
        updateScaledMouse();
        boolean staticFallbackBackdrop = !isInGame() && !hasBackgroundScreen();

        if (staticFallbackBackdrop)
            renderTitleBackdrop(vg, screenWidth, screenHeight);

        vg.globalAlpha(easeOut(openProgress), () -> {
            renderPanelEffects(vg, panel.x, panel.y, panel.width, panel.height);
            renderPanel(vg, panel.x, panel.y, panel.width, panel.height);
            renderSettingsPanel(vg, panel);
            renderHoverTooltip(vg);
            applyHoverCursor();
        });
    }

    private static float u(float value) {
        return value * LAYOUT;
    }

    private static float font(float size) {
        return Math.round(size * LAYOUT);
    }

    private static Rect panelBounds(float screenWidth, float screenHeight) {
        float panelWidth = Math.min(u(860f), Math.max(u(560f), screenWidth - u(36f)));
        float panelHeight = Math.min(u(590f), Math.max(u(390f), screenHeight - u(36f)));
        return new Rect((screenWidth - panelWidth) / 2f, (screenHeight - panelHeight) / 2f, panelWidth, panelHeight);
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

    private void storeCurrentCategoryScroll() {
        if (configView || !searchQuery.isBlank() || selectedCategory == null)
            return;

        categoryScrolls.put(selectedCategory, new ScrollState(listScroll, targetListScroll));
    }

    private void restoreCategoryScroll(ModuleCategory category) {
        if (category == null) {
            resetListScroll();
            return;
        }

        ScrollState state = categoryScrolls.get(category);
        if (state == null) {
            resetListScroll();
            return;
        }

        listScroll = state.listScroll();
        targetListScroll = state.targetListScroll();
        scrollVelocity = 0f;
    }

    private void resetListScroll() {
        targetListScroll = 0f;
        listScroll = 0f;
        scrollVelocity = 0f;
    }

    private float updateDelta() {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1f : Math.clamp((now - lastFrameNanos) / 16_666_666f, 0.25f, 3f);
        lastFrameNanos = now;
        return delta;
    }

    private void updateSmoothScroll(float delta) {
        if (listViewport.height <= 0f)
            return;

        targetListScroll = Math.clamp(targetListScroll, 0f, maxListScroll);

        if (activeScrollbar != null) {
            scrollVelocity = 0f;
            listScroll = targetListScroll;
            return;
        }

        if (maxListScroll <= 0f) {
            targetListScroll = 0f;
            listScroll = 0f;
            scrollVelocity = 0f;
            return;
        }

        float distance = targetListScroll - listScroll;
        scrollVelocity += distance * 0.045f * delta;
        scrollVelocity *= (float) Math.pow(0.74f, delta);
        scrollVelocity = Math.clamp(scrollVelocity, -42f, 42f);

        listScroll += scrollVelocity * delta;
        listScroll = approach(listScroll, targetListScroll, 1f - (float) Math.pow(0.91f, delta));

        if (listScroll < 0f || listScroll > maxListScroll) {
            listScroll = Math.clamp(listScroll, 0f, maxListScroll);
            scrollVelocity = 0f;
        }

        if (Math.abs(targetListScroll - listScroll) < 0.06f && Math.abs(scrollVelocity) < 0.06f) {
            listScroll = targetListScroll;
            scrollVelocity = 0f;
        }
    }

    private void updateSettingsPanelSmoothScroll(float delta) {
        if (settingsPanelModule == null || settingsPanelViewport.height <= 0f)
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
            CustomTitleScreen.bgOffsetX = approach(CustomTitleScreen.bgOffsetX, screenMouseX, Math.clamp(0.03f * frameDelta, 0f, 1f));
            CustomTitleScreen.bgOffsetY = approach(CustomTitleScreen.bgOffsetY, screenMouseY, Math.clamp(0.03f * frameDelta, 0f, 1f));

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

    private void renderPanelEffects(NVGU vg, float x, float y, float width, float height) {
        vg.beginEffectBatch();
        NanoVGTheme.renderPanelEffects(vg, x, y, width, height, u(NanoVGTheme.RADIUS_PANEL), 1f);
        vg.flushEffectBatch();
    }

    private void renderPanel(NVGU vg, float x, float y, float width, float height) {
        NanoVGTheme.renderPanel(vg, x, y, width, height, u(NanoVGTheme.RADIUS_PANEL), 1f, ROOT_PANEL_BACKGROUND_ALPHA_OFFSET);

        renderHeader(vg, x, y, width);
        Rect content = panelContentBounds(new Rect(x, y, width, height));
        NanoVGTheme.renderCompact(vg, content.x, content.y, CATEGORY_SIDEBAR_WIDTH, content.height, u(NanoVGTheme.RADIUS_HUD), 1f, COMPACT_BACKGROUND_ALPHA_OFFSET);
        renderTabs(vg, content.x, content.y, CATEGORY_SIDEBAR_WIDTH);

        Rect mainContent = mainContentBounds(content);
        float listWidth = mainContent.width - settingsDockReservedWidth(content.width, settingsDockReveal);
        int visibleCount = configView
                ? renderConfigList(vg, mainContent.x, mainContent.y, listWidth, mainContent.height)
                : renderModuleList(vg, mainContent.x, mainContent.y, listWidth, mainContent.height);
        renderFooter(vg, x + PANEL_PADDING, y + height - PANEL_FOOTER_HEIGHT - u(4f), width - PANEL_PADDING * 2f, visibleCount);
    }

    private void renderSettingsPanel(NVGU vg, Rect parentPanel) {
        settingsPanelControlStartIndex = controls.size();

        Module module = settingsPanelRenderModule;
        float reveal = settingsDockReveal;
        if (module == null || reveal <= 0f) {
            settingsPanelRect = new Rect(0f, 0f, 0f, 0f);
            settingsPanelCloseRect = new Rect(0f, 0f, 0f, 0f);
            settingsPanelViewport = new Rect(0f, 0f, 0f, 0f);
            settingsPanelScrollbarTrackRect = new Rect(0f, 0f, 0f, 0f);
            settingsPanelScrollbarThumbRect = new Rect(0f, 0f, 0f, 0f);
            maxSettingsPanelScroll = 0f;
            return;
        }

        Rect content = panelContentBounds(parentPanel);
        Rect mainContent = mainContentBounds(content);
        float dockWidth = settingsDockWidth(content.width);
        float slide = (1f - reveal) * dockWidth;
        settingsPanelRect = new Rect(
                mainContent.x + mainContent.width - dockWidth + slide,
                mainContent.y,
                dockWidth,
                mainContent.height
        );
        settingsPanelCloseRect = new Rect(
                settingsPanelRect.x + settingsPanelRect.width - u(28f),
                settingsPanelRect.y + u(8f),
                u(20f),
                u(20f)
        );

        Rect dockClip = mainContent;
        float settingsListX = settingsPanelRect.x + u(6f);
        float settingsListY = settingsPanelRect.y + SETTINGS_PANEL_HEADER_HEIGHT + u(4f);
        float settingsListWidth = settingsPanelRect.width - u(12f);
        float settingsListHeight = settingsPanelRect.height - SETTINGS_PANEL_HEADER_HEIGHT - u(10f);
        settingsPanelViewport = new Rect(
                settingsListX + u(5f),
                settingsListY + u(5f),
                settingsListWidth - u(16f),
                settingsListHeight - u(10f)
        );
        float settingsContentHeight = expandedSettingsHeight(module);
        maxSettingsPanelScroll = Math.max(0f, settingsContentHeight - settingsPanelViewport.height);
        targetSettingsPanelScroll = Math.clamp(targetSettingsPanelScroll, 0f, maxSettingsPanelScroll);
        settingsPanelScroll = Math.clamp(settingsPanelScroll, 0f, maxSettingsPanelScroll);
        if (maxSettingsPanelScroll <= 0f)
            settingsPanelScrollVelocity = 0f;

        vg.globalAlpha(reveal, () -> {
            deferControlChrome = true;
            try {
                withInputClip(dockClip, () -> vg.scissor(dockClip.x, dockClip.y, dockClip.width, dockClip.height, () -> {
                    NanoVGTheme.renderPanel(
                            vg,
                            settingsPanelRect.x,
                            settingsPanelRect.y,
                            settingsPanelRect.width,
                            settingsPanelRect.height,
                            u(8f),
                            1f
                    );
                    vg.rectangle(
                            settingsPanelRect.x + 1f,
                            settingsPanelRect.y + SETTINGS_PANEL_HEADER_HEIGHT,
                            settingsPanelRect.width - 2f,
                            u(1f),
                            alpha(255, 255, 255, 28)
                    );

                    Rect enableRect = new Rect(
                            settingsPanelRect.x + u(12f),
                            settingsPanelRect.y + u(14f),
                            SWITCH_WIDTH,
                            SWITCH_HEIGHT
                    );
                    float headerNameX = enableRect.x + enableRect.width + u(8f);
                    float headerFont = font(14f);
                    NVGFonts.INTER.drawText(
                            fitText(module.moduleName, NVGFonts.INTER, headerFont, Math.max(u(40f), settingsPanelCloseRect.x - u(8f) - headerNameX)),
                            headerNameX,
                            settingsPanelRect.y + u(14f),
                            headerFont,
                            NanoVGTheme.TEXT,
                            Alignment.LEFT_TOP,
                            false
                    );
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

                    boolean closeHovered = settingsPanelCloseRect.contains(scaledMouseX, scaledMouseY);
                    float closeProgress = animate("settings-panel-close", closeHovered, 0.18f);
                    vg.roundedRectangle(
                            settingsPanelCloseRect.x,
                            settingsPanelCloseRect.y,
                            settingsPanelCloseRect.width,
                            settingsPanelCloseRect.height,
                            u(5f),
                            mix(alpha(255, 255, 255, 10), alpha(255, 74, 74, 50), closeProgress)
                    );
                    NVGFonts.ICON.drawText(
                            MaterialIcon.CLOSE,
                            settingsPanelCloseRect.centerX(),
                            settingsPanelCloseRect.centerY() - u(1f),
                            font(12f),
                            mix(alpha(176, 186, 196, 230), alpha(255, 195, 195, 245), closeProgress),
                            Alignment.CENTER_MIDDLE,
                            false
                    );

                    NanoVGTheme.renderCompact(vg, settingsListX, settingsListY, settingsListWidth, settingsListHeight, u(NanoVGTheme.RADIUS_HUD), 1f, COMPACT_BACKGROUND_ALPHA_OFFSET);
                    withInputClip(settingsPanelViewport, () -> vg.scissor(
                            settingsPanelViewport.x,
                            settingsPanelViewport.y,
                            settingsPanelViewport.width,
                            settingsPanelViewport.height,
                            () -> renderExpandedSettings(
                                    vg,
                                    module,
                                    settingsPanelViewport.x,
                                    settingsPanelViewport.y - settingsPanelScroll,
                                    settingsPanelViewport.width
                            )
                    ));
                }));
                flushPendingControlGlass(vg);
                vg.scissor(dockClip.x, dockClip.y, dockClip.width, dockClip.height, () -> {
                    drawPendingControlForegrounds(vg);
                    renderSettingsPanelScrollbar(vg, settingsContentHeight);
                });
            } finally {
                deferControlChrome = false;
                pendingControlGlass.clear();
                pendingControlForegrounds.clear();
            }
        });

        if (settingsPanelModule == null) {
            while (controls.size() > settingsPanelControlStartIndex)
                controls.remove(controls.size() - 1);
        }
    }

    private float settingsPanelReveal() {
        float raw = animate("settings-panel-open", settingsPanelModule != null, 0.14f);
        if (settingsPanelModule == null && raw <= 0.02f) {
            animations.put("settings-panel-open", 0f);
            return 0f;
        }

        float reveal = settingsPanelModule != null ? easeOut(raw) : easeIn(raw);
        return reveal <= 0.0005f ? 0f : reveal;
    }

    private static float settingsDockWidth(float contentWidth) {
        return Math.clamp(contentWidth * 0.40f, u(248f), u(332f));
    }

    private static float settingsDockReservedWidth(float contentWidth, float reveal) {
        return (settingsDockWidth(contentWidth) + SETTINGS_DOCK_GAP) * Math.clamp(reveal, 0f, 1f);
    }

    private static Rect panelContentBounds(Rect panel) {
        return new Rect(
                panel.x + PANEL_PADDING,
                panel.y + PANEL_HEADER_HEIGHT + PANEL_PADDING,
                panel.width - PANEL_PADDING * 2f,
                panel.height - PANEL_HEADER_HEIGHT - PANEL_PADDING * 2f - PANEL_FOOTER_HEIGHT
        );
    }

    private static Rect mainContentBounds(Rect content) {
        return new Rect(
                content.x + CATEGORY_SIDEBAR_WIDTH + CATEGORY_CONTENT_GAP,
                content.y,
                content.width - CATEGORY_SIDEBAR_WIDTH - CATEGORY_CONTENT_GAP,
                content.height
        );
    }

    private void renderHeader(NVGU vg, float x, float y, float width) {
        closeRect = new Rect(x + width - u(30f), y + u(11f), u(18f), u(18f));
        searchRect = new Rect(x + width - u(242f), y + u(10f), u(196f), u(20f));

        renderSearch(vg, searchRect);

        boolean closeHovered = closeRect.contains(scaledMouseX, scaledMouseY);
        vg.roundedRectangle(closeRect.x, closeRect.y, closeRect.width, closeRect.height, u(4f), closeHovered ? alpha(255, 90, 90, 120) : alpha(255, 255, 255, 18));
        NVGFonts.ICON.drawText(MaterialIcon.CLOSE, closeRect.centerX(), closeRect.centerY() - u(1f), font(14f), closeHovered ? NanoVGTheme.TEXT : NanoVGTheme.muted(220), Alignment.CENTER_MIDDLE, false);

        vg.rectangle(x + u(10f), y + PANEL_HEADER_HEIGHT - u(1f), width - u(20f), u(1f), alpha(255, 255, 255, 40));
    }

    private void renderSearch(NVGU vg, Rect rect) {
        boolean focused = textFocus == TextFocus.SEARCH;
        boolean hovered = rect.contains(scaledMouseX, scaledMouseY);
        vg.roundedRectangle(rect.x, rect.y, rect.width, rect.height, u(5f), alpha(255, 255, 255, focused ? 30 : hovered ? 23 : 16));
        vg.roundedRectangleBorder(rect.x, rect.y, rect.width, rect.height, u(5f), u(1f), focused ? NanoVGTheme.inputFocus(120) : alpha(255, 255, 255, 30), Border.INSIDE);
        NVGFonts.ICON.drawText(MaterialIcon.SEARCH, rect.x + u(7f), rect.y + u(3f), font(12f), focused ? NanoVGTheme.INPUT_FOCUS : alpha(176, 186, 196, 220), Alignment.LEFT_TOP, false);

        String text = inputText(searchQuery, focused, "Search");
        Color color = searchQuery.isBlank() && !focused ? alpha(120, 130, 140, 205) : alpha(255, 255, 255, 235);
        float textReserve = searchQuery.isBlank() ? u(33f) : u(51f);
        float searchFont = font(11f);
        String visibleText = fitText(text, NVGFonts.INTER, searchFont, rect.width - textReserve);
        rememberTextField(TextFocus.SEARCH, null, rect.x + u(24f), searchFont, rect.width - textReserve);
        renderSelectionHighlight(vg, focused, NVGFonts.INTER, searchFont, rect.x + u(24f), rect.y + u(5f), rect.width - textReserve);
        NVGFonts.INTER.drawText(visibleText, rect.x + u(24f), rect.y + u(4.5f), searchFont, color, Alignment.LEFT_TOP, false);
        renderInlineCaret(vg, focused, NVGFonts.INTER, searchFont, rect.x + u(24f), rect.y + u(4.5f), color);

        if (searchQuery.isBlank()) {
            searchClearRect = new Rect(0f, 0f, 0f, 0f);
            return;
        }

        searchClearRect = new Rect(rect.x + rect.width - u(19f), rect.y + u(2f), u(16f), u(16f));
        boolean clearHovered = searchClearRect.contains(scaledMouseX, scaledMouseY);
        vg.roundedRectangle(searchClearRect.x, searchClearRect.y, searchClearRect.width, searchClearRect.height, u(4f),
                clearHovered ? alpha(255, 255, 255, 28) : alpha(255, 255, 255, 10));
        NVGFonts.ICON.drawText(MaterialIcon.CLOSE, searchClearRect.centerX(), searchClearRect.centerY() - u(1f), font(11f),
                clearHovered ? NanoVGTheme.TEXT : NanoVGTheme.muted(210), Alignment.CENTER_MIDDLE, false);
    }

    private void renderFooter(NVGU vg, float x, float y, float width, int visibleCount) {
        vg.rectangle(x, y - u(2f), width, u(1f), alpha(255, 255, 255, 28));

        String countLabel;
        if (!searchQuery.isBlank())
            countLabel = visibleCount + (visibleCount == 1 ? " result" : " results");
        else if (configView)
            countLabel = visibleCount + (visibleCount == 1 ? " config" : " configs");
        else
            countLabel = visibleCount + (visibleCount == 1 ? " module" : " modules");
        NVGFonts.INTER.drawText(countLabel, x + width - u(2f), y + u(5f), font(10f),
                searchQuery.isBlank() ? NanoVGTheme.muted(220) : NanoVGTheme.text(230),
                Alignment.RIGHT_TOP, false);
    }

    private void renderTabs(NVGU vg, float x, float y, float width) {
        ModuleCategory[] categories = ModuleCategory.values();
        float gap = u(5f);
        float tabHeight = u(22f);
        float tabFont = font(14f);
        Rect selectedRect = null;

        for (int i = 0; i < categories.length; i++) {
            ModuleCategory category = categories[i];
            Rect rect = new Rect(x, y + u(5f) + i * (tabHeight + gap), width, tabHeight);
            tabBounds.add(new TabBounds(category, null, rect));

            if (!configView && searchQuery.isBlank() && selectedCategory == category)
                selectedRect = rect;

            NVGFonts.INTER.drawText(fitText(category.name(), NVGFonts.INTER, tabFont, rect.width - u(20f)), rect.x + u(12f), rect.y + u(4f), tabFont, NanoVGTheme.TEXT, Alignment.LEFT_TOP, false);
        }

        float dividerY = y + u(5f) + categories.length * (tabHeight + gap) + u(2f);
        vg.rectangle(x + u(8f), dividerY, width - u(16f), u(1f), alpha(255, 255, 255, 48));

        ConfigTab[] configTabs = ConfigTab.values();
        float configStartY = dividerY + u(9f);
        for (int i = 0; i < configTabs.length; i++) {
            ConfigTab tab = configTabs[i];
            Rect rect = new Rect(x, configStartY + i * (tabHeight + gap), width, tabHeight);
            tabBounds.add(new TabBounds(null, tab, rect));

            if (configView && searchQuery.isBlank() && selectedConfigTab == tab)
                selectedRect = rect;

            NVGFonts.INTER.drawText(fitText(tab.sidebarLabel, NVGFonts.INTER, tabFont, rect.width - u(20f)), rect.x + u(12f), rect.y + u(4f), tabFont, NanoVGTheme.TEXT, Alignment.LEFT_TOP, false);
        }

        if (selectedRect == null)
            return;

        tabMarkY = Float.isNaN(tabMarkY)
                ? selectedRect.y
                : approach(tabMarkY, selectedRect.y, Math.clamp(0.16f * frameDelta * 0.9f, 0.08f, 1f));
        renderListRowMark(vg, new Rect(selectedRect.x, tabMarkY, selectedRect.width, selectedRect.height));
    }

    private int renderModuleList(NVGU vg, float x, float y, float width, float height) {
        NanoVGTheme.renderCompact(vg, x, y, width, height, u(NanoVGTheme.RADIUS_HUD), 1f, COMPACT_BACKGROUND_ALPHA_OFFSET);

        List<Module> modules = visibleModules();
        listViewport = new Rect(x + u(5f), y + u(5f), width - u(10f), height - u(10f));
        float contentHeight = contentHeight(modules);
        maxListScroll = Math.max(0f, contentHeight - listViewport.height);
        targetListScroll = Math.clamp(targetListScroll, 0f, maxListScroll);
        listScroll = Math.clamp(listScroll, 0f, maxListScroll);
        if (maxListScroll <= 0f)
            scrollVelocity = 0f;

        withInputClip(listViewport, () -> withControlChrome(vg, listViewport, () -> {
            float rowY = listViewport.y - listScroll;

            if (modules.isEmpty()) {
                renderEmptyState(
                        vg,
                        listViewport,
                        searchQuery.isBlank() ? MaterialIcon.INFO : MaterialIcon.SEARCH,
                        searchQuery.isBlank() ? "No modules in this category" : "No matching modules",
                        searchQuery.isBlank() ? "Choose another category." : "Try another keyword or clear the search."
                );
                return;
            }

            for (int i = 0; i < modules.size(); i++) {
                Module module = modules.get(i);
                float blockHeight = moduleBlockHeight(module);
                if (rowY + blockHeight > listViewport.y && rowY < listViewport.y + listViewport.height)
                    renderModuleBlock(vg, module, i, modules.size(), listViewport.x, rowY, listViewport.width);
                rowY += blockHeight;
            }
        }));

        renderScrollbar(vg, listViewport, contentHeight, listScroll);
        return modules.size();
    }

    private int renderConfigList(NVGU vg, float x, float y, float width, float height) {
        NanoVGTheme.renderCompact(vg, x, y, width, height, u(NanoVGTheme.RADIUS_HUD), 1f, COMPACT_BACKGROUND_ALPHA_OFFSET);

        float innerX = x + u(8f);
        float innerWidth = width - u(16f);
        float rowTop = y + u(8f);

        if (selectedConfigTab != ConfigTab.ONLINE) {
            renderConfigCreateRow(vg, innerX, rowTop, innerWidth);
            rowTop += u(36f);
        }

        List<ConfigEntry> configs = visibleConfigEntries();
        listViewport = new Rect(x + u(5f), rowTop, width - u(10f), Math.max(u(42f), y + height - rowTop - u(6f)));
        float contentHeight = Math.max(ROW_HEIGHT, configs.size() * SETTING_ROW_STEP + u(2f));
        maxListScroll = Math.max(0f, contentHeight - listViewport.height);
        targetListScroll = Math.clamp(targetListScroll, 0f, maxListScroll);
        listScroll = Math.clamp(listScroll, 0f, maxListScroll);
        if (maxListScroll <= 0f)
            scrollVelocity = 0f;

        withInputClip(listViewport, () -> vg.scissor(listViewport.x, listViewport.y, listViewport.width, listViewport.height, () -> {
            float rowY = listViewport.y - listScroll;

            if (configs.isEmpty()) {
                renderEmptyState(
                        vg,
                        listViewport,
                        searchQuery.isBlank() ? MaterialIcon.INFO : MaterialIcon.SEARCH,
                        searchQuery.isBlank() ? "No " + selectedConfigTab.emptyName + " configs" : "No matching configs",
                        searchQuery.isBlank() ? "Create one above or choose another category." : "Try another keyword or clear the search."
                );
                return;
            }

            for (int i = 0; i < configs.size(); i++) {
                ConfigEntry config = configs.get(i);
                if (rowY + SETTING_ROW_STEP > listViewport.y && rowY < listViewport.y + listViewport.height)
                    renderConfigRow(vg, config, i, configs.size(), listViewport.x, rowY, listViewport.width);
                rowY += SETTING_ROW_STEP;
            }
        }));

        renderScrollbar(vg, listViewport, contentHeight, listScroll);
        return configs.size();
    }

    private static void renderEmptyState(NVGU vg, Rect viewport, String icon, String title, String detail) {
        float centerX = viewport.centerX();
        float centerY = viewport.centerY() - u(7f);
        NVGFonts.ICON.drawText(icon, centerX, centerY - u(20f), font(16f), NanoVGTheme.muted(200), Alignment.CENTER_MIDDLE, false);
        NVGFonts.INTER.drawText(
                fitText(title, NVGFonts.INTER, font(14f), Math.max(u(40f), viewport.width - u(32f))),
                centerX,
                centerY + u(4f),
                font(14f),
                NanoVGTheme.TEXT,
                Alignment.CENTER_TOP,
                false
        );
        NVGFonts.INTER.drawText(
                fitText(detail, NVGFonts.INTER, font(11f), Math.max(u(40f), viewport.width - u(32f))),
                centerX,
                centerY + u(23f),
                font(11f),
                NanoVGTheme.MUTED,
                Alignment.CENTER_TOP,
                false
        );
    }

    private void renderConfigCreateRow(NVGU vg, float x, float y, float width) {
        boolean active = textFocus == TextFocus.CONFIG_NAME;
        Rect input = new Rect(x, y, width - u(91f), u(24f));
        Rect button = new Rect(x + width - u(83f), y, u(83f), u(24f));
        addControl(ControlType.CONFIG_NAME, input, null, null, 0);
        addControl(ControlType.CONFIG_CREATE, button, null, null, 0);

        boolean inputHovered = input.contains(scaledMouseX, scaledMouseY);
        vg.roundedRectangle(input.x, input.y, input.width, input.height, u(5f), alpha(255, 255, 255, active ? 29 : inputHovered ? 22 : 16));
        vg.roundedRectangleBorder(input.x, input.y, input.width, input.height, u(5f), u(1f), active ? NanoVGTheme.inputFocus(110) : alpha(255, 255, 255, 28), Border.INSIDE);

        String placeholder = selectedConfigTab == ConfigTab.MODULE ? "New module config" : "New bind config";
        String text = inputText(newConfigName, active, placeholder);
        float createFont = font(11f);
        String visibleText = fitText(text, NVGFonts.INTER, createFont, input.width - u(32f));
        Color nameColor = newConfigName.isBlank() && !active ? NanoVGTheme.muted(180) : NanoVGTheme.TEXT;
        rememberTextField(TextFocus.CONFIG_NAME, null, input.x + u(26f), createFont, input.width - u(32f));
        renderSelectionHighlight(vg, active, NVGFonts.INTER, createFont, input.x + u(26f), input.y + u(6f), input.width - u(32f));
        NVGFonts.ICON.drawText(MaterialIcon.ADD, input.x + u(8f), input.y + u(5f), font(12f), active ? NanoVGTheme.INPUT_FOCUS : alpha(176, 186, 196, 220), Alignment.LEFT_TOP, false);
        NVGFonts.INTER.drawText(visibleText, input.x + u(26f), input.y + u(6f), createFont, nameColor, Alignment.LEFT_TOP, false);
        renderInlineCaret(vg, active, NVGFonts.INTER, createFont, input.x + u(26f), input.y + u(6f), nameColor);

        boolean canCreate = !cleanConfigName(newConfigName).isBlank();
        boolean hovered = button.contains(scaledMouseX, scaledMouseY);
        float progress = animate("config-create", canCreate && hovered, 0.18f);
        vg.roundedRectangle(button.x, button.y, button.width, button.height, u(5f), alpha(255, 255, 255, canCreate ? (int) (22 + 16 * progress) : 14));
        vg.roundedRectangleBorder(button.x, button.y, button.width, button.height, u(5f), u(1f), canCreate ? NanoVGTheme.inputFocus(88) : alpha(255, 255, 255, 24), Border.INSIDE);
        NVGFonts.INTER.drawText("Create", button.centerX(), button.y + u(6f), font(11f), canCreate ? NanoVGTheme.TEXT : NanoVGTheme.muted(180), Alignment.CENTER_TOP, false);
    }

    private void renderConfigRow(NVGU vg, ConfigEntry config, int index, int visibleCount, float x, float y, float width) {
        Rect row = new Rect(x, y, width, SETTING_ROW_STEP - u(4f));
        boolean showDelete = config.type != ConfigTab.ONLINE;
        boolean deletable = showDelete && !config.current;
        Rect delete = showDelete ? new Rect(row.x + row.width - u(28f), row.y + u(5f), u(20f), u(20f)) : null;
        Rect load = showDelete ? new Rect(row.x, row.y, Math.max(u(1f), row.width - SETTING_ROW_STEP), row.height) : row;
        addControl(ControlType.CONFIG_LOAD, load, config, null, 0);
        if (deletable)
            addControl(ControlType.CONFIG_DELETE, delete, config, null, 0);

        float currentProgress = easeOut(animate("config-current:" + config.type.name() + ":" + config.name, config.current, 0.12f));
        if (currentProgress > 0.01f)
            renderListRowWash(vg, row, currentProgress);

        float nameReserve = showDelete ? u(50f) : u(16f);
        float nameWidth = Math.max(u(28f), row.width - nameReserve);
        NVGFonts.INTER.drawText(fitText(config.name, NVGFonts.INTER, font(14f), nameWidth), row.x + u(12f), row.y + u(7f), font(14f), NanoVGTheme.TEXT, Alignment.LEFT_TOP, false);

        if (delete != null) {
            boolean deleteHovered = deletable && isHovered(delete);
            float deleteProgress = animate("config-delete:" + config.type.name() + ":" + config.name, deleteHovered, 0.18f);
            vg.roundedRectangle(delete.x, delete.y, delete.width, delete.height, u(5f), deletable ? mix(alpha(255, 255, 255, 12), alpha(255, 74, 74, 52), deleteProgress) : alpha(255, 255, 255, 8));
            vg.roundedRectangleBorder(delete.x, delete.y, delete.width, delete.height, u(5f), u(1f), deletable ? mix(alpha(255, 255, 255, 24), alpha(255, 96, 96, 110), deleteProgress) : alpha(255, 255, 255, 16), Border.INSIDE);
            Color deleteColor = !deletable ? alpha(120, 130, 140, 135) : deleteHovered ? alpha(255, 195, 195, 245) : alpha(176, 186, 196, 220);
            NVGFonts.ICON.drawText(MaterialIcon.DELETE, delete.centerX(), delete.centerY() - u(1f), font(12f), deleteColor, Alignment.CENTER_MIDDLE, false);
        }
    }

    private void renderModuleBlock(NVGU vg, Module module, int index, int visibleCount, float x, float y, float width) {
        Rect row = new Rect(x, y, width, ROW_HEIGHT - u(3f));
        addModuleRow(module, row);

        boolean hovered = isHovered(row);
        if (hovered)
            rememberHoveredModule(module);
        float enabled = animateIdentity(enabledAnimations, enabledAnimationFrames, module, module.tempEnabled, 0.12f);
        boolean selected = settingsPanelModule == module;
        float wash = washProgress("module", module, module.tempEnabled);
        if (wash > 0.01f)
            renderListRowWash(vg, row, wash);
        float mark = animate("module-mark:" + System.identityHashCode(module), selected, 0.18f);
        if (mark > 0.01f)
            renderListRowMark(vg, row, mark);

        Rect switchRect = new Rect(row.x + u(14f), row.y + u(7f), SWITCH_WIDTH, SWITCH_HEIGHT);
        float nameX = switchRect.x + switchRect.width + u(8f);

        String bindLabel = module.key != GLFW.GLFW_KEY_UNKNOWN ? keyName(module.key) : "";
        float nameRight = row.x + row.width - u(8f);
        if (!bindLabel.isBlank()) {
            float bindWidth = Math.min(u(72f), NVGFonts.INTER.getWidth(bindLabel, font(9f)) + u(12f));
            Rect bindRect = new Rect(nameRight - bindWidth, row.y + u(6f), bindWidth, u(16f));
            nameRight = bindRect.x - u(8f);
            vg.roundedRectangle(bindRect.x, bindRect.y, bindRect.width, bindRect.height, u(4f), alpha(255, 255, 255, hovered || selected ? 18 : 12));
            vg.roundedRectangleBorder(bindRect.x, bindRect.y, bindRect.width, bindRect.height, u(4f), u(1f), alpha(255, 255, 255, 24), Border.INSIDE);
            NVGFonts.INTER.drawText(
                    fitText(bindLabel, NVGFonts.INTER, font(10f), bindRect.width - u(6f)),
                    bindRect.centerX(),
                    bindRect.y + u(3f),
                    font(10f),
                    NanoVGTheme.MUTED,
                    Alignment.CENTER_TOP,
                    false
            );
        }

        float nameMaxWidth = Math.max(u(36f), nameRight - nameX);
        NVGFonts.INTER.drawText(fitText(module.moduleName, NVGFonts.INTER, font(14f), nameMaxWidth), nameX, row.y + u(7f), font(14f), NanoVGTheme.TEXT, Alignment.LEFT_TOP, false);

        drawSwitch(vg, switchRect.x, switchRect.y, switchRect.width, switchRect.height, enabled, "module-list:" + System.identityHashCode(module), pressedModule == module);
    }

    private void renderExpandedSettings(NVGU vg, Module module, float x, float y, float width) {
        float rowY = y + u(2f);
        rowY = renderBaseSettingRows(vg, module, x + u(6f), rowY, width - u(12f));
        List<SettingValue<?>> settings = collectSettings(module);

        if (!hasRenderableSettings(module)) {
            NVGFonts.INTER.drawText("No settings", x + u(10f), rowY + u(7f), font(12f), NanoVGTheme.MUTED, Alignment.LEFT_TOP, false);
            return;
        }

        for (SettingValue<?> setting : settings) {
            float progress = settingVisibilityProgress(setting);
            if (progress <= 0.001f)
                continue;

            float settingHeight = (settingRowHeight(setting, width - u(12f)) + settingRowGap() + listDropdownHeight(module, setting)) * easeOut(progress);
            float finalRowY = rowY;
            Rect settingClip = new Rect(x + u(6f), rowY, width - u(12f), settingHeight);
            vg.scissor(settingClip.x, settingClip.y, settingClip.width, settingClip.height, () ->
                    withInputClip(settingClip, () ->
                            vg.globalAlpha(easeOut(progress), () -> renderSetting(vg, module, setting, x + u(6f), finalRowY, width - u(12f)))));
            rowY += settingHeight;
        }
    }

    private float renderBaseSettingRows(NVGU vg, Module module, float x, float y, float width) {
        float arrayHeight = wrappedSettingRowHeight("Show on array", booleanLabelMaxWidth(width));
        Rect array = new Rect(x, y, width, arrayHeight);
        addControl(ControlType.SHOW_ON_ARRAY, array, module, null, 0);
        float shownOnArray = animateIdentity(switchAnimations, switchAnimationFrames, module, module.showOnArray, 0.12f);
        renderBooleanRow(vg, array, "Show on array", "array", module, module.showOnArray, shownOnArray, "array:" + System.identityHashCode(module), isPressed(ControlType.SHOW_ON_ARRAY, module));

        float rowY = renderModuleNote(vg, module, x, y + arrayHeight + settingRowGap(), width);

        Rect key = new Rect(x, rowY, width, SETTING_ROW_HEIGHT);
        addControl(ControlType.MODULE_KEY, key, module, null, 0);
        renderSettingRow(vg, key, "Keybind", bindingModule == module ? "Press key..." : keyName(module.key), 0f);
        NVGFonts.ICON.drawText(MaterialIcon.KEY, key.x + key.width - u(9f), key.y + u(6f), font(12f), bindingModule == module ? NanoVGTheme.TEXT : NanoVGTheme.muted(220), Alignment.RIGHT_TOP, false);

        return rowY + SETTING_ROW_STEP;
    }

    private float renderModuleNote(NVGU vg, Module module, float x, float y, float width) {
        String note = moduleNote(module);
        if (note.isEmpty())
            return y;

        List<String> lines = wrapNoteLines(note, width);
        float height = noteBlockHeight(lines);
        Rect row = new Rect(x, y, width, height);
        boolean hovered = isHovered(row);
        vg.roundedRectangle(row.x, row.y + u(1f), row.width, row.height - u(2f), u(4f), hovered ? alpha(255, 255, 255, 22) : alpha(255, 255, 255, 14));
        float textY = row.y + u(6f);
        float noteFont = font(12f);
        float noteLine = u(15f);
        for (int i = 0; i < lines.size(); i++) {
            NVGFonts.INTER.drawText(lines.get(i), row.x + u(12f), textY + i * noteLine, noteFont, NanoVGTheme.TEXT, Alignment.LEFT_TOP, false);
        }
        return y + height + u(7f);
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
            default -> renderSettingRow(vg, row, setting.name, "Unsupported", 0f);
        }

        return SETTING_ROW_STEP + listDropdownHeight(module, setting);
    }

    private void renderBooleanRow(NVGU vg, Rect row, String label, String washScope, Object washKey, boolean washActive, float enabled, String grabId, boolean grabbed) {
        float wash = washProgress(washScope, washKey, washActive);
        if (wash > 0.01f)
            renderListRowWash(vg, row, wash);
        Rect switchRect = new Rect(row.x + u(8f), row.y + u(6f), SWITCH_WIDTH, SWITCH_HEIGHT);
        float labelX = switchRect.x + switchRect.width + u(8f);
        drawWrappedSettingLabel(label, labelX, row.y + u(7f), booleanLabelMaxWidth(row.width));
        drawSwitch(vg, switchRect.x, switchRect.y, switchRect.width, switchRect.height, enabled, grabId, grabbed);
    }

    private void renderSettingRow(NVGU vg, Rect row, String label, String value, float enabled) {
        if (enabled > 0.01f)
            renderListRowWash(vg, row, enabled);
        NVGFonts.INTER.drawText(fitText(label, NVGFonts.INTER, font(12f), row.width * 0.50f), row.x + u(12f), row.y + u(7f), font(12f), NanoVGTheme.TEXT, Alignment.LEFT_TOP, false);
        if (value != null)
            NVGFonts.INTER.drawText(fitText(value, NVGFonts.INTER, font(11f), row.width * 0.36f), row.x + row.width - u(25f), row.y + u(8f), font(11f), NanoVGTheme.MUTED, Alignment.RIGHT_TOP, false);
    }

    private void renderListSetting(NVGU vg, Rect row, Module module, ListValue value) {
        boolean open = openedListModule == module && openedListValue == value;
        float dropdownProgress = listDropdownProgress(module, value);
        float easedDropdown = easeOut(dropdownProgress);
        float valueWidth = listValueWidth(row.width);
        float optionX = row.x + row.width - valueWidth - u(8f);
        float optionY = row.y + u(4f);

        drawWrappedSettingLabel(value.name, row.x + u(12f), row.y + u(7f), listLabelMaxWidth(row.width));
        if (dropdownProgress <= 0.01f && value.get() != null)
            NVGFonts.INTER.drawText(fitText(value.get(), NVGFonts.INTER, font(11f), row.width * 0.36f), row.x + row.width - u(25f), row.y + u(8f), font(11f), NanoVGTheme.MUTED, Alignment.RIGHT_TOP, false);
        if (dropdownProgress <= 0.01f) {
            addControl(ControlType.LIST_VALUE, row, value, module, 0);
            return;
        }

        renderListOptions(vg, value, optionX, optionY, valueWidth, easedDropdown, open);
        addControl(ControlType.LIST_VALUE, new Rect(row.x, row.y, Math.max(u(40f), optionX - row.x), row.height), value, module, 0);
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
                float optionProgress = Math.clamp((progress - i * 0.035f) / 0.78f, 0f, 1f);
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
                float optionProgress = Math.clamp((progress - i * 0.035f) / 0.78f, 0f, 1f);
                if (optionProgress > 0.01f) {
                    Rect optionRect = new Rect(x, optionY, width, optionHeight);
                    boolean selected = i == value.getCurrentIndex();
                    float optionGrab = knobGrab("list-option:" + System.identityHashCode(value) + ":" + i, isListOptionPressed(value, i));
                    Rect optionChip = squashedRect(optionRect, optionGrab, 0.04f, 0.16f);
                    float textOffset = u(3f) * (1f - optionProgress);
                    float finalOptionProgress = optionProgress;
                    vg.globalAlpha(finalOptionProgress, () -> {
                        if (selected)
                            renderListRowMark(vg, optionChip);
                        drawWrappedListOption(option, optionChip.x + u(12f), optionChip.y + u(4f) + textOffset, optionChip.width - u(18f));
                    });
                }
                optionY += optionHeight;
            }
        }));
    }

    private void renderFloatSetting(NVGU vg, Rect row, FloatValue value) {
        boolean active = textFocus == TextFocus.NUMBER && focusedNumberValue == value;
        addControl(ControlType.FLOAT_INPUT, row, value, null, 0);
        renderSettingRow(vg, row, value.name, active ? null : String.format(Locale.ROOT, "%.2f%s", value.get(), value.suffix == null ? "" : value.suffix), 0f);
        if (active) {
            renderNumberInput(vg, row, value, value.suffix);
            return;
        }

        Rect track = new Rect(row.x + row.width - u(168f), row.y + u(11f), u(102f), u(7f));
        addControl(ControlType.FLOAT_SLIDER, track.expand(u(8f), u(10f)), value, null, 0);
        drawSlider(vg, track, normalize(value.get(), value.minimum, value.maximum), value, 0);
    }

    private void renderIntSetting(NVGU vg, Rect row, IntValue value) {
        boolean active = textFocus == TextFocus.NUMBER && focusedNumberValue == value;
        addControl(ControlType.INT_INPUT, row, value, null, 0);
        renderSettingRow(vg, row, value.name, active ? null : value.get() + (value.suffix == null ? "" : value.suffix), 0f);
        if (active) {
            renderNumberInput(vg, row, value, value.suffix);
            return;
        }

        Rect track = new Rect(row.x + row.width - u(168f), row.y + u(11f), u(102f), u(7f));
        addControl(ControlType.INT_SLIDER, track.expand(u(8f), u(10f)), value, null, 0);
        drawSlider(vg, track, normalize(value.get(), value.minimum, value.maximum), value, 0);
    }

    private void renderNumberInput(NVGU vg, Rect row, SettingValue<?> value, String suffix) {
        float inputWidth = Math.clamp(row.width * 0.44f, u(92f), u(160f));
        Rect input = new Rect(row.x + row.width - inputWidth - u(8f), row.y + u(4f), inputWidth, u(19f));
        boolean valid = parseNumberInput() != null;
        Color borderColor = valid ? NanoVGTheme.inputFocus(120) : alpha(255, 88, 88, 150);
        vg.roundedRectangle(input.x, input.y, input.width, input.height, u(4f), alpha(255, 255, 255, 28));
        vg.roundedRectangleBorder(input.x, input.y, input.width, input.height, u(4f), u(1f), borderColor, Border.INSIDE);

        String shownSuffix = suffix == null ? "" : suffix;
        float suffixWidth = shownSuffix.isEmpty() ? 0f : NVGFonts.INTER.getWidth(shownSuffix, font(9f)) + u(7f);
        String text = inputText(numberInput, true, "");
        float numberFont = font(10f);
        String visibleText = fitText(text, NVGFonts.INTER, numberFont, input.width - suffixWidth - u(12f));
        Color numberColor = valid ? alpha(255, 255, 255, 240) : alpha(255, 175, 175, 240);
        rememberTextField(TextFocus.NUMBER, value, input.x + u(6f), numberFont, input.width - suffixWidth - u(12f));
        renderSelectionHighlight(vg, true, NVGFonts.INTER, numberFont, input.x + u(6f), input.y + u(5f), input.width - suffixWidth - u(12f));
        NVGFonts.INTER.drawText(
                visibleText,
                input.x + u(6f),
                input.y + u(5f),
                numberFont,
                numberColor,
                Alignment.LEFT_TOP,
                false
        );
        renderInlineCaret(vg, true, NVGFonts.INTER, numberFont, input.x + u(6f), input.y + u(5f), numberColor);
        if (!shownSuffix.isEmpty())
            NVGFonts.INTER.drawText(shownSuffix, input.x + input.width - u(6f), input.y + u(5f), font(9f),
                    alpha(150, 160, 170, 220), Alignment.RIGHT_TOP, false);
    }

    private void renderColorSetting(NVGU vg, Rect row, ColorValue value) {
        renderSettingRow(vg, row, value.name, value.toHex().substring(0, 7), 0f);
        Color color = value.get();
        float startX = row.x + row.width - u(154f);
        drawColorTrack(vg, value, startX, row.y + u(11f), color.getRed(), 0, alpha(255, 80, 80, 230));
        drawColorTrack(vg, value, startX + u(38f), row.y + u(11f), color.getGreen(), 1, alpha(80, 255, 120, 230));
        drawColorTrack(vg, value, startX + u(76f), row.y + u(11f), color.getBlue(), 2, alpha(80, 175, 255, 230));
        vg.roundedRectangle(row.x + row.width - u(19f), row.y + u(7f), u(12f), u(12f), u(3f), color);
        vg.roundedRectangleBorder(row.x + row.width - u(19f), row.y + u(7f), u(12f), u(12f), u(3f), u(1f), alpha(255, 255, 255, 80), Border.INSIDE);
    }

    private void drawColorTrack(NVGU vg, ColorValue value, float x, float y, int component, int index, Color accent) {
        Rect track = new Rect(x, y, u(28f), u(7f));
        addControl(ControlType.COLOR_SLIDER, track.expand(u(6f), u(10f)), value, null, index);
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
        renderSettingRow(vg, row, value.name, null, 0f);
        Rect input = new Rect(row.x + row.width - u(132f), row.y + u(4f), u(124f), u(19f));
        addControl(ControlType.TEXT_VALUE, input, value, module, 0);
        vg.roundedRectangle(input.x, input.y, input.width, input.height, u(4f), alpha(255, 255, 255, active ? 28 : 18));
        vg.roundedRectangleBorder(input.x, input.y, input.width, input.height, u(4f), u(1f), active ? NanoVGTheme.inputFocus(110) : alpha(255, 255, 255, 30), Border.INSIDE);
        String text = inputText(value.get(), active, "");
        float settingFont = font(10f);
        String visibleText = fitText(text, NVGFonts.INTER, settingFont, input.width - u(10f));
        rememberTextField(TextFocus.SETTING, value, input.x + u(6f), settingFont, input.width - u(10f));
        renderSelectionHighlight(vg, active, NVGFonts.INTER, settingFont, input.x + u(6f), input.y + u(5f), input.width - u(10f));
        Color textColor = value.get().isEmpty() && !active ? alpha(120, 130, 140, 205) : alpha(255, 255, 255, 235);
        NVGFonts.INTER.drawText(visibleText, input.x + u(6f), input.y + u(5f), settingFont, textColor, Alignment.LEFT_TOP, false);
        renderInlineCaret(vg, active, NVGFonts.INTER, settingFont, input.x + u(6f), input.y + u(5f), textColor);
    }

    private void renderKeyBindSetting(NVGU vg, Rect row, KeyBindValue value) {
        boolean active = bindingValue == value;
        renderSettingRow(vg, row, value.name, active ? "Press key..." : keyName(value.get()), 0f);
        addControl(ControlType.KEY_VALUE, row, value, null, 0);
        NVGFonts.ICON.drawText(MaterialIcon.KEY, row.x + row.width - u(9f), row.y + u(6f), font(12f), active ? NanoVGTheme.TEXT : NanoVGTheme.muted(220), Alignment.RIGHT_TOP, false);
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

    private boolean handleControlClick(ControlBounds control, float mouseX, int button) {
        if (control.type == ControlType.FLOAT_INPUT || control.type == ControlType.INT_INPUT) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                focusNumberInput((SettingValue<?>) control.target);
                return true;
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && textFocus == TextFocus.NUMBER
                    && focusedNumberValue == control.target) {
                beginTextSelection(mouseX);
                return true;
            }

            return false;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
            return false;

        clearTextFocus();
        if (control.type != ControlType.MODULE_KEY) bindingModule = null;
        if (control.type != ControlType.KEY_VALUE) bindingValue = null;

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (control.type == ControlType.LIST_VALUE) {
                toggleListDropdown((Module) control.owner, (ListValue) control.target);
                pressedControl = control;
                return true;
            }

            if (control.type == ControlType.MODULE_KEY) {
                ((Module) control.target).key = GLFW.GLFW_KEY_UNKNOWN;
                bindingModule = null;
                return true;
            }

            if (control.type == ControlType.KEY_VALUE) {
                ((KeyBindValue) control.target).set(GLFW.GLFW_KEY_UNKNOWN);
                bindingValue = null;
                return true;
            }

            if (openedListValue != null) {
                closeListDropdown();
                return true;
            }

            return false;
        }

        if (control.type != ControlType.LIST_VALUE && control.type != ControlType.LIST_OPTION)
            closeListDropdown();

        switch (control.type) {
            case CONFIG_NAME -> {
                textFocus = TextFocus.CONFIG_NAME;
                beginTextSelection(mouseX);
            }
            case CONFIG_CREATE -> createConfigFromInput();
            case CONFIG_LOAD -> loadConfigEntry((ConfigEntry) control.target);
            case CONFIG_DELETE -> deleteConfigEntry((ConfigEntry) control.target);
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
                beginTextSelection(mouseX);
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

    private static void renderListRowWash(NVGU vg, Rect row, float alphaScale) {
        vg.roundedRectangle(row.x + u(6f), row.y + u(1f), row.width - u(12f), row.height - u(2f), u(4f), alpha(255, 255, 255, (int) (18 * alphaScale)));
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

    private void withControlChrome(NVGU vg, Rect clip, Runnable content) {
        deferControlChrome = true;
        try {
            vg.scissor(clip.x, clip.y, clip.width, clip.height, content);
            flushPendingControlGlass(vg);
            if (!pendingControlForegrounds.isEmpty())
                vg.scissor(clip.x, clip.y, clip.width, clip.height, () -> drawPendingControlForegrounds(vg));
        } finally {
            deferControlChrome = false;
            pendingControlGlass.clear();
            pendingControlForegrounds.clear();
        }
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
            drawKnobFill(vg, knob);
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
                vg.roundedRectangle(track.x, track.y, track.width * fill, track.height, radius, NanoVGTheme.sliderFill(235));
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

    private static Rect sliderKnob(Rect track, float progress, float grab) {
        float knobWidth = u(9f) * (1f + 0.46f * grab);
        float knobHeight = u(13f) * (1f - 0.30f * grab);
        float centerX = track.x + track.width * progress;
        float centerY = track.centerY();
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

    private void renderScrollbar(NVGU vg, Rect viewport, float contentHeight, float scroll) {
        if (contentHeight <= viewport.height + 1f) {
            scrollbarTrackRect = new Rect(0f, 0f, 0f, 0f);
            scrollbarThumbRect = new Rect(0f, 0f, 0f, 0f);
            return;
        }

        float thumbHeight = Math.max(u(22f), viewport.height * viewport.height / contentHeight);
        float travel = viewport.height - thumbHeight;
        float thumbY = viewport.y + travel * (scroll / Math.max(1f, contentHeight - viewport.height));
        float trackX = viewport.x + viewport.width + u(1f);
        scrollbarTrackRect = new Rect(trackX - u(5f), viewport.y, u(12f), viewport.height);
        scrollbarThumbRect = new Rect(trackX - u(1f), thumbY, u(5f), thumbHeight);

        boolean hovered = scrollbarTrackRect.contains(scaledMouseX, scaledMouseY) || activeScrollbar != null;
        float progress = animate("scrollbar", hovered, 0.18f);
        vg.roundedRectangle(trackX + u(1f), viewport.y + u(4f), u(2f), viewport.height - u(8f), u(1f), alpha(255, 255, 255, (int) (18 + 18 * progress)));
        vg.roundedRectangle(scrollbarThumbRect.x, scrollbarThumbRect.y, scrollbarThumbRect.width, scrollbarThumbRect.height, u(2.5f), NanoVGTheme.scrollbarThumb((int) (112 + 86 * progress)));
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

    private void updateScrollbarDrag(float mouseY) {
        if (activeScrollbar == null || maxListScroll <= 0f || scrollbarTrackRect.height <= 0f || scrollbarThumbRect.height <= 0f)
            return;

        float travel = Math.max(1f, scrollbarTrackRect.height - scrollbarThumbRect.height);
        float thumbY = Math.clamp(mouseY - activeScrollbar.offsetY, scrollbarTrackRect.y, scrollbarTrackRect.y + travel);
        float progress = (thumbY - scrollbarTrackRect.y) / travel;
        targetListScroll = Math.clamp(progress * maxListScroll, 0f, maxListScroll);
        listScroll = targetListScroll;
        scrollVelocity = 0f;
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

    private List<Module> visibleModules() {
        String query = normalize(searchQuery);
        List<Module> modules = new ArrayList<>();

        for (Module module : ModuleManager.allModules) {
            if (module.moduleCategory == null) continue;
            if (!query.isBlank() && module.moduleCategory == ModuleCategory.Dev) continue;

            boolean matches = query.isBlank()
                    ? module.moduleCategory == selectedCategory
                    : normalize(module.moduleName).contains(query)
                    || normalize(module.moduleCategory.name()).contains(query)
                    || (module.tag() != null && normalize(module.tag()).contains(query))
                    || (module.description() != null && normalize(module.description()).contains(query));

            if (matches)
                modules.add(module);
        }

        modules.sort(Comparator.comparing(module -> module.moduleName, String.CASE_INSENSITIVE_ORDER));
        return modules;
    }

    private List<ConfigEntry> visibleConfigEntries() {
        String query = normalize(searchQuery);
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
        targetListScroll = 0f;
        listScroll = 0f;
        scrollVelocity = 0f;
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

        targetListScroll = Math.clamp(targetListScroll, 0f, maxListScroll);
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
        targetListScroll = Math.clamp(targetListScroll, 0f, maxListScroll);
        listScroll = Math.clamp(listScroll, 0f, maxListScroll);
        scrollVelocity = 0f;
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
            Client.configManager.saveConfigFile(configName, true);
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

    private float contentHeight(List<Module> modules) {
        return Math.max(modules.size() * ROW_HEIGHT, u(30f));
    }

    private float moduleBlockHeight(Module module) {
        return ROW_HEIGHT;
    }

    private float expansionProgress(Module module) {
        return animateIdentity(
                expansionAnimations,
                expansionAnimationFrames,
                module,
                settingsPanelModule == module,
                0.08f
        );
    }

    private float expandedSettingsHeight(Module module) {
        Float cached = expandedSettingsHeightCache.get(module);
        if (cached != null)
            return cached;

        List<SettingValue<?>> settings = collectSettings(module);
        float rowWidth = Math.max(u(40f), settingsPanelViewport.width - u(12f));
        float arrayHeight = wrappedSettingRowHeight("Show on array", booleanLabelMaxWidth(rowWidth));
        float height = u(6f) + arrayHeight + settingRowGap() + SETTING_ROW_STEP + moduleNoteHeight(module);

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
        float rowWidth = Math.max(u(40f), settingsPanelViewport.width - u(12f));
        float optionWidth = listValueWidth(rowWidth);
        float[] heights = listOptionHeights(value, optionWidth);
        float total = listOptionsTotalHeight(heights);
        float settingHeight = wrappedSettingRowHeight(value.name, listLabelMaxWidth(rowWidth));
        return Math.max(0f, u(4f) + total - settingHeight);
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

        return noteBlockHeight(wrapNoteLines(note, Math.max(u(40f), settingsPanelViewport.width - u(12f)))) + u(7f);
    }

    private static float noteBlockHeight(List<String> lines) {
        return Math.max(u(27f), u(12f) + Math.max(1, lines.size()) * u(15f));
    }

    private static List<String> wrapNoteLines(String note, float rowWidth) {
        return wrapTextToWidth(note, NVGFonts.INTER, font(12f), Math.max(u(20f), rowWidth - u(24f)));
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
        if (settingsPanelModule == module)
            closeSettingsPanel();
        else
            openSettingsPanel(module);
    }

    private void openSettingsPanel(Module module) {
        boolean alreadyOpen = settingsPanelModule != null;
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
        settingsPanelModule = null;
        settingsPanelScroll = 0f;
        targetSettingsPanelScroll = 0f;
        settingsPanelScrollVelocity = 0f;
        maxSettingsPanelScroll = 0f;
        activeSettingsPanelScrollbar = null;
    }

    private void focusSearch() {
        storeCurrentCategoryScroll();
        clearInteractionState();
        textFocus = TextFocus.SEARCH;
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

    private void clearSearch() {
        searchQuery = "";
        textCaret = 0;
        textAnchor = 0;
        if (!configView && selectedCategory != null)
            restoreCategoryScroll(selectedCategory);
        else
            resetListScroll();
        closeListDropdown();
    }

    private void clearInteractionState() {
        clearTextFocus();
        bindingModule = null;
        bindingValue = null;
        activeSlider = null;
        pressedControl = null;
        pressedModule = null;
        activeScrollbar = null;
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
        String text = fitText(tooltipText, NVGFonts.INTER, tooltipFont, maxWidth);
        float width = NVGFonts.INTER.getWidth(text, tooltipFont) + paddingX * 2f;
        float height = u(24f);
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
            NanoVGTheme.renderPanel(vg, drawX, drawY, width, height, u(NanoVGTheme.RADIUS_HUD), 1f);
            NVGFonts.INTER.drawText(text, drawX + paddingX, drawY + u(6f), tooltipFont, NanoVGTheme.TEXT, Alignment.LEFT_TOP, false);
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

    private void cycleCategory(int delta) {
        storeCurrentCategoryScroll();
        ModuleCategory[] categories = ModuleCategory.values();
        ConfigTab[] configTabs = ConfigTab.values();
        int categoryCount = categories.length;
        int total = categoryCount + configTabs.length;
        int current = configView
                ? categoryCount + selectedConfigTab.ordinal()
                : selectedCategory.ordinal();
        int next = Math.floorMod(current + delta, total);
        searchQuery = "";
        if (next < categoryCount) {
            configView = false;
            selectedCategory = categories[next];
            restoreCategoryScroll(selectedCategory);
        } else {
            configView = true;
            selectedConfigTab = configTabs[next - categoryCount];
            resetListScroll();
        }
        clearInteractionState();
    }

    private void scrollWithKeyboard(int key) {
        boolean settingsFocused = settingsPanelModule != null
                && settingsPanelRect.contains(scaledMouseX, scaledMouseY);
        if (settingsFocused) {
            float page = Math.max(u(48f), settingsPanelViewport.height * 0.85f);
            if (key == GLFW.GLFW_KEY_HOME)
                targetSettingsPanelScroll = 0f;
            else if (key == GLFW.GLFW_KEY_END)
                targetSettingsPanelScroll = maxSettingsPanelScroll;
            else if (key == GLFW.GLFW_KEY_PAGE_UP)
                targetSettingsPanelScroll = Math.max(0f, targetSettingsPanelScroll - page);
            else
                targetSettingsPanelScroll = Math.min(maxSettingsPanelScroll, targetSettingsPanelScroll + page);
            return;
        }

        float page = Math.max(u(48f), listViewport.height * 0.85f);
        if (key == GLFW.GLFW_KEY_HOME)
            targetListScroll = 0f;
        else if (key == GLFW.GLFW_KEY_END)
            targetListScroll = maxListScroll;
        else if (key == GLFW.GLFW_KEY_PAGE_UP)
            targetListScroll = Math.max(0f, targetListScroll - page);
        else
            targetListScroll = Math.min(maxListScroll, targetListScroll + page);
    }

    private void clearTextFocus() {
        if (textFocus == TextFocus.NUMBER)
            commitNumberInput();
        resetTextFocus();
    }

    private void resetTextFocus() {
        textFocus = TextFocus.NONE;
        focusedTextValue = null;
        focusedTextModule = null;
        focusedNumberValue = null;
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
        if (textFocus == TextFocus.SETTING && focusedTextValue != null) return focusedTextValue.get();
        if (textFocus == TextFocus.NUMBER) return numberInput;
        return "";
    }

    private void setFocusedText(String text) {
        if (textFocus == TextFocus.SEARCH) {
            searchQuery = text == null ? "" : text;
            if (searchQuery.isBlank() && !configView && selectedCategory != null)
                restoreCategoryScroll(selectedCategory);
            else
                resetListScroll();
            return;
        }

        if (textFocus == TextFocus.CONFIG_NAME) {
            newConfigName = text == null ? "" : text;
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

        if (hitRect(searchClearRect))
            return CursorTypes.POINTING_HAND;
        if (hitRect(closeRect))
            return CursorTypes.POINTING_HAND;
        if (settingsPanelModule != null && hitRect(settingsPanelCloseRect))
            return CursorTypes.POINTING_HAND;
        if (hitRect(scrollbarThumbRect) || hitRect(scrollbarTrackRect))
            return CursorTypes.POINTING_HAND;
        if (hitRect(settingsPanelScrollbarThumbRect) || hitRect(settingsPanelScrollbarTrackRect))
            return CursorTypes.POINTING_HAND;

        if (settingsPanelModule != null && hitRect(settingsPanelRect)) {
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
        for (int i = 0, n = tabBounds.size(); i < n; i++) {
            if (tabBounds.get(i).rect.contains(scaledMouseX, scaledMouseY))
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
            case TEXT_VALUE, CONFIG_NAME -> CursorTypes.IBEAM;
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
            case FLOAT_SLIDER, INT_SLIDER -> control.rect.contract(u(8f), u(10f));
            case COLOR_SLIDER -> control.rect.contract(u(6f), u(10f));
            default -> control.rect;
        };
    }

    private float washProgress(String scope, Object key, boolean active) {
        return animations.compute(scope + ":" + System.identityHashCode(key), (ignored, current) -> {
            if (current == null)
                return active ? 1f : 0f;
            return approach(current, active ? 1f : 0f, 0.18f * frameDelta);
        });
    }

    private float animate(String key, boolean active, float speed) {
        return animations.compute(key, (ignored, current) -> approach(current == null ? 0f : current, active ? 1f : 0f, speed * frameDelta));
    }

    private <T> float animateIdentity(Map<T, Float> states, Map<T, Long> frames, T key, boolean active, float speed) {
        Float current = states.get(key);
        if (!active && current == null)
            return 0f;

        Long frame = frames.get(key);
        if (frame != null && frame == animationFrame)
            return current == null ? 0f : current;

        float progress = stepTowards(current == null ? 0f : current, active ? 1f : 0f, speed * frameDelta);
        if (progress <= 0.001f && !active) {
            states.remove(key);
            frames.remove(key);
            return 0f;
        }

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
        font.drawText("|", caretX, textY, fontSize, color, Alignment.CENTER_TOP, false);
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
        return SETTING_ROW_STEP - SETTING_ROW_HEIGHT;
    }

    private float settingRowHeight(SettingValue<?> setting, float rowWidth) {
        if (setting instanceof BooleanValue value)
            return wrappedSettingRowHeight(value.name, booleanLabelMaxWidth(rowWidth));
        if (setting instanceof ListValue value)
            return wrappedSettingRowHeight(value.name, listLabelMaxWidth(rowWidth));
        return SETTING_ROW_HEIGHT;
    }

    private float booleanLabelMaxWidth(float rowWidth) {
        return Math.max(u(36f), rowWidth - SWITCH_WIDTH - u(24f));
    }

    private float listValueWidth(float rowWidth) {
        return Math.max(u(88f), Math.min(u(180f), rowWidth * 0.38f));
    }

    private float listLabelMaxWidth(float rowWidth) {
        return Math.max(u(36f), rowWidth - listValueWidth(rowWidth) - u(28f));
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
        int lines = wrapListOption(option, optionWidth - u(18f)).size();
        return Math.max(u(20f), u(4f) + lines * u(14f) + u(3f));
    }

    private void drawWrappedListOption(String option, float x, float y, float maxWidth) {
        float size = font(11f);
        float lineHeight = u(14f);
        List<String> lines = wrapListOption(option, maxWidth);
        for (int i = 0; i < lines.size(); i++)
            NVGFonts.INTER.drawText(lines.get(i), x, y + i * lineHeight, size, NanoVGTheme.TEXT, Alignment.LEFT_TOP, false);
    }

    private static List<String> wrapListOption(String option, float maxWidth) {
        return wrapTextToWidth(option == null ? "" : option, NVGFonts.INTER, font(11f), Math.max(u(20f), maxWidth));
    }

    private float wrappedSettingRowHeight(String label, float maxWidth) {
        int lines = wrapSettingLabel(label, maxWidth).size();
        return Math.max(SETTING_ROW_HEIGHT, u(6f) + lines * u(15f) + u(4f));
    }

    private void drawWrappedSettingLabel(String label, float x, float y, float maxWidth) {
        float size = font(12f);
        float lineHeight = u(15f);
        List<String> lines = wrapSettingLabel(label, maxWidth);
        for (int i = 0; i < lines.size(); i++)
            NVGFonts.INTER.drawText(lines.get(i), x, y + i * lineHeight, size, NanoVGTheme.TEXT, Alignment.LEFT_TOP, false);
    }

    private static List<String> wrapSettingLabel(String label, float maxWidth) {
        return wrapTextToWidth(label == null ? "" : label, NVGFonts.INTER, font(12f), Math.max(u(20f), maxWidth));
    }

    private static List<String> wrapTextToWidth(String text, NVGFont font, float size, float maxWidth) {
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

    private static String fitText(String text, NVGFont font, float size, float maxWidth) {
        if (text == null) return "";
        if (font.getWidth(text, size) <= maxWidth) return text;

        String suffix = "...";
        float suffixWidth = font.getWidth(suffix, size);
        int end = text.length();
        while (end > 0 && font.getWidth(text.substring(0, end), size) + suffixWidth > maxWidth) {
            end--;
        }
        return end <= 0 ? suffix : text.substring(0, end) + suffix;
    }

    private static int normalizeKey(int key) {
        return key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE
                ? GLFW.GLFW_KEY_UNKNOWN
                : key;
    }

    private static String keyName(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN)
            return "NONE";

        String name = GLFW.glfwGetKeyName(key, GLFW.glfwGetKeyScancode(key));
        if (name != null)
            return name.toUpperCase(Locale.ROOT);

        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_ESCAPE -> "ESCAPE";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
            case GLFW.GLFW_KEY_INSERT -> "INSERT";
            case GLFW.GLFW_KEY_DELETE -> "DELETE";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_PAGE_UP -> "PAGE UP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PAGE DOWN";
            case GLFW.GLFW_KEY_HOME -> "HOME";
            case GLFW.GLFW_KEY_END -> "END";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LEFT SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LEFT CTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LEFT ALT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RIGHT SHIFT";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RIGHT CTRL";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RIGHT ALT";
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
            default -> String.valueOf(key);
        };
    }

    private enum ConfigTab {
        MODULE("Module Configs", "module"),
        BIND("Bind Configs", "bind"),
        ONLINE("Online Configs", "online");

        private final String sidebarLabel;
        private final String emptyName;

        ConfigTab(String sidebarLabel, String emptyName) {
            this.sidebarLabel = sidebarLabel;
            this.emptyName = emptyName;
        }
    }

    private enum TextFocus {
        NONE,
        SEARCH,
        SETTING,
        NUMBER,
        CONFIG_NAME
    }

    private enum ControlType {
        CONFIG_NAME,
        CONFIG_CREATE,
        CONFIG_LOAD,
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

    private record TabBounds(ModuleCategory category, ConfigTab configTab, Rect rect) {
        private boolean isConfig() {
            return configTab != null;
        }
    }

    private record ModuleRowBounds(Module module, Rect rect) {
    }

    private record ConfigEntry(String name, ConfigTab type, boolean current) {
    }

    private record ScrollState(float listScroll, float targetListScroll) {
    }

    private record ControlBounds(ControlType type, Rect rect, Object target, Object owner, int index) {
    }

    private record SliderDrag(ControlBounds control) {
    }

    private record ScrollbarDrag(float offsetY) {
    }

    private record PendingControlGlass(float x, float y, float width, float height, float radius, float alphaAtQueue, boolean dark, float brightness) {
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
