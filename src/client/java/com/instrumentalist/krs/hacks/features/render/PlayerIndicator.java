package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.combat.AntiBot;
import com.instrumentalist.krs.hacks.features.combat.Teams;
import com.instrumentalist.krs.utils.entity.StreamConverter;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import com.instrumentalist.krs.utils.render.RenderUtil;
import com.instrumentalist.krs.utils.render.Shader2DRenderer;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.ColorValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shader-backed, off-screen player indicators with compact tactical metadata.
 */
public class PlayerIndicator extends Module {
    private static final int MAX_TRACKED_PLAYERS = 128;
    private static final int MAX_LABELED_PLAYERS = 32;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float LABEL_FONT_SIZE = 13f;
    private static final float PLAYER_HEAD_SIZE_SCALE = 0.95f;
    private static final float PLAYER_HEAD_OFFSET_SCALE = 0.72f;
    private static final long PLAYER_HEAD_RETRY_DELAY_NANOS = 5_000_000_000L;
    private static final Color LABEL_TEXT = new Color(245, 248, 255, 235);
    private static final Color PLAYER_HEAD_BACKGROUND = new Color(8, 11, 16, 225);
    private static final Comparator<IndicatorData> NEAREST_FIRST = Comparator.comparingDouble(data -> data.distanceSquared);

    @Setting
    private static final ListValue targets = new ListValue(
            "Targets",
            new String[]{"Off Screen", "All"},
            "Off Screen"
    );

    @Setting
    private static final FloatValue radius = new FloatValue("Radius", 42f, 24f, 48f, "%");

    @Setting
    private static final FloatValue size = new FloatValue("Size", 20f, 8f, 24f, "px");

    @Setting
    private static final FloatValue thickness = new FloatValue("Thickness", 2.2f, 1f, 5f, "px");

    @Setting
    private static final BooleanValue glow = new BooleanValue("Glow", true);

    @Setting
    private static final FloatValue glowRadius = new FloatValue(
            "Glow Radius",
            8f,
            2f,
            18f,
            "px",
            glow::get
    );

    @Setting
    private static final BooleanValue pulse = new BooleanValue("Pulse", true, glow::get);

    @Setting
    private static final ListValue colorMode = new ListValue(
            "Color Mode",
            new String[]{"Health", "Static"},
            "Health"
    );

    @Setting
    private static final ColorValue color = new ColorValue(
            "Color",
            new Color(0, 230, 255, 235),
            () -> colorMode.get().equalsIgnoreCase("Static")
    );

    @Setting
    private static final BooleanValue ignoreTeams = new BooleanValue("Ignore Teams", true);

    @Setting
    private static final BooleanValue ignoreBots = new BooleanValue("Ignore Bots", true);

    @Setting
    private static final IntValue maxDistance = new IntValue("Max Distance", 160, 16, 512, "m");

    private final ArrayList<IndicatorData> indicators = new ArrayList<>(32);
    private final ArrayList<IndicatorData> indicatorPool = new ArrayList<>(32);
    private final ArrayList<IndicatorData> placedIndicators = new ArrayList<>(32);
    private final ArrayList<Shader2DRenderer.IndicatorRequest> shaderRequests = new ArrayList<>(32);
    private final ArrayList<Shader2DRenderer.IndicatorRequest> shaderRequestPool = new ArrayList<>(32);
    private final Map<String, PlayerHeadTextureState> playerHeadTextures = new HashMap<>();
    private final float[] projectedPosition = new float[3];
    private long playerHeadTextureGeneration;

