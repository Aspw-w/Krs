package com.instrumentalist.krs.screen;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.configs.ConfigManager;
import com.instrumentalist.krs.utils.render.TitleBackground;
import com.instrumentalist.krs.utils.GuiInputBlocker;
import com.instrumentalist.krs.utils.audio.Mp3MusicPlayer;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.network.FileUtil;
import com.instrumentalist.krs.utils.render.GraphicsApiCompatibility;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import com.instrumentalist.krs.utils.render.Shader2DRenderer;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.realmsclient.RealmsMainScreen;
import com.instrumentalist.krs.utils.input.Keys;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.NVGFont;

import java.awt.*;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

public class CustomTitleScreen extends Screen implements IMinecraft {

    private static long cachedTimeSecond = -1L;
    private static String cachedTime = "";
    private static final Mp3MusicPlayer MAIN_MENU_MUSIC = new Mp3MusicPlayer("assets/krs/musics/mainmenu.mp3");
    private static final float MUSIC_SLIDER_WIDTH = 180f;
    private static final float MUSIC_SLIDER_TRACK_HEIGHT = 7f;
    private static final Color BUTTON_IDLE = NanoVGTheme.base(168);
    private static final Color BUTTON_HOVERED = NanoVGTheme.base(210);
    private static final Color HOVER_LINE = NanoVGTheme.accent(235);
    private static final Color IDLE_LINE = NanoVGTheme.muted(200);
    private static final Color HOVER_TEXT = NanoVGTheme.TEXT;
    private static final Color IDLE_TEXT = NanoVGTheme.text(200);
    private static final float DEVELOPER_TOGGLE_WIDTH = 196f;
    private static final float DEVELOPER_TOGGLE_HEIGHT = 26f;
    private static final float DEVELOPER_TOGGLE_Y = 84f;
    private static final float DEVELOPER_SWITCH_WIDTH = 28f;
    private static final float DEVELOPER_SWITCH_HEIGHT = 14f;
    private static final String RELEASES_URL = "https://github.com/Aspw-w/Krs/releases";

    private boolean musicVolumeSliderDragging;
    private boolean musicVolumeDirty;
    private float musicSliderGrab;
    private long lastMusicSliderNanos;
    private float developerToggleProgress = -1f;
    private float developerToggleHover;
    private float developerToggleGrab;
    private boolean developerTogglePressed;

    private final MenuButton singlePlayerButton = new MenuButton("Single Player");
    private final MenuButton multiPlayerButton = new MenuButton("Multi Player");
    private final MenuButton realmsButton = new MenuButton("Realms");
    private final MenuButton friendsButton = new MenuButton("Friends");
    private final MenuButton optionsButton = new MenuButton("Options");
    private final MenuButton updateAvailableButton = new MenuButton("Update");
    private final MenuButton exitButton = new MenuButton("Exit");
    private final List<MenuButton> menuButtons = List.of(
            singlePlayerButton,
            multiPlayerButton,
            realmsButton,
            friendsButton,
            optionsButton,
            exitButton
    );
    private final List<MenuButton> outdatedClientMenuButtons = List.of(
            singlePlayerButton,
            multiPlayerButton,
            realmsButton,
            friendsButton,
            optionsButton,
            updateAvailableButton,
            exitButton
    );

    public static float bgOffsetX = 0;
    public static float bgOffsetY = 0;
    private static float bgParallaxScreenWidth = -1f;
    private static float bgParallaxScreenHeight = -1f;
    private static boolean bgParallaxHoldCenter;
    private static float bgParallaxHoldMouseX;
    private static float bgParallaxHoldMouseY;

