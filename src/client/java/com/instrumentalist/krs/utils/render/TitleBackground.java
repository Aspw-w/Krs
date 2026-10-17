package com.instrumentalist.krs.utils.render;

import com.instrumentalist.krs.screen.CustomTitleScreen;
import com.instrumentalist.krs.utils.GuiInputBlocker;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

public final class TitleBackground {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("krs", "title_background");
    private static final String PATH = "assets/krs/title.png";
    private static int textureWidth;
    private static int textureHeight;
    private static boolean failed;

    private TitleBackground() {
    }

    public static boolean render(GuiGraphicsExtractor graphics, int width, int height) {
        if (!ensureLoaded() || width <= 0 || height <= 0)
            return false;

        Minecraft mc = Minecraft.getInstance();
        if (!GuiInputBlocker.shouldBlockMinecraftMouse()) {
            float targetX = NanoVGManager.toScaledMouseX(mc.mouseHandler.getScaledXPos(mc.getWindow()));
            float targetY = NanoVGManager.toScaledMouseY(mc.mouseHandler.getScaledYPos(mc.getWindow()));
            CustomTitleScreen.stepBackgroundParallax(targetX, targetY, 0.03f);
        }

        float offsetX = CustomTitleScreen.bgOffsetX * width / Math.max(1f, NanoVGManager.getScaledScreenWidth());
        float offsetY = CustomTitleScreen.bgOffsetY * height / Math.max(1f, NanoVGManager.getScaledScreenHeight());
        int layerWidth = width * 2;
        int layerHeight = height * 2;

        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0f, 0f, layerWidth, layerHeight, textureWidth, textureHeight, textureWidth, textureHeight);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(offsetX, offsetY);
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, -width, -height, 0f, 0f, layerWidth, layerHeight, textureWidth, textureHeight, textureWidth, textureHeight);
        } finally {
            graphics.pose().popMatrix();
        }
        return true;
    }

    private static boolean ensureLoaded() {
        if (textureWidth > 0)
            return true;
        if (failed)
            return false;

        try (InputStream stream = TitleBackground.class.getClassLoader().getResourceAsStream(PATH)) {
            BufferedImage source = stream == null ? null : ImageIO.read(stream);
            if (source == null) {
                failed = true;
                return false;
            }

            NativeImage image = new NativeImage(source.getWidth(), source.getHeight(), false);
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++)
                    image.setPixel(x, y, source.getRGB(x, y) | 0xFF000000);
            }

            DynamicTexture texture = new DynamicTexture(() -> "krs title background", image) {
                {
                    sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                }
            };
            Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
            textureWidth = source.getWidth();
            textureHeight = source.getHeight();
            return true;
        } catch (Exception ignored) {
            failed = true;
            return false;
        }
    }
}
