package com.instrumentalist.krs.utils.render;

import com.instrumentalist.mixin.injector.SpriteAnimationStateAccessor;
import com.instrumentalist.mixin.injector.SpriteContentsAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.WeakHashMap;

public final class LegacyWater {

    private static final int LEGACY_BLUE = 244;

    private static final Map<SpriteContents, NativeImage> ORIGINALS = new WeakHashMap<>();
    private static final Map<SpriteContents, SpriteContents.AnimationState> ANIMATIONS = new WeakHashMap<>();

    private static boolean applied;

    private LegacyWater() {
    }

    private static float[] factors(Identifier name) {
        if (!name.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) return null;

        return switch (name.getPath()) {
            case "block/water_still" -> new float[]{0.2633F, 0.3791F};
            case "block/water_flow" -> new float[]{0.2905F, 0.4252F};
            case "block/water_overlay" -> new float[]{0.2238F, 0.3190F};
            default -> null;
        };
    }

    public static void onSpriteLoaded(SpriteContents contents, NativeImage image, boolean active) {
        if (factors(contents.name()) == null) return;

        NativeImage original = new NativeImage(image.getWidth(), image.getHeight(), false);
        original.copyFrom(image);

        synchronized (ORIGINALS) {
            NativeImage previous = ORIGINALS.put(contents, original);
            if (previous != null) previous.close();
            applied = active;
        }

        if (active)
            recolor(image, factors(contents.name()));
    }

    public static void onAnimationCreated(SpriteContents contents, SpriteContents.AnimationState state) {
        if (state == null || factors(contents.name()) == null) return;

        synchronized (ORIGINALS) {
            ANIMATIONS.put(contents, state);
        }
    }

    public static void onSpriteClosed(SpriteContents contents) {
        synchronized (ORIGINALS) {
            ANIMATIONS.remove(contents);
            NativeImage original = ORIGINALS.remove(contents);
            if (original != null) original.close();
        }
    }

    public static void refresh(boolean active) {
        if (applied == active) return;
        applied = active;

        synchronized (ORIGINALS) {
            for (Map.Entry<SpriteContents, SpriteContents.AnimationState> entry : ANIMATIONS.entrySet()) {
                NativeImage original = ORIGINALS.get(entry.getKey());
                if (original != null)
                    reupload(entry.getKey(), entry.getValue(), original, active);
            }

            TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
            TextureAtlasSprite overlay = atlas.getSprite(Identifier.withDefaultNamespace("block/water_overlay"));
            NativeImage original = ORIGINALS.get(overlay.contents());
            if (original != null && !overlay.contents().isAnimated())
                reuploadStatic(atlas, overlay, original, active);
        }
    }

    private static NativeImage[] buildMips(SpriteContents contents, NativeImage original, boolean active) {
        SpriteContentsAccessor accessor = (SpriteContentsAccessor) contents;
        NativeImage base = new NativeImage(original.getWidth(), original.getHeight(), false);
        base.copyFrom(original);
        if (active)
            recolor(base, factors(contents.name()));

        return MipmapGenerator.generateMipLevels(contents.name(), new NativeImage[]{base}, accessor.krs$getByMipLevel().length - 1, accessor.krs$getMipmapStrategy(), accessor.krs$getAlphaCutoffBias(), contents.transparency());
    }

    private static void reuploadStatic(TextureAtlas atlas, TextureAtlasSprite sprite, NativeImage original, boolean active) {
        if (atlas.getTexture().isClosed()) return;

        NativeImage[] mips = buildMips(sprite.contents(), original, active);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        try {
            for (int level = 0; level < mips.length && level < atlas.getTexture().getMipLevels(); level++)
                encoder.writeToTexture(atlas.getTexture(), mips[level], level, 0, sprite.getX() >> level, sprite.getY() >> level);
        } finally {
            for (NativeImage mip : mips)
                mip.close();
        }
    }

    private static void recolor(NativeImage image, float[] factors) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixel(x, y);
                int grey = ARGB.blue(pixel);
                int red = Mth.clamp(Math.round(grey * factors[0]), 0, 255);
                int green = Mth.clamp(Math.round(grey * factors[1]), 0, 255);
                image.setPixel(x, y, ARGB.color(ARGB.alpha(pixel), red, green, LEGACY_BLUE));
            }
        }
    }

    private static void reupload(SpriteContents contents, SpriteContents.AnimationState state, NativeImage original, boolean active) {
        SpriteAnimationStateAccessor animation = (SpriteAnimationStateAccessor) (Object) state;
        for (GpuTextureView view : animation.krs$getFrameTexturesByIndex().values())
            if (view.isClosed() || view.texture().isClosed()) return;

        int levels = ((SpriteContentsAccessor) contents).krs$getByMipLevel().length;
        int width = contents.width();
        int height = contents.height();
        NativeImage[] mips = buildMips(contents, original, active);
        int framesPerRow = Math.max(1, original.getWidth() / width);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        try {
            for (Map.Entry<Integer, GpuTextureView> frame : animation.krs$getFrameTexturesByIndex().entrySet()) {
                int index = frame.getKey();
                int offsetX = (index % framesPerRow) * width;
                int offsetY = (index / framesPerRow) * height;

                for (int level = 0; level < mips.length && level < levels; level++) {
                    int frameWidth = Math.max(1, width >> level);
                    int frameHeight = Math.max(1, height >> level);

                    try (NativeImage frameImage = new NativeImage(frameWidth, frameHeight, false)) {
                        for (int y = 0; y < frameHeight; y++)
                            for (int x = 0; x < frameWidth; x++)
                                frameImage.setPixel(x, y, mips[level].getPixel((offsetX >> level) + x, (offsetY >> level) + y));

                        encoder.writeToTexture(frame.getValue().texture(), frameImage, level, 0, 0, 0);
                    }
                }
            }
        } finally {
            for (NativeImage mip : mips)
                mip.close();
        }

        animation.krs$setDirty(true);
    }
}