    public static void stepBackgroundParallax(float targetX, float targetY, float amount) {
        if (!Float.isFinite(targetX) || !Float.isFinite(targetY) || !Float.isFinite(amount))
            return;

        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        if (!(screenWidth > 0f) || !(screenHeight > 0f))
            return;

        if (screenWidth != bgParallaxScreenWidth || screenHeight != bgParallaxScreenHeight
                || !Float.isFinite(bgOffsetX) || !Float.isFinite(bgOffsetY)) {
            bgParallaxScreenWidth = screenWidth;
            bgParallaxScreenHeight = screenHeight;
            bgOffsetX = screenWidth / 2f;
            bgOffsetY = screenHeight / 2f;
            bgParallaxHoldCenter = true;
            bgParallaxHoldMouseX = targetX;
            bgParallaxHoldMouseY = targetY;
            return;
        }

        if (bgParallaxHoldCenter) {
            if (Math.abs(targetX - bgParallaxHoldMouseX) < 0.5f && Math.abs(targetY - bgParallaxHoldMouseY) < 0.5f) {
                targetX = screenWidth / 2f;
                targetY = screenHeight / 2f;
            } else {
                bgParallaxHoldCenter = false;
            }
        }

        bgOffsetX += (targetX - bgOffsetX) * amount;
        bgOffsetY += (targetY - bgOffsetY) * amount;
    }

    public CustomTitleScreen(Component title) {
        super(title);
    }

    public static void stopMainMenuMusic() {
        int frame = MAIN_MENU_MUSIC.stopAndGetFramePosition();
        if (Client.configManager != null)
            Client.configManager.setMainMenuMusicFrame(frame, true);
    }

    public static void resetBackgroundState() {
        bgParallaxScreenWidth = -1f;
        bgParallaxScreenHeight = -1f;
        bgParallaxHoldCenter = false;
        mc.mouseHandler.resyncMousePosition();
        mc.mouseHandler.setIgnoreFirstMove();
        Shader2DRenderer.INSTANCE.invalidateBackdrop();
    }

    @Override
    public void added() {
        super.added();
        resetBackgroundState();
        syncMainMenuMusicVolume();
        MAIN_MENU_MUSIC.playLoop();
    }

    @Override
    protected void init() {
        super.init();
        syncMainMenuMusicVolume();
        MAIN_MENU_MUSIC.playLoop();
    }

    @Override
    public void removed() {
        saveMainMenuMusicVolumeIfDirty();
        stopMainMenuMusic();
        super.removed();
    }

    private void syncMainMenuMusicVolume() {
        if (Client.configManager != null) {
            MAIN_MENU_MUSIC.setVolume(Client.configManager.getMainMenuMusicVolume());
            MAIN_MENU_MUSIC.setStartFrame(Client.configManager.getMainMenuMusicFrame());
        }
    }

    private boolean isMinecraftMouseBlocked() {
        return GuiInputBlocker.shouldBlockMinecraftMouse();
    }

    private boolean isHovered(double mouseX, double mouseY, double x, double y, double width, double height) {
        if (isMinecraftMouseBlocked()) return false;

        float scaledMouseX = NanoVGManager.toScaledMouseX(mouseX);
        float scaledMouseY = NanoVGManager.toScaledMouseY(mouseY);
        return scaledMouseX >= x && scaledMouseX - width <= x && scaledMouseY >= y && scaledMouseY - height <= y;
    }

    private MenuButton updateUiButtonState(MenuButton button, float x, float y, float mouseX, float mouseY) {
        boolean hovered = isHovered(mouseX, mouseY, x - 110f, y - 34f, 220f, 42f);
        float target = hovered ? 24f : 21f;
        button.fontSize = button.fontSize * 0.85f + target * 0.15f;
        button.animation = hovered ? button.animation * 0.8f + 10f : button.animation * 0.8f;
        button.x = x;
        button.y = y;
        button.hovered = hovered;
        return button;
    }

    private void renderUiButton(NVGU vg, MenuButton state) {
        Color baseColor = state.hovered ? BUTTON_HOVERED : BUTTON_IDLE;
        vg.roundedRectangle(state.x - 110f, state.y - 34f, 220f, 38f, NanoVGTheme.RADIUS_PANEL, baseColor);

        if (state.animation > 1f) {
            Color gradientColor = state.hovered ? HOVER_LINE : IDLE_LINE;
            vg.roundedRectangle(state.x - state.animation, state.y - 2.5f, state.animation * 2f, 3f, 2f, gradientColor);
        }

        NVGFont font = state.fontSize > 22f ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
        font.drawText(state.label, state.x, state.y - 15f - (state.fontSize - 21f), state.fontSize, state.hovered ? HOVER_TEXT : IDLE_TEXT, Alignment.CENTER_MIDDLE, true);
    }

