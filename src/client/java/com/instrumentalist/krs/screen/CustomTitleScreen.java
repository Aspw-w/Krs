package com.instrumentalist.krs.screen;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.utils.GuiInputBlocker;
import com.instrumentalist.krs.utils.audio.Mp3MusicPlayer;
import com.instrumentalist.krs.utils.math.Interpolation;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.network.FileUtil;
import com.instrumentalist.krs.utils.render.GraphicsApiCompatibility;
import com.mojang.realmsclient.RealmsMainScreen;
import org.lwjgl.glfw.GLFW;
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
    private static final float MUSIC_SLIDER_TRACK_HEIGHT = 6f;
    private static final Color BUTTON_IDLE = new Color(20, 20, 20, 180);
    private static final Color BUTTON_HOVERED = new Color(50, 50, 50, 200);
    private static final Color BUTTON_SHADOW_IDLE = new Color(0, 0, 0, 120);
    private static final Color BUTTON_SHADOW_HOVERED = new Color(0, 0, 0, 150);
    private static final String RELEASES_URL = "https://github.com/Aspw-w/Krs/releases";

    private boolean musicVolumeSliderDragging;
    private boolean musicVolumeDirty;

    private final MenuButton singlePlayerButton = new MenuButton("Single Player");
    private final MenuButton multiPlayerButton = new MenuButton("Multi Player");
    private final MenuButton realmsButton = new MenuButton("Realms");
    private final MenuButton friendsButton = new MenuButton("Friends");
    private final MenuButton optionsButton = new MenuButton("Options");
    private final MenuButton updateAvailableButton = new MenuButton("Update Available");
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

    public CustomTitleScreen(Component title) {
        super(title);
    }

    public static void stopMainMenuMusic() {
        int frame = MAIN_MENU_MUSIC.stopAndGetFramePosition();
        if (Client.configManager != null)
            Client.configManager.setMainMenuMusicFrame(frame, true);
    }

    @Override
    public void added() {
        super.added();
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
        vg.roundedRectangle(state.x - 110f, state.y - 34f, 220f, 38f, 12f, baseColor);

        if (state.animation > 1f) {
            Color gradientColor = state.hovered ? new Color(255, 215, 0, 220) : Interface.getFadedColor(0, 1);
            vg.roundedRectangle(state.x - state.animation, state.y - 2.5f, state.animation * 2f, 3f, 2f, gradientColor);
        }

        NVGFont font = state.fontSize > 22f ? NVGFonts.INTER_MEDIUM : NVGFonts.INTER;
        font.drawText(state.label, state.x, state.y - 15f - (state.fontSize - 21f), state.fontSize, state.hovered ? Color.YELLOW : Color.WHITE, Alignment.CENTER_MIDDLE, true);
    }

    private void renderMusicVolumeSlider(NVGU vg, float centerX, float mouseX, float mouseY) {
        float sliderX = getMusicSliderX(centerX);
        float sliderY = getMusicSliderY();
        float volume = getMainMenuMusicVolume();
        boolean hovered = musicVolumeSliderDragging || isMusicSliderHovered(mouseX, mouseY);
        float filledWidth = MUSIC_SLIDER_WIDTH * volume;
        float knobX = sliderX + filledWidth;
        Color accent = hovered ? new Color(255, 215, 0, 245) : new Color(255, 215, 0, 215);

        String sliderText = volume == 0f ? "Music OFF" : "Volume: " + Math.round(volume * 100f) + "%";
        NVGFonts.INTER.drawText(sliderText, centerX, sliderY - 10f, 12f, hovered ? Color.YELLOW : Color.WHITE, Alignment.CENTER_MIDDLE, true);
        vg.roundedRectangle(sliderX, sliderY, MUSIC_SLIDER_WIDTH, MUSIC_SLIDER_TRACK_HEIGHT, 3f, new Color(20, 20, 20, 190));
        vg.roundedRectangle(sliderX, sliderY, filledWidth, MUSIC_SLIDER_TRACK_HEIGHT, 3f, accent);
        vg.roundedRectangle(knobX - 5f, sliderY - 4f, 10f, 14f, 5f, hovered ? Color.WHITE : new Color(235, 235, 235, 245));
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

    public static void renderStartupLoadBarIfNeeded(NanoVGManager manager) {
        if (manager == null || !shouldRenderStartupLoadBar())
            return;

        manager.renderImmediate(CustomTitleScreen::renderStartupLoadBar);
    }

    public static boolean shouldRenderStartupLoadBar() {
        return mc.gui.screen() instanceof CustomTitleScreen && mc.gui.overlay() != null;
    }

    private static void renderStartupLoadBar(NVGU vg) {
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        float panelWidth = Math.min(320f, Math.max(240f, screenWidth - 48f));
        float panelHeight = 54f;
        float panelX = (screenWidth - panelWidth) / 2f;
        float panelY = Math.min(screenHeight - panelHeight - 28f, screenHeight * 0.72f);
        float trackX = panelX + 22f;
        float trackY = panelY + 33f;
        float trackWidth = panelWidth - 44f;
        float trackHeight = 5f;

        vg.beginEffectBatch();
        vg.shadowRoundedRectangle(panelX, panelY, panelWidth, panelHeight, 8f, 18f, 4f, 0f, 5f, new Color(0, 0, 0, 150));
        vg.flushEffectBatch();

        vg.roundedRectangle(panelX, panelY, panelWidth, panelHeight, 8f, new Color(10, 10, 10, 165));
        NVGFonts.INTER_MEDIUM.drawText("Loading", panelX + 22f, panelY + 12f, 15f, new Color(255, 255, 255, 230), Alignment.LEFT_TOP, true);
        vg.roundedRectangle(trackX, trackY, trackWidth, trackHeight, 3f, new Color(255, 255, 255, 45));

        float phase = (System.currentTimeMillis() % 1350L) / 1350f;
        float markerWidth = Math.max(54f, trackWidth * 0.28f);
        float markerX = trackX - markerWidth + (trackWidth + markerWidth * 2f) * phase;

        vg.scissor(trackX, trackY, trackWidth, trackHeight, () ->
                vg.roundedRectangle(markerX, trackY, markerWidth, trackHeight, 3f, new Color(255, 215, 0, 235))
        );
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (Client.nanoVgManager == null)
            return;

        Consumer<NVGU> renderer = vg -> {
            float screenWidth = NanoVGManager.getScaledScreenWidth();
            float screenHeight = NanoVGManager.getScaledScreenHeight();
            float centerX = screenWidth / 2f;
            float centerY = screenHeight / 2f;

            if (!isMinecraftMouseBlocked()) {
                float targetBgOffsetX = NanoVGManager.toScaledMouseX(mouseX);
                float targetBgOffsetY = NanoVGManager.toScaledMouseY(mouseY);

                bgOffsetX = Interpolation.INSTANCE.lerp(bgOffsetX, targetBgOffsetX, 0.03f);
                bgOffsetY = Interpolation.INSTANCE.lerp(bgOffsetY, targetBgOffsetY, 0.03f);
            }

            if (ensureTexture(vg, "title_screen", "assets/krs/title.png")) {
                vg.texturedRectangle(0f, 0f, screenWidth * 2, screenHeight * 2, "title_screen");

                vg.save();
                try {
                    vg.translate(bgOffsetX, bgOffsetY);
                    vg.texturedRectangle(-screenWidth, -screenHeight, screenWidth * 2, screenHeight * 2, "title_screen");
                } finally {
                    vg.restore();
                }
            }

            float menuX = centerX - 120f;
            float menuY = centerY - 160f;
            float menuWidth = 240f;
            List<MenuButton> visibleMenuButtons = getVisibleMenuButtons();
            float menuHeight = 135f + (45f * visibleMenuButtons.size());
            float iconX = centerX - 40f;
            float iconY = centerY - 140f;
            float iconSize = 80f;

            vg.beginEffectBatch();
            vg.blurRoundedRectangle(menuX, menuY, menuWidth, menuHeight, 10f, 7f, 0.45f);
            vg.shadowRoundedRectangle(menuX, menuY, menuWidth, menuHeight, 10f, 18f, 4f, 0f, 6f, new Color(0, 0, 0, 135));
            vg.flushEffectBatch();

            if (ensureTexture(vg, "drug", "assets/krs/drug.png"))
                vg.texturedRoundedRectangle(menuX, menuY, menuWidth, menuHeight, 10f, "drug");

            vg.beginEffectBatch();
            vg.blurRoundedRectangle(iconX, iconY, iconSize, iconSize, 10f, 7f, 0.35f);
            vg.shadowRoundedRectangle(iconX, iconY, iconSize, iconSize, 10f, 12f, 2f, 0f, 4f, new Color(0, 0, 0, 120));
            vg.flushEffectBatch();
            if (ensureTexture(vg, "icon", "assets/krs/icon.png"))
                vg.texturedRoundedRectangle(iconX, iconY, iconSize, iconSize, 10f, "icon");

            NVGFonts.INTER_MEDIUM.drawText("Welcome to Krs Client (v" + Client.clientVersion + ")", 8f, screenHeight - 8f, 22f, Color.WHITE, Alignment.LEFT_BOTTOM, true);
            NVGFonts.INTER_MEDIUM.drawText(getCurrentTime(), screenWidth - 10f, 10f, 22f, Color.WHITE, Alignment.RIGHT_TOP, true);
            NVGFonts.INTER.drawText("Currently Logged Into: " + mc.getUser().getName(), centerX, 20f, 21f, Color.WHITE, Alignment.CENTER_TOP, true);
            renderMusicVolumeSlider(vg, centerX, mouseX, mouseY);

            if (GraphicsApiCompatibility.usesCompatibilityRenderer()) {
                NVGFonts.INTER.drawText("Vulkan support is not yet complete, so performance will be significantly lower than when using OpenGL. Please use OpenGL unless there is a specific reason not to.", centerX, 80f, 21f, Color.CYAN, Alignment.CENTER_TOP, true);
            }

            for (int i = 0; i < visibleMenuButtons.size(); i++)
                updateUiButtonState(visibleMenuButtons.get(i), centerX, centerY + (45f * i), mouseX, mouseY);

            vg.beginEffectBatch();
            for (MenuButton state : visibleMenuButtons) {
                float effectAlpha = state.hovered ? 0.55f : 0.38f;
                Color shadowColor = state.hovered ? BUTTON_SHADOW_HOVERED : BUTTON_SHADOW_IDLE;
                vg.blurRoundedRectangle(state.x - 110f, state.y - 34f, 220f, 38f, 12f, 7f, effectAlpha);
                vg.shadowRoundedRectangle(state.x - 110f, state.y - 34f, 220f, 38f, 12f, state.hovered ? 14f : 11f, 2f, 0f, state.hovered ? 5f : 3f, shadowColor);
            }
            vg.flushEffectBatch();

            for (MenuButton state : visibleMenuButtons) {
                renderUiButton(vg, state);
            }

            Client.notificationManager.renderNotifications(vg);
        };

        if (mc.gui.screen() == this)
            Client.nanoVgManager.load(renderer);
        else
            Client.nanoVgManager.loadBeforeGui(renderer);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (shouldRenderStartupLoadBar())
            return true;

        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        float centerX = (NanoVGManager.getScaledScreenWidth() / 2f) - 100f;
        float centerY = (NanoVGManager.getScaledScreenHeight() / 2f) - 34f;

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (isMusicSliderHovered(mouseX, mouseY)) {
                musicVolumeSliderDragging = true;
                updateMainMenuMusicVolumeFromMouse(mouseX, true);
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
                            mc.gui.setScreen(new OptionsScreen(this, mc.options, false));
                            return true;

                        case "Update Available":
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
        if (musicVolumeSliderDragging && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateMainMenuMusicVolumeFromMouse(event.x(), false);
            return true;
        }

        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (musicVolumeSliderDragging && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateMainMenuMusicVolumeFromMouse(event.x(), true);
            musicVolumeSliderDragging = false;
            musicVolumeDirty = false;
            return true;
        }

        return super.mouseReleased(event);
    }
}