    public PlayerIndicator() {
        super("Player Indicator", ModuleCategory.Render, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        clearFrameData();
        clearPlayerHeadTextureStates();
    }

    @Override
    public void onWorld(WorldEvent event) {
        clearFrameData();
        clearPlayerHeadTextureStates();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null || !RenderUtil.shouldRenderWorldHudOverlays()) {
            indicators.clear();
            return;
        }

        boolean offScreenOnly = targets.get().equalsIgnoreCase("Off Screen");
        boolean teamsActive = ignoreTeams.get() && ModuleManager.getModuleState(Teams.class);
        boolean antiBotActive = ignoreBots.get() && ModuleManager.getModuleState(AntiBot.class);
        double maximumDistanceSquared = (double) maxDistance.get() * maxDistance.get();
        Vec3 cameraPosition = mc.gameRenderer.mainCamera().position();
        float cameraYawRadians = (float) Math.toRadians(mc.gameRenderer.mainCamera().yRot());
        float framebufferToScaledX = NanoVGManager.getScaledScreenWidth() / Math.max(1, mc.getWindow().getWidth());
        float framebufferToScaledY = NanoVGManager.getScaledScreenHeight() / Math.max(1, mc.getWindow().getHeight());

        indicators.clear();
        int pooledCount = 0;

        for (AbstractClientPlayer player : mc.level.players()) {
            if (player == mc.player || player.isRemoved() || player.isSpectator() || !player.isAlive())
                continue;
            if (teamsActive && Teams.isInClientPlayersTeam(player))
                continue;
            if (antiBotActive && AntiBot.inBotList(player))
                continue;

            Vec3 playerPosition = RenderUtil.INSTANCE.getLerpedPos(player, event.partialTicks);
            double deltaX = playerPosition.x - cameraPosition.x;
            double deltaZ = playerPosition.z - cameraPosition.z;
            double horizontalDistanceSquared = deltaX * deltaX + deltaZ * deltaZ;
            if (horizontalDistanceSquared < 0.01 || horizontalDistanceSquared > maximumDistanceSquared)
                continue;

            boolean visibleOnScreen = RenderUtil.INSTANCE.renderedWorldToScreen(
                    playerPosition.x,
                    playerPosition.y + player.getBbHeight() * 0.55,
                    playerPosition.z,
                    projectedPosition
            );
            if (offScreenOnly && visibleOnScreen)
                continue;

            float worldBearing = (float) Math.atan2(-deltaX, deltaZ);
            float relativeAngle = wrapRadians(worldBearing - cameraYawRadians);
            float screenX = visibleOnScreen ? projectedPosition[0] * framebufferToScaledX : Float.NaN;
            float screenY = visibleOnScreen ? projectedPosition[1] * framebufferToScaledY : Float.NaN;
            float healthRatio = healthRatio(player);
            while (indicatorPool.size() <= pooledCount)
                indicatorPool.add(new IndicatorData());
            IndicatorData data = indicatorPool.get(pooledCount++);
            data.update(
                    player,
                    relativeAngle,
                    screenX,
                    screenY,
                    Math.sqrt(horizontalDistanceSquared),
                    healthRatio,
                    horizontalDistanceSquared,
                    visibleOnScreen
            );
            indicators.add(data);
        }

        indicators.sort(NEAREST_FIRST);
        if (indicators.size() > MAX_TRACKED_PLAYERS)
            indicators.subList(MAX_TRACKED_PLAYERS, indicators.size()).clear();

        for (IndicatorData data : indicators) {
            requestPlayerHeadTexture(data.player, data.playerTextureIdentifier);
        }
        for (int index = 0; index < pooledCount; index++)
            indicatorPool.get(index).player = null;
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        if (mc.player == null || mc.level == null || indicators.isEmpty())
            return;
        if (!RenderUtil.shouldRenderWorldHudOverlays()) {
            indicators.clear();
            return;
        }

        Client.nanoVgManager.load(vg -> renderIndicators(vg));
    }

