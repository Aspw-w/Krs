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
    private static final float CLICK_GUI_SCALE = 1.4f;
    private static final float PANEL_FOOTER_HEIGHT = 22f;
    private static final float SETTINGS_PANEL_HEADER_HEIGHT = 44f;

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
    private float clickGuiScale = 1f;
    private float clickGuiScaleOriginX;
    private float clickGuiScaleOriginY;
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
    private Module bindingModule;
    private KeyBindValue bindingValue;
    private Module openedListModule;
    private ListValue openedListValue;
    private SliderDrag activeSlider;
    private ScrollbarDrag activeScrollbar;
    private ScrollbarDrag activeSettingsPanelScrollbar;
    private Module settingsPanelModule;
    private int settingsPanelControlStartIndex;

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
        Client.nanoVgManager.load(nanoVgRenderer);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing)
            return true;

        if (bindingModule != null) {
            bindingModule.key = - (event.button() + 1);
            bindingModule = null;
            return true;
        }

        if (bindingValue != null) {
            bindingValue.set(- (event.button() + 1));
            bindingValue = null;
            return true;
        }

        updateClickGuiTransform(NanoVGManager.getScaledScreenWidth(), NanoVGManager.getScaledScreenHeight());
        float mouseX = toClickGuiMouseX(NanoVGManager.toScaledMouseX(event.x()));
        float mouseY = toClickGuiMouseY(NanoVGManager.toScaledMouseY(event.y()));
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

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                for (int i = settingsPanelControlStartIndex; i < controls.size(); i++) {
                    ControlBounds control = controls.get(i);
                    if (control.rect.contains(mouseX, mouseY) && handleControlClick(control, mouseX, button))
                        return true;
                }

                if (!settingsPanelRect.contains(mouseX, mouseY)) {
                    closeSettingsPanel();
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

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            for (ControlBounds control : controls) {
                if (control.rect.contains(mouseX, mouseY) && handleControlClick(control, mouseX, button))
                    return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && searchRect.contains(mouseX, mouseY)) {
            focusSearch();
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (TabBounds tab : tabBounds) {
                if (tab.rect.contains(mouseX, mouseY)) {
                    storeCurrentCategoryScroll();
                    if (tab.configTab) {
                        configView = true;
                        searchQuery = "";
                        resetListScroll();
                    } else {
                        selectedCategory = tab.category;
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
                row.module.toggle();
                return true;
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                closeListDropdown();
                openSettingsPanel(row.module);
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
        if (activeSettingsPanelScrollbar != null) {
            updateClickGuiTransform(NanoVGManager.getScaledScreenWidth(), NanoVGManager.getScaledScreenHeight());
            updateSettingsPanelScrollbarDrag(toClickGuiMouseY(NanoVGManager.toScaledMouseY(event.y())));
            return true;
        }

        if (activeScrollbar != null) {
            updateClickGuiTransform(NanoVGManager.getScaledScreenWidth(), NanoVGManager.getScaledScreenHeight());
            updateScrollbarDrag(toClickGuiMouseY(NanoVGManager.toScaledMouseY(event.y())));
            return true;
        }

        if (activeSlider == null) return false;

        updateClickGuiTransform(NanoVGManager.getScaledScreenWidth(), NanoVGManager.getScaledScreenHeight());
        updateSlider(activeSlider, toClickGuiMouseX(NanoVGManager.toScaledMouseX(event.x())));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        activeSlider = null;
        activeScrollbar = null;
        activeSettingsPanelScrollbar = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        updateClickGuiTransform(NanoVGManager.getScaledScreenWidth(), NanoVGManager.getScaledScreenHeight());
        float scaledX = toClickGuiMouseX(NanoVGManager.toScaledMouseX(mouseX));
        float scaledY = toClickGuiMouseY(NanoVGManager.toScaledMouseY(mouseY));

        if (settingsPanelModule != null) {
            if (settingsPanelViewport.contains(scaledX, scaledY)
                    || settingsPanelScrollbarTrackRect.contains(scaledX, scaledY)) {
                float previousTarget = targetSettingsPanelScroll;
                float scrollStep = Math.clamp(settingsPanelViewport.height * 0.095f, 32f, 58f);
                targetSettingsPanelScroll = Math.clamp(
                        targetSettingsPanelScroll - (float) vertical * scrollStep,
                        0f,
                        maxSettingsPanelScroll
                );
                settingsPanelScrollVelocity = Math.clamp(
                        settingsPanelScrollVelocity + (targetSettingsPanelScroll - previousTarget) * 0.075f,
                        -18f,
                        18f
                );
            }
            return true;
        }

        if (listViewport.contains(scaledX, scaledY) || scrollbarTrackRect.contains(scaledX, scaledY)) {
            float previousTarget = targetListScroll;
            float scrollStep = Math.clamp(listViewport.height * 0.095f, 32f, 58f);
            targetListScroll = Math.clamp(targetListScroll - (float) vertical * scrollStep, 0f, maxListScroll);
            scrollVelocity = Math.clamp(scrollVelocity + (targetListScroll - previousTarget) * 0.075f, -18f, 18f);
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

        boolean searchShortcut = key == GLFW.GLFW_KEY_F
                && (event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (searchShortcut) {
            focusSearch();
            return true;
        }

        if (textFocus != TextFocus.NONE) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (textFocus == TextFocus.SEARCH && !searchQuery.isBlank())
                    clearSearch();
                clearTextFocus();
                return true;
            }

            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (textFocus == TextFocus.CONFIG_NAME)
                    createConfigFromInput();
                else
                    clearTextFocus();
                return true;
            }

            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                removeLastFocusedCharacter();
                return true;
            }

            if (key == GLFW.GLFW_KEY_DELETE) {
                setFocusedText("");
                return true;
            }
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (openedListValue != null) {
                closeListDropdown();
                return true;
            }
            if (settingsPanelModule != null) {
                closeSettingsPanel();
                return true;
            }
            if (!searchQuery.isBlank()) {
                clearSearch();
                return true;
            }
            onClose();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (textFocus == TextFocus.NONE || !event.isAllowedChatCharacter())
            return false;

        String input = event.codepointAsString();
        if (input != null && !input.isEmpty())
            setFocusedText(getFocusedText() + input);

        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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
        activeScrollbar = null;
        activeSettingsPanelScrollbar = null;
        bindingModule = null;
        bindingValue = null;
        clearTextFocus();
        super.removed();
    }

    @Override
    public void onClose() {
        if (closing)
            return;

        rememberUiState();
        activeSlider = null;
        activeScrollbar = null;
        activeSettingsPanelScrollbar = null;
        bindingModule = null;
        bindingValue = null;
        clearTextFocus();
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

        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        Rect panel = panelBounds(screenWidth, screenHeight);
        updateClickGuiTransform(screenWidth, screenHeight, panel);
        updateScaledMouse();
        boolean staticFallbackBackdrop = !isInGame() && !hasBackgroundScreen();

        if (staticFallbackBackdrop)
            renderBackdrop(vg, screenWidth, screenHeight);

        vg.globalAlpha(easeOut(openProgress), () -> {
            if (!staticFallbackBackdrop)
                renderBackdrop(vg, screenWidth, screenHeight);
            vg.save();
            try {
                vg.scale(clickGuiScaleOriginX, clickGuiScaleOriginY, clickGuiScale);
                renderPanelEffects(vg, panel.x, panel.y, panel.width, panel.height);
                renderPanel(vg, panel.x, panel.y, panel.width, panel.height);
                renderSettingsPanel(vg, panel);
            } finally {
                vg.restore();
            }
        });
    }

    private static Rect panelBounds(float screenWidth, float screenHeight) {
        float panelWidth = Math.min(760f, Math.max(520f, screenWidth - 44f));
        float panelHeight = Math.min(570f, Math.max(380f, screenHeight - 44f));
        return new Rect((screenWidth - panelWidth) / 2f, (screenHeight - panelHeight) / 2f, panelWidth, panelHeight);
    }

    private void updateClickGuiTransform(float screenWidth, float screenHeight) {
        updateClickGuiTransform(screenWidth, screenHeight, panelBounds(screenWidth, screenHeight));
    }

    private void updateClickGuiTransform(float screenWidth, float screenHeight, Rect panel) {
        clickGuiScaleOriginX = panel.centerX();
        clickGuiScaleOriginY = panel.centerY();
        float maxScaleX = screenWidth / Math.max(1f, panel.width);
        float maxScaleY = screenHeight / Math.max(1f, panel.height);
        clickGuiScale = Math.clamp(Math.min(CLICK_GUI_SCALE, Math.min(maxScaleX, maxScaleY)), 1f, CLICK_GUI_SCALE);
    }

    private void updateScaledMouse() {
        scaledMouseX = toClickGuiMouseX(screenMouseX);
        scaledMouseY = toClickGuiMouseY(screenMouseY);
    }

    private float toClickGuiMouseX(float mouseX) {
        return clickGuiScaleOriginX + (mouseX - clickGuiScaleOriginX) / Math.max(1f, clickGuiScale);
    }

    private float toClickGuiMouseY(float mouseY) {
        return clickGuiScaleOriginY + (mouseY - clickGuiScaleOriginY) / Math.max(1f, clickGuiScale);
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

    private void renderBackdrop(NVGU vg, float width, float height) {
        if (!isInGame() && !hasBackgroundScreen()) {
            renderTitleBackdrop(vg, width, height);
            return;
        }

        vg.beginEffectBatch();
        vg.blurRoundedRectangle(0f, 0f, width, height, 0f, 7f, isInGame() ? 0.26f : 0.18f);
        vg.flushEffectBatch();
        vg.rectangle(0f, 0f, width, height, alpha(0, 0, 0, isInGame() ? 92 : 86));
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

        vg.beginEffectBatch();
        vg.blurRoundedRectangle(0f, 0f, width, height, 0f, 7f, 0.18f);
        vg.flushEffectBatch();
        vg.rectangle(0f, 0f, width, height, alpha(0, 0, 0, 86));
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
        vg.blurRoundedRectangle(x, y, width, height, 9f, 7f, 0.42f);
        vg.shadowRoundedRectangle(x, y, width, height, 9f, 18f, 4f, 0f, 5f, alpha(0, 0, 0, 132));
        vg.flushEffectBatch();
    }

    private void renderPanel(NVGU vg, float x, float y, float width, float height) {
        vg.roundedRectangle(x, y, width, height, 9f, alpha(0, 0, 0, 154));
        vg.roundedRectangleBorder(x, y, width, height, 9f, 1f, alpha(255, 255, 255, 44), Border.INSIDE);

        renderHeader(vg, x, y, width);
        renderTabs(vg, x + 10f, y + 42f, width - 20f);
        float contentY = y + 42f + 30f + 10f;
        float contentHeight = height - 42f - 30f - 20f - PANEL_FOOTER_HEIGHT;
        int visibleCount = configView
                ? renderConfigList(vg, x + 10f, contentY, width - 20f, contentHeight)
                : renderModuleList(vg, x + 10f, contentY, width - 20f, contentHeight);
        renderFooter(vg, x + 10f, y + height - PANEL_FOOTER_HEIGHT - 4f, width - 20f, visibleCount);
    }

    private void renderSettingsPanel(NVGU vg, Rect parentPanel) {
        settingsPanelControlStartIndex = controls.size();

        Module module = settingsPanelModule;
        if (module == null) {
            settingsPanelRect = new Rect(0f, 0f, 0f, 0f);
            settingsPanelCloseRect = new Rect(0f, 0f, 0f, 0f);
            settingsPanelViewport = new Rect(0f, 0f, 0f, 0f);
            settingsPanelScrollbarTrackRect = new Rect(0f, 0f, 0f, 0f);
            settingsPanelScrollbarThumbRect = new Rect(0f, 0f, 0f, 0f);
            maxSettingsPanelScroll = 0f;
            return;
        }

        float width = Math.min(440f, parentPanel.width - 54f);
        float height = Math.min(480f, parentPanel.height - 46f);
        settingsPanelRect = new Rect(
                parentPanel.centerX() - width / 2f,
                parentPanel.centerY() - height / 2f,
                width,
                height
        );
        settingsPanelCloseRect = new Rect(
                settingsPanelRect.x + settingsPanelRect.width - 34f,
                settingsPanelRect.y + 10f,
                24f,
                24f
        );

        float progress = easeOut(animate("settings-panel-open", true, 0.14f));
        vg.globalAlpha(progress, () -> {
            vg.roundedRectangle(
                    parentPanel.x,
                    parentPanel.y,
                    parentPanel.width,
                    parentPanel.height,
                    9f,
                    alpha(0, 0, 0, 142)
            );

            vg.beginEffectBatch();
            vg.shadowRoundedRectangle(
                    settingsPanelRect.x,
                    settingsPanelRect.y,
                    settingsPanelRect.width,
                    settingsPanelRect.height,
                    9f,
                    22f,
                    4f,
                    0f,
                    6f,
                    alpha(0, 0, 0, 178)
            );
            vg.flushEffectBatch();

            vg.roundedRectangle(
                    settingsPanelRect.x,
                    settingsPanelRect.y,
                    settingsPanelRect.width,
                    settingsPanelRect.height,
                    9f,
                    alpha(7, 10, 14, 244)
            );
            vg.roundedRectangleBorder(
                    settingsPanelRect.x,
                    settingsPanelRect.y,
                    settingsPanelRect.width,
                    settingsPanelRect.height,
                    9f,
                    1f,
                    alpha(0, 255, 255, 94),
                    Border.INSIDE
            );
            vg.rectangle(
                    settingsPanelRect.x + 1f,
                    settingsPanelRect.y + SETTINGS_PANEL_HEADER_HEIGHT,
                    settingsPanelRect.width - 2f,
                    1f,
                    alpha(255, 255, 255, 30)
            );

            NVGFonts.ICON.drawText(
                    MaterialIcon.TUNE,
                    settingsPanelRect.x + 15f,
                    settingsPanelRect.y + 14f,
                    14f,
                    new Color(0, 255, 255),
                    Alignment.LEFT_TOP,
                    false
            );
            NVGFonts.INTER_MEDIUM.drawText(
                    fitText(module.moduleName, NVGFonts.INTER_MEDIUM, 14f, settingsPanelRect.width - 150f),
                    settingsPanelRect.x + 38f,
                    settingsPanelRect.y + 11f,
                    14f,
                    alpha(255, 255, 255, 245),
                    Alignment.LEFT_TOP,
                    true
            );
            NVGFonts.INTER.drawText(
                    module.moduleCategory.name(),
                    settingsPanelRect.x + 39f,
                    settingsPanelRect.y + 27f,
                    9f,
                    alpha(120, 130, 140, 220),
                    Alignment.LEFT_TOP,
                    false
            );

            boolean closeHovered = settingsPanelCloseRect.contains(scaledMouseX, scaledMouseY);
            float closeProgress = animate("settings-panel-close", closeHovered, 0.18f);
            vg.roundedRectangle(
                    settingsPanelCloseRect.x,
                    settingsPanelCloseRect.y,
                    settingsPanelCloseRect.width,
                    settingsPanelCloseRect.height,
                    6f,
                    mix(alpha(255, 255, 255, 10), alpha(255, 74, 74, 50), closeProgress)
            );
            NVGFonts.ICON.drawText(
                    MaterialIcon.CLOSE,
                    settingsPanelCloseRect.centerX(),
                    settingsPanelCloseRect.centerY() - 1f,
                    13f,
                    mix(alpha(176, 186, 196, 230), alpha(255, 195, 195, 245), closeProgress),
                    Alignment.CENTER_MIDDLE,
                    false
            );

            settingsPanelViewport = new Rect(
                    settingsPanelRect.x + 10f,
                    settingsPanelRect.y + SETTINGS_PANEL_HEADER_HEIGHT + 8f,
                    settingsPanelRect.width - 25f,
                    settingsPanelRect.height - SETTINGS_PANEL_HEADER_HEIGHT - 18f
            );
            float contentHeight = expandedSettingsHeight(module);
            maxSettingsPanelScroll = Math.max(0f, contentHeight - settingsPanelViewport.height);
            targetSettingsPanelScroll = Math.clamp(targetSettingsPanelScroll, 0f, maxSettingsPanelScroll);
            settingsPanelScroll = Math.clamp(settingsPanelScroll, 0f, maxSettingsPanelScroll);
            if (maxSettingsPanelScroll <= 0f)
                settingsPanelScrollVelocity = 0f;

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
            renderSettingsPanelScrollbar(vg, contentHeight);
        });
    }

    private void renderHeader(NVGU vg, float x, float y, float width) {
        NVGFonts.ICON.drawText(MaterialIcon.MENU, x + 14f, y + 12f, 14f, new Color(0, 255, 255), Alignment.LEFT_TOP, true);
        NVGFonts.INTER_MEDIUM.drawText(configView ? "Configs" : "Modules", x + 34f, y + 12f, 15f, alpha(255, 255, 255, 235), Alignment.LEFT_TOP, true);

        closeRect = new Rect(x + width - 30f, y + 11f, 18f, 18f);
        searchRect = new Rect(x + width - 242f, y + 10f, 196f, 20f);

        renderSearch(vg, searchRect);

        boolean closeHovered = closeRect.contains(scaledMouseX, scaledMouseY);
        vg.roundedRectangle(closeRect.x, closeRect.y, closeRect.width, closeRect.height, 4f, closeHovered ? alpha(255, 90, 90, 120) : alpha(255, 255, 255, 18));
        NVGFonts.ICON.drawText(MaterialIcon.CLOSE, closeRect.centerX(), closeRect.centerY() - 1f, 14f, closeHovered ? Color.WHITE : alpha(176, 186, 196, 220), Alignment.CENTER_MIDDLE, false);

        vg.rectangle(x + 10f, y + 42f - 1f, width - 20f, 1f, alpha(255, 255, 255, 40));
    }

    private void renderSearch(NVGU vg, Rect rect) {
        boolean focused = textFocus == TextFocus.SEARCH;
        boolean hovered = rect.contains(scaledMouseX, scaledMouseY);
        vg.roundedRectangle(rect.x, rect.y, rect.width, rect.height, 5f, alpha(255, 255, 255, focused ? 30 : hovered ? 23 : 16));
        vg.roundedRectangleBorder(rect.x, rect.y, rect.width, rect.height, 5f, 1f, focused ? alpha(0, 255, 255, 96) : alpha(255, 255, 255, 30), Border.INSIDE);
        NVGFonts.ICON.drawText(MaterialIcon.SEARCH, rect.x + 7f, rect.y + 5f, 12f, focused ? new Color(0, 255, 255) : alpha(176, 186, 196, 220), Alignment.LEFT_TOP, false);

        String text = inputText(searchQuery, focused, "Search");
        Color color = searchQuery.isBlank() && !focused ? alpha(120, 130, 140, 205) : alpha(255, 255, 255, 235);
        float textReserve = searchQuery.isBlank() ? 33f : 51f;
        NVGFonts.INTER.drawText(fitText(text, NVGFonts.INTER, 11f, rect.width - textReserve), rect.x + 24f, rect.y + 5f, 11f, color, Alignment.LEFT_TOP, false);

        if (searchQuery.isBlank()) {
            searchClearRect = new Rect(0f, 0f, 0f, 0f);
            return;
        }

        searchClearRect = new Rect(rect.x + rect.width - 19f, rect.y + 2f, 16f, 16f);
        boolean clearHovered = searchClearRect.contains(scaledMouseX, scaledMouseY);
        vg.roundedRectangle(searchClearRect.x, searchClearRect.y, searchClearRect.width, searchClearRect.height, 4f,
                clearHovered ? alpha(255, 255, 255, 28) : alpha(255, 255, 255, 10));
        NVGFonts.ICON.drawText(MaterialIcon.CLOSE, searchClearRect.centerX(), searchClearRect.centerY() - 1f, 11f,
                clearHovered ? Color.WHITE : alpha(176, 186, 196, 210), Alignment.CENTER_MIDDLE, false);
    }

    private void renderFooter(NVGU vg, float x, float y, float width, int visibleCount) {
        vg.rectangle(x, y - 2f, width, 1f, alpha(255, 255, 255, 28));

        String hint = interactionHint();
        String countLabel;
        if (!searchQuery.isBlank())
            countLabel = visibleCount + (visibleCount == 1 ? " result" : " results");
        else if (configView)
            countLabel = visibleCount + (visibleCount == 1 ? " config" : " configs");
        else
            countLabel = visibleCount + (visibleCount == 1 ? " module" : " modules");
        float countWidth = NVGFonts.INTER.getWidth(countLabel, 10f);
        NVGFonts.INTER.drawText(
                fitText(hint, NVGFonts.INTER, 10f, Math.max(40f, width - countWidth - 18f)),
                x + 2f,
                y + 5f,
                10f,
                alpha(150, 160, 170, 215),
                Alignment.LEFT_TOP,
                false
        );
        NVGFonts.INTER_MEDIUM.drawText(countLabel, x + width - 2f, y + 5f, 10f,
                searchQuery.isBlank() ? alpha(176, 186, 196, 220) : alpha(0, 255, 255, 225),
                Alignment.RIGHT_TOP, false);
    }

    private String interactionHint() {
        if (bindingModule != null || bindingValue != null)
            return "Press a key";
        if (textFocus == TextFocus.CONFIG_NAME)
            return "Enter to create";
        if (textFocus == TextFocus.SEARCH)
            return "Type to filter";
        if (textFocus == TextFocus.SETTING)
            return "Type to edit, Enter confirm";
        if (openedListValue != null)
            return "Choose an option";
        return "Ctrl+F to search  •  Escape to close";
    }

    private void renderTabs(NVGU vg, float x, float y, float width) {
        ModuleCategory[] categories = ModuleCategory.values();
        float gap = 5f;
        int tabCount = categories.length + 1;
        float tabWidth = (width - gap * (tabCount - 1)) / tabCount;

        for (int i = 0; i < categories.length; i++) {
            ModuleCategory category = categories[i];
            Rect rect = new Rect(x + i * (tabWidth + gap), y + 5f, tabWidth, 22f);
            tabBounds.add(new TabBounds(category, rect, false));

            boolean selected = !configView && searchQuery.isBlank() && selectedCategory == category;
            boolean hovered = rect.contains(scaledMouseX, scaledMouseY);
            float hoverProgress = animate("tab-hover:" + category.name(), hovered, 0.16f);
            float progress = Math.max(selected ? 1f : 0f, hoverProgress);
            vg.roundedRectangle(rect.x, rect.y, rect.width, rect.height, 5f, progress > 0.01f ? alpha(0, 255, 255, (int) (12 + 26 * progress)) : alpha(255, 255, 255, 13));
            if (selected)
                vg.roundedRectangle(rect.x + 6f, rect.y + rect.height - 2f, rect.width - 12f, 2f, 1f, alpha(0, 255, 255, 170));

            Color iconColor = mix(alpha(176, 186, 196, 220), new Color(0, 255, 255), selected ? 1f : hoverProgress * 0.72f);
            Color textColor = mix(alpha(255, 255, 255, 202), alpha(255, 255, 255, 235), selected ? 1f : hoverProgress);
            NVGFonts.ICON.drawText(categoryIcon(category), rect.x + 8f, rect.y + 4f, 12f, iconColor, Alignment.LEFT_TOP, false);
            NVGFonts.INTER.drawText(fitText(category.name(), NVGFonts.INTER, 11f, rect.width - 28f), rect.x + 24f, rect.y + 5f, 11f, textColor, Alignment.LEFT_TOP, false);
        }

        Rect configRect = new Rect(x + categories.length * (tabWidth + gap), y + 5f, tabWidth, 22f);
        tabBounds.add(new TabBounds(null, configRect, true));
        boolean hovered = configRect.contains(scaledMouseX, scaledMouseY);
        float hoverProgress = animate("tab-hover:configs", hovered, 0.16f);
        float progress = Math.max(configView ? 1f : 0f, hoverProgress);
        vg.roundedRectangle(configRect.x, configRect.y, configRect.width, configRect.height, 5f, progress > 0.01f ? alpha(0, 255, 255, (int) (12 + 26 * progress)) : alpha(255, 255, 255, 13));
        if (configView)
            vg.roundedRectangle(configRect.x + 6f, configRect.y + configRect.height - 2f, configRect.width - 12f, 2f, 1f, alpha(0, 255, 255, 170));

        NVGFonts.ICON.drawText(MaterialIcon.FILE_OPEN, configRect.x + 8f, configRect.y + 4f, 12f, mix(alpha(176, 186, 196, 220), new Color(0, 255, 255), configView ? 1f : hoverProgress * 0.72f), Alignment.LEFT_TOP, false);
        NVGFonts.INTER.drawText(fitText("Configs", NVGFonts.INTER, 11f, configRect.width - 28f), configRect.x + 24f, configRect.y + 5f, 11f, mix(alpha(255, 255, 255, 202), alpha(255, 255, 255, 235), configView ? 1f : hoverProgress), Alignment.LEFT_TOP, false);
    }

    private int renderModuleList(NVGU vg, float x, float y, float width, float height) {
        vg.roundedRectangle(x, y, width, height, 7f, alpha(0, 0, 0, 174));
        vg.roundedRectangleBorder(x, y, width, height, 7f, 1f, alpha(255, 255, 255, 32), Border.INSIDE);

        List<Module> modules = visibleModules();
        listViewport = new Rect(x + 5f, y + 5f, width - 10f, height - 10f);
        float contentHeight = contentHeight(modules);
        maxListScroll = Math.max(0f, contentHeight - listViewport.height);
        targetListScroll = Math.clamp(targetListScroll, 0f, maxListScroll);
        listScroll = Math.clamp(listScroll, 0f, maxListScroll);
        if (maxListScroll <= 0f)
            scrollVelocity = 0f;

        withInputClip(listViewport, () -> vg.scissor(listViewport.x, listViewport.y, listViewport.width, listViewport.height, () -> {
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
        vg.roundedRectangle(x, y, width, height, 7f, alpha(0, 0, 0, 174));
        vg.roundedRectangleBorder(x, y, width, height, 7f, 1f, alpha(255, 255, 255, 32), Border.INSIDE);

        float innerX = x + 8f;
        float innerWidth = width - 16f;
        float rowTop = y + 8f;
        renderConfigModeTabs(vg, innerX, rowTop, innerWidth);
        rowTop += 34f;

        if (selectedConfigTab != ConfigTab.ONLINE) {
            renderConfigCreateRow(vg, innerX, rowTop, innerWidth);
            rowTop += 36f;
        }

        List<ConfigEntry> configs = visibleConfigEntries();
        listViewport = new Rect(x + 5f, rowTop, width - 10f, Math.max(42f, y + height - rowTop - 6f));
        float contentHeight = Math.max(32f, configs.size() * 34f + 2f);
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
                        searchQuery.isBlank() ? "Create one above or choose another tab." : "Try another keyword or clear the search."
                );
                return;
            }

            for (int i = 0; i < configs.size(); i++) {
                ConfigEntry config = configs.get(i);
                if (rowY + 34f > listViewport.y && rowY < listViewport.y + listViewport.height)
                    renderConfigRow(vg, config, i, configs.size(), listViewport.x, rowY, listViewport.width);
                rowY += 34f;
            }
        }));

        renderScrollbar(vg, listViewport, contentHeight, listScroll);
        return configs.size();
    }

    private static void renderEmptyState(NVGU vg, Rect viewport, String icon, String title, String detail) {
        float centerX = viewport.centerX();
        float centerY = viewport.centerY() - 7f;
        vg.circle(centerX, centerY - 19f, 17f, alpha(0, 255, 255, 14));
        NVGFonts.ICON.drawText(icon, centerX, centerY - 20f, 17f, alpha(0, 255, 255, 205), Alignment.CENTER_MIDDLE, false);
        NVGFonts.INTER_MEDIUM.drawText(
                fitText(title, NVGFonts.INTER_MEDIUM, 12f, Math.max(40f, viewport.width - 32f)),
                centerX,
                centerY + 5f,
                12f,
                alpha(230, 235, 240, 225),
                Alignment.CENTER_TOP,
                false
        );
        NVGFonts.INTER.drawText(
                fitText(detail, NVGFonts.INTER, 10f, Math.max(40f, viewport.width - 32f)),
                centerX,
                centerY + 23f,
                10f,
                alpha(130, 140, 150, 205),
                Alignment.CENTER_TOP,
                false
        );
    }

    private void renderConfigModeTabs(NVGU vg, float x, float y, float width) {
        ConfigTab[] tabs = ConfigTab.values();
        float gap = 6f;
        float tabWidth = (width - gap * (tabs.length - 1)) / tabs.length;

        for (int i = 0; i < tabs.length; i++) {
            ConfigTab tab = tabs[i];
            Rect rect = new Rect(x + i * (tabWidth + gap), y, tabWidth, 25f);
            addControl(ControlType.CONFIG_TAB, rect, tab, null, 0);

            boolean selected = selectedConfigTab == tab;
            boolean hovered = rect.contains(scaledMouseX, scaledMouseY);
            float hoverProgress = animate("config-tab-hover:" + tab.name(), hovered, 0.16f);
            float progress = Math.max(selected ? 1f : 0f, hoverProgress);
            vg.roundedRectangle(rect.x, rect.y, rect.width, rect.height, 5f, mix(alpha(255, 255, 255, 14), alpha(0, 255, 255, 32), progress));
            vg.roundedRectangleBorder(rect.x, rect.y, rect.width, rect.height, 5f, 1f, mix(alpha(255, 255, 255, 26), alpha(0, 255, 255, 86), progress), Border.INSIDE);

            NVGFonts.ICON.drawText(configIcon(tab), rect.x + 9f, rect.y + 5f, 12f, mix(alpha(176, 186, 196, 220), new Color(0, 255, 255), selected ? 1f : hoverProgress * 0.72f), Alignment.LEFT_TOP, false);
            NVGFonts.INTER.drawText(fitText(tab.label, NVGFonts.INTER, 11f, rect.width - 32f), rect.x + 27f, rect.y + 6f, 11f, mix(alpha(255, 255, 255, 202), alpha(255, 255, 255, 235), selected ? 1f : hoverProgress), Alignment.LEFT_TOP, false);
        }
    }

    private void renderConfigCreateRow(NVGU vg, float x, float y, float width) {
        boolean active = textFocus == TextFocus.CONFIG_NAME;
        Rect input = new Rect(x, y, width - 91f, 24f);
        Rect button = new Rect(x + width - 83f, y, 83f, 24f);
        addControl(ControlType.CONFIG_NAME, input, null, null, 0);
        addControl(ControlType.CONFIG_CREATE, button, null, null, 0);

        boolean inputHovered = input.contains(scaledMouseX, scaledMouseY);
        vg.roundedRectangle(input.x, input.y, input.width, input.height, 5f, alpha(255, 255, 255, active ? 29 : inputHovered ? 22 : 16));
        vg.roundedRectangleBorder(input.x, input.y, input.width, input.height, 5f, 1f, active ? alpha(0, 255, 255, 90) : alpha(255, 255, 255, 28), Border.INSIDE);

        String placeholder = selectedConfigTab == ConfigTab.MODULE ? "New module config" : "New bind config";
        String text = inputText(newConfigName, active, placeholder);
        NVGFonts.ICON.drawText(MaterialIcon.ADD, input.x + 8f, input.y + 5f, 12f, active ? new Color(0, 255, 255) : alpha(176, 186, 196, 220), Alignment.LEFT_TOP, false);
        NVGFonts.INTER.drawText(fitText(text, NVGFonts.INTER, 11f, input.width - 32f), input.x + 26f, input.y + 6f, 11f, newConfigName.isBlank() && !active ? alpha(120, 130, 140, 205) : alpha(255, 255, 255, 235), Alignment.LEFT_TOP, false);

        boolean canCreate = !cleanConfigName(newConfigName).isBlank();
        boolean hovered = button.contains(scaledMouseX, scaledMouseY);
        float progress = animate("config-create", canCreate && hovered, 0.18f);
        vg.roundedRectangle(button.x, button.y, button.width, button.height, 5f, canCreate ? alpha(0, 255, 255, (int) (35 + 22 * progress)) : alpha(255, 255, 255, 14));
        vg.roundedRectangleBorder(button.x, button.y, button.width, button.height, 5f, 1f, canCreate ? alpha(0, 255, 255, 88) : alpha(255, 255, 255, 24), Border.INSIDE);
        NVGFonts.INTER_MEDIUM.drawText("Create", button.centerX(), button.y + 6f, 11f, canCreate ? alpha(255, 255, 255, 235) : alpha(120, 130, 140, 205), Alignment.CENTER_TOP, false);
    }

    private void renderConfigRow(NVGU vg, ConfigEntry config, int index, int visibleCount, float x, float y, float width) {
        Rect row = new Rect(x, y, width, 34f - 4f);
        boolean showDelete = config.type != ConfigTab.ONLINE;
        boolean deletable = showDelete && !config.current;
        Rect delete = showDelete ? new Rect(row.x + row.width - 28f, row.y + 5f, 20f, 20f) : null;
        Rect load = showDelete ? new Rect(row.x, row.y, Math.max(1f, row.width - 34f), row.height) : row;
        addControl(ControlType.CONFIG_LOAD, load, config, null, 0);
        if (deletable)
            addControl(ControlType.CONFIG_DELETE, delete, config, null, 0);

        boolean hovered = isHovered(row);
        float hoverProgress = animate("config-hover:" + config.type.name() + ":" + config.name, hovered, 0.16f);
        float currentProgress = easeOut(animate("config-current:" + config.type.name() + ":" + config.name, config.current, 0.12f));
        float progress = Math.max(currentProgress, hoverProgress);
        Color accent = new Color(0, 255, 255);
        vg.roundedRectangle(row.x, row.y + 2f, row.width, row.height - 4f, 5f, mix(alpha(255, 255, 255, 12), alpha(0, 255, 255, (int) (30 + 12 * currentProgress)), progress));
        if (currentProgress > 0.01f)
            vg.roundedRectangle(row.x + 3f, row.y + 7f, 2f, row.height - 14f, 1f, alpha(0, 255, 255, (int) (175 * currentProgress)));

        vg.circle(row.x + 16f, row.y + row.height / 2f, 3.7f, mix(alpha(120, 120, 120, 220), accent, Math.max(currentProgress, 0.46f + hoverProgress * 0.34f)));
        NVGFonts.ICON.drawText(configIcon(config.type), row.x + 29f, row.y + 7f, 12f, mix(alpha(176, 186, 196, 220), new Color(0, 255, 255), Math.max(currentProgress, hoverProgress * 0.72f)), Alignment.LEFT_TOP, false);
        float nameReserve = showDelete ? (config.current ? 158f : 86f) : 148f;
        float nameWidth = Math.max(28f, row.width - nameReserve);
        NVGFonts.INTER.drawText(fitText(config.name, NVGFonts.INTER, 13f, nameWidth), row.x + 48f, row.y + 7f, 13f, mix(alpha(255, 255, 255, 208), alpha(255, 255, 255, 235), Math.max(currentProgress, hoverProgress)), Alignment.LEFT_TOP, true);

        if (currentProgress > 0.01f) {
            Rect current = new Rect(row.x + row.width - (showDelete ? 101f : 74f), row.y + 6f, 62f, 17f);
            vg.roundedRectangle(current.x, current.y, current.width, current.height, 8f, alpha(0, 255, 255, (int) (28 * currentProgress)));
            vg.roundedRectangleBorder(current.x, current.y, current.width, current.height, 8f, 1f, alpha(0, 255, 255, (int) (74 * currentProgress)), Border.INSIDE);
            NVGFonts.INTER.drawText("Current", current.centerX(), current.y + 4f, 9f, alpha(0, 255, 255, (int) (255 * currentProgress)), Alignment.CENTER_TOP, false);
        }

        if (delete != null) {
            boolean deleteHovered = deletable && isHovered(delete);
            float deleteProgress = animate("config-delete:" + config.type.name() + ":" + config.name, deleteHovered, 0.18f);
            vg.roundedRectangle(delete.x, delete.y, delete.width, delete.height, 5f, deletable ? mix(alpha(255, 255, 255, 12), alpha(255, 74, 74, 52), deleteProgress) : alpha(255, 255, 255, 8));
            vg.roundedRectangleBorder(delete.x, delete.y, delete.width, delete.height, 5f, 1f, deletable ? mix(alpha(255, 255, 255, 24), alpha(255, 96, 96, 110), deleteProgress) : alpha(255, 255, 255, 16), Border.INSIDE);
            Color deleteColor = !deletable ? alpha(120, 130, 140, 135) : deleteHovered ? alpha(255, 195, 195, 245) : alpha(176, 186, 196, 220);
            NVGFonts.ICON.drawText(MaterialIcon.DELETE, delete.centerX(), delete.centerY() - 1f, 12f, deleteColor, Alignment.CENTER_MIDDLE, false);
        }
    }

    private void renderModuleBlock(NVGU vg, Module module, int index, int visibleCount, float x, float y, float width) {
        Rect row = new Rect(x, y, width, 32f - 3f);
        addModuleRow(module, row);

        float expandProgress = expansionProgress(module);
        boolean hovered = isHovered(row);
        float hover = animate("module-hover:" + module.moduleName, hovered, 0.16f);
        float rowState = Math.max(hover, expandProgress);
        float enabled = animateIdentity(enabledAnimations, enabledAnimationFrames, module, module.tempEnabled, 0.12f);
        Color accent = new Color(0, 255, 255);

        float rowGlow = Math.max(rowState, enabled * 0.55f);
        vg.roundedRectangle(row.x, row.y + 2f, row.width, row.height - 4f, 5f, rowGlow > 0.01f ? alpha(0, 255, 255, (int) (10 + 22 * rowState + 12 * enabled)) : alpha(255, 255, 255, 12));
        if (expandProgress > 0.01f)
            vg.roundedRectangle(row.x + 3f, row.y + 7f, 2f, row.height - 14f, 1f, alpha(0, 255, 255, (int) (175 * easeOut(expandProgress))));

        vg.circle(row.x + 16f, row.y + row.height / 2f, 3.7f, mix(alpha(120, 120, 120, 220), accent, Math.max(enabled, hover * 0.38f)));

        float rightReserve = 78f;

        Color nameColor = mix(mix(alpha(255, 255, 255, 208), alpha(255, 255, 255, 235), hover), new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 245), enabled);
        NVGFonts.INTER.drawText(fitText(module.moduleName, NVGFonts.INTER, 13f, row.width - rightReserve), row.x + 29f, row.y + 7f, 13f, nameColor, Alignment.LEFT_TOP, true);

        if (!searchQuery.isBlank()) {
            Color metaColor = mix(alpha(120, 130, 140, 205), alpha(176, 186, 196, 225), hover);
            NVGFonts.INTER.drawText(module.moduleCategory.name(), row.x + row.width - 54f, row.y + 9f, 10f, metaColor, Alignment.RIGHT_TOP, false);
        }

        drawSwitch(vg, row.x + row.width - 40f, row.y + 7f, 31f, 15f, enabled);

    }

    private void renderExpandedSettings(NVGU vg, Module module, float x, float y, float width) {
        float height = expandedSettingsHeight(module);
        vg.roundedRectangle(x, y + 1f, width, height - 5f, 6f, alpha(0, 0, 0, 72));
        vg.roundedRectangleBorder(x, y + 1f, width, height - 5f, 6f, 1f, alpha(255, 255, 255, 28), Border.INSIDE);

        float rowY = y + 5f;
        rowY = renderBaseSettingRows(vg, module, x + 6f, rowY, width - 12f);
        List<SettingValue<?>> settings = collectSettings(module);

        if (!hasRenderableSettings(module)) {
            NVGFonts.INTER.drawText("No settings", x + 10f, rowY + 7f, 11f, alpha(120, 130, 140, 205), Alignment.LEFT_TOP, false);
            return;
        }

        for (SettingValue<?> setting : settings) {
            float progress = settingVisibilityProgress(setting);
            if (progress <= 0.001f)
                continue;

            float settingHeight = (34f + listDropdownHeight(module, setting)) * easeOut(progress);
            float finalRowY = rowY;
            Rect settingClip = new Rect(x + 6f, rowY, width - 12f, settingHeight);
            vg.scissor(settingClip.x, settingClip.y, settingClip.width, settingClip.height, () ->
                    withInputClip(settingClip, () ->
                            vg.globalAlpha(easeOut(progress), () -> renderSetting(vg, module, setting, x + 6f, finalRowY, width - 12f))));
            rowY += settingHeight;
        }
    }

    private float renderBaseSettingRows(NVGU vg, Module module, float x, float y, float width) {
        Rect array = new Rect(x, y, width, 27f);
        addControl(ControlType.SHOW_ON_ARRAY, array, module, null, 0);
        renderSettingRow(vg, array, "Show on array", null);
        drawSwitch(vg, array.x + array.width - 40f, array.y + 6f, 31f, 15f, animateIdentity(switchAnimations, switchAnimationFrames, module, module.showOnArray, 0.12f));

        float rowY = renderModuleNote(vg, module, x, y + 34f, width);

        Rect key = new Rect(x, rowY, width, 27f);
        addControl(ControlType.MODULE_KEY, key, module, null, 0);
        renderSettingRow(vg, key, "Keybind", bindingModule == module ? "Press key..." : keyName(module.key));
        NVGFonts.ICON.drawText(MaterialIcon.KEY, key.x + key.width - 9f, key.y + 6f, 12f, bindingModule == module ? new Color(0, 255, 255) : alpha(176, 186, 196, 220), Alignment.RIGHT_TOP, false);

        return rowY + 34f;
    }

    private float renderModuleNote(NVGU vg, Module module, float x, float y, float width) {
        String note = moduleNote(module);
        if (note.isEmpty())
            return y;

        Rect row = new Rect(x, y, width, 27f);
        boolean hovered = isHovered(row);
        vg.roundedRectangle(row.x, row.y + 1f, row.width, row.height - 2f, 4f, hovered ? alpha(255, 205, 92, 34) : alpha(255, 205, 92, 20));
        vg.roundedRectangleBorder(row.x, row.y + 1f, row.width, row.height - 2f, 4f, 1f, alpha(255, 205, 92, hovered ? 70 : 46), Border.INSIDE);
        NVGFonts.ICON.drawText(MaterialIcon.INFO, row.x + 8f, row.y + 6f, 12f, alpha(255, 205, 92, 235), Alignment.LEFT_TOP, false);
        NVGFonts.INTER_MEDIUM.drawText("Note", row.x + 25f, row.y + 7f, 10f, alpha(255, 205, 92, 235), Alignment.LEFT_TOP, false);
        NVGFonts.INTER.drawText(fitText(note, NVGFonts.INTER, 10f, row.width - 74f), row.x + 66f, row.y + 8f, 10f, alpha(255, 230, 170, 230), Alignment.LEFT_TOP, false);
        return y + 34f;
    }

    private float renderSetting(NVGU vg, Module module, SettingValue<?> setting, float x, float y, float width) {
        Rect row = new Rect(x, y, width, 27f);

        switch (setting) {
            case BooleanValue value -> {
                renderSettingRow(vg, row, value.name, null);
                addControl(ControlType.BOOLEAN, row, value, null, 0);
                drawSwitch(vg, row.x + row.width - 40f, row.y + 6f, 31f, 15f, animateIdentity(switchAnimations, switchAnimationFrames, value, value.get(), 0.12f));
            }
            case ListValue value -> renderListSetting(vg, row, module, value);
            case FloatValue value -> renderFloatSetting(vg, row, value);
            case IntValue value -> renderIntSetting(vg, row, value);
            case ColorValue value -> renderColorSetting(vg, row, value);
            case TextValue value -> renderTextSetting(vg, row, module, value);
            case KeyBindValue value -> renderKeyBindSetting(vg, row, value);
            default -> renderSettingRow(vg, row, setting.name, "Unsupported");
        }

        return 34f + listDropdownHeight(module, setting);
    }

    private void renderSettingRow(NVGU vg, Rect row, String label, String value) {
        boolean hovered = isHovered(row);
        vg.roundedRectangle(row.x, row.y + 1f, row.width, row.height - 2f, 4f, hovered ? alpha(0, 255, 255, 22) : alpha(255, 255, 255, 10));
        NVGFonts.INTER.drawText(fitText(label, NVGFonts.INTER, 11f, row.width * 0.50f), row.x + 8f, row.y + 7f, 11f, alpha(255, 255, 255, 235), Alignment.LEFT_TOP, false);
        if (value != null)
            NVGFonts.INTER.drawText(fitText(value, NVGFonts.INTER, 10f, row.width * 0.36f), row.x + row.width - 25f, row.y + 8f, 10f, alpha(176, 186, 196, 220), Alignment.RIGHT_TOP, false);
    }

    private void renderListSetting(NVGU vg, Rect row, Module module, ListValue value) {
        renderSettingRow(vg, row, value.name, null);
        addControl(ControlType.LIST_VALUE, row, value, module, 0);

        boolean open = openedListModule == module && openedListValue == value;
        float dropdownProgress = listDropdownProgress(module, value);
        float easedDropdown = easeOut(dropdownProgress);
        float valueWidth = Math.max(88f, Math.min(180f, row.width * 0.38f));
        Rect valueRect = new Rect(row.x + row.width - valueWidth - 8f, row.y + 4f, valueWidth, 19f);
        vg.roundedRectangle(valueRect.x, valueRect.y, valueRect.width, valueRect.height, 4f, mix(alpha(255, 255, 255, 18), alpha(0, 255, 255, 30), easedDropdown));
        vg.roundedRectangleBorder(valueRect.x, valueRect.y, valueRect.width, valueRect.height, 4f, 1f, mix(alpha(255, 255, 255, 30), alpha(0, 255, 255, 88), easedDropdown), Border.INSIDE);
        NVGFonts.INTER.drawText(fitText(value.get(), NVGFonts.INTER, 10f, valueRect.width - 12f), valueRect.x + 6f, valueRect.y + 5f, 10f, mix(alpha(255, 255, 255, 235), new Color(0, 255, 255), easedDropdown), Alignment.LEFT_TOP, false);

        if (dropdownProgress > 0.01f)
            renderListDropdown(vg, value, valueRect.x, row.y + 34f - 2f, valueRect.width, easedDropdown, open);
    }

    private void renderListDropdown(NVGU vg, ListValue value, float x, float y, float width, float progress, boolean interactive) {
        float rowHeight = 20f;
        float height = listDropdownBaseHeight(value);
        float visibleHeight = Math.max(1f, height * progress);
        float drawY = y - 4f * (1f - progress);

        Rect dropdownClip = new Rect(x - 3f, y - 3f, width + 6f, visibleHeight + 6f);
        vg.scissor(dropdownClip.x, dropdownClip.y, dropdownClip.width, dropdownClip.height, () ->
                withInputClip(dropdownClip, () -> vg.globalAlpha(progress, () -> {
                    vg.roundedRectangle(x + 1f, drawY + 2f, width, height, 5f, alpha(0, 0, 0, 72));
                    vg.roundedRectangle(x, drawY, width, height, 5f, alpha(0, 0, 0, 190));
                    vg.roundedRectangleBorder(x, drawY, width, height, 5f, 1f, alpha(255, 255, 255, 38), Border.INSIDE);

                    for (int i = 0; i < value.values.length; i++) {
                        String option = value.values[i];
                        float optionProgress = Math.clamp((progress - i * 0.035f) / 0.78f, 0f, 1f);
                        if (optionProgress <= 0.01f)
                            continue;

                        Rect optionRect = new Rect(x + 3f, drawY + 3f + i * rowHeight, width - 6f, rowHeight - 2f);
                        if (interactive && progress > 0.62f)
                            addControl(ControlType.LIST_OPTION, optionRect, value, null, i);

                        boolean selected = i == value.getCurrentIndex();
                        boolean hovered = isHovered(optionRect);
                        float hover = animate("list-option:" + value.name + ":" + i, hovered || selected, 0.18f);
                        float textOffset = 3f * (1f - optionProgress);
                        float finalOptionProgress = optionProgress;
                        vg.globalAlpha(finalOptionProgress, () -> {
                            if (selected || hovered)
                                vg.roundedRectangle(optionRect.x, optionRect.y, optionRect.width, optionRect.height, 4f, selected ? alpha(0, 255, 255, (int) (24 + 20 * hover)) : alpha(255, 255, 255, (int) (10 + 14 * hover)));
                            if (selected)
                                vg.roundedRectangle(optionRect.x + 2f, optionRect.y + 4f, 2f, optionRect.height - 8f, 1f, alpha(0, 255, 255, 170));
                            NVGFonts.INTER.drawText(fitText(option, NVGFonts.INTER, 10f, optionRect.width - 14f), optionRect.x + 8f, optionRect.y + 5f + textOffset, 10f, selected ? new Color(0, 255, 255) : alpha(255, 255, 255, 235), Alignment.LEFT_TOP, false);
                        });
                    }
                })));
    }

    private void renderFloatSetting(NVGU vg, Rect row, FloatValue value) {
        renderSettingRow(vg, row, value.name, String.format(Locale.ROOT, "%.2f%s", value.get(), value.suffix == null ? "" : value.suffix));
        Rect track = new Rect(row.x + row.width - 150f, row.y + 13f, 84f, 4f);
        addControl(ControlType.FLOAT_SLIDER, track.expand(6f, 9f), value, null, 0);
        drawSlider(vg, track, normalize(value.get(), value.minimum, value.maximum));
    }

    private void renderIntSetting(NVGU vg, Rect row, IntValue value) {
        renderSettingRow(vg, row, value.name, value.get() + (value.suffix == null ? "" : value.suffix));
        Rect track = new Rect(row.x + row.width - 150f, row.y + 13f, 84f, 4f);
        addControl(ControlType.INT_SLIDER, track.expand(6f, 9f), value, null, 0);
        drawSlider(vg, track, normalize(value.get(), value.minimum, value.maximum));
    }

    private void renderColorSetting(NVGU vg, Rect row, ColorValue value) {
        renderSettingRow(vg, row, value.name, value.toHex().substring(0, 7));
        Color color = value.get();
        float startX = row.x + row.width - 142f;
        drawColorTrack(vg, value, startX, row.y + 13f, color.getRed(), 0, alpha(255, 80, 80, 230));
        drawColorTrack(vg, value, startX + 34f, row.y + 13f, color.getGreen(), 1, alpha(80, 255, 120, 230));
        drawColorTrack(vg, value, startX + 68f, row.y + 13f, color.getBlue(), 2, alpha(80, 175, 255, 230));
        vg.roundedRectangle(row.x + row.width - 19f, row.y + 7f, 12f, 12f, 3f, color);
        vg.roundedRectangleBorder(row.x + row.width - 19f, row.y + 7f, 12f, 12f, 3f, 1f, alpha(255, 255, 255, 80), Border.INSIDE);
    }

    private void drawColorTrack(NVGU vg, ColorValue value, float x, float y, int component, int index, Color accent) {
        Rect track = new Rect(x, y, 24f, 4f);
        addControl(ControlType.COLOR_SLIDER, track.expand(5f, 9f), value, null, index);
        vg.roundedRectangle(track.x, track.y, track.width, track.height, 2f, alpha(255, 255, 255, 32));
        vg.roundedRectangle(track.x, track.y, track.width * component / 255f, track.height, 2f, accent);
        vg.circle(track.x + track.width * component / 255f, track.centerY(), 3f, Color.WHITE);
    }

    private void renderTextSetting(NVGU vg, Rect row, Module module, TextValue value) {
        boolean active = textFocus == TextFocus.SETTING && focusedTextValue == value;
        renderSettingRow(vg, row, value.name, null);
        Rect input = new Rect(row.x + row.width - 132f, row.y + 4f, 124f, 19f);
        addControl(ControlType.TEXT_VALUE, input, value, module, 0);
        vg.roundedRectangle(input.x, input.y, input.width, input.height, 4f, alpha(255, 255, 255, active ? 28 : 18));
        vg.roundedRectangleBorder(input.x, input.y, input.width, input.height, 4f, 1f, active ? alpha(0, 255, 255, 90) : alpha(255, 255, 255, 30), Border.INSIDE);
        String text = inputText(value.get(), active, "");
        Color textColor = value.get().isEmpty() && !active ? alpha(120, 130, 140, 205) : alpha(255, 255, 255, 235);
        NVGFonts.INTER.drawText(fitText(text, NVGFonts.INTER, 10f, input.width - 10f), input.x + 6f, input.y + 5f, 10f, textColor, Alignment.LEFT_TOP, false);
    }

    private void renderKeyBindSetting(NVGU vg, Rect row, KeyBindValue value) {
        boolean active = bindingValue == value;
        renderSettingRow(vg, row, value.name, active ? "Press key..." : keyName(value.get()));
        addControl(ControlType.KEY_VALUE, row, value, null, 0);
        NVGFonts.ICON.drawText(MaterialIcon.KEY, row.x + row.width - 9f, row.y + 6f, 12f, active ? new Color(0, 255, 255) : alpha(176, 186, 196, 220), Alignment.RIGHT_TOP, false);
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
        clearTextFocus();
        if (control.type != ControlType.MODULE_KEY) bindingModule = null;
        if (control.type != ControlType.KEY_VALUE) bindingValue = null;

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (control.type == ControlType.LIST_VALUE) {
                Module module = (Module) control.owner;
                ListValue value = (ListValue) control.target;
                if (openedListModule == module && openedListValue == value)
                    closeListDropdown();
                else {
                    openedListModule = module;
                    openedListValue = value;
                }
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
            case CONFIG_TAB -> {
                ConfigTab tab = (ConfigTab) control.target;
                if (selectedConfigTab != tab) {
                    selectedConfigTab = tab;
                    targetListScroll = 0f;
                    listScroll = 0f;
                    scrollVelocity = 0f;
                    clearTextFocus();
                }
            }
            case CONFIG_NAME -> textFocus = TextFocus.CONFIG_NAME;
            case CONFIG_CREATE -> createConfigFromInput();
            case CONFIG_LOAD -> loadConfigEntry((ConfigEntry) control.target);
            case CONFIG_DELETE -> deleteConfigEntry((ConfigEntry) control.target);
            case SHOW_ON_ARRAY -> {
                Module module = (Module) control.target;
                module.showOnArray = !module.showOnArray;
                Interface.reloadSortedModules();
            }
            case MODULE_KEY -> bindingModule = (Module) control.target;
            case BOOLEAN -> {
                BooleanValue value = (BooleanValue) control.target;
                value.set(!value.get());
            }
            case LIST_VALUE -> {
                ((ListValue) control.target).nextValue();
                closeListDropdown();
            }
            case LIST_OPTION -> {
                ((ListValue) control.target).setByIndex(control.index);
                closeListDropdown();
            }
            case FLOAT_SLIDER, INT_SLIDER, COLOR_SLIDER -> {
                activeSlider = new SliderDrag(control);
                updateSlider(activeSlider, mouseX);
            }
            case TEXT_VALUE -> {
                textFocus = TextFocus.SETTING;
                focusedTextValue = (TextValue) control.target;
                focusedTextModule = (Module) control.owner;
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

    private void drawSwitch(NVGU vg, float x, float y, float width, float height, float progress) {
        progress = Math.clamp(progress, 0f, 1f);
        vg.roundedRectangle(x, y, width, height, height / 2f, mix(alpha(120, 120, 120, 180), alpha(0, 255, 255, 160), progress));
        vg.circle(x + height / 2f + (width - height) * progress, y + height / 2f, height * 0.34f, Color.WHITE);
    }

    private void drawSlider(NVGU vg, Rect track, float progress) {
        progress = Math.clamp(progress, 0f, 1f);
        vg.roundedRectangle(track.x, track.y, track.width, track.height, 2f, alpha(255, 255, 255, 32));
        vg.roundedRectangle(track.x, track.y, track.width * progress, track.height, 2f, alpha(0, 255, 255, 180));
        vg.circle(track.x + track.width * progress, track.centerY(), 3.2f, Color.WHITE);
    }

    private void renderScrollbar(NVGU vg, Rect viewport, float contentHeight, float scroll) {
        if (contentHeight <= viewport.height + 1f) {
            scrollbarTrackRect = new Rect(0f, 0f, 0f, 0f);
            scrollbarThumbRect = new Rect(0f, 0f, 0f, 0f);
            return;
        }

        float thumbHeight = Math.max(22f, viewport.height * viewport.height / contentHeight);
        float travel = viewport.height - thumbHeight;
        float thumbY = viewport.y + travel * (scroll / Math.max(1f, contentHeight - viewport.height));
        float trackX = viewport.x + viewport.width + 1f;
        scrollbarTrackRect = new Rect(trackX - 5f, viewport.y, 12f, viewport.height);
        scrollbarThumbRect = new Rect(trackX - 1f, thumbY, 5f, thumbHeight);

        boolean hovered = scrollbarTrackRect.contains(scaledMouseX, scaledMouseY) || activeScrollbar != null;
        float progress = animate("scrollbar", hovered, 0.18f);
        vg.roundedRectangle(trackX + 1f, viewport.y + 4f, 2f, viewport.height - 8f, 1f, alpha(255, 255, 255, (int) (18 + 18 * progress)));
        vg.roundedRectangle(scrollbarThumbRect.x, scrollbarThumbRect.y, scrollbarThumbRect.width, scrollbarThumbRect.height, 2.5f, alpha(0, 255, 255, (int) (145 + 55 * progress)));
    }

    private void renderSettingsPanelScrollbar(NVGU vg, float contentHeight) {
        if (contentHeight <= settingsPanelViewport.height + 1f) {
            settingsPanelScrollbarTrackRect = new Rect(0f, 0f, 0f, 0f);
            settingsPanelScrollbarThumbRect = new Rect(0f, 0f, 0f, 0f);
            return;
        }

        float thumbHeight = Math.max(
                22f,
                settingsPanelViewport.height * settingsPanelViewport.height / contentHeight
        );
        float travel = settingsPanelViewport.height - thumbHeight;
        float thumbY = settingsPanelViewport.y + travel
                * (settingsPanelScroll / Math.max(1f, contentHeight - settingsPanelViewport.height));
        float trackX = settingsPanelViewport.x + settingsPanelViewport.width + 2f;
        settingsPanelScrollbarTrackRect = new Rect(
                trackX - 5f,
                settingsPanelViewport.y,
                12f,
                settingsPanelViewport.height
        );
        settingsPanelScrollbarThumbRect = new Rect(trackX - 1f, thumbY, 5f, thumbHeight);

        boolean hovered = settingsPanelScrollbarTrackRect.contains(scaledMouseX, scaledMouseY)
                || activeSettingsPanelScrollbar != null;
        float progress = animate("settings-panel-scrollbar", hovered, 0.18f);
        vg.roundedRectangle(
                trackX + 1f,
                settingsPanelViewport.y + 4f,
                2f,
                settingsPanelViewport.height - 8f,
                1f,
                alpha(255, 255, 255, (int) (18 + 18 * progress))
        );
        vg.roundedRectangle(
                settingsPanelScrollbarThumbRect.x,
                settingsPanelScrollbarThumbRect.y,
                settingsPanelScrollbarThumbRect.width,
                settingsPanelScrollbarThumbRect.height,
                2.5f,
                alpha(0, 255, 255, (int) (145 + 55 * progress))
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
                    || (module.tag() != null && normalize(module.tag()).contains(query));

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
        return Math.max(modules.size() * 32f, 30f);
    }

    private float moduleBlockHeight(Module module) {
        return 32f;
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
        float height = 10f + 34f * 2f + moduleNoteHeight(module);

        if (settings.isEmpty()) {
            height += 30f;
            expandedSettingsHeightCache.put(module, height);
            return height;
        }

        for (SettingValue<?> setting : settings) {
            height += (34f + listDropdownHeight(module, setting)) * easeOut(settingVisibilityProgress(setting));
        }

        if (!hasRenderableSettings(module))
            height += 30f;

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
        return value == null ? 0f : 6f + value.values.length * 20f;
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
        return moduleNote(module).isEmpty() ? 0f : 34f;
    }

    private String moduleNote(Module module) {
        String description = module.description();
        return description == null ? "" : description.trim();
    }

    private float settingVisibilityProgress(SettingValue<?> setting) {
        return animateIdentity(settingVisibilityAnimations, settingVisibilityAnimationFrames, setting, setting.canDisplay.canDisplay(), 0.08f);
    }

    private void openSettingsPanel(Module module) {
        clearInteractionState();
        settingsPanelModule = module;
        settingsPanelScroll = 0f;
        targetSettingsPanelScroll = 0f;
        settingsPanelScrollVelocity = 0f;
        activeSettingsPanelScrollbar = null;
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
        animations.remove("settings-panel-open");
    }

    private void focusSearch() {
        storeCurrentCategoryScroll();
        clearInteractionState();
        textFocus = TextFocus.SEARCH;
    }

    private void clearSearch() {
        searchQuery = "";
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
        activeScrollbar = null;
        activeSettingsPanelScrollbar = null;
        closeListDropdown();
    }

    private void closeListDropdown() {
        openedListModule = null;
        openedListValue = null;
    }

    private void clearTextFocus() {
        textFocus = TextFocus.NONE;
        focusedTextValue = null;
        focusedTextModule = null;
    }

    private String getFocusedText() {
        if (textFocus == TextFocus.SEARCH) return searchQuery;
        if (textFocus == TextFocus.CONFIG_NAME) return newConfigName;
        if (textFocus == TextFocus.SETTING && focusedTextValue != null) return focusedTextValue.get();
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
        }
    }

    private void removeLastFocusedCharacter() {
        String text = getFocusedText();
        if (text.isEmpty()) return;

        int previous = text.offsetByCodePoints(text.length(), -1);
        setFocusedText(text.substring(0, previous));
    }

    private static Rect sliderTrack(ControlBounds control) {
        return switch (control.type) {
            case FLOAT_SLIDER, INT_SLIDER -> control.rect.contract(6f, 9f);
            case COLOR_SLIDER -> control.rect.contract(5f, 9f);
            default -> control.rect;
        };
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

    private static float normalize(float value, float min, float max) {
        if (Math.abs(max - min) <= 0.0001f) return 0f;
        return Math.clamp((value - min) / (max - min), 0f, 1f);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private String inputText(String value, boolean active, String placeholder) {
        String text = value == null ? "" : value;
        if (active)
            return text + (caretVisible() ? "_" : "");
        return text.isBlank() ? (placeholder == null ? "" : placeholder) : text;
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

    private static String categoryIcon(ModuleCategory category) {
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

    private static String configIcon(ConfigTab tab) {
        return switch (tab) {
            case MODULE -> MaterialIcon.FILE_OPEN;
            case BIND -> MaterialIcon.KEY;
            case ONLINE -> MaterialIcon.PUBLIC;
        };
    }

    private static int normalizeKey(int key) {
        return key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE
                ? GLFW.GLFW_KEY_UNKNOWN
                : key;
    }

    private static String keyName(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN)
            return "NONE";

        if (key < 0) {
            return switch (-key) {
                case 1 -> "MOUSE LEFT";
                case 2 -> "MOUSE RIGHT";
                case 3 -> "MOUSE MIDDLE";
                case 4 -> "MOUSE 4";
                case 5 -> "MOUSE 5";
                case 6 -> "MOUSE 6";
                case 7 -> "MOUSE 7";
                case 8 -> "MOUSE 8";
                default -> "MOUSE " + (-key);
            };
        }

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
        MODULE("Module", "module"),
        BIND("Bind", "bind"),
        ONLINE("Online", "online");

        private final String label;
        private final String emptyName;

        ConfigTab(String label, String emptyName) {
            this.label = label;
            this.emptyName = emptyName;
        }
    }

    private enum TextFocus {
        NONE,
        SEARCH,
        SETTING,
        CONFIG_NAME
    }

    private enum ControlType {
        CONFIG_TAB,
        CONFIG_NAME,
        CONFIG_CREATE,
        CONFIG_LOAD,
        CONFIG_DELETE,
        SHOW_ON_ARRAY,
        MODULE_KEY,
        BOOLEAN,
        LIST_VALUE,
        LIST_OPTION,
        FLOAT_SLIDER,
        INT_SLIDER,
        COLOR_SLIDER,
        TEXT_VALUE,
        KEY_VALUE
    }

    private record TabBounds(ModuleCategory category, Rect rect, boolean configTab) {
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