    private void renderMusicVolumeSlider(NVGU vg, float centerX, float mouseX, float mouseY) {
        float sliderX = getMusicSliderX(centerX);
        float sliderY = getMusicSliderY();
        float volume = getMainMenuMusicVolume();
        float fill = Math.clamp(volume, 0f, 1f);
        float radius = MUSIC_SLIDER_TRACK_HEIGHT / 2f;
        float grab = updateMusicSliderGrab();
        float knobWidth = 9f * (1f + 0.46f * grab);
        float knobHeight = 13f * (1f - 0.30f * grab);
        float knobX = sliderX + MUSIC_SLIDER_WIDTH * fill - knobWidth / 2f;
        float knobY = sliderY + MUSIC_SLIDER_TRACK_HEIGHT / 2f - knobHeight / 2f;
        String sliderText = volume == 0f ? "Music OFF" : "Volume: " + Math.round(volume * 100f) + "%";
        NVGFonts.INTER.drawText(sliderText, centerX, sliderY - 10f, 12f, Color.WHITE, Alignment.CENTER_MIDDLE, true);
        vg.beginEffectBatch();
        NanoVGTheme.renderControlEffects(vg, sliderX, sliderY, MUSIC_SLIDER_WIDTH, MUSIC_SLIDER_TRACK_HEIGHT, radius, 1f);
        NanoVGTheme.renderControlEffects(vg, knobX, knobY, knobWidth, knobHeight, Math.min(knobWidth, knobHeight) / 2f, 1f);
        vg.flushEffectBatch();
        vg.roundedRectangle(sliderX, sliderY, MUSIC_SLIDER_WIDTH, MUSIC_SLIDER_TRACK_HEIGHT, radius, NanoVGTheme.CONTROL_TRACK);
        if (fill > 0.001f)
            vg.roundedRectangle(sliderX, sliderY, MUSIC_SLIDER_WIDTH * fill, MUSIC_SLIDER_TRACK_HEIGHT, radius, NanoVGTheme.sliderFill(190));
        vg.roundedRectangle(knobX, knobY, knobWidth, knobHeight, Math.min(knobWidth, knobHeight) / 2f, NanoVGTheme.KNOB_FILL);
    }

    private void renderDeveloperToggle(NVGU vg, float centerX, float mouseX, float mouseY) {
        float x = centerX - DEVELOPER_TOGGLE_WIDTH / 2f;
        float y = DEVELOPER_TOGGLE_Y;
        boolean enabled = ConfigManager.isDeveloperFeaturesEnabled();
        boolean hovered = isDeveloperToggleHovered(mouseX, mouseY);
        if (developerToggleProgress < 0f)
            developerToggleProgress = enabled ? 1f : 0f;
        developerToggleProgress += ((enabled ? 1f : 0f) - developerToggleProgress) * 0.2f;
        developerToggleHover += ((hovered ? 1f : 0f) - developerToggleHover) * 0.2f;
        float progress = developerToggleProgress;

        float grabTarget = developerTogglePressed ? 1f : 0f;
        developerToggleGrab += (grabTarget - developerToggleGrab) * (developerTogglePressed ? 0.38f : 0.11f);
        float grab = developerToggleGrab;

        float switchX = x + DEVELOPER_TOGGLE_WIDTH - 10f - DEVELOPER_SWITCH_WIDTH;
        float switchY = y + (DEVELOPER_TOGGLE_HEIGHT - DEVELOPER_SWITCH_HEIGHT) / 2f;
        float switchRadius = DEVELOPER_SWITCH_HEIGHT / 2f;
        float knobBase = DEVELOPER_SWITCH_HEIGHT * 0.74f;
        float knobWidth = knobBase * (1f + 0.38f * grab);
        float knobHeight = knobBase * (1f - 0.28f * grab);
        float knobCenterX = switchX + switchRadius + (DEVELOPER_SWITCH_WIDTH - DEVELOPER_SWITCH_HEIGHT) * progress;
        float knobX = knobCenterX - knobWidth / 2f;
        float knobY = switchY + switchRadius - knobHeight / 2f;
        float knobRadius = Math.min(knobWidth, knobHeight) / 2f;

        vg.beginEffectBatch();
        NanoVGTheme.renderPanelEffects(vg, x, y, DEVELOPER_TOGGLE_WIDTH, DEVELOPER_TOGGLE_HEIGHT, NanoVGTheme.RADIUS_HUD, 1f);
        vg.flushEffectBatch();
        NanoVGTheme.renderPanel(vg, x, y, DEVELOPER_TOGGLE_WIDTH, DEVELOPER_TOGGLE_HEIGHT, NanoVGTheme.RADIUS_HUD, 1f);

        Color textColor = mix(mix(NanoVGTheme.MUTED, IDLE_TEXT, developerToggleHover), NanoVGTheme.TEXT, progress);
        NVGFonts.INTER_MEDIUM.drawText("Developer Features", x + 12f, y + DEVELOPER_TOGGLE_HEIGHT / 2f, 13f, textColor, Alignment.LEFT_MIDDLE, true);

        vg.beginEffectBatch();
        NanoVGTheme.renderControlEffects(vg, switchX, switchY, DEVELOPER_SWITCH_WIDTH, DEVELOPER_SWITCH_HEIGHT, switchRadius, 1f, 0.04f * progress);
        NanoVGTheme.renderControlEffects(vg, knobX, knobY, knobWidth, knobHeight, knobRadius, 1f);
        vg.flushEffectBatch();
        vg.roundedRectangle(switchX, switchY, DEVELOPER_SWITCH_WIDTH, DEVELOPER_SWITCH_HEIGHT, switchRadius, mix(NanoVGTheme.CONTROL_TRACK, NanoVGTheme.switchOn(240), progress));
        vg.roundedRectangle(knobX, knobY, knobWidth, knobHeight, knobRadius, mix(NanoVGTheme.KNOB_FILL, NanoVGTheme.KNOB_ON_FILL, progress));
    }