    private void renderIndicators(NVGU vg) {
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        if (screenWidth <= 1f || screenHeight <= 1f)
            return;

        float centerX = screenWidth * 0.5f;
        float centerY = screenHeight * 0.5f;
        float radiusFactor = Mth.clamp(radius.get() / 100f, 0.24f, 0.48f);
        float ringRadius = Math.min(screenWidth, screenHeight) * radiusFactor;
        float edgePadding = size.get() + Math.max(14f, glow.get() ? glowRadius.get() : 0f) + 8f;
        float radiusX = Math.max(18f, Math.min(ringRadius, centerX - edgePadding));
        float radiusY = Math.max(18f, Math.min(ringRadius, centerY - edgePadding));
        float playerHeadDiameter = Math.max(8f, size.get() * PLAYER_HEAD_SIZE_SCALE);
        float playerHeadOffset = size.get() * PLAYER_HEAD_OFFSET_SCALE;
        float time = (System.nanoTime() % 20_000_000_000L) / 1_000_000_000f;

        shaderRequests.clear();
        placedIndicators.clear();
        int shaderRequestCount = 0;

        for (IndicatorData data : indicators) {
            float positionX;
            float positionY;
            boolean onScreenPlacement = data.onScreen && targets.get().equalsIgnoreCase("All");
            if (onScreenPlacement) {
                positionX = Mth.clamp(data.screenX, edgePadding, screenWidth - edgePadding);
                positionY = Mth.clamp(data.screenY - size.get() - 5f, edgePadding, screenHeight - edgePadding);
            } else {
                positionX = centerX + (float) Math.sin(data.angle) * radiusX;
                positionY = centerY - (float) Math.cos(data.angle) * radiusY;
            }

            data.drawAngle = onScreenPlacement ? 0f : data.angle;
            data.drawX = positionX;
            data.drawY = positionY;
            separateOverlappingIndicator(data, centerX, centerY, radiusX, radiusY, onScreenPlacement, edgePadding, screenWidth, screenHeight);
            preparePlayerHeadPlacement(data, centerX, centerY, playerHeadDiameter, playerHeadOffset);
            placedIndicators.add(data);

            float pulseAmount = pulse.get()
                    ? 0.5f + 0.5f * (float) Math.sin(time * TAU * 0.65f + data.angle * 1.7f)
                    : 0f;
            Color indicatorColor = indicatorColor(data.healthRatio);
            data.drawColor = indicatorColor;

            while (shaderRequestPool.size() <= shaderRequestCount)
                shaderRequestPool.add(new Shader2DRenderer.IndicatorRequest());
            Shader2DRenderer.IndicatorRequest request = shaderRequestPool.get(shaderRequestCount++).set(
                    data.drawX,
                    data.drawY,
                    data.drawAngle,
                    size.get(),
                    thickness.get(),
                    glow.get() ? glowRadius.get() : 0f,
                    pulseAmount,
                    indicatorColor
            );
            shaderRequests.add(request);
        }

        vg.directionalIndicators(shaderRequests);

        for (IndicatorData data : placedIndicators)
            drawPlayerHead(vg, data);

        for (int index = 0; index < placedIndicators.size(); index++) {
            IndicatorData data = placedIndicators.get(index);
            prepareLabel(data);
            data.labelVisible = index < MAX_LABELED_PLAYERS && !data.label.isEmpty();
            if (data.labelVisible)
                separateOverlappingLabel(data, index, screenWidth, screenHeight);
        }

        vg.beginEffectBatch();
        for (IndicatorData data : placedIndicators) {
            if (!data.labelVisible)
                continue;
            NanoVGTheme.renderCompactEffects(vg, data.labelX, data.labelY, data.labelWidth, data.labelHeight, 5f, 1f);
        }
        vg.flushEffectBatch();

        for (IndicatorData data : placedIndicators) {
            if (!data.labelVisible)
                continue;
            NanoVGTheme.renderCompact(vg, data.labelX, data.labelY, data.labelWidth, data.labelHeight, 5f, 1f);
            vg.roundedRectangle(data.labelX + 3f, data.labelY + 3f, 2f, data.labelHeight - 6f, 1f, withAlpha(data.drawColor, 210));
            NVGFonts.INTER_MEDIUM.drawText(
                    data.label,
                    data.labelX + data.labelWidth * 0.5f + 1f,
                    data.labelY + data.labelHeight * 0.5f,
                    LABEL_FONT_SIZE,
                    LABEL_TEXT,
                    Alignment.CENTER_MIDDLE,
                    false
            );
        }
    }

