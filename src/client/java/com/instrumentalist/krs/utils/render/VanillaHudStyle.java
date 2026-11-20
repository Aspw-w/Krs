package com.instrumentalist.krs.utils.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.utils.ServerAddressSpoof;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.mixin.injector.ChatComponentAccessor;
import com.instrumentalist.mixin.injector.ChatScreenAccessor;
import com.instrumentalist.mixin.injector.CommandSuggestionsAccessor;
import com.instrumentalist.mixin.injector.SuggestionsListAccessor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;
import com.instrumentalist.mixin.injector.GuiGraphicsExtractorAccessor;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import com.instrumentalist.krs.hacks.features.render.Interface;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import com.instrumentalist.mixin.injector.GuiRenderStateAccessor;
import com.instrumentalist.mixin.injector.ToastManagerAccessor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Vector2ic;
import java.util.function.BiFunction;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.Border;
import org.nvgu.util.NVGFont;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public final class VanillaHudStyle implements IMinecraft {
    private static final float TEXT_SIZE = 15f;
    private static final float SIDEBAR_ROW = 19f;
    private static final float SIDEBAR_PAD = 9f;
    private static final float TAB_ROW = 20f;
    private static final float TAB_LINE = 18f;
    private static final float TAB_PAD = 10f;
    private static final float TAB_COLUMN_GAP = 6f;
    private static final float TAB_INSET = 6f;
    private static final Color TAB_INSET_EDGE = new Color(255, 255, 255, 26);
    private static final float BOSS_TITLE = 18f;
    private static final float BOSS_BAR = 6f;
    private static final float BOSS_GAP = 8f;
    private static final float BOSS_PAD = 9f;
    private static final Color BOSS_TRACK = new Color(255, 255, 255, 36);
    private static final Color BOSS_NOTCH = new Color(0x11, 0x11, 0x13, 150);
    private static final Color HOTBAR_ATTACK = new Color(255, 255, 255, 120);
    private static final float[] hotbarRect = new float[4];
    private static final float[] hotbarSelectionRect = new float[4];
    private static final float[] hotbarOffhandRect = new float[4];
    private static final float[] hotbarAttackRect = new float[4];
    private static boolean hotbarActive;
    private static boolean hotbarValid;
    private static boolean hotbarSelectionValid;
    private static boolean hotbarOffhandValid;
    private static boolean hotbarAttackValid;
    private static float hotbarAttackProgress;
    private static int hotbarLevel;
    private static Component hotbarItemName;
    private static float hotbarItemAlpha;
    private static int hotbarItemY;
    private static final float TOOLTIP_PAD = 8f;
    private static final float TOOLTIP_LINE = 18f;
    private static final Color INPUT_SELECTION = new Color(255, 255, 255, 70);
    private static final Color SUGGESTION_SELECTED = new Color(255, 255, 255, 30);
    private static final Color SUGGESTION_MARKER = new Color(255, 255, 255, 120);
    private static boolean suggestionsActive;
    private static final float SUGGESTION_ROW = 12f;
    private static Object suggestionScrollOwner;
    private static float suggestionScrollPx;
    private static float suggestionScrollVelocity;
    private static long suggestionScrollNanos;
    private static int suggestionMaxOffset;
    private static boolean suggestionDragging;
    private static float suggestionDragOffset;
    private static float suggestionDragLines;
    private static float suggestionScrollbarHover;
    private static boolean suggestionTrackValid;
    private static float suggestionTrackX;
    private static float suggestionTrackY;
    private static float suggestionTrackHeight;
    private static float suggestionThumbY;
    private static float suggestionThumbHeight;
    private static final List<float[]> suggestionRows = new ArrayList<>();
    private static final List<Float> suggestionMarkers = new ArrayList<>();
    private static final List<TextDraw> suggestionTexts = new ArrayList<>();
    private static float suggestionSelectedY = Float.NaN;
    private static boolean screenRouting;
    private static boolean debugOnTop;
    private static boolean screenSplitPending;
    private static int hudSplitStratum = -1;
    private static int screenSplitStratum = -1;
    private static int hudSplitDraw = -1;
    private static int screenSplitDraw = -1;
    private static boolean hudLayerRendered;
    private static boolean screenLayerRendered;
    private static int traversedStratum;
    private static int overlaySplitStratum = -1;
    private static int overlaySplitDraw = -1;
    private static boolean overlayLayerRendered;
    private static boolean overlayRouting;
    private static float chatPanelRight = Float.NaN;
    private static int toastSplitStratum = -1;
    private static int toastSplitDraw = -1;
    private static boolean toastLayerRendered;
    private static boolean toastActive;
    private static final List<Consumer<NVGU>> toastQueue = new ArrayList<>();
    private static int hudTextSplitStratum = -1;
    private static int hudTextSplitDraw = -1;
    private static boolean hudTextLayerRendered;
    private static final List<Consumer<NVGU>> hudTextQueue = new ArrayList<>();
    private static EditBox chatInputBox;
    private static final Vector2f POINT = new Vector2f();
    private static final float[] RECT = new float[4];

    private static boolean chatActive;
    private static boolean chatForeground;
    private static boolean chatCursorOnInsertions;
    private static float chatMouseX;
    private static float chatMouseY;
    private static GuiGraphicsExtractor chatGraphics;
    private static int chatLineCount;
    private static float[] chatLines = new float[64 * 5];
    private static int chatMouseGuiX;
    private static int chatMouseGuiY;
    private static final List<float[]> chatTags = new ArrayList<>();
    private static boolean chatClip;
    private static float chatClipX0;
    private static float chatClipY0;
    private static float chatClipX1;
    private static float chatClipY1;
    private static float chatOffsetY;
    private static float chatLineHeight;
    private static int chatMaxScroll;
    private static float chatScroll;
    private static float chatScrollVelocity;
    private static long chatScrollNanos;
    private static int chatLastPos;
    private static int chatLastTotal;
    private static Object chatNewestLine;
    private static boolean chatWasForeground;
    private static ChatComponent chatComponent;
    private static boolean chatDragging;
    private static float chatDragOffset;
    private static float chatDragLines;
    private static float chatScrollbarHover;
    private static boolean chatTrackValid;
    private static float chatTrackX;
    private static float chatTrackY;
    private static float chatTrackHeight;
    private static float chatThumbY;
    private static float chatThumbHeight;
    private static final List<TextDraw> chatTexts = new ArrayList<>();
    private static final List<ChatRow> chatRows = new ArrayList<>();
    private static List<ChatRow> lastChatRows = List.of();
    private static long lastChatRowsFrame;

    private VanillaHudStyle() {
    }

    private record Run(String text, Style style, NVGFont font, int rgb, boolean vanilla, float width) {
    }

    private record Line(List<Run> runs, float width, boolean vanilla) {
    }

    private record TextDraw(String text, NVGFont font, float x, float y, float size, Color color) {
    }

    private record HitRun(float x0, float x1, Style style) {
    }

    private record ChatRow(float x0, float y0, float x1, float y1, boolean vanilla, List<HitRun> runs) {
    }

    private record Corners(float x, float y, float width, float height, float tl, float tr, float br, float bl)
            implements NanoVGTheme.ConnectedGlassRect {
        @Override
        public boolean hasExplicitCorners() {
            return true;
        }

        @Override
        public float topLeftRadius() {
            return tl;
        }

        @Override
        public float topRightRadius() {
            return tr;
        }

        @Override
        public float bottomRightRadius() {
            return br;
        }

        @Override
        public float bottomLeftRadius() {
            return bl;
        }
    }

    private static float guiToNvg() {
        return NanoVGManager.getScaledScreenWidth() / Math.max(1, mc.getWindow().getGuiScaledWidth());
    }

    private static boolean nvgReady() {
        NVGU vg = NVGU.INSTANCE;
        return vg != null && vg.isCreated() && Client.nanoVgManager != null;
    }

    private static float[] rect(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        float scale = guiToNvg();
        graphics.pose().transformPosition(x0, y0, POINT);
        float ax = POINT.x;
        float ay = POINT.y;
        graphics.pose().transformPosition(x1, y1, POINT);
        RECT[0] = Math.min(ax, POINT.x) * scale;
        RECT[1] = Math.min(ay, POINT.y) * scale;
        RECT[2] = Math.max(ax, POINT.x) * scale;
        RECT[3] = Math.max(ay, POINT.y) * scale;
        return RECT;
    }

    private static void queue(Consumer<NVGU> renderer) {
        if (Client.nanoVgManager == null)
            return;
        if (overlayRouting)
            Client.nanoVgManager.loadOverlayLayer(renderer);
        else if (screenRouting)
            Client.nanoVgManager.loadScreenLayer(renderer);
        else
            Client.nanoVgManager.loadHudLayer(renderer);
    }

    public static void loadHud(Consumer<NVGU> renderer) {
        if (Client.nanoVgManager == null)
            return;
        if (mc.gui.screen() instanceof ChatScreen && Interface.styleChat() && nvgReady())
            Client.nanoVgManager.loadScreenLayer(renderer);
        else if (toastsVisible())
            toastQueue.add(renderer);
        else
            Client.nanoVgManager.load(renderer);
    }

    private static void queueScreen(Consumer<NVGU> renderer) {
        if (Client.nanoVgManager == null)
            return;
        if (overlaySplitStratum >= 0)
            Client.nanoVgManager.loadOverlayLayer(renderer);
        else
            Client.nanoVgManager.loadScreenLayer(renderer);
    }

    public static void beginChatOverlay(GuiGraphicsExtractor graphics) {
        if (!screenRouting)
            return;
        graphics.nextStratum();
        overlaySplitStratum = strataCount(graphics.guiRenderState) - 1;
        overlayRouting = true;
    }

    public static boolean chatScreenStyled() {
        return mc.gui.screen() instanceof ChatScreen && Interface.styleChat() && nvgReady();
    }

    public static float chatInputTop() {
        return (mc.getWindow().getGuiScaledHeight() - 14) * guiToNvg();
    }

    public static float chatPanelRight() {
        return chatPanelRight;
    }

    public static void beginChatScreen(GuiGraphicsExtractor graphics, boolean styled, EditBox input) {
        screenRouting = false;
        chatInputBox = styled ? input : null;
        if (!styled || !nvgReady())
            return;
        if (!screenSplitPending) {
            graphics.nextStratum();
            screenSplitStratum = strataCount(graphics.guiRenderState) - 1;
            screenSplitPending = true;
        }
        screenRouting = true;
    }

    public static void endChatScreen() {
        screenRouting = false;
        overlayRouting = false;
    }

    public static void markHudSplit(GuiRenderState state) {
        hudSplitStratum = strataCount(state) - 1;
    }

    private static int strataCount(GuiRenderState state) {
        return ((GuiRenderStateAccessor) state).krs$getStrata().size();
    }

    public static boolean hasHudSplit() {
        return hudSplitStratum >= 0;
    }

    public static void setTraversedStratum(int stratum) {
        traversedStratum = stratum;
    }

    public static void beginGuiMesh() {
        hudSplitDraw = -1;
        screenSplitDraw = -1;
        overlaySplitDraw = -1;
        toastSplitDraw = -1;
        hudTextSplitDraw = -1;
    }

    public static boolean startsSplit(int drawCount) {
        boolean split = false;
        if (hudSplitStratum >= 0 && hudSplitDraw < 0 && traversedStratum >= hudSplitStratum) {
            hudSplitDraw = drawCount;
            split = true;
        }
        if (screenSplitStratum >= 0 && screenSplitDraw < 0 && traversedStratum >= screenSplitStratum) {
            screenSplitDraw = drawCount;
            split = true;
        }
        if (overlaySplitStratum >= 0 && overlaySplitDraw < 0 && traversedStratum >= overlaySplitStratum) {
            overlaySplitDraw = drawCount;
            split = true;
        }
        if (toastSplitStratum >= 0 && toastSplitDraw < 0 && traversedStratum >= toastSplitStratum) {
            toastSplitDraw = drawCount;
            split = true;
        }
        if (hudTextSplitStratum >= 0 && hudTextSplitDraw < 0 && traversedStratum >= hudTextSplitStratum) {
            hudTextSplitDraw = drawCount;
            split = true;
        }
        return split;
    }

    public static void endGuiMesh(int drawCount) {
        if (hudSplitStratum >= 0 && hudSplitDraw < 0)
            hudSplitDraw = drawCount;
        if (screenSplitStratum >= 0 && screenSplitDraw < 0)
            screenSplitDraw = drawCount;
        if (overlaySplitStratum >= 0 && overlaySplitDraw < 0)
            overlaySplitDraw = drawCount;
        if (toastSplitStratum >= 0 && toastSplitDraw < 0)
            toastSplitDraw = drawCount;
        if (hudTextSplitStratum >= 0 && hudTextSplitDraw < 0)
            hudTextSplitDraw = drawCount;
    }

    public static int nextSplit(int start, int end) {
        int next = Integer.MAX_VALUE;
        if (toastSplitDraw >= start && toastSplitDraw <= end && !toastLayerRendered)
            next = Math.min(next, toastSplitDraw);
        if (hudTextSplitDraw >= start && hudTextSplitDraw <= end && !hudTextLayerRendered)
            next = Math.min(next, hudTextSplitDraw);
        if (hudSplitDraw >= start && hudSplitDraw <= end && !hudLayerRendered)
            next = Math.min(next, hudSplitDraw);
        if (screenSplitDraw >= start && screenSplitDraw <= end && !screenLayerRendered)
            next = Math.min(next, screenSplitDraw);
        if (overlaySplitDraw >= start && overlaySplitDraw <= end && !overlayLayerRendered)
            next = Math.min(next, overlaySplitDraw);
        return next == Integer.MAX_VALUE ? -1 : next;
    }

    public static void renderSplit(int drawIndex) {
        boolean renderHud = hudSplitDraw == drawIndex && !hudLayerRendered;
        boolean renderScreen = screenSplitDraw == drawIndex && !screenLayerRendered;
        boolean renderOverlay = overlaySplitDraw == drawIndex && !overlayLayerRendered;
        if (renderHud)
            hudLayerRendered = true;
        if (renderScreen)
            screenLayerRendered = true;
        if (renderOverlay)
            overlayLayerRendered = true;
        boolean renderToast = toastSplitDraw == drawIndex && !toastLayerRendered;
        if (renderToast)
            toastLayerRendered = true;
        boolean renderHudText = hudTextSplitDraw == drawIndex && !hudTextLayerRendered;
        if (renderHudText)
            hudTextLayerRendered = true;
        if (Client.nanoVgManager == null)
            return;
        if (renderHud && Client.nanoVgManager.hasQueuedHudLayerRenderers())
            MainTargetFramebuffer.runLayer(GraphicsApiCompatibility.Layer.NANO_VG_HUD,
                    Client.nanoVgManager::renderQueuedHudLayer, Client.nanoVgManager::discardHudLayerRenderQueue);
        if (renderHudText)
            renderHudTextLayer();
        if (renderScreen && Client.nanoVgManager.hasQueuedScreenLayerRenderers())
            MainTargetFramebuffer.runLayer(GraphicsApiCompatibility.Layer.NANO_VG_SCREEN,
                    Client.nanoVgManager::renderQueuedScreenLayer, Client.nanoVgManager::discardScreenLayerRenderQueue);
        if (renderOverlay && Client.nanoVgManager.hasQueuedOverlayLayerRenderers())
            MainTargetFramebuffer.runLayer(GraphicsApiCompatibility.Layer.NANO_VG_OVERLAY,
                    Client.nanoVgManager::renderQueuedOverlayLayer, Client.nanoVgManager::discardOverlayLayerRenderQueue);
        if (renderToast)
            renderToastLayer();
    }

    public static void renderToastLayerFallback() {
        renderHudTextLayer();
        renderToastLayer();
    }

    public static void renderToastLayer() {
        if (toastQueue.isEmpty())
            return;
        List<Consumer<NVGU>> consumers = new ArrayList<>(toastQueue);
        toastQueue.clear();
        if (Client.nanoVgManager != null)
            MainTargetFramebuffer.runLayer(GraphicsApiCompatibility.Layer.NANO_VG_TOAST,
                    () -> Client.nanoVgManager.renderConsumers(consumers), null);
    }

    public static void endGuiFrame() {
        hudSplitStratum = -1;
        screenSplitStratum = -1;
        hudSplitDraw = -1;
        screenSplitDraw = -1;
        hudLayerRendered = false;
        screenLayerRendered = false;
        overlaySplitStratum = -1;
        overlaySplitDraw = -1;
        overlayLayerRendered = false;
        overlayRouting = false;
        toastSplitStratum = -1;
        toastSplitDraw = -1;
        toastLayerRendered = false;
        toastActive = false;
        hudTextSplitStratum = -1;
        hudTextSplitDraw = -1;
        hudTextLayerRendered = false;
        screenSplitPending = false;
        traversedStratum = 0;
    }

    public static boolean isStyledChatInput(Object box) {
        return box != null && box == chatInputBox && nvgReady();
    }

    public static String chatInputVisibleText(String tail, int offset, int innerWidth,
                                              BiFunction<String, Integer, FormattedCharSequence> format) {
        float limit = innerWidth * guiToNvg();
        if (layout(format.apply(tail, offset), TEXT_SIZE, 0xFFFFFF, false).width <= limit)
            return tail;
        int low = 0;
        int high = tail.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (layout(format.apply(tail.substring(0, mid), offset), TEXT_SIZE, 0xFFFFFF, false).width <= limit)
                low = mid;
            else
                high = mid - 1;
        }
        return tail.substring(0, low);
    }

    public static int chatInputScreenX(String value, int charIndex, int x,
                                       BiFunction<String, Integer, FormattedCharSequence> format) {
        if (charIndex > value.length())
            return x;
        float width = layout(format.apply(value.substring(0, Math.max(0, charIndex)), 0), TEXT_SIZE, 0xFFFFFF, false).width;
        return x + Math.round(width / guiToNvg());
    }

    public static int chatInputClickIndex(String tail, int offset, float guiX, int innerWidth,
                                          BiFunction<String, Integer, FormattedCharSequence> format) {
        float scale = guiToNvg();
        float target = Math.clamp(guiX, 0f, innerWidth) * scale;
        String displayed = chatInputVisibleText(tail, offset, innerWidth, format);
        float previous = 0f;
        for (int i = 1; i <= displayed.length(); i++) {
            float width = layout(format.apply(displayed.substring(0, i), offset), TEXT_SIZE, 0xFFFFFF, false).width;
            if (target < (previous + width) / 2f)
                return i - 1;
            previous = width;
        }
        return displayed.length();
    }

    public static float chatInputText(GuiGraphicsExtractor graphics, String displayed, int offset, int relCursor, int relHighlight,
                                      boolean cursorOnScreen, boolean showCursor, boolean insert, @Nullable String suggestion,
                                      @Nullable Component hint, int textX, int textY, int width, int color,
                                      BiFunction<String, Integer, FormattedCharSequence> format) {
        float scale = guiToNvg();
        float x = textX * scale;
        float centerY = (textY + 4f) * scale;
        int rgb = color & 0xFFFFFF;
        List<TextDraw> texts = new ArrayList<>();

        String before = cursorOnScreen ? displayed.substring(0, relCursor) : displayed;
        Line left = layout(format.apply(before, offset), TEXT_SIZE, rgb, false);
        emit(graphics, left, x, centerY, TEXT_SIZE, 1f, texts);
        float cursorX = x + left.width;
        if (cursorOnScreen && relCursor < displayed.length()) {
            Line right = layout(format.apply(displayed.substring(relCursor), offset + relCursor), TEXT_SIZE, rgb, false);
            emit(graphics, right, cursorX, centerY, TEXT_SIZE, 1f, texts);
        }
        if (!cursorOnScreen)
            cursorX = relCursor > 0 ? (textX + width) * scale : x;

        if (hint != null)
            emit(graphics, layout(hint, TEXT_SIZE, 0x808080, false), x, centerY, TEXT_SIZE, 1f, texts);
        if (!insert && suggestion != null)
            emit(graphics, layout(FormattedCharSequence.forward(suggestion, Style.EMPTY), TEXT_SIZE, 0x808080, false), cursorX, centerY, TEXT_SIZE, 1f, texts);

        float highlightStart = Float.NaN;
        float highlightEnd = Float.NaN;
        if (relHighlight != relCursor) {
            float highlightX = x + layout(format.apply(displayed.substring(0, relHighlight), offset), TEXT_SIZE, rgb, false).width;
            highlightStart = Math.min(cursorX, highlightX);
            highlightEnd = Math.min(Math.max(cursorX, highlightX), (textX + width) * scale);
        }
        float caretX = cursorX;
        float selectionStart = highlightStart;
        float selectionEnd = highlightEnd;
        queue(vg -> {
            if (!Float.isNaN(selectionStart) && selectionEnd > selectionStart)
                vg.roundedRectangle(selectionStart, centerY - 9f, selectionEnd - selectionStart, 18f, 3f, INPUT_SELECTION);
            drawTexts(texts);
            if (showCursor)
                vg.roundedRectangle(caretX, centerY - 8f, 1.5f, 16f, 0.75f, NanoVGTheme.TEXT);
        });
        return cursorX / scale;
    }

    public static boolean screenRoutingActive() {
        return screenRouting && nvgReady();
    }

    public static float suggestionScroll(Object owner, int offset, int maxOffset) {
        long now = System.nanoTime();
        float delta = suggestionScrollNanos == 0L ? 1f : Math.clamp((now - suggestionScrollNanos) / 16_666_666f, 0.25f, 3f);
        suggestionScrollNanos = now;
        float rowHeight = SUGGESTION_ROW * guiToNvg();
        float maxScroll = maxOffset * rowHeight;
        if (owner != suggestionScrollOwner) {
            suggestionScrollOwner = owner;
            suggestionScrollPx = offset * rowHeight;
            suggestionScrollVelocity = 0f;
            suggestionDragging = false;
            suggestionScrollbarHover = 0f;
        }
        suggestionMaxOffset = maxOffset;
        if (suggestionDragging) {
            suggestionScrollPx = Math.clamp(suggestionDragLines * rowHeight, 0f, maxScroll);
            suggestionScrollVelocity = 0f;
        } else {
            float target = Math.clamp(offset * rowHeight, 0f, maxScroll);
            suggestionScrollVelocity += (target - suggestionScrollPx) * 0.045f * delta;
            suggestionScrollVelocity *= (float) Math.pow(0.74f, delta);
            suggestionScrollVelocity = Math.clamp(suggestionScrollVelocity, -42f, 42f);
            suggestionScrollPx += suggestionScrollVelocity * delta;
            suggestionScrollPx += (target - suggestionScrollPx) * Math.clamp(1f - (float) Math.pow(0.91f, delta), 0f, 1f);
            if (suggestionScrollPx < 0f || suggestionScrollPx > maxScroll) {
                suggestionScrollPx = Math.clamp(suggestionScrollPx, 0f, maxScroll);
                suggestionScrollVelocity = 0f;
            }
            if (Math.abs(target - suggestionScrollPx) < 0.06f && Math.abs(suggestionScrollVelocity) < 0.06f) {
                suggestionScrollPx = target;
                suggestionScrollVelocity = 0f;
            }
        }
        return rowHeight <= 0f ? offset : suggestionScrollPx / rowHeight;
    }

    public static boolean suggestionScrollbarContains(double mouseX, double mouseY) {
        float scale = guiToNvg();
        float x = (float) mouseX * scale;
        float y = (float) mouseY * scale;
        return suggestionTrackValid && x >= suggestionTrackX - 6f && x < suggestionTrackX + 6f
                && y >= suggestionTrackY && y < suggestionTrackY + suggestionTrackHeight;
    }

    public static void drawSuggestionList(GuiGraphicsExtractor graphics, Rect2i rect, List<String> rows, int base, float display,
                                          int current, int size, int limit, int mouseX, int mouseY) {
        float scale = guiToNvg();
        float rowHeight = SUGGESTION_ROW * scale;
        float left = rect.getX() * scale;
        float top = rect.getY() * scale;
        float right = (rect.getX() + rect.getWidth()) * scale;
        float bottom = (rect.getY() + rect.getHeight()) * scale;
        float[] clip = {left, top, right, bottom};
        boolean scrollable = suggestionMaxOffset > 0;
        float textRight = scrollable ? right - 10f : right;

        List<TextDraw> texts = new ArrayList<>();
        float selectedY = Float.NaN;
        for (int i = 0; i < rows.size(); i++) {
            int index = base + i;
            float rowTop = top + (index - display) * rowHeight;
            if (rowTop >= bottom || rowTop + rowHeight <= top)
                continue;
            boolean selected = index == current;
            if (selected)
                selectedY = rowTop;
            Line line = layout(FormattedCharSequence.forward(rows.get(i), Style.EMPTY), TEXT_SIZE,
                    selected ? 0xFFFFFF : NanoVGTheme.MUTED.getRGB() & 0xFFFFFF, false);
            emit(graphics, line, left + 3f, rowTop + rowHeight / 2f, TEXT_SIZE, 1f, texts, clip);
        }
        if (Float.isNaN(selectedY) && current >= 0) {
            float rowTop = top + (current - display) * rowHeight;
            if (rowTop < bottom && rowTop + rowHeight > top)
                selectedY = rowTop;
        }

        suggestionTrackValid = scrollable;
        if (scrollable) {
            float viewport = bottom - top;
            suggestionThumbHeight = Math.min(viewport, Math.max(16f, viewport * limit / (float) Math.max(1, size)));
            suggestionTrackX = right - 5f;
            suggestionTrackY = top;
            suggestionTrackHeight = viewport;
            suggestionThumbY = top + (viewport - suggestionThumbHeight) * Math.clamp(display / suggestionMaxOffset, 0f, 1f);
            boolean hovered = suggestionDragging || suggestionScrollbarContains(mouseX, mouseY);
            suggestionScrollbarHover += ((hovered ? 1f : 0f) - suggestionScrollbarHover) * 0.18f;
            if (hovered)
                graphics.requestCursor(CursorTypes.POINTING_HAND);
        }

        float x = left - 2f;
        float y = top - 2f;
        float width = right - left + 4f;
        float height = bottom - top + 4f;
        float highlightY = selectedY;
        float highlightRight = textRight;
        boolean bar = scrollable;
        float trackX = suggestionTrackX;
        float trackY = suggestionTrackY;
        float trackHeight = suggestionTrackHeight;
        float thumbY = suggestionThumbY;
        float thumbHeight = suggestionThumbHeight;
        float hover = Math.clamp(suggestionScrollbarHover, 0f, 1f);
        queue(vg -> {
            glassPanel(vg, x, y, width, height, NanoVGTheme.RADIUS_HUD);
            vg.pushScissor(left, top, right - left, bottom - top);
            if (!Float.isNaN(highlightY))
                vg.roundedRectangle(left + 1f, highlightY + 1f, highlightRight - left - 2f, rowHeight - 2f, NanoVGTheme.RADIUS_LABEL, SUGGESTION_SELECTED);
            drawTexts(texts);
            vg.popScissor();
            if (bar) {
                vg.roundedRectangle(trackX + 1f, trackY + 4f, 2f, trackHeight - 8f, 1f, new Color(255, 255, 255, (int) (18 + 18 * hover)));
                vg.roundedRectangle(trackX - 1f, thumbY, 5f, thumbHeight, 2.5f, NanoVGTheme.scrollbarThumb((int) (112 + 86 * hover)));
            }
        });
    }

    public static boolean suggestionScrollbarPress(Object list, double mouseX, double mouseY) {
        if (list == null || list != suggestionScrollOwner || !suggestionScrollbarContains(mouseX, mouseY))
            return false;
        float y = (float) mouseY * guiToNvg();
        suggestionDragOffset = y >= suggestionThumbY && y < suggestionThumbY + suggestionThumbHeight
                ? y - suggestionThumbY : suggestionThumbHeight / 2f;
        suggestionDragging = true;
        suggestionScrollbarDrag(list, mouseY);
        return true;
    }

    public static boolean suggestionScrollbarDrag(Object list, double mouseY) {
        if (!suggestionDragging || list == null || list != suggestionScrollOwner)
            return false;
        float y = (float) mouseY * guiToNvg();
        float travel = Math.max(1f, suggestionTrackHeight - suggestionThumbHeight);
        float thumbY = Math.clamp(y - suggestionDragOffset, suggestionTrackY, suggestionTrackY + travel);
        suggestionDragLines = (thumbY - suggestionTrackY) / travel * suggestionMaxOffset;
        ((SuggestionsListAccessor) list).krs$setOffset(Math.clamp(Math.round(suggestionDragLines), 0, suggestionMaxOffset));
        return true;
    }

    public static boolean suggestionScrollbarRelease() {
        boolean wasDragging = suggestionDragging;
        suggestionDragging = false;
        return wasDragging;
    }

    public static void beginSuggestions(boolean styled) {
        suggestionsActive = styled && screenRouting && nvgReady();
        suggestionRows.clear();
        suggestionMarkers.clear();
        suggestionTexts.clear();
        suggestionSelectedY = Float.NaN;
    }

    public static boolean suggestionsActive() {
        return suggestionsActive;
    }

    public static void suggestionFill(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        float[] r = rect(graphics, x0, y0, x1, y1);
        if (y1 - y0 <= 1) {
            if (color == -1)
                suggestionMarkers.add(r[1]);
            return;
        }
        suggestionRows.add(r.clone());
    }

    public static void suggestionText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        float scale = guiToNvg();
        boolean selected = color == -256;
        float centerY = (y + 4f) * scale;
        if (selected)
            suggestionSelectedY = centerY;
        Line line = layout(FormattedCharSequence.forward(text, Style.EMPTY), TEXT_SIZE,
                selected ? 0xFFFFFF : NanoVGTheme.MUTED.getRGB() & 0xFFFFFF, false);
        emit(graphics, line, x * scale + 3f, centerY, TEXT_SIZE, 1f, suggestionTexts);
    }

    public static void suggestionUsageText(GuiGraphicsExtractor graphics, FormattedCharSequence text, int x, int y) {
        float scale = guiToNvg();
        Line line = layout(text, TEXT_SIZE, 0xFFFFFF, false);
        emit(graphics, line, x * scale + 3f, (y + 4f) * scale, TEXT_SIZE, 1f, suggestionTexts);
    }

    public static void endSuggestions() {
        if (!suggestionsActive)
            return;
        suggestionsActive = false;
        if (suggestionRows.isEmpty())
            return;

        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (float[] row : suggestionRows) {
            left = Math.min(left, row[0]);
            top = Math.min(top, row[1]);
            right = Math.max(right, row[2]);
            bottom = Math.max(bottom, row[3]);
        }
        boolean more = false;
        boolean less = false;
        for (float markerY : suggestionMarkers) {
            if (markerY < top)
                less = true;
            else
                more = true;
        }

        float x = left - 2f;
        float y = top - 2f;
        float width = right - left + 4f;
        float height = bottom - top + 4f;
        float rowHeight = suggestionRows.get(0)[3] - suggestionRows.get(0)[1];
        float selectedY = suggestionSelectedY;
        boolean showLess = less;
        boolean showMore = more;
        List<TextDraw> texts = List.copyOf(suggestionTexts);
        queue(vg -> {
            glassPanel(vg, x, y, width, height, NanoVGTheme.RADIUS_HUD);
            if (!Float.isNaN(selectedY))
                vg.roundedRectangle(x + 3f, selectedY - rowHeight / 2f + 1f, width - 6f, rowHeight - 2f, NanoVGTheme.RADIUS_LABEL, SUGGESTION_SELECTED);
            if (showLess)
                vg.roundedRectangle(x + width / 2f - 10f, y + 1.5f, 20f, 2.5f, 1.25f, SUGGESTION_MARKER);
            if (showMore)
                vg.roundedRectangle(x + width / 2f - 10f, y + height - 4f, 20f, 2.5f, 1.25f, SUGGESTION_MARKER);
            drawTexts(texts);
        });
    }

    private record Chip(float x, float y, float width, float height) implements NanoVGTheme.ConnectedGlassRect {
    }

    public static void setDebugOnTop(boolean onTop) {
        debugOnTop = onTop;
    }

    public static boolean debugLines(GuiGraphicsExtractor graphics, List<String> lines, boolean alignLeft, int screenWidth) {
        if (debugOnTop)
            return true;
        if (!nvgReady())
            return false;

        float scale = guiToNvg();
        graphics.pose().transformPosition(0f, 0f, POINT);
        float originY = POINT.y;
        graphics.pose().transformPosition(0f, 1f, POINT);
        float unit = Math.max(0.01f, POINT.y - originY) * scale;
        float rowHeight = 9f * unit;
        float size = Math.min(TEXT_SIZE, rowHeight * 0.78f);
        float pad = 4f;

        List<List<Chip>> groups = new ArrayList<>();
        List<Chip> group = new ArrayList<>();
        List<TextDraw> texts = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            if (text == null || text.isEmpty()) {
                if (!group.isEmpty()) {
                    groups.add(group);
                    group = new ArrayList<>();
                }
                continue;
            }
            FormattedCharSequence sequence = sink -> StringDecomposer.iterateFormatted(text, Style.EMPTY, sink);
            Line line = layout(sequence, size, 0xE0E0E0, false);
            float width = line.width + pad * 2f;
            graphics.pose().transformPosition(alignLeft ? 1f : screenWidth - 1f, 1f + 9f * i, POINT);
            float anchorX = POINT.x * scale;
            float top = POINT.y * scale;
            float x = alignLeft ? anchorX : anchorX - width;
            group.add(new Chip(x, top, width, rowHeight));
            emit(graphics, line, x + pad, top + rowHeight / 2f, size, 1f, texts);
        }
        if (!group.isEmpty())
            groups.add(group);

        Client.nanoVgManager.load(vg -> {
            vg.beginEffectBatch();
            for (List<Chip> chips : groups)
                NanoVGTheme.renderConnectedEffects(vg, chips, NanoVGTheme.RADIUS_HUD, 1f);
            vg.flushEffectBatch();
            for (List<Chip> chips : groups)
                NanoVGTheme.renderConnected(vg, chips, NanoVGTheme.RADIUS_HUD, 1f);
            drawTexts(texts);
        });
        return true;
    }

    private static boolean toastsVisible() {
        if (!Interface.styleToasts() || !nvgReady() || mc.gui.hud.isHidden())
            return false;
        return !((ToastManagerAccessor) mc.gui.toastManager()).krs$getVisibleToasts().isEmpty();
    }

    private static void queueHudText(Consumer<NVGU> renderer) {
        if (screenRouting || overlayRouting)
            queue(renderer);
        else
            hudTextQueue.add(renderer);
    }

    public static void markHudTextSplit(GuiGraphicsExtractor graphics) {
        graphics.nextStratum();
        hudTextSplitStratum = strataCount(graphics.guiRenderState) - 1;
    }

    public static void renderHudTextLayer() {
        if (hudTextQueue.isEmpty())
            return;
        List<Consumer<NVGU>> consumers = new ArrayList<>(hudTextQueue);
        hudTextQueue.clear();
        if (Client.nanoVgManager != null)
            MainTargetFramebuffer.runLayer(GraphicsApiCompatibility.Layer.NANO_VG_HUD_TEXT,
                    () -> Client.nanoVgManager.renderConsumers(consumers), null);
    }

    public static void markToastSplit(GuiGraphicsExtractor graphics) {
        if (!Interface.styleToasts() || !nvgReady())
            return;
        graphics.nextStratum();
        toastSplitStratum = strataCount(graphics.guiRenderState) - 1;
    }

    public static void beginToast() {
        toastActive = toastSplitStratum >= 0;
    }

    public static void endToast() {
        toastActive = false;
    }

    public static boolean toastBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (!toastActive || x != 0 || y != 0)
            return false;
        float[] r = rect(graphics, x, y, x + width, y + height);
        float px = r[0];
        float py = r[1];
        float pw = r[2] - r[0];
        float ph = r[3] - r[1];
        toastQueue.add(vg -> glassPanel(vg, px, py, pw, ph, NanoVGTheme.RADIUS_HUD));
        return true;
    }

    public static boolean toastText(GuiGraphicsExtractor graphics, FormattedCharSequence text, int x, int y, int color) {
        if (!toastActive)
            return false;
        float scale = guiToNvg();
        graphics.pose().transformPosition(x, y, POINT);
        float left = POINT.x * scale;
        float top = POINT.y * scale;
        graphics.pose().transformPosition(x, y + 4f, POINT);
        float centerY = POINT.y * scale;
        float size = TEXT_SIZE * (centerY - top) / (4f * scale);
        int rgb = color & 0xFFFFFF;
        float luminance = (0.299f * (rgb >> 16 & 0xFF) + 0.587f * (rgb >> 8 & 0xFF) + 0.114f * (rgb & 0xFF)) / 255f;
        if (luminance < 0.35f)
            rgb = 0xF2F2F2;
        float alpha = (color >>> 24) / 255f;
        Line line = layout(text, size, rgb, false);
        List<TextDraw> texts = new ArrayList<>();
        toastActive = false;
        try {
            emit(graphics, line, left, centerY, size, alpha, texts);
        } finally {
            toastActive = true;
        }
        toastQueue.add(vg -> drawTexts(texts));
        return true;
    }

    public static boolean tooltip(GuiGraphicsExtractor graphics, List<FormattedCharSequence> lines, int mouseX, int mouseY,
                                  ClientTooltipPositioner positioner) {
        if (!screenSplitPending || !nvgReady() || lines.isEmpty())
            return false;
        float scale = guiToNvg();
        List<Line> laid = new ArrayList<>(lines.size());
        float contentWidth = 0f;
        for (FormattedCharSequence line : lines) {
            Line l = layout(line, TEXT_SIZE, 0xFFFFFF, false);
            laid.add(l);
            contentWidth = Math.max(contentWidth, l.width);
        }
        float width = contentWidth + TOOLTIP_PAD * 2f;
        float height = laid.size() * TOOLTIP_LINE + TOOLTIP_PAD * 2f - 4f;
        Vector2ic position = positioner.positionTooltip(graphics.guiWidth(), graphics.guiHeight(), mouseX, mouseY,
                Math.round(width / scale) - 6, Math.round(height / scale) - 6);
        float x = position.x() * scale - TOOLTIP_PAD + 6f;
        float y = position.y() * scale - TOOLTIP_PAD + 6f;
        List<TextDraw> texts = new ArrayList<>();
        float lineY = y + TOOLTIP_PAD - 2f;
        for (Line line : laid) {
            emit(graphics, line, x + TOOLTIP_PAD, lineY + TOOLTIP_LINE / 2f, TEXT_SIZE, 1f, texts);
            lineY += TOOLTIP_LINE;
        }
        queueScreen(vg -> {
            glassPanel(vg, x, y, width, height, NanoVGTheme.RADIUS_HUD);
            drawTexts(texts);
        });
        return true;
    }

    public static void title(GuiGraphicsExtractor graphics, Component text, boolean subtitle, int color) {
        centeredText(graphics, text, graphics.guiHeight() / 2f + (subtitle ? 18f : -24f),
                subtitle ? TEXT_SIZE * 2f : TEXT_SIZE * 4f, color, !subtitle);
    }

    public static void actionBar(GuiGraphicsExtractor graphics, Component text, int color) {
        centeredText(graphics, text, graphics.guiHeight() - 68f, TEXT_SIZE, color, false);
    }

    private static void centeredText(GuiGraphicsExtractor graphics, Component text, float guiCenterY, float size, int color, boolean bold) {
        if (!nvgReady())
            return;
        float scale = guiToNvg();
        float alpha = (color >>> 24) / 255f;
        Line line = layout(text, size, color & 0xFFFFFF, bold);
        List<TextDraw> texts = new ArrayList<>();
        emit(graphics, line, (NanoVGManager.getScaledScreenWidth() - line.width) / 2f, guiCenterY * scale, size, alpha, texts);
        queueHudText(vg -> drawTexts(texts));
    }

    private static Line layout(@Nullable FormattedCharSequence sequence, float size, int defaultRgb, boolean bold) {
        List<Run> runs = new ArrayList<>();
        if (sequence == null)
            return new Line(runs, 0f, false);

        StringBuilder text = new StringBuilder();
        Style[] current = {null};
        sequence.accept((index, style, codePoint) -> {
            if (current[0] != null && current[0] != style && !current[0].equals(style))
                addRun(runs, text, current[0], size, defaultRgb, bold);
            current[0] = style;
            text.appendCodePoint(codePoint);
            return true;
        });
        if (current[0] != null)
            addRun(runs, text, current[0], size, defaultRgb, bold);

        float width = 0f;
        boolean vanilla = false;
        for (Run run : runs) {
            width += run.width;
            vanilla |= run.vanilla;
        }
        return new Line(runs, width, vanilla);
    }

    private static Line layout(@Nullable Component component, float size, int defaultRgb, boolean bold) {
        return layout(component == null ? null : component.getVisualOrderText(), size, defaultRgb, bold);
    }

    private static void addRun(List<Run> runs, StringBuilder builder, Style style, float size, int defaultRgb, boolean bold) {
        if (builder.isEmpty())
            return;

        String text = builder.toString();
        builder.setLength(0);
        NVGFont font = bold || style.isBold() ? NVGFonts.INTER_SEMIBOLD : NVGFonts.INTER;
        int rgb = style.getColor() != null ? style.getColor().getValue() & 0xFFFFFF : defaultRgb;
        NVGU vg = NVGU.INSTANCE;
        if (vg.canRenderText(text, font.getIdentifier())) {
            runs.add(new Run(text, style, font, rgb, false, font.getWidth(text, size)));
            return;
        }

        int segmentStart = 0;
        boolean segmentVanilla = false;
        boolean segmentDecided = false;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int next = i + Character.charCount(codePoint);
            if (!Character.isWhitespace(codePoint)) {
                boolean vanilla = !vg.canRenderText(text.substring(i, next), font.getIdentifier());
                if (!segmentDecided) {
                    segmentVanilla = vanilla;
                    segmentDecided = true;
                } else if (vanilla != segmentVanilla) {
                    addSegment(runs, text.substring(segmentStart, i), style, font, rgb, segmentVanilla, size);
                    segmentStart = i;
                    segmentVanilla = vanilla;
                }
            }
            i = next;
        }
        addSegment(runs, text.substring(segmentStart), style, font, rgb, segmentVanilla, size);
    }

    private static void addSegment(List<Run> runs, String text, Style style, NVGFont font, int rgb, boolean vanilla, float size) {
        if (text.isEmpty())
            return;
        float width = vanilla
                ? mc.font.width(FormattedCharSequence.forward(text, style)) * guiToNvg() * (size / TEXT_SIZE)
                : font.getWidth(text, size);
        runs.add(new Run(text, style, font, rgb, vanilla, width));
    }

    private static Color color(int rgb, float alpha) {
        return new Color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, Math.clamp(Math.round(alpha * 255f), 0, 255));
    }

    private static void emit(GuiGraphicsExtractor graphics, Line line, float x, float centerY, float size, float alpha, List<TextDraw> output) {
        emit(graphics, line, x, centerY, size, alpha, output, false);
    }

    private static void emit(GuiGraphicsExtractor graphics, Line line, float x, float centerY, float size, float alpha,
                             List<TextDraw> output, boolean clipToChat) {
        emit(graphics, line, x, centerY, size, alpha, output,
                clipToChat ? new float[]{chatClipX0, chatClipY0, chatClipX1, chatClipY1} : null);
    }

    private static void emit(GuiGraphicsExtractor graphics, Line line, float x, float centerY, float size, float alpha,
                             List<TextDraw> output, @Nullable float[] clip) {
        float scale = guiToNvg();
        boolean clipToChat = clip != null;
        float cursor = x;
        for (Run run : line.runs) {
            if (run.vanilla) {
                float textScale = size / TEXT_SIZE;
                graphics.pose().pushMatrix();
                graphics.pose().identity();
                if (clipToChat)
                    graphics.enableScissor((int) Math.floor(clip[0] / scale), (int) Math.floor(clip[1] / scale),
                            (int) Math.ceil(clip[2] / scale), (int) Math.ceil(clip[3] / scale));
                graphics.pose().translate(cursor / scale, centerY / scale - 4f * textScale);
                graphics.pose().scale(textScale, textScale);
                graphics.text(mc.font, FormattedCharSequence.forward(run.text, run.style), 0, 0,
                        color(run.rgb, alpha).getRGB(), true);
                if (clipToChat)
                    graphics.disableScissor();
                graphics.pose().popMatrix();
            } else if (!run.text.isBlank()) {
                output.add(new TextDraw(run.text, run.font, cursor, centerY, size, color(run.rgb, alpha)));
            }
            cursor += run.width;
        }
    }

    private static void drawTexts(List<TextDraw> texts) {
        for (TextDraw draw : texts)
            draw.font.drawText(draw.text, draw.x, draw.y, draw.size, draw.color, Alignment.LEFT_MIDDLE, true);
    }

    private static void glassPanel(NVGU vg, float x, float y, float width, float height, float radius) {
        vg.beginEffectBatch();
        NanoVGTheme.renderPanelEffects(vg, x, y, width, height, radius, 1f);
        vg.flushEffectBatch();
        NanoVGTheme.renderPanel(vg, x, y, width, height, radius, 1f);
    }

    public static boolean scoreboard(GuiGraphicsExtractor graphics, Component title, Component[] names) {
        if (!nvgReady())
            return false;

        Line titleLine = layout(title, TEXT_SIZE, 0xFFFFFF, true);
        Line[] nameLines = new Line[names.length];
        float contentWidth = titleLine.width;
        for (int i = 0; i < names.length; i++) {
            nameLines[i] = layout(names[i], TEXT_SIZE, 0xFFFFFF, false);
            contentWidth = Math.max(contentWidth, nameLines[i].width);
        }

        float width = contentWidth + SIDEBAR_PAD * 2f;
        float headerHeight = SIDEBAR_ROW + 8f;
        float height = headerHeight + names.length * SIDEBAR_ROW + 10f;
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        float y = Math.max(10f, screenHeight / 2f + height / 3f - height);
        float moduleListLeft = Interface.moduleListLeftEdge(y, y + height);
        float right = Float.isNaN(moduleListLeft) ? NanoVGManager.getScaledScreenWidth() - 10f : moduleListLeft - 8f;
        float x = right - width;

        List<TextDraw> texts = new ArrayList<>();
        emit(graphics, titleLine, x + (width - titleLine.width) / 2f, y + 4f + SIDEBAR_ROW / 2f, TEXT_SIZE, 1f, texts);
        float rowY = y + headerHeight + 5f;
        for (Line nameLine : nameLines) {
            emit(graphics, nameLine, x + SIDEBAR_PAD, rowY + SIDEBAR_ROW / 2f, TEXT_SIZE, 1f, texts);
            rowY += SIDEBAR_ROW;
        }

        queue(vg -> {
            glassPanel(vg, x, y, width, height, NanoVGTheme.RADIUS_HUD);
            drawTexts(texts);
        });
        return true;
    }

    public static void beginHotbar(boolean styled) {
        hotbarActive = styled && nvgReady();
        hotbarValid = false;
        hotbarSelectionValid = false;
        hotbarOffhandValid = false;
        hotbarAttackValid = false;
        hotbarAttackProgress = 0f;
        hotbarLevel = 0;
        hotbarItemName = null;
        hotbarItemAlpha = 0f;
    }

    public static boolean hotbarActive() {
        return hotbarActive;
    }

    public static void hotbar(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        System.arraycopy(rect(graphics, x, y, x + width, y + height), 0, hotbarRect, 0, 4);
        hotbarValid = true;
    }

    public static void hotbarSelection(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        System.arraycopy(rect(graphics, x + 2, y + 2, x + 22, y + 22), 0, hotbarSelectionRect, 0, 4);
        hotbarSelectionValid = true;
    }

    public static void hotbarOffhand(GuiGraphicsExtractor graphics, int x, int y, boolean left) {
        int slotX = left ? x : x + 7;
        System.arraycopy(rect(graphics, slotX, y + 1, slotX + 22, y + 23), 0, hotbarOffhandRect, 0, 4);
        hotbarOffhandValid = true;
    }

    public static void hotbarAttackBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        System.arraycopy(rect(graphics, x, y, x + width, y + height), 0, hotbarAttackRect, 0, 4);
        hotbarAttackValid = true;
    }

    public static void hotbarAttackProgress(int height, int fullHeight) {
        hotbarAttackProgress = fullHeight <= 0 ? 0f : Math.clamp(height / (float) fullHeight, 0f, 1f);
    }

    public static void hotbarLevel(int level) {
        hotbarLevel = level;
    }

    public static void hotbarItemName(Component name, float alpha, int guiY) {
        hotbarItemName = name;
        hotbarItemAlpha = Math.clamp(alpha, 0f, 1f);
        hotbarItemY = guiY;
    }

    public static void endHotbar(GuiGraphicsExtractor graphics) {
        if (!hotbarActive)
            return;
        hotbarActive = false;

        float scale = guiToNvg();
        float screenWidth = NanoVGManager.getScaledScreenWidth();

        float x = hotbarRect[0];
        float y = hotbarRect[1];
        float width = hotbarRect[2] - hotbarRect[0];
        float height = hotbarRect[3] - hotbarRect[1];
        boolean bar = hotbarValid;

        boolean selection = hotbarSelectionValid;
        float selectionX = hotbarSelectionRect[0];
        float selectionY = hotbarSelectionRect[1];
        float selectionWidth = hotbarSelectionRect[2] - hotbarSelectionRect[0];
        float selectionHeight = hotbarSelectionRect[3] - hotbarSelectionRect[1];

        float[] offhand = hotbarOffhandValid ? hotbarOffhandRect.clone() : null;
        float[] attack = hotbarAttackValid ? hotbarAttackRect.clone() : null;
        float attackProgress = hotbarAttackProgress;

        List<TextDraw> texts = new ArrayList<>();
        if (hotbarLevel > 0) {
            Line level = layout(Component.translatable("gui.experience.level", hotbarLevel), TEXT_SIZE, 0x80FF20, true);
            emit(graphics, level, (screenWidth - level.width) / 2f, (graphics.guiHeight() - 32f) * scale, TEXT_SIZE, 1f, texts);
        }

        if (hotbarItemName != null && hotbarItemAlpha > 0.01f) {
            Line name = layout(hotbarItemName, TEXT_SIZE, 0xFFFFFF, false);
            emit(graphics, name, (screenWidth - name.width) / 2f, (hotbarItemY + 4f) * scale, TEXT_SIZE, hotbarItemAlpha, texts);
        }

        queue(vg -> {
            vg.beginEffectBatch();
            if (bar)
                NanoVGTheme.renderPanelEffects(vg, x, y, width, height, NanoVGTheme.RADIUS_HUD, 1f);
            if (offhand != null)
                NanoVGTheme.renderPanelEffects(vg, offhand[0], offhand[1], offhand[2] - offhand[0], offhand[3] - offhand[1], NanoVGTheme.RADIUS_HUD, 1f);
            if (attack != null)
                NanoVGTheme.renderPanelEffects(vg, attack[0], attack[1], attack[2] - attack[0], attack[3] - attack[1], NanoVGTheme.RADIUS_LABEL, 1f);
            vg.flushEffectBatch();

            if (bar)
                NanoVGTheme.renderPanel(vg, x, y, width, height, NanoVGTheme.RADIUS_HUD, 1f);
            if (offhand != null)
                NanoVGTheme.renderPanel(vg, offhand[0], offhand[1], offhand[2] - offhand[0], offhand[3] - offhand[1], NanoVGTheme.RADIUS_HUD, 1f);

            if (selection) {
                vg.beginEffectBatch();
                NanoVGTheme.renderPanelEffects(vg, selectionX, selectionY, selectionWidth, selectionHeight, NanoVGTheme.RADIUS_LABEL, 1f);
                vg.flushEffectBatch();
                NanoVGTheme.renderPanel(vg, selectionX, selectionY, selectionWidth, selectionHeight, NanoVGTheme.RADIUS_LABEL, 1f, 70);
                vg.roundedRectangleBorder(selectionX, selectionY, selectionWidth, selectionHeight, NanoVGTheme.RADIUS_LABEL, 1f, TAB_INSET_EDGE, Border.INSIDE);
            }

            if (attack != null) {
                float attackWidth = attack[2] - attack[0];
                float attackHeight = attack[3] - attack[1];
                NanoVGTheme.renderPanel(vg, attack[0], attack[1], attackWidth, attackHeight, NanoVGTheme.RADIUS_LABEL, 1f);
                float fill = attackHeight * attackProgress;
                if (fill > 0.5f)
                    vg.roundedRectangle(attack[0], attack[3] - fill, attackWidth, fill, Math.min(NanoVGTheme.RADIUS_LABEL, fill / 2f), HOTBAR_ATTACK);
            }
        });
        if (!texts.isEmpty())
            queueHudText(vg -> drawTexts(texts));
    }

    public static boolean bossBars(GuiGraphicsExtractor graphics, Collection<? extends BossEvent> events) {
        if (!nvgReady())
            return false;

        float scale = guiToNvg();
        float barWidth = 182f * scale;
        float limit = NanoVGManager.getScaledScreenHeight() / 3f;
        List<Line> titles = new ArrayList<>();
        List<BossEvent> shown = new ArrayList<>();
        float contentWidth = barWidth;
        float entryHeight = BOSS_TITLE + 4f + BOSS_BAR + BOSS_GAP;
        float cursorY = 0f;
        for (BossEvent event : events) {
            Line title = layout(ServerAddressSpoof.bossBar(event.getName()), TEXT_SIZE, 0xFFFFFF, false);
            titles.add(title);
            shown.add(event);
            contentWidth = Math.max(contentWidth, title.width);
            cursorY += entryHeight;
            if (10f + cursorY >= limit)
                break;
        }
        if (shown.isEmpty())
            return true;

        float width = contentWidth + BOSS_PAD * 2f;
        float height = shown.size() * entryHeight - BOSS_GAP + BOSS_PAD * 2f - 2f;
        float x = (NanoVGManager.getScaledScreenWidth() - width) / 2f;
        float y = 8f;
        float barX = x + (width - barWidth) / 2f;

        List<TextDraw> texts = new ArrayList<>();
        float[] bars = new float[shown.size() * 3];
        Color[] colors = new Color[shown.size()];
        int[] notches = new int[shown.size()];
        float rowY = y + BOSS_PAD - 2f;
        for (int i = 0; i < shown.size(); i++) {
            BossEvent event = shown.get(i);
            Line title = titles.get(i);
            emit(graphics, title, x + (width - title.width) / 2f, rowY + BOSS_TITLE / 2f, TEXT_SIZE, 1f, texts);
            bars[i * 3] = rowY + BOSS_TITLE + 4f;
            bars[i * 3 + 1] = Math.clamp(event.getProgress(), 0f, 1f);
            colors[i] = bossColor(event.getColor());
            notches[i] = switch (event.getOverlay()) {
                case NOTCHED_6 -> 6;
                case NOTCHED_10 -> 10;
                case NOTCHED_12 -> 12;
                case NOTCHED_20 -> 20;
                default -> 0;
            };
            rowY += entryHeight;
        }

        queue(vg -> {
            glassPanel(vg, x, y, width, height, NanoVGTheme.RADIUS_HUD);
            float radius = BOSS_BAR / 2f;
            for (int i = 0; i < colors.length; i++) {
                float barY = bars[i * 3];
                float progress = bars[i * 3 + 1];
                vg.roundedRectangle(barX, barY, barWidth, BOSS_BAR, radius, BOSS_TRACK);
                float fill = barWidth * progress;
                if (fill > 0.5f)
                    vg.roundedRectangle(barX, barY, Math.max(fill, BOSS_BAR), BOSS_BAR, radius, colors[i]);
                int segments = notches[i];
                for (int n = 1; n < segments; n++) {
                    float notchX = barX + barWidth * n / segments;
                    vg.rectangle(notchX - 0.75f, barY, 1.5f, BOSS_BAR, BOSS_NOTCH);
                }
            }
            drawTexts(texts);
        });
        return true;
    }

    private static Color bossColor(BossEvent.BossBarColor color) {
        return switch (color) {
            case PINK -> new Color(0xF2, 0x6D, 0xC2);
            case BLUE -> new Color(0x4F, 0xA3, 0xFF);
            case RED -> new Color(0xE5, 0x48, 0x4D);
            case GREEN -> new Color(0x5B, 0xD9, 0x7B);
            case YELLOW -> new Color(0xE8, 0xB0, 0x4A);
            case PURPLE -> new Color(0xA8, 0x6B, 0xF0);
            case WHITE -> NanoVGTheme.ACCENT;
        };
    }

    public static boolean tabList(GuiGraphicsExtractor graphics, List<PlayerInfo> players, Component[] names,
                                  @Nullable Component[] scores, @Nullable Component header, @Nullable Component footer,
                                  boolean showHead) {
        if (!nvgReady())
            return false;

        float scale = guiToNvg();
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        int guiWidth = graphics.guiWidth();

        int count = players.size();
        Line[] nameLines = new Line[count];
        Line[] scoreLines = new Line[count];
        String[] pings = new String[count];
        float maxName = 0f;
        float maxScore = 0f;
        float maxPing = 0f;
        for (int i = 0; i < count; i++) {
            PlayerInfo info = players.get(i);
            boolean spectator = info.getGameMode() == GameType.SPECTATOR;
            nameLines[i] = layout(names[i], TEXT_SIZE, spectator ? 0xAAAAAA : 0xFFFFFF, false);
            scoreLines[i] = scores != null && scores[i] != null && !spectator
                    ? layout(scores[i], TEXT_SIZE, 0xFFFF55, false)
                    : new Line(List.of(), 0f, false);
            pings[i] = info.getLatency() >= 0 ? info.getLatency() + "ms" : "--";
            maxName = Math.max(maxName, nameLines[i].width);
            maxScore = Math.max(maxScore, scoreLines[i].width);
            maxPing = Math.max(maxPing, NVGFonts.INTER.getWidth(pings[i], 13f));
        }

        int rows = count;
        int cols = 1;
        while (rows > 20) {
            cols++;
            rows = (count + cols - 1) / cols;
        }

        float headSize = 8f * scale;
        float slotWidth = 6f + (showHead ? headSize + 6f : 0f) + maxName + (maxScore > 0f ? 14f + maxScore : 0f) + 14f + maxPing + 8f;
        float maxGridWidth = screenWidth - 100f;
        slotWidth = Math.min(slotWidth, (maxGridWidth - (cols - 1) * TAB_COLUMN_GAP) / cols);
        float gridWidth = count == 0 ? 0f : slotWidth * cols + (cols - 1) * TAB_COLUMN_GAP;

        List<Line> headerLines = splitLines(header, guiWidth - 50);
        List<Line> footerLines = splitLines(footer, guiWidth - 50);
        float contentWidth = gridWidth;
        for (Line line : headerLines)
            contentWidth = Math.max(contentWidth, line.width);
        for (Line line : footerLines)
            contentWidth = Math.max(contentWidth, line.width);

        float width = Math.max(contentWidth, gridWidth + TAB_INSET * 2f) + TAB_PAD * 2f;
        float x = (screenWidth - width) / 2f;
        float y = 10f * scale;
        float cursorY = y + TAB_PAD;
        List<TextDraw> texts = new ArrayList<>();

        for (Line line : headerLines) {
            emit(graphics, line, x + (width - line.width) / 2f, cursorY + TAB_LINE / 2f, TEXT_SIZE, 1f, texts);
            cursorY += TAB_LINE;
        }
        if (!headerLines.isEmpty() && count > 0)
            cursorY += 11f + TAB_INSET;
        else if (count > 0)
            cursorY += TAB_INSET;

        float gridX = x + (width - gridWidth) / 2f;
        float gridY = cursorY;
        for (int i = 0; i < count; i++) {
            PlayerInfo info = players.get(i);
            int col = i / rows;
            int row = i % rows;
            float slotX = gridX + col * (slotWidth + TAB_COLUMN_GAP);
            float slotY = gridY + row * TAB_ROW;
            float centerY = slotY + (TAB_ROW - 2f) / 2f;

            float textX = slotX + 6f;
            if (showHead) {
                Player player = mc.level != null ? mc.level.getPlayerByUUID(info.getProfile().id()) : null;
                boolean flip = player != null && AvatarRenderer.isPlayerUpsideDown(player);
                PlayerFaceExtractor.extractRenderState(graphics, info.getSkin().body().texturePath(),
                        Math.round(textX / scale), Math.round(centerY / scale - 4f), 8, info.showHat(), flip, -1);
                textX += headSize + 6f;
            }

            float alpha = info.getGameMode() == GameType.SPECTATOR ? 0.6f : 1f;
            emit(graphics, nameLines[i], textX, centerY, TEXT_SIZE, alpha, texts);
            float pingRight = slotX + slotWidth - 8f;
            if (scoreLines[i].width > 0f)
                emit(graphics, scoreLines[i], pingRight - maxPing - 14f - scoreLines[i].width, centerY, TEXT_SIZE, 1f, texts);
            float pingWidth = NVGFonts.INTER.getWidth(pings[i], 13f);
            texts.add(new TextDraw(pings[i], NVGFonts.INTER, pingRight - pingWidth, centerY, 13f, pingColor(info.getLatency())));
        }
        cursorY = gridY + rows * TAB_ROW - 2f + (count > 0 ? TAB_INSET : 0f);

        if (!footerLines.isEmpty()) {
            cursorY += 11f;
            for (Line line : footerLines) {
                emit(graphics, line, x + (width - line.width) / 2f, cursorY + TAB_LINE / 2f, TEXT_SIZE, 1f, texts);
                cursorY += TAB_LINE;
            }
        }

        float height = cursorY - y + TAB_PAD;
        boolean hasGrid = count > 0;
        float insetX = gridX - TAB_INSET;
        float insetY = gridY - TAB_INSET;
        float insetWidth = gridWidth + TAB_INSET * 2f;
        float insetHeight = rows * TAB_ROW - 2f + TAB_INSET * 2f;
        queue(vg -> {
            glassPanel(vg, x, y, width, height, NanoVGTheme.RADIUS_PANEL);
            if (hasGrid) {
                vg.beginEffectBatch();
                NanoVGTheme.renderPanelEffects(vg, insetX, insetY, insetWidth, insetHeight, NanoVGTheme.RADIUS_HUD, 1f);
                vg.flushEffectBatch();
                NanoVGTheme.renderPanel(vg, insetX, insetY, insetWidth, insetHeight, NanoVGTheme.RADIUS_HUD, 1f, 70);
                vg.roundedRectangleBorder(insetX, insetY, insetWidth, insetHeight, NanoVGTheme.RADIUS_HUD, 1f, TAB_INSET_EDGE, Border.INSIDE);
            }
            drawTexts(texts);
        });
        return true;
    }

    private static List<Line> splitLines(@Nullable Component component, int width) {
        if (component == null)
            return List.of();
        List<Line> lines = new ArrayList<>();
        for (FormattedCharSequence sequence : mc.font.split(component, Math.max(1, width)))
            lines.add(layout(sequence, TEXT_SIZE, 0xFFFFFF, false));
        return lines;
    }

    private static Color pingColor(int latency) {
        if (latency < 0)
            return NanoVGTheme.muted(160);
        if (latency <= 80)
            return NanoVGTheme.muted(220);
        if (latency <= 180)
            return NanoVGTheme.warning(220);
        return NanoVGTheme.danger(220);
    }

    public static void beginChat(GuiGraphicsExtractor graphics, boolean styled, boolean foreground, int mouseX, int mouseY, boolean cursorOnInsertions) {
        chatActive = styled && nvgReady();
        chatForeground = foreground;
        chatCursorOnInsertions = cursorOnInsertions;
        chatGraphics = graphics;
        float scale = guiToNvg();
        chatMouseX = mouseX * scale;
        chatMouseY = mouseY * scale;
        chatMouseGuiX = mouseX;
        chatMouseGuiY = mouseY;
        chatLineCount = 0;
        chatTexts.clear();
        chatRows.clear();
        chatTags.clear();
        chatClip = false;
        chatOffsetY = 0f;
        if (!foreground)
            chatDragging = false;
    }

    public static int chatScroll(ChatComponent chat, List<?> lines, int pos, int perPage,
                                 float left, float top, float right, float bottom, float lineHeight) {
        float scale = guiToNvg();
        float height = lineHeight * scale;
        int total = lines.size();
        Object newest = total > 0 ? lines.get(0) : null;
        Object previousNewest = chatNewestLine;
        chatNewestLine = newest;
        chatComponent = chat;
        chatLineHeight = height;
        chatMaxScroll = Math.max(0, total - perPage);
        chatClipX0 = left * scale;
        chatClipY0 = top * scale;
        chatClipX1 = right * scale;
        chatClipY1 = bottom * scale;
        chatClip = chatForeground;
        if (chatForeground)
            chatPanelRight = chatClipX1;

        long now = System.nanoTime();
        float delta = chatScrollNanos == 0L ? 1f : Math.clamp((now - chatScrollNanos) / 16_666_666f, 0.25f, 3f);
        chatScrollNanos = now;

        if (!chatForeground || height <= 0f) {
            chatScroll = pos * height;
            chatScrollVelocity = 0f;
            chatWasForeground = false;
            chatLastPos = pos;
            chatLastTotal = total;
            chatTrackValid = false;
            return pos;
        }

        if (!chatWasForeground) {
            chatScroll = pos * height;
            chatScrollVelocity = 0f;
            chatScrollbarHover = 0f;
        } else if (chatLastPos > 0 && previousNewest != null && newest != previousNewest) {
            int added = 0;
            for (int i = 1; i < total; i++) {
                if (lines.get(i) == previousNewest) {
                    added = i;
                    break;
                }
            }
            chatScroll += added * height;
        }
        chatWasForeground = true;
        chatLastPos = pos;
        chatLastTotal = total;

        float maxScroll = chatMaxScroll * height;
        if (chatDragging) {
            chatScroll = Math.clamp(chatDragLines * height, 0f, maxScroll);
            chatScrollVelocity = 0f;
        } else {
            float target = Math.clamp(pos * height, 0f, maxScroll);
            float distance = target - chatScroll;
            chatScrollVelocity += distance * 0.045f * delta;
            chatScrollVelocity *= (float) Math.pow(0.74f, delta);
            chatScrollVelocity = Math.clamp(chatScrollVelocity, -42f, 42f);
            chatScroll += chatScrollVelocity * delta;
            chatScroll += (target - chatScroll) * Math.clamp(1f - (float) Math.pow(0.91f, delta), 0f, 1f);
            if (chatScroll < 0f || chatScroll > maxScroll) {
                chatScroll = Math.clamp(chatScroll, 0f, maxScroll);
                chatScrollVelocity = 0f;
            }
            if (Math.abs(target - chatScroll) < 0.06f && Math.abs(chatScrollVelocity) < 0.06f) {
                chatScroll = target;
                chatScrollVelocity = 0f;
            }
        }

        float scrolledLines = chatScroll / height;
        int base = Math.clamp((int) Math.floor(scrolledLines), 0, chatMaxScroll);
        chatOffsetY = (scrolledLines - base) * height;
        updateChatScrollbar();
        return base;
    }

    public static boolean chatExtraLine() {
        return chatActive() && chatOffsetY > 0.01f;
    }

    private static void updateChatScrollbar() {
        float viewport = chatClipY1 - chatClipY0;
        if (chatMaxScroll <= 0 || viewport <= 0f) {
            chatTrackValid = false;
            return;
        }
        float maxScroll = chatMaxScroll * chatLineHeight;
        float contentHeight = viewport + maxScroll;
        chatThumbHeight = Math.min(viewport, Math.max(22f, viewport * viewport / contentHeight));
        float travel = viewport - chatThumbHeight;
        chatThumbY = chatClipY0 + travel * (1f - chatScroll / maxScroll);
        chatTrackX = chatClipX1 + 4f;
        chatTrackY = chatClipY0;
        chatTrackHeight = viewport;
        chatTrackValid = true;
        boolean hovered = chatDragging || chatTrackContains(chatMouseX, chatMouseY);
        chatScrollbarHover += ((hovered ? 1f : 0f) - chatScrollbarHover) * 0.18f;
        if (hovered && chatGraphics != null)
            chatGraphics.requestCursor(CursorTypes.POINTING_HAND);
    }

    private static boolean chatTrackContains(float x, float y) {
        return chatTrackValid && x >= chatTrackX - 5f && x < chatTrackX + 7f && y >= chatTrackY && y < chatTrackY + chatTrackHeight;
    }

    public static boolean chatScrollbarPress(double mouseX, double mouseY) {
        float scale = guiToNvg();
        float x = (float) mouseX * scale;
        float y = (float) mouseY * scale;
        if (!chatTrackContains(x, y) || chatComponent == null)
            return false;
        chatDragOffset = y >= chatThumbY && y < chatThumbY + chatThumbHeight ? y - chatThumbY : chatThumbHeight / 2f;
        chatDragging = true;
        chatDragLines = chatScroll / Math.max(1f, chatLineHeight);
        chatScrollbarDrag(mouseY);
        return true;
    }

    public static boolean chatScrollbarDrag(double mouseY) {
        if (!chatDragging || chatComponent == null || !chatTrackValid)
            return false;
        float y = (float) mouseY * guiToNvg();
        float travel = Math.max(1f, chatTrackHeight - chatThumbHeight);
        float thumbY = Math.clamp(y - chatDragOffset, chatTrackY, chatTrackY + travel);
        float progress = (thumbY - chatTrackY) / travel;
        chatDragLines = (1f - progress) * chatMaxScroll;
        int pos = Math.clamp(Math.round(chatDragLines), 0, chatMaxScroll);
        ChatComponentAccessor accessor = (ChatComponentAccessor) chatComponent;
        accessor.krs$setChatScrollbarPos(pos);
        if (pos == 0)
            accessor.krs$setNewMessageSinceScroll(false);
        return true;
    }

    public static boolean chatScrollbarRelease() {
        boolean wasDragging = chatDragging;
        chatDragging = false;
        return wasDragging;
    }

    private static float[] clipChat(float y0, float y1) {
        if (!chatClip)
            return new float[]{y0, y1};
        float top = Math.max(y0, chatClipY0);
        float bottom = Math.min(y1, chatClipY1);
        return bottom - top > 0.01f ? new float[]{top, bottom} : null;
    }

    public static void chatTag(int x0, int y0, int x1, int y1, float opacity, GuiMessageTag tag) {
        float[] r = rect(chatGraphics, x0, y0, x1, y1);
        float left = r[0];
        float right = r[2];
        float[] clipped = clipChat(r[1] + chatOffsetY, r[3] + chatOffsetY);
        if (clipped == null)
            return;
        chatTags.add(new float[]{left, clipped[0], right - left, clipped[1] - clipped[0], opacity, tag.indicatorColor()});
        if (chatForeground && tag.text() != null && !chatMouseOverSuggestions()
                && chatMouseX >= left && chatMouseX < right && chatMouseY >= clipped[0] && chatMouseY < clipped[1])
            chatGraphics.setTooltipForNextFrame(mc.font, mc.font.split(tag.text(), 210), chatMouseGuiX, chatMouseGuiY);
    }

    private static boolean chatMouseOverSuggestions() {
        if (!(mc.gui.screen() instanceof ChatScreen chatScreen))
            return false;
        CommandSuggestions suggestions = ((ChatScreenAccessor) chatScreen).krs$getCommandSuggestions();
        if (suggestions == null)
            return false;
        CommandSuggestions.SuggestionsList list = ((CommandSuggestionsAccessor) suggestions).krs$getSuggestions();
        if (list == null)
            return false;
        Rect2i rect = ((SuggestionsListAccessor) (Object) list).krs$getRect();
        return chatMouseGuiX >= rect.getX() - 2 && chatMouseGuiX < rect.getX() + rect.getWidth() + 2
                && chatMouseGuiY >= rect.getY() - 2 && chatMouseGuiY < rect.getY() + rect.getHeight() + 2;
    }

    public static boolean chatActive() {
        return chatActive && chatGraphics != null;
    }

    public static void chatLine(int x0, int y0, int x1, int y1, float alpha) {
        if (alpha <= 0.001f)
            return;
        if (chatLineCount * 5 + 5 > chatLines.length)
            chatLines = Arrays.copyOf(chatLines, chatLines.length * 2);
        float[] r = rect(chatGraphics, x0, y0, x1, y1);
        float[] clipped = clipChat(r[1] + chatOffsetY, r[3] + chatOffsetY);
        if (clipped == null)
            return;
        int o = chatLineCount * 5;
        chatLines[o] = r[0];
        chatLines[o + 1] = clipped[0];
        chatLines[o + 2] = r[2];
        chatLines[o + 3] = clipped[1];
        chatLines[o + 4] = Math.min(1f, alpha);
        chatLineCount++;
    }

    public static @Nullable Boolean chatMessage(int textTop, float opacity, FormattedCharSequence message) {
        float scale = guiToNvg();
        chatGraphics.pose().transformPosition(0f, textTop, POINT);
        float topY = POINT.y * scale + chatOffsetY;
        chatGraphics.pose().transformPosition(0f, textTop + 4f, POINT);
        float originX = POINT.x * scale;
        float centerY = POINT.y * scale + chatOffsetY;
        float halfHeight = Math.max(1f, centerY - topY) * 9f / 8f;
        float size = TEXT_SIZE * (centerY - topY) / (4f * scale);

        Line line = layout(message, size, 0xFFFFFF, false);

        List<HitRun> hits = new ArrayList<>(line.runs.size());
        float cursor = originX;
        for (Run run : line.runs) {
            hits.add(new HitRun(cursor, cursor + run.width, run.style));
            cursor += run.width;
        }
        float[] rowBounds = clipChat(centerY - halfHeight, centerY + halfHeight);
        if (rowBounds == null)
            return false;
        emit(chatGraphics, line, originX, centerY, size, opacity, chatTexts, chatClip);
        ChatRow row = new ChatRow(originX, rowBounds[0], cursor, rowBounds[1], false, hits);
        chatRows.add(row);

        if (!chatForeground || chatMouseOverSuggestions())
            return false;
        Style hovered = styleAt(row, chatMouseX, chatMouseY);
        if (hovered == null)
            return false;

        GuiGraphicsExtractorAccessor accessor = (GuiGraphicsExtractorAccessor) chatGraphics;
        if (hovered.getHoverEvent() != null)
            accessor.krs$setHoveredTextStyle(hovered);
        if (hovered.getClickEvent() != null)
            accessor.krs$setClickableTextStyle(hovered);
        if (chatCursorOnInsertions && hovered.getInsertion() != null)
            chatGraphics.requestCursor(CursorTypes.POINTING_HAND);
        return true;
    }

    private static @Nullable Style styleAt(ChatRow row, float x, float y) {
        if (y < row.y0 || y >= row.y1)
            return null;
        for (HitRun hit : row.runs) {
            if (x >= hit.x0 && x < hit.x1)
                return hit.style;
        }
        return null;
    }

    public static boolean chatClickHandled(double mouseX, double mouseY) {
        if (mc.gui.hud.getGuiTicks() - lastChatRowsFrame > 2)
            return false;
        float scale = guiToNvg();
        float y = (float) mouseY * scale;
        for (ChatRow row : lastChatRows) {
            if (y >= row.y0 && y < row.y1)
                return !row.vanilla;
        }
        return false;
    }

    public static @Nullable Style chatClickStyle(double mouseX, double mouseY, boolean includeInsertions) {
        float scale = guiToNvg();
        float x = (float) mouseX * scale;
        float y = (float) mouseY * scale;
        for (ChatRow row : lastChatRows) {
            Style style = styleAt(row, x, y);
            if (style != null && (style.getClickEvent() != null || includeInsertions && style.getInsertion() != null))
                return style;
        }
        return null;
    }

    public static void endChat() {
        if (!chatActive) {
            chatGraphics = null;
            return;
        }
        chatActive = false;
        chatGraphics = null;
        if (chatForeground) {
            lastChatRows = List.copyOf(chatRows);
            lastChatRowsFrame = mc.gui.hud.getGuiTicks();
        }
        boolean scrollbar = chatForeground && chatTrackValid;
        if (chatLineCount == 0 && !scrollbar && chatTexts.isEmpty())
            return;

        int lineCount = chatLineCount;
        float[] lines = Arrays.copyOf(chatLines, lineCount * 5);
        sortLinesByTop(lines, lineCount);
        float trackX = chatTrackX;
        float trackY = chatTrackY;
        float trackHeight = chatTrackHeight;
        float thumbY = chatThumbY;
        float thumbHeight = chatThumbHeight;
        float hover = Math.clamp(chatScrollbarHover, 0f, 1f);
        boolean clip = chatClip;
        float clipX0 = chatClipX0;
        float clipY0 = chatClipY0;
        float clipX1 = chatClipX1;
        float clipY1 = chatClipY1;
        List<float[]> tags = List.copyOf(chatTags);
        List<Corners> groups = groupChatLines(lines, lineCount);
        float[] groupAlpha = new float[groups.size()];
        int groupIndex = 0;
        for (int start = 0; start < lineCount; ) {
            int end = chatGroupEnd(lines, lineCount, start);
            groupAlpha[groupIndex++] = lines[start * 5 + 4];
            start = end + 1;
        }
        List<TextDraw> texts = List.copyOf(chatTexts);

        queue(vg -> {
            vg.beginEffectBatch();
            for (int i = 0; i < groups.size(); i++)
                NanoVGTheme.renderConnectedEffects(vg, List.of(groups.get(i)), NanoVGTheme.RADIUS_HUD, groupAlpha[i]);
            vg.flushEffectBatch();
            for (int i = 0; i < groups.size(); i++)
                NanoVGTheme.renderConnected(vg, List.of(groups.get(i)), NanoVGTheme.RADIUS_HUD, groupAlpha[i]);

            for (float[] tag : tags) {
                int rgb = (int) tag[5];
                vg.rectangle(tag[0], tag[1], tag[2], tag[3], color(rgb, tag[4]));
            }

            if (scrollbar) {
                vg.roundedRectangle(trackX + 1f, trackY + 4f, 2f, trackHeight - 8f, 1f,
                        new Color(255, 255, 255, (int) (18 + 18 * hover)));
                vg.roundedRectangle(trackX - 1f, thumbY, 5f, thumbHeight, 2.5f,
                        NanoVGTheme.scrollbarThumb((int) (112 + 86 * hover)));
            }

            if (clip)
                vg.pushScissor(clipX0, clipY0, clipX1 - clipX0, clipY1 - clipY0);
            drawTexts(texts);
            if (clip)
                vg.popScissor();
        });
    }

    private static int chatGroupEnd(float[] lines, int count, int start) {
        int end = start;
        float alpha = lines[start * 5 + 4];
        while (end + 1 < count
                && Math.abs(lines[(end + 1) * 5 + 4] - alpha) < 0.002f
                && Math.abs(lines[(end + 1) * 5 + 1] - lines[end * 5 + 3]) < 0.5f)
            end++;
        return end;
    }

    private static List<Corners> groupChatLines(float[] lines, int count) {
        List<Corners> groups = new ArrayList<>();
        if (count == 0)
            return groups;
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            left = Math.min(left, lines[i * 5]);
            right = Math.max(right, lines[i * 5 + 2]);
        }
        float radius = NanoVGTheme.RADIUS_HUD;
        for (int start = 0; start < count; ) {
            int end = chatGroupEnd(lines, count, start);
            float top = lines[start * 5 + 1];
            float bottom = lines[end * 5 + 3];
            float topRadius = start == 0 ? radius : 0f;
            float bottomRadius = end == count - 1 ? radius : 0f;
            groups.add(new Corners(left, top, right - left, bottom - top, topRadius, topRadius, bottomRadius, bottomRadius));
            start = end + 1;
        }
        return groups;
    }

    private static void sortLinesByTop(float[] lines, int count) {
        for (int i = 1; i < count; i++) {
            int j = i;
            while (j > 0 && lines[(j - 1) * 5 + 1] > lines[j * 5 + 1]) {
                for (int k = 0; k < 5; k++) {
                    float tmp = lines[(j - 1) * 5 + k];
                    lines[(j - 1) * 5 + k] = lines[j * 5 + k];
                    lines[j * 5 + k] = tmp;
                }
                j--;
            }
        }
    }

    public static void chatInput(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        float[] r = rect(graphics, x0, y0, x1, y1);
        float x = r[0];
        float y = r[1];
        float width = r[2] - r[0];
        float height = r[3] - r[1];
        queue(vg -> glassPanel(vg, x, y, width, height, NanoVGTheme.RADIUS_HUD));
    }
}