    private static Color mix(Color from, Color to, float amount) {
        float t = Math.clamp(amount, 0f, 1f);
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t),
                Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * t)
        );
    }

    private boolean isDeveloperToggleHovered(double mouseX, double mouseY) {
        float centerX = NanoVGManager.getScaledScreenWidth() / 2f;
        return isHovered(mouseX, mouseY, centerX - DEVELOPER_TOGGLE_WIDTH / 2f, DEVELOPER_TOGGLE_Y, DEVELOPER_TOGGLE_WIDTH, DEVELOPER_TOGGLE_HEIGHT);
    }

    private float updateMusicSliderGrab() {
        long now = System.nanoTime();
        float delta = lastMusicSliderNanos == 0L ? 1f : Math.clamp((now - lastMusicSliderNanos) / 16_666_666f, 0.25f, 3f);
        lastMusicSliderNanos = now;
        float target = musicVolumeSliderDragging ? 1f : 0f;
        float speed = musicVolumeSliderDragging ? 0.38f : 0.11f;
        musicSliderGrab += (target - musicSliderGrab) * Math.clamp(speed * delta, 0f, 1f);
        return musicSliderGrab;
    }

    private CursorType resolveMenuCursor(double mouseX, double mouseY) {
        if (isMinecraftMouseBlocked())
            return CursorType.DEFAULT;

        if (musicVolumeSliderDragging || isMusicSliderHovered(mouseX, mouseY) || isDeveloperToggleHovered(mouseX, mouseY))
            return CursorTypes.POINTING_HAND;

        for (MenuButton button : getVisibleMenuButtons()) {
            if (isHovered(mouseX, mouseY, button.x - 110f, button.y - 34f, 220f, 42f))
                return CursorTypes.POINTING_HAND;
        }

        return CursorType.DEFAULT;
    }

    private boolean isMusicSliderHovered(double mouseX, double mouseY) {
        float centerX = NanoVGManager.getScaledScreenWidth() / 2f;
        float sliderX = getMusicSliderX(centerX);
        float sliderY = getMusicSliderY();

        return isHovered(mouseX, mouseY, sliderX - 8f, sliderY - 22f, MUSIC_SLIDER_WIDTH + 16f, 40f);
    }

    private void updateMainMenuMusicVolumeFromMouse(double mouseX, boolean save) {
        float sliderX = getMusicSliderX(NanoVGManager.getScaledScreenWidth() / 2f);
        float scaledMouseX = NanoVGManager.toScaledMouseX(mouseX);
        float volume = (scaledMouseX - sliderX) / MUSIC_SLIDER_WIDTH;
        volume = Math.max(0f, Math.min(1f, volume));

        MAIN_MENU_MUSIC.setVolume(volume);
        if (Client.configManager != null)
            Client.configManager.setMainMenuMusicVolume(volume, save);

        musicVolumeDirty = !save;
    }

    private void saveMainMenuMusicVolumeIfDirty() {
        if (!musicVolumeDirty || Client.configManager == null)
            return;

        Client.configManager.setMainMenuMusicVolume(MAIN_MENU_MUSIC.getVolume(), true);
        musicVolumeDirty = false;
    }

    private float getMainMenuMusicVolume() {
        return Client.configManager == null ? MAIN_MENU_MUSIC.getVolume() : Client.configManager.getMainMenuMusicVolume();
    }

    private List<MenuButton> getVisibleMenuButtons() {
        return FileUtil.INSTANCE.isLatestClient() ? menuButtons : outdatedClientMenuButtons;
    }

    private static float getMusicSliderX(float centerX) {
        return centerX - MUSIC_SLIDER_WIDTH / 2f;
    }

    private static float getMusicSliderY() {
        return 62f;
    }

    private static class MenuButton {
        final String label;
        float x;
        float y;
        boolean hovered;
        float animation;
        float fontSize = 21f;

        MenuButton(String label) {
            this.label = label;
        }
    }

    public static String getCurrentTime() {
        long currentSecond = System.currentTimeMillis() / 1000L;
        if (currentSecond != cachedTimeSecond) {
            cachedTimeSecond = currentSecond;
            cachedTime = formatTime(LocalTime.now());
        }

        return cachedTime;
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

    private static boolean ensureTexture(NVGU vg, String identifier, String path) {
        if (vg.hasTexture(identifier))
            return true;

        try (InputStream texture = CustomTitleScreen.class.getClassLoader().getResourceAsStream(path)) {
            if (texture == null)
                return false;

            vg.createTexture(identifier, texture);
            return vg.hasTexture(identifier);
        } catch (RuntimeException | java.io.IOException ignored) {
            return false;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        TitleBackground.render(context, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (Client.nanoVgManager == null)
            return;

        context.requestCursor(resolveMenuCursor(mouseX, mouseY));

        Consumer<NVGU> renderer = vg -> {
            float screenWidth = NanoVGManager.getScaledScreenWidth();
            float screenHeight = NanoVGManager.getScaledScreenHeight();
            float centerX = screenWidth / 2f;
            float centerY = screenHeight / 2f;

            float menuX = centerX - 120f;
            float menuY = centerY - 160f;
            float menuWidth = 240f;
            List<MenuButton> visibleMenuButtons = getVisibleMenuButtons();
            float menuHeight = 135f + (45f * visibleMenuButtons.size());
            float iconX = centerX - 40f;
            float iconY = centerY - 140f;
            float iconSize = 80f;

            vg.beginEffectBatch();
            NanoVGTheme.renderPanelEffects(vg, menuX, menuY, menuWidth, menuHeight, NanoVGTheme.RADIUS_MENU, 1f);
            NanoVGTheme.renderPanelEffects(vg, iconX, iconY, iconSize, iconSize, NanoVGTheme.RADIUS_MENU, 1f);
            vg.flushEffectBatch();

            NanoVGTheme.renderPanel(vg, menuX, menuY, menuWidth, menuHeight, NanoVGTheme.RADIUS_MENU, 1f);
            NanoVGTheme.renderPanel(vg, iconX, iconY, iconSize, iconSize, NanoVGTheme.RADIUS_MENU, 1f);
            if (ensureTexture(vg, "icon", "assets/krs/icon.png"))
                vg.texturedRoundedRectangle(iconX, iconY, iconSize, iconSize, NanoVGTheme.RADIUS_MENU, "icon");

            NVGFonts.INTER_MEDIUM.drawText("Welcome to Krs Client (v" + Client.clientVersion + ")", 8f, screenHeight - 8f, 22f, Color.WHITE, Alignment.LEFT_BOTTOM, true);
            NVGFonts.INTER_MEDIUM.drawText(getCurrentTime(), screenWidth - 10f, 10f, 22f, Color.WHITE, Alignment.RIGHT_TOP, true);
            NVGFonts.INTER.drawText("Currently Logged Into: " + mc.getUser().getName(), centerX, 20f, 21f, Color.WHITE, Alignment.CENTER_TOP, true);
            renderMusicVolumeSlider(vg, centerX, mouseX, mouseY);
            renderDeveloperToggle(vg, centerX, mouseX, mouseY);

            if (GraphicsApiCompatibility.usesCompatibilityRenderer()) {
                NVGFonts.INTER.drawText("Vulkan support is not yet complete, so performance will be significantly lower than when using OpenGL. Please use OpenGL unless there is a specific reason not to.", centerX, DEVELOPER_TOGGLE_Y + DEVELOPER_TOGGLE_HEIGHT + 10f, 21f, NanoVGTheme.ACCENT, Alignment.CENTER_TOP, true);
            }

            for (int i = 0; i < visibleMenuButtons.size(); i++)
                updateUiButtonState(visibleMenuButtons.get(i), centerX, centerY + (45f * i), mouseX, mouseY);

            vg.beginEffectBatch();
            for (MenuButton state : visibleMenuButtons) {
                NanoVGTheme.renderPanelEffects(vg, state.x - 110f, state.y - 34f, 220f, 38f, NanoVGTheme.RADIUS_PANEL, state.hovered ? 1f : 0.92f);
            }
            vg.flushEffectBatch();

            for (MenuButton state : visibleMenuButtons) {
                renderUiButton(vg, state);
            }

            if (!NanoVGClickGuiScreen.isShowing())
                Client.notificationManager.renderNotifications(vg);
        };

        if (mc.gui.screen() == this || mc.gui.screen() instanceof NanoVGClickGuiScreen)
            Client.nanoVgManager.load(renderer);
        else
            Client.nanoVgManager.loadBeforeGui(renderer);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        float centerX = (NanoVGManager.getScaledScreenWidth() / 2f) - 100f;
        float centerY = (NanoVGManager.getScaledScreenHeight() / 2f) - 34f;

        if (button == Keys.MOUSE_BUTTON_LEFT) {
            if (isMusicSliderHovered(mouseX, mouseY)) {
                musicVolumeSliderDragging = true;
                updateMainMenuMusicVolumeFromMouse(mouseX, true);
                return true;
            }

            if (isDeveloperToggleHovered(mouseX, mouseY)) {
                developerTogglePressed = true;
                if (Client.configManager != null)
                    Client.configManager.setDeveloperFeatures(!ConfigManager.isDeveloperFeaturesEnabled());
                return true;
            }

            List<MenuButton> visibleMenuButtons = getVisibleMenuButtons();
            for (int i = 0; i < visibleMenuButtons.size(); i++) {
                MenuButton menuButton = visibleMenuButtons.get(i);
                String label = menuButton.label;
                float y = 45f * i;

                if (isHovered(mouseX, mouseY, centerX, centerY + y, 200f, 38f)) {
                    switch (label) {
                        case "Single Player":
                            mc.gui.setScreen(new SelectWorldScreen(this));
                            return true;

                        case "Multi Player":
                            mc.gui.setScreen(new JoinMultiplayerScreen(this));
                            return true;

                        case "Realms":
                            mc.gui.setScreen(new RealmsMainScreen(this));
                            return true;

                        case "Friends":
                            OnlineOptionsScreen.confirmFriendsListEnabled(
                                    mc,
                                    () -> mc.gui.setScreen(new FriendsOverlayScreen(this)),
                                    this
                            );
                            return true;

                        case "Options":
                            mc.gui.setScreen(new OptionsScreen(this, mc.options));
                            return true;

                        case "Update":
                            FileUtil.INSTANCE.openInBrowser(RELEASES_URL);
                            return true;

                        case "Exit":
                            stopMainMenuMusic();
                            mc.stop();
                            return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (musicVolumeSliderDragging && event.button() == Keys.MOUSE_BUTTON_LEFT) {
            updateMainMenuMusicVolumeFromMouse(event.x(), false);
            return true;
        }

        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        developerTogglePressed = false;
        if (musicVolumeSliderDragging && event.button() == Keys.MOUSE_BUTTON_LEFT) {
            updateMainMenuMusicVolumeFromMouse(event.x(), true);
            musicVolumeSliderDragging = false;
            musicVolumeDirty = false;
            return true;
        }

        return super.mouseReleased(event);
    }
}