    private void separateOverlappingIndicator(IndicatorData data, float centerX, float centerY,
                                              float radiusX, float radiusY, boolean onScreenPlacement,
                                              float edgePadding, float screenWidth, float screenHeight) {
        float minimumSpacing = size.get() * 1.7f + 5f;
        float minimumSpacingSquared = minimumSpacing * minimumSpacing;

        for (int attempt = 0; attempt < 8; attempt++) {
            boolean collision = false;
            for (IndicatorData placed : placedIndicators) {
                float dx = data.drawX - placed.drawX;
                float dy = data.drawY - placed.drawY;
                if (dx * dx + dy * dy < minimumSpacingSquared) {
                    collision = true;
                    break;
                }
            }
            if (!collision)
                return;

            if (onScreenPlacement) {
                float direction = (attempt & 1) == 0 ? -1f : 1f;
                float step = (attempt / 2f + 1f) * minimumSpacing;
                data.drawX = Mth.clamp(data.screenX + direction * step, edgePadding, screenWidth - edgePadding);
                data.drawY = Mth.clamp(data.screenY - size.get() - 5f, edgePadding, screenHeight - edgePadding);
            } else {
                float laneOffset = (attempt + 1f) * minimumSpacing;
                float laneRadiusX = Math.max(18f, radiusX - laneOffset);
                float laneRadiusY = Math.max(18f, radiusY - laneOffset);
                data.drawX = centerX + (float) Math.sin(data.angle) * laneRadiusX;
                data.drawY = centerY - (float) Math.cos(data.angle) * laneRadiusY;
            }
        }
    }

    private void separateOverlappingLabel(IndicatorData data, int dataIndex, float screenWidth, float screenHeight) {
        float baseX = data.labelX;
        float baseY = data.labelY;
        float directionX = data.drawX - screenWidth * 0.5f;
        float directionY = data.drawY - screenHeight * 0.5f;
        float directionLength = (float) Math.hypot(directionX, directionY);
        if (directionLength < 0.001f)
            directionLength = 1f;
        float tangentX = -directionY / directionLength;
        float tangentY = directionX / directionLength;
        float step = data.labelHeight + 3f;

        for (int attempt = 0; attempt < 13; attempt++) {
            int signedStep = attempt == 0 ? 0 : (attempt + 1) / 2 * ((attempt & 1) == 1 ? 1 : -1);
            data.labelX = Mth.clamp(baseX + tangentX * step * signedStep, 3f, Math.max(3f, screenWidth - data.labelWidth - 3f));
            data.labelY = Mth.clamp(baseY + tangentY * step * signedStep, 3f, Math.max(3f, screenHeight - data.labelHeight - 3f));

            boolean collision = false;
            for (int otherIndex = 0; otherIndex < dataIndex; otherIndex++) {
                IndicatorData other = placedIndicators.get(otherIndex);
                if (other.labelVisible && labelsOverlap(data, other)) {
                    collision = true;
                    break;
                }
            }
            if (!collision)
                return;
        }
    }

    private static boolean labelsOverlap(IndicatorData first, IndicatorData second) {
        float padding = 2f;
        return first.labelX < second.labelX + second.labelWidth + padding
                && first.labelX + first.labelWidth + padding > second.labelX
                && first.labelY < second.labelY + second.labelHeight + padding
                && first.labelY + first.labelHeight + padding > second.labelY;
    }

    private void prepareLabel(IndicatorData data) {
        StringBuilder builder = data.labelBuilder;
        builder.setLength(0);

        if (data.distance <= 10.0)
            builder.append(Math.round(data.distance)).append('m');

        data.label = builder.toString();
        float textWidth = NVGFonts.INTER_MEDIUM.getWidth(data.label, LABEL_FONT_SIZE);
        data.labelWidth = textWidth + 14f;
        data.labelHeight = 20f;

        float labelOffset = data.playerHeadRadius + data.labelHeight * 0.5f + 3f;
        float labelCenterX = data.playerHeadX - data.outwardDirectionX * labelOffset;
        float labelCenterY = data.playerHeadY - data.outwardDirectionY * labelOffset;
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        data.labelX = Mth.clamp(labelCenterX - data.labelWidth * 0.5f, 3f, Math.max(3f, screenWidth - data.labelWidth - 3f));
        data.labelY = Mth.clamp(labelCenterY - data.labelHeight * 0.5f, 3f, Math.max(3f, screenHeight - data.labelHeight - 3f));
    }

    private static void preparePlayerHeadPlacement(IndicatorData data, float centerX, float centerY,
                                                   float diameter, float offset) {
        float directionX = data.drawX - centerX;
        float directionY = data.drawY - centerY;
        float length = (float) Math.hypot(directionX, directionY);
        if (length < 0.001f) {
            directionX = (float) Math.sin(data.drawAngle);
            directionY = (float) -Math.cos(data.drawAngle);
            length = 1f;
        }

        data.outwardDirectionX = directionX / length;
        data.outwardDirectionY = directionY / length;
        data.playerHeadRadius = diameter * 0.5f;
        data.playerHeadX = data.drawX - data.outwardDirectionX * offset;
        data.playerHeadY = data.drawY - data.outwardDirectionY * offset;
    }

    private void drawPlayerHead(NVGU vg, IndicatorData data) {
        float borderThickness = Math.max(1f, thickness.get() * 0.55f);
        float backgroundRadius = data.playerHeadRadius + borderThickness;
        vg.circle(data.playerHeadX, data.playerHeadY, backgroundRadius, PLAYER_HEAD_BACKGROUND);

        if (uploadPlayerHeadTexture(vg, data.playerTextureIdentifier)) {
            float diameter = data.playerHeadRadius * 2f;
            vg.texturedRoundedRectangle(
                    data.playerHeadX - data.playerHeadRadius,
                    data.playerHeadY - data.playerHeadRadius,
                    diameter,
                    diameter,
                    data.playerHeadRadius,
                    data.playerTextureIdentifier
            );
        }

        vg.circleBorder(
                data.playerHeadX,
                data.playerHeadY,
                data.playerHeadRadius + borderThickness * 0.5f,
                borderThickness,
                withAlpha(data.drawColor, 225)
        );
    }

    private void requestPlayerHeadTexture(AbstractClientPlayer player, String identifier) {
        if (player == null || identifier == null || identifier.isEmpty())
            return;

        NVGU vg = NVGU.INSTANCE;
        PlayerHeadTextureState state = playerHeadTextures.computeIfAbsent(identifier, ignored -> new PlayerHeadTextureState());
        if (vg != null && vg.isCreated() && vg.hasTexture(identifier)) {
            discardPlayerHeadTextureData(state);
            return;
        }

        long now = System.nanoTime();
        if (state.requestPending || state.textureData != null || now < state.retryAfterNanos)
            return;

        state.requestPending = true;
        long requestGeneration = playerHeadTextureGeneration;
        StreamConverter.getPlayerFaceAsInputStream(player, inputStream -> {
            if (requestGeneration != playerHeadTextureGeneration || playerHeadTextures.get(identifier) != state) {
                closeInputStream(inputStream);
                return;
            }

            state.requestPending = false;
            if (inputStream == null) {
                state.retryAfterNanos = System.nanoTime() + PLAYER_HEAD_RETRY_DELAY_NANOS;
                return;
            }

            closeInputStream(state.textureData);
            state.textureData = inputStream;
        });
    }

    private boolean uploadPlayerHeadTexture(NVGU vg, String identifier) {
        if (identifier == null || identifier.isEmpty())
            return false;

        PlayerHeadTextureState state = playerHeadTextures.get(identifier);
        if (vg.hasTexture(identifier)) {
            discardPlayerHeadTextureData(state);
            return true;
        }
        if (state == null || state.textureData == null)
            return false;

        InputStream textureData = state.textureData;
        state.textureData = null;
        try {
            vg.createTexture(identifier, textureData);
        } catch (RuntimeException ignored) {
            state.retryAfterNanos = System.nanoTime() + PLAYER_HEAD_RETRY_DELAY_NANOS;
        } finally {
            closeInputStream(textureData);
        }
        return vg.hasTexture(identifier);
    }

    private static void discardPlayerHeadTextureData(PlayerHeadTextureState state) {
        if (state == null || state.textureData == null)
            return;
        closeInputStream(state.textureData);
        state.textureData = null;
    }

    private void clearPlayerHeadTextureStates() {
        playerHeadTextureGeneration++;
        for (PlayerHeadTextureState state : playerHeadTextures.values())
            discardPlayerHeadTextureData(state);
        playerHeadTextures.clear();
    }

    private static void closeInputStream(InputStream inputStream) {
        if (inputStream == null)
            return;
        try {
            inputStream.close();
        } catch (IOException ignored) {
        }
    }

    private Color indicatorColor(float healthRatio) {
        if (colorMode.get().equalsIgnoreCase("Static"))
            return color.get();

        float ratio = Mth.clamp(healthRatio, 0f, 1f);
        float hue = ratio * 0.33f;
        Color rgb = Color.getHSBColor(hue, 0.82f, 1f);
        return new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 235);
    }

    private static float healthRatio(AbstractClientPlayer player) {
        double maxHealth = player.getAttributeValue(Attributes.MAX_HEALTH);
        if (!Double.isFinite(maxHealth) || maxHealth <= 0.0)
            return 0f;
        return (float) Mth.clamp(player.getHealth() / maxHealth, 0.0, 1.0);
    }

    private static float wrapRadians(float radians) {
        radians %= TAU;
        if (radians <= -Math.PI)
            radians += TAU;
        else if (radians > Math.PI)
            radians -= TAU;
        return radians;
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp(alpha, 0, 255));
    }

    private void clearFrameData() {
        for (IndicatorData data : indicatorPool)
            data.player = null;
        indicators.clear();
        placedIndicators.clear();
        shaderRequests.clear();
    }

    private static final class IndicatorData {
        private AbstractClientPlayer player;
        private String playerTextureIdentifier;
        private float angle;
        private float drawAngle;
        private float screenX;
        private float screenY;
        private double distance;
        private float healthRatio;
        private double distanceSquared;
        private boolean onScreen;
        private float drawX;
        private float drawY;
        private float outwardDirectionX;
        private float outwardDirectionY;
        private float playerHeadX;
        private float playerHeadY;
        private float playerHeadRadius;
        private Color drawColor;
        private String label = "";
        private float labelX;
        private float labelY;
        private float labelWidth;
        private float labelHeight;
        private boolean labelVisible;
        private final StringBuilder labelBuilder = new StringBuilder(48);

        private void update(AbstractClientPlayer player, float angle, float screenX, float screenY,
                            double distance, float healthRatio,
                            double distanceSquared, boolean onScreen) {
            this.player = player;
            this.playerTextureIdentifier = player.getStringUUID().toLowerCase(Locale.ROOT);
            this.angle = angle;
            this.screenX = screenX;
            this.screenY = screenY;
            this.distance = distance;
            this.healthRatio = healthRatio;
            this.distanceSquared = distanceSquared;
            this.onScreen = onScreen;
        }
    }

    private static final class PlayerHeadTextureState {
        private InputStream textureData;
        private boolean requestPending;
        private long retryAfterNanos;
    }
}
