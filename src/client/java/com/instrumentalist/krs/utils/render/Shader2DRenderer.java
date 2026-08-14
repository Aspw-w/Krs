package com.instrumentalist.krs.utils.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public final class Shader2DRenderer {
    public static final Shader2DRenderer INSTANCE = new Shader2DRenderer();
    private static final int HUD_BLUR_BASE_DOWNSCALE = 2;
    private static final int EFFECT_BATCH_SIZE = 64;
    private static final int INDICATOR_BATCH_SIZE = 64;

    private static String rectVertexShader() {
        return """
            #version 330 core

            uniform vec4 uDrawRects[%d];
            uniform vec2 uScreenSize;

            out vec2 vPosition;
            out vec2 vScreenUv;
            flat out int vEffectIndex;

            const vec2 POSITIONS[6] = vec2[](
                vec2(0.0, 0.0),
                vec2(1.0, 0.0),
                vec2(1.0, 1.0),
                vec2(0.0, 0.0),
                vec2(1.0, 1.0),
                vec2(0.0, 1.0)
            );

            void main() {
                int effectIndex = gl_InstanceID;
                vec4 drawRect = uDrawRects[effectIndex];
                vec2 unitPosition = POSITIONS[gl_VertexID];
                vec2 pixelPosition = drawRect.xy + unitPosition * drawRect.zw;
                vPosition = pixelPosition;
                vScreenUv = pixelPosition / uScreenSize;
                vEffectIndex = effectIndex;

                vec2 ndc = vec2(
                    pixelPosition.x / uScreenSize.x * 2.0 - 1.0,
                    1.0 - pixelPosition.y / uScreenSize.y * 2.0
                );
                gl_Position = vec4(ndc, 0.0, 1.0);
            }
            """.formatted(EFFECT_BATCH_SIZE);
    }

    private static String fullscreenVertexShader() {
        return """
            #version 330 core

            out vec2 vUv;

            const vec2 POSITIONS[6] = vec2[](
                vec2(-1.0, -1.0),
                vec2( 1.0, -1.0),
                vec2( 1.0,  1.0),
                vec2(-1.0, -1.0),
                vec2( 1.0,  1.0),
                vec2(-1.0,  1.0)
            );

            const vec2 UVS[6] = vec2[](
                vec2(0.0, 0.0),
                vec2(1.0, 0.0),
                vec2(1.0, 1.0),
                vec2(0.0, 0.0),
                vec2(1.0, 1.0),
                vec2(0.0, 1.0)
            );

            void main() {
                gl_Position = vec4(POSITIONS[gl_VertexID], 0.0, 1.0);
                vUv = UVS[gl_VertexID];
            }
            """;
    }

    private static String triangleMaskVertexShader() {
        return """
            #version 330 core

            layout(location = 0) in vec3 aPosition;
            uniform vec2 uScreenSize;
            uniform float uDepthScale;
            uniform float uDepthOffset;

            void main() {
                vec2 ndc = vec2(
                    aPosition.x / uScreenSize.x * 2.0 - 1.0,
                    1.0 - aPosition.y / uScreenSize.y * 2.0
                );
                gl_Position = vec4(ndc, aPosition.z * uDepthScale + uDepthOffset, 1.0);
            }
            """;
    }

    private static String blurFragmentShader() {
        return """
            #version 330 core

            uniform sampler2D uTexture;
            uniform vec2 uTexelSize;
            uniform vec2 uDirection;
            uniform float uRadius;

            in vec2 vUv;
            out vec4 fragColor;

            float gaussian(float offset, float sigma) {
                return exp(-(offset * offset) / (2.0 * sigma * sigma));
            }

            void main() {
                float radius = clamp(uRadius, 0.0, float(%d));
                float sigma = max(radius * 0.5, 0.001);

                vec4 color = texture(uTexture, vUv) * gaussian(0.0, sigma);
                float weightSum = gaussian(0.0, sigma);

                for (int i = 1; i <= %d; i++) {
                    if (float(i) > radius) {
                        break;
                    }

                    float weight = gaussian(float(i), sigma);
                    vec2 offset = uDirection * uTexelSize * float(i);
                    color += texture(uTexture, vUv + offset) * weight;
                    color += texture(uTexture, vUv - offset) * weight;
                    weightSum += weight * 2.0;
                }

                fragColor = color / weightSum;
            }
            """.formatted(24, 24);
    }

    private static String maskFragmentShader() {
        return """
            #version 330 core

            out vec4 fragColor;

            void main() {
                fragColor = vec4(1.0);
            }
            """;
    }

    private static String occludedMaskFragmentShader() {
        return """
            #version 330 core

            uniform sampler2D uDepthTexture;
            uniform sampler2D uSelfDepthTexture;
            uniform sampler2D uArmorDepthTexture;
            uniform sampler2D uArmorMaskTexture;
            uniform vec2 uScreenSize;
            uniform float uDepthEpsilon;
            uniform float uSelfDepthEpsilon;
            uniform float uArmorDepthEpsilon;
            uniform float uArmorMaskDepthEpsilon;
            uniform float uArmorMaskPaddingPixels;
            uniform float uReversedDepth;

            out vec4 fragColor;

            float armorMaskAt(vec2 uv) {
                return texture(uArmorMaskTexture, clamp(uv, vec2(0.0), vec2(1.0))).a;
            }

            float armorDepthAt(vec2 uv) {
                return texture(uArmorDepthTexture, clamp(uv, vec2(0.0), vec2(1.0))).r;
            }

            bool isClearDepth(float depth) {
                return depth <= 0.0001 || depth >= 0.9999;
            }

            float mergeArmorDepth(float currentDepth, float sampleDepth, bool reversedDepth) {
                if (isClearDepth(sampleDepth)) {
                    return currentDepth;
                }
                if (isClearDepth(currentDepth)) {
                    return sampleDepth;
                }
                return reversedDepth ? max(currentDepth, sampleDepth) : min(currentDepth, sampleDepth);
            }

            void main() {
                vec2 uv = gl_FragCoord.xy / uScreenSize;
                float sceneDepth = texture(uDepthTexture, uv).r;
                float selfDepth = texture(uSelfDepthTexture, uv).r;
                float fragmentDepth = gl_FragCoord.z;
                bool reversedDepth = uReversedDepth > 0.5;
                vec2 armorPadding = vec2(max(uArmorMaskPaddingPixels, 0.0)) / uScreenSize;
                vec2 armorDiagonalPadding = armorPadding * 0.70710678;
                float armorDepth = armorDepthAt(uv);
                float armorMask = armorMaskAt(uv);
                armorMask = max(armorMask, armorMaskAt(uv + vec2( armorPadding.x, 0.0)));
                armorDepth = mergeArmorDepth(armorDepth, armorDepthAt(uv + vec2( armorPadding.x, 0.0)), reversedDepth);
                armorMask = max(armorMask, armorMaskAt(uv + vec2(-armorPadding.x, 0.0)));
                armorDepth = mergeArmorDepth(armorDepth, armorDepthAt(uv + vec2(-armorPadding.x, 0.0)), reversedDepth);
                armorMask = max(armorMask, armorMaskAt(uv + vec2(0.0,  armorPadding.y)));
                armorDepth = mergeArmorDepth(armorDepth, armorDepthAt(uv + vec2(0.0,  armorPadding.y)), reversedDepth);
                armorMask = max(armorMask, armorMaskAt(uv + vec2(0.0, -armorPadding.y)));
                armorDepth = mergeArmorDepth(armorDepth, armorDepthAt(uv + vec2(0.0, -armorPadding.y)), reversedDepth);
                armorMask = max(armorMask, armorMaskAt(uv + vec2( armorDiagonalPadding.x,  armorDiagonalPadding.y)));
                armorDepth = mergeArmorDepth(armorDepth, armorDepthAt(uv + vec2( armorDiagonalPadding.x,  armorDiagonalPadding.y)), reversedDepth);
                armorMask = max(armorMask, armorMaskAt(uv + vec2(-armorDiagonalPadding.x,  armorDiagonalPadding.y)));
                armorDepth = mergeArmorDepth(armorDepth, armorDepthAt(uv + vec2(-armorDiagonalPadding.x,  armorDiagonalPadding.y)), reversedDepth);
                armorMask = max(armorMask, armorMaskAt(uv + vec2( armorDiagonalPadding.x, -armorDiagonalPadding.y)));
                armorDepth = mergeArmorDepth(armorDepth, armorDepthAt(uv + vec2( armorDiagonalPadding.x, -armorDiagonalPadding.y)), reversedDepth);
                armorMask = max(armorMask, armorMaskAt(uv + vec2(-armorDiagonalPadding.x, -armorDiagonalPadding.y)));
                armorDepth = mergeArmorDepth(armorDepth, armorDepthAt(uv + vec2(-armorDiagonalPadding.x, -armorDiagonalPadding.y)), reversedDepth);
                bool clearDepth = isClearDepth(sceneDepth);
                bool clearSelfDepth = isClearDepth(selfDepth);
                bool clearArmorDepth = isClearDepth(armorDepth);
                float armorDepthEpsilon = mix(uArmorDepthEpsilon, uArmorMaskDepthEpsilon, clamp(armorMask, 0.0, 1.0));
                bool sceneClearlyInFrontOfArmor = !clearArmorDepth && (
                    reversedDepth
                        ? sceneDepth > armorDepth + uDepthEpsilon
                        : sceneDepth < armorDepth - uDepthEpsilon
                );
                bool blockedBySelf = !clearSelfDepth && (
                    reversedDepth
                        ? sceneDepth <= selfDepth + uSelfDepthEpsilon
                        : sceneDepth >= selfDepth - uSelfDepthEpsilon
                );
                bool blockedByArmor = !clearArmorDepth && !sceneClearlyInFrontOfArmor && (
                    reversedDepth
                        ? sceneDepth <= armorDepth + armorDepthEpsilon
                        : sceneDepth >= armorDepth - armorDepthEpsilon
                );
                bool occluded = reversedDepth
                    ? fragmentDepth < sceneDepth - uDepthEpsilon
                    : fragmentDepth > sceneDepth + uDepthEpsilon;

                if (clearDepth || blockedBySelf || blockedByArmor || !occluded) {
                    discard;
                }

                fragColor = vec4(1.0);
            }
            """;
    }

    private static String roundedBlurFragmentShader() {
        return """
            #version 330 core

            uniform sampler2D uTexture;
            uniform vec4 uBoxRects[%d];
            uniform vec4 uEffectParams[%d];

            in vec2 vPosition;
            in vec2 vScreenUv;
            flat in int vEffectIndex;
            out vec4 fragColor;

            float roundedBoxSdf(vec2 point, vec2 halfSize, float radius) {
                vec2 q = abs(point) - halfSize + vec2(radius);
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
            }

            void main() {
                vec4 boxRect = uBoxRects[vEffectIndex];
                vec4 effectParams = uEffectParams[vEffectIndex];
                vec2 center = boxRect.xy + boxRect.zw * 0.5;
                vec2 halfSize = max(boxRect.zw * 0.5, vec2(0.0));
                float distanceToEdge = roundedBoxSdf(vPosition - center, halfSize, max(effectParams.x, 0.0));
                float mask = 1.0 - smoothstep(-1.0, 1.0, distanceToEdge);

                if (mask <= 0.001) {
                    discard;
                }

                vec4 color = texture(uTexture, vec2(vScreenUv.x, 1.0 - vScreenUv.y));
                fragColor = vec4(color.rgb, color.a * mask * clamp(effectParams.y, 0.0, 1.0));
            }
            """.formatted(EFFECT_BATCH_SIZE, EFFECT_BATCH_SIZE);
    }

    private static String shadowFragmentShader() {
        return """
            #version 330 core

            uniform vec4 uBoxRects[%d];
            uniform vec4 uEffectParams[%d];
            uniform vec4 uColors[%d];

            in vec2 vPosition;
            flat in int vEffectIndex;
            out vec4 fragColor;

            float roundedBoxSdf(vec2 point, vec2 halfSize, float radius) {
                vec2 q = abs(point) - halfSize + vec2(radius);
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
            }

            void main() {
                vec4 boxRect = uBoxRects[vEffectIndex];
                vec4 effectParams = uEffectParams[vEffectIndex];
                vec4 color = uColors[vEffectIndex];
                float spread = effectParams.z;
                vec2 center = boxRect.xy + boxRect.zw * 0.5;
                vec2 halfSize = max(boxRect.zw * 0.5 + vec2(spread), vec2(0.0));
                float radius = max(effectParams.x + spread, 0.0);
                float softness = max(effectParams.y, 0.001);
                float distanceToEdge = roundedBoxSdf(vPosition - center, halfSize, radius);
                float alpha = 1.0 - smoothstep(-softness, softness, distanceToEdge);

                if (alpha <= 0.001) {
                    discard;
                }

                fragColor = vec4(color.rgb, color.a * alpha);
            }
            """.formatted(EFFECT_BATCH_SIZE, EFFECT_BATCH_SIZE, EFFECT_BATCH_SIZE);
    }

    private static String indicatorVertexShader() {
        return """
            #version 330 core

            uniform vec4 uIndicators[%d];
            uniform vec2 uScreenSize;

            out vec2 vLocalPosition;
            flat out int vIndicatorIndex;

            const vec2 POSITIONS[6] = vec2[](
                vec2(-1.0, -1.0),
                vec2( 1.0, -1.0),
                vec2( 1.0,  1.0),
                vec2(-1.0, -1.0),
                vec2( 1.0,  1.0),
                vec2(-1.0,  1.0)
            );

            void main() {
                int indicatorIndex = gl_InstanceID;
                vec4 indicator = uIndicators[indicatorIndex];
                vec2 localPosition = POSITIONS[gl_VertexID] * indicator.z;
                float sine = sin(indicator.w);
                float cosine = cos(indicator.w);
                vec2 rotatedPosition = vec2(
                    localPosition.x * cosine - localPosition.y * sine,
                    localPosition.x * sine + localPosition.y * cosine
                );
                vec2 pixelPosition = indicator.xy + rotatedPosition;

                vLocalPosition = localPosition;
                vIndicatorIndex = indicatorIndex;

                vec2 ndc = vec2(
                    pixelPosition.x / uScreenSize.x * 2.0 - 1.0,
                    1.0 - pixelPosition.y / uScreenSize.y * 2.0
                );
                gl_Position = vec4(ndc, 0.0, 1.0);
            }
            """.formatted(INDICATOR_BATCH_SIZE);
    }

    private static String indicatorFragmentShader() {
        return """
            #version 330 core

            uniform vec4 uIndicatorParams[%d];
            uniform vec4 uIndicatorColors[%d];

            in vec2 vLocalPosition;
            flat in int vIndicatorIndex;
            out vec4 fragColor;

            float segmentDistance(vec2 point, vec2 start, vec2 end) {
                vec2 segment = end - start;
                float denominator = max(dot(segment, segment), 0.0001);
                float amount = clamp(dot(point - start, segment) / denominator, 0.0, 1.0);
                return length(point - (start + segment * amount));
            }

            void main() {
                vec4 params = uIndicatorParams[vIndicatorIndex];
                vec4 color = uIndicatorColors[vIndicatorIndex];
                float size = max(params.x, 1.0);
                float thickness = max(params.y, 0.75);
                float glowRadius = max(params.z, 0.0);
                float pulse = clamp(params.w, 0.0, 1.0);

                vec2 tip = vec2(0.0, -size * 0.66);
                vec2 leftWing = vec2(-size * 0.62, size * 0.08);
                vec2 rightWing = vec2(size * 0.62, size * 0.08);
                float distanceToArrow = min(
                    segmentDistance(vLocalPosition, tip, leftWing),
                    segmentDistance(vLocalPosition, tip, rightWing)
                );

                float antialiasWidth = max(fwidth(distanceToArrow), 0.7);
                float core = 1.0 - smoothstep(
                    thickness - antialiasWidth,
                    thickness + antialiasWidth,
                    distanceToArrow
                );
                float glow = 0.0;
                if (glowRadius > 0.01) {
                    glow = 1.0 - smoothstep(thickness, thickness + glowRadius, distanceToArrow);
                    glow *= glow;
                }

                float glowStrength = glow * (0.24 + pulse * 0.18) * (1.0 - core * 0.55);
                float alpha = clamp(core + glowStrength, 0.0, 1.0) * color.a;
                if (alpha <= 0.001) {
                    discard;
                }

                vec3 highlightedColor = mix(color.rgb, vec3(1.0), 0.16 + pulse * 0.10);
                fragColor = vec4(mix(color.rgb, highlightedColor, core), alpha);
            }
            """.formatted(INDICATOR_BATCH_SIZE, INDICATOR_BATCH_SIZE);
    }

    private static String silhouetteCompositeFragmentShader() {
        return """
            #version 330 core

            uniform sampler2D uBlurTexture;
            uniform sampler2D uMaskTexture;
            uniform sampler2D uArmorMaskTexture;
            uniform sampler2D uOccludedMaskTexture;
            uniform vec4 uColor;
            uniform vec2 uTexelSize;
            uniform float uInsetPixels;
            uniform float uLineAlpha;
            uniform float uArmoredLineAlpha;
            uniform float uThroughBlockLineAlpha;

            in vec2 vUv;
            out vec4 fragColor;

            float maskAt(vec2 uv) {
                return texture(uMaskTexture, clamp(uv, vec2(0.0), vec2(1.0))).a;
            }

            float blurredAt(vec2 uv) {
                return texture(uBlurTexture, clamp(uv, vec2(0.0), vec2(1.0))).a;
            }

            float armorMaskAt(vec2 uv) {
                return texture(uArmorMaskTexture, clamp(uv, vec2(0.0), vec2(1.0))).a;
            }

            float occludedMaskAt(vec2 uv) {
                return texture(uOccludedMaskTexture, clamp(uv, vec2(0.0), vec2(1.0))).a;
            }

            void main() {
                float blurredAlpha = blurredAt(vUv);
                float bodyAlpha = texture(uMaskTexture, vUv).a;
                vec2 inset = uTexelSize * max(uInsetPixels, 0.0);
                vec2 diagonalInset = inset * 0.70710678;

                blurredAlpha = max(blurredAlpha, blurredAt(vUv + vec2( inset.x, 0.0)));
                blurredAlpha = max(blurredAlpha, blurredAt(vUv + vec2(-inset.x, 0.0)));
                blurredAlpha = max(blurredAlpha, blurredAt(vUv + vec2(0.0,  inset.y)));
                blurredAlpha = max(blurredAlpha, blurredAt(vUv + vec2(0.0, -inset.y)));
                blurredAlpha = max(blurredAlpha, blurredAt(vUv + vec2( diagonalInset.x,  diagonalInset.y)));
                blurredAlpha = max(blurredAlpha, blurredAt(vUv + vec2(-diagonalInset.x,  diagonalInset.y)));
                blurredAlpha = max(blurredAlpha, blurredAt(vUv + vec2( diagonalInset.x, -diagonalInset.y)));
                blurredAlpha = max(blurredAlpha, blurredAt(vUv + vec2(-diagonalInset.x, -diagonalInset.y)));

                float erodedBodyAlpha = bodyAlpha;
                erodedBodyAlpha = min(erodedBodyAlpha, maskAt(vUv + vec2( inset.x, 0.0)));
                erodedBodyAlpha = min(erodedBodyAlpha, maskAt(vUv + vec2(-inset.x, 0.0)));
                erodedBodyAlpha = min(erodedBodyAlpha, maskAt(vUv + vec2(0.0,  inset.y)));
                erodedBodyAlpha = min(erodedBodyAlpha, maskAt(vUv + vec2(0.0, -inset.y)));
                erodedBodyAlpha = min(erodedBodyAlpha, maskAt(vUv + vec2( diagonalInset.x,  diagonalInset.y)));
                erodedBodyAlpha = min(erodedBodyAlpha, maskAt(vUv + vec2(-diagonalInset.x,  diagonalInset.y)));
                erodedBodyAlpha = min(erodedBodyAlpha, maskAt(vUv + vec2( diagonalInset.x, -diagonalInset.y)));
                erodedBodyAlpha = min(erodedBodyAlpha, maskAt(vUv + vec2(-diagonalInset.x, -diagonalInset.y)));

                float armorAlpha = armorMaskAt(vUv);
                armorAlpha = max(armorAlpha, armorMaskAt(vUv + vec2( inset.x, 0.0)));
                armorAlpha = max(armorAlpha, armorMaskAt(vUv + vec2(-inset.x, 0.0)));
                armorAlpha = max(armorAlpha, armorMaskAt(vUv + vec2(0.0,  inset.y)));
                armorAlpha = max(armorAlpha, armorMaskAt(vUv + vec2(0.0, -inset.y)));
                armorAlpha = max(armorAlpha, armorMaskAt(vUv + vec2( diagonalInset.x,  diagonalInset.y)));
                armorAlpha = max(armorAlpha, armorMaskAt(vUv + vec2(-diagonalInset.x,  diagonalInset.y)));
                armorAlpha = max(armorAlpha, armorMaskAt(vUv + vec2( diagonalInset.x, -diagonalInset.y)));
                armorAlpha = max(armorAlpha, armorMaskAt(vUv + vec2(-diagonalInset.x, -diagonalInset.y)));

                float occludedAlpha = occludedMaskAt(vUv);
                occludedAlpha = max(occludedAlpha, occludedMaskAt(vUv + vec2( inset.x, 0.0)));
                occludedAlpha = max(occludedAlpha, occludedMaskAt(vUv + vec2(-inset.x, 0.0)));
                occludedAlpha = max(occludedAlpha, occludedMaskAt(vUv + vec2(0.0,  inset.y)));
                occludedAlpha = max(occludedAlpha, occludedMaskAt(vUv + vec2(0.0, -inset.y)));
                occludedAlpha = max(occludedAlpha, occludedMaskAt(vUv + vec2( diagonalInset.x,  diagonalInset.y)));
                occludedAlpha = max(occludedAlpha, occludedMaskAt(vUv + vec2(-diagonalInset.x,  diagonalInset.y)));
                occludedAlpha = max(occludedAlpha, occludedMaskAt(vUv + vec2( diagonalInset.x, -diagonalInset.y)));
                occludedAlpha = max(occludedAlpha, occludedMaskAt(vUv + vec2(-diagonalInset.x, -diagonalInset.y)));

                float outerAlpha = max(blurredAlpha - bodyAlpha, 0.0);
                float innerAlpha = max(bodyAlpha - erodedBodyAlpha, 0.0);
                float shadowAlpha = pow(clamp(outerAlpha, 0.0, 1.0), 1.35) * 0.65 * uColor.a;
                float baseLineStrength = mix(clamp(uLineAlpha, 0.0, 1.0), clamp(uArmoredLineAlpha, 0.0, 1.0), clamp(armorAlpha, 0.0, 1.0));
                float lineStrength = mix(baseLineStrength, clamp(uThroughBlockLineAlpha, 0.0, 1.0), clamp(occludedAlpha, 0.0, 1.0));
                float lineAlpha = innerAlpha * lineStrength;
                float finalAlpha = max(shadowAlpha, lineAlpha);

                if (finalAlpha <= 0.001) {
                    discard;
                }

                fragColor = vec4(uColor.rgb, finalAlpha);
            }
            """;
    }

    private int vertexArray = 0;
    private int triangleVertexArray = 0;
    private int triangleVertexBuffer = 0;
    private int triangleArmorVertexBuffer = 0;
    private int blurProgram = 0;
    private int roundedBlurProgram = 0;
    private int shadowProgram = 0;
    private int indicatorProgram = 0;
    private int maskProgram = 0;
    private int occludedMaskProgram = 0;
    private int silhouetteCompositeProgram = 0;

    private int blurTextureUniform = -1;
    private int blurTexelSizeUniform = -1;
    private int blurDirectionUniform = -1;
    private int blurRadiusUniform = -1;
    private int roundedBlurDrawRectsUniform = -1;
    private int roundedBlurScreenSizeUniform = -1;
    private int roundedBlurBoxRectsUniform = -1;
    private int roundedBlurEffectParamsUniform = -1;
    private int roundedBlurTextureUniform = -1;
    private int shadowDrawRectsUniform = -1;
    private int shadowScreenSizeUniform = -1;
    private int shadowBoxRectsUniform = -1;
    private int shadowEffectParamsUniform = -1;
    private int shadowColorsUniform = -1;
    private int indicatorTransformsUniform = -1;
    private int indicatorScreenSizeUniform = -1;
    private int indicatorParamsUniform = -1;
    private int indicatorColorsUniform = -1;
    private int maskScreenSizeUniform = -1;
    private int maskDepthScaleUniform = -1;
    private int maskDepthOffsetUniform = -1;
    private int occludedMaskScreenSizeUniform = -1;
    private int occludedMaskDepthScaleUniform = -1;
    private int occludedMaskDepthOffsetUniform = -1;
    private int occludedDepthTextureUniform = -1;
    private int occludedDepthEpsilonUniform = -1;
    private int occludedSelfDepthTextureUniform = -1;
    private int occludedSelfDepthEpsilonUniform = -1;
    private int occludedArmorDepthTextureUniform = -1;
    private int occludedArmorDepthEpsilonUniform = -1;
    private int occludedArmorMaskTextureUniform = -1;
    private int occludedArmorMaskDepthEpsilonUniform = -1;
    private int occludedArmorMaskPaddingPixelsUniform = -1;
    private int occludedReversedDepthUniform = -1;
    private int silhouetteBlurTextureUniform = -1;
    private int silhouetteMaskTextureUniform = -1;
    private int silhouetteArmorMaskTextureUniform = -1;
    private int silhouetteOccludedMaskTextureUniform = -1;
    private int silhouetteColorUniform = -1;
    private int silhouetteTexelSizeUniform = -1;
    private int silhouetteInsetPixelsUniform = -1;
    private int silhouetteLineAlphaUniform = -1;
    private int silhouetteArmoredLineAlphaUniform = -1;
    private int silhouetteThroughBlockLineAlphaUniform = -1;

    private final RenderTexture sourceTexture = new RenderTexture(false);
    private final RenderTexture blurPingTexture = new RenderTexture(true);
    private final RenderTexture blurPongTexture = new RenderTexture(true);
    private final RenderTexture silhouetteMaskTexture = new RenderTexture(true);
    private final RenderTexture silhouetteArmorMaskTexture = new RenderTexture(true);
    private final RenderTexture silhouetteOccludedMaskTexture = new RenderTexture(true);
    private final RenderTexture silhouetteSelfDepthTexture = new RenderTexture(true, true);
    private final RenderTexture silhouetteArmorDepthTexture = new RenderTexture(true, true);
    private final RenderTexture silhouetteBlurPingTexture = new RenderTexture(true);
    private final RenderTexture silhouetteBlurPongTexture = new RenderTexture(true);
    private final ArrayList<BlurRequest> singleBlurRequest = new ArrayList<>(1);
    private final ArrayList<ShadowRequest> singleShadowRequest = new ArrayList<>(1);
    private final BlurRequest reusableSingleBlurRequest = new BlurRequest();
    private final ShadowRequest reusableSingleShadowRequest = new ShadowRequest();
    private final FloatBuffer effectDrawRects = BufferUtils.createFloatBuffer(EFFECT_BATCH_SIZE * 4);
    private final FloatBuffer effectBoxRects = BufferUtils.createFloatBuffer(EFFECT_BATCH_SIZE * 4);
    private final FloatBuffer effectParams = BufferUtils.createFloatBuffer(EFFECT_BATCH_SIZE * 4);
    private final FloatBuffer effectColors = BufferUtils.createFloatBuffer(EFFECT_BATCH_SIZE * 4);
    private final FloatBuffer indicatorTransforms = BufferUtils.createFloatBuffer(INDICATOR_BATCH_SIZE * 4);
    private final FloatBuffer indicatorParams = BufferUtils.createFloatBuffer(INDICATOR_BATCH_SIZE * 4);
    private final FloatBuffer indicatorColors = BufferUtils.createFloatBuffer(INDICATOR_BATCH_SIZE * 4);
    private boolean blurCacheValid = false;
    private int cachedBlurWidth = 0;
    private int cachedBlurHeight = 0;
    private float cachedBlurRadius = -1f;

    private Shader2DRenderer() {
        singleBlurRequest.add(reusableSingleBlurRequest);
        singleShadowRequest.add(reusableSingleShadowRequest);
    }

    public void beginFrame() {
        blurCacheValid = false;
    }

    public void drawBlurredRoundedRect(float frameWidth, float frameHeight, float x, float y, float width, float height, float radius, float blurRadius, float alpha) {
        reusableSingleBlurRequest.set(x, y, width, height, radius, blurRadius, alpha);
        drawBlurredRoundedRects(frameWidth, frameHeight, singleBlurRequest);
    }

    public void drawBlurredRoundedRects(float frameWidth, float frameHeight, List<BlurRequest> requests) {
        drawEffects(frameWidth, frameHeight, requests, null);
    }

    public void drawShadowRoundedRect(float frameWidth, float frameHeight, float x, float y, float width, float height, float radius, float blurRadius, float spread, float offsetX, float offsetY, Color color) {
        reusableSingleShadowRequest.set(x, y, width, height, radius, blurRadius, spread, offsetX, offsetY, color);
        drawShadowRoundedRects(frameWidth, frameHeight, singleShadowRequest);
    }

    public void drawShadowRoundedRects(float frameWidth, float frameHeight, List<ShadowRequest> requests) {
        drawEffects(frameWidth, frameHeight, null, requests);
    }

    public void drawDirectionalIndicators(float frameWidth, float frameHeight, List<IndicatorRequest> requests) {
        if (frameWidth <= 0f || frameHeight <= 0f || requests == null || requests.isEmpty())
            return;

        ensureInitialized();

        GLState state = GLState.capture();
        try {
            prepareCommonState();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, state.drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, state.readFramebuffer);
            GL11.glViewport(state.viewportX, state.viewportY, state.viewportWidth, state.viewportHeight);
            GL11.glEnable(GL11.GL_BLEND);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GL20.glUseProgram(indicatorProgram);
            GL20.glUniform2f(indicatorScreenSizeUniform, frameWidth, frameHeight);
            GL30.glBindVertexArray(vertexArray);

            int requestIndex = 0;
            while (requestIndex < requests.size()) {
                indicatorTransforms.clear();
                indicatorParams.clear();
                indicatorColors.clear();
                int batchCount = 0;

                while (requestIndex < requests.size() && batchCount < INDICATOR_BATCH_SIZE) {
                    IndicatorRequest request = requests.get(requestIndex++);
                    if (!isDrawableIndicatorRequest(request))
                        continue;

                    float size = Math.max(1f, request.size);
                    float thickness = Math.max(0.75f, request.thickness);
                    float glowRadius = Math.max(0f, request.glowRadius);
                    float halfExtent = size * 0.72f + thickness + glowRadius + 2f;
                    indicatorTransforms.put(request.centerX)
                            .put(request.centerY)
                            .put(halfExtent)
                            .put(request.angle);
                    indicatorParams.put(size)
                            .put(thickness)
                            .put(glowRadius)
                            .put(clamp(request.pulse, 0f, 1f));
                    Color color = request.color;
                    indicatorColors.put(color.getRed() / 255f)
                            .put(color.getGreen() / 255f)
                            .put(color.getBlue() / 255f)
                            .put(color.getAlpha() / 255f);
                    batchCount++;
                }

                if (batchCount <= 0)
                    continue;

                indicatorTransforms.flip();
                indicatorParams.flip();
                indicatorColors.flip();
                GL20.glUniform4fv(indicatorTransformsUniform, indicatorTransforms);
                GL20.glUniform4fv(indicatorParamsUniform, indicatorParams);
                GL20.glUniform4fv(indicatorColorsUniform, indicatorColors);
                GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 6, batchCount);
            }
        } finally {
            state.restore();
        }
    }

    public void drawSilhouetteShadow(float frameWidth, float frameHeight, float[] vertices, float blurRadius, float insetPixels, float lineAlpha, Color color) {
        drawSilhouetteShadow(frameWidth, frameHeight, vertices, null, blurRadius, insetPixels, lineAlpha, lineAlpha, lineAlpha, false, color);
    }

    public void drawSilhouetteShadow(float frameWidth, float frameHeight, float[] vertices, float[] armorVertices, float blurRadius, float insetPixels, float lineAlpha, float armoredLineAlpha, float throughBlockLineAlpha, Color color) {
        drawSilhouetteShadow(frameWidth, frameHeight, vertices, armorVertices, blurRadius, insetPixels, lineAlpha, armoredLineAlpha, throughBlockLineAlpha, false, color);
    }

    public void drawSilhouetteShadow(float frameWidth, float frameHeight, float[] vertices, float[] armorVertices, float blurRadius, float insetPixels, float lineAlpha, float armoredLineAlpha, float throughBlockLineAlpha, boolean reversedDepth, Color color) {
        drawSilhouetteShadow(frameWidth, frameHeight, vertices, armorVertices, blurRadius, insetPixels, lineAlpha, armoredLineAlpha, throughBlockLineAlpha, reversedDepth, color, 0, 0, 0, 0);
    }

    public void drawSilhouetteShadow(float frameWidth, float frameHeight, float[] vertices, float[] armorVertices, float blurRadius, float insetPixels, float lineAlpha, float armoredLineAlpha, float throughBlockLineAlpha, boolean reversedDepth, Color color, int targetFramebuffer, int targetWidth, int targetHeight, int sourceDepthTexture) {
        FloatBuffer vertexBuffer = directFloatBuffer(vertices);
        FloatBuffer armorVertexBuffer = directFloatBuffer(armorVertices);
        drawSilhouetteShadow(
                frameWidth,
                frameHeight,
                vertexBuffer,
                vertices == null ? 0 : vertices.length / 3,
                armorVertexBuffer,
                armorVertices == null ? 0 : armorVertices.length / 3,
                blurRadius,
                insetPixels,
                lineAlpha,
                armoredLineAlpha,
                throughBlockLineAlpha,
                reversedDepth,
                color,
                targetFramebuffer,
                targetWidth,
                targetHeight,
                sourceDepthTexture
        );
    }

    public void drawSilhouetteShadow(float frameWidth, float frameHeight, FloatBuffer vertices, int vertexCount, FloatBuffer armorVertices, int armorVertexCount, float blurRadius, float insetPixels, float lineAlpha, float armoredLineAlpha, float throughBlockLineAlpha, boolean reversedDepth, Color color, int targetFramebuffer, int targetWidth, int targetHeight, int sourceDepthTexture) {
        if (frameWidth <= 0f || frameHeight <= 0f || vertices == null || color == null || color.getAlpha() <= 0)
            return;

        if (vertexCount < 3)
            return;
        if (vertices.remaining() < vertexCount * 3)
            return;
        if (armorVertices == null || armorVertexCount < 3 || armorVertices.remaining() < armorVertexCount * 3) {
            armorVertices = null;
            armorVertexCount = 0;
        }

        ensureInitialized();

        GLState state = GLState.capture();
        GLState renderState = targetFramebuffer > 0 && targetWidth > 0 && targetHeight > 0
                ? state.withRenderTarget(targetFramebuffer, targetWidth, targetHeight)
                : state;
        try {
            int viewportWidth = Math.max(1, renderState.viewportWidth);
            int viewportHeight = Math.max(1, renderState.viewportHeight);
            float requestedRadius = Math.max(0f, blurRadius);
            int downscale = silhouetteDownscale(requestedRadius);
            int textureWidth = Math.max(1, (int) Math.ceil(viewportWidth / (float) downscale));
            int textureHeight = Math.max(1, (int) Math.ceil(viewportHeight / (float) downscale));
            float textureRadius = clamp(requestedRadius / downscale, 0f, 24);

            ensureSilhouetteTextures(viewportWidth, viewportHeight, textureWidth, textureHeight);
            uploadTriangleBuffer(vertices, vertexCount, triangleVertexBuffer);
            uploadTriangleBuffer(armorVertices, armorVertexCount, triangleArmorVertexBuffer);
            renderSilhouetteMask(frameWidth, frameHeight, triangleVertexBuffer, vertexCount, viewportWidth, viewportHeight, silhouetteMaskTexture);
            renderSilhouetteMask(frameWidth, frameHeight, triangleArmorVertexBuffer, armorVertexCount, viewportWidth, viewportHeight, silhouetteMaskTexture, false);
            renderSilhouetteMask(frameWidth, frameHeight, triangleArmorVertexBuffer, armorVertexCount, viewportWidth, viewportHeight, silhouetteArmorMaskTexture);
            renderSelfDepthMask(frameWidth, frameHeight, triangleVertexBuffer, vertexCount, viewportWidth, viewportHeight, reversedDepth);
            renderArmorDepthMask(frameWidth, frameHeight, triangleArmorVertexBuffer, armorVertexCount, viewportWidth, viewportHeight, reversedDepth);
            renderOccludedSilhouetteMask(frameWidth, frameHeight, triangleVertexBuffer, vertexCount, triangleArmorVertexBuffer, armorVertexCount, viewportWidth, viewportHeight, reversedDepth, sourceDepthTexture);
            renderBlurPass(silhouetteMaskTexture.texture, silhouetteBlurPingTexture.framebuffer, textureWidth, textureHeight, textureRadius, 1f, 0f);
            renderBlurPass(silhouetteBlurPingTexture.texture, silhouetteBlurPongTexture.framebuffer, textureWidth, textureHeight, textureRadius, 0f, 1f);
            drawSilhouetteComposite(renderState, frameWidth, frameHeight, Math.max(0f, insetPixels), clamp(lineAlpha, 0f, 1f), clamp(armoredLineAlpha, 0f, 1f), clamp(throughBlockLineAlpha, 0f, 1f), color);
        } finally {
            state.restore();
        }
    }

    public void drawEffects(float frameWidth, float frameHeight, List<BlurRequest> blurRequests, List<ShadowRequest> shadowRequests) {
        if (frameWidth <= 0f || frameHeight <= 0f)
            return;

        boolean hasBlurRequests = blurRequests != null && !blurRequests.isEmpty();
        boolean hasShadowRequests = shadowRequests != null && !shadowRequests.isEmpty();
        if (!hasBlurRequests && !hasShadowRequests)
            return;

        ensureInitialized();

        GLState state = GLState.capture();
        try {
            if (hasBlurRequests) {
                int viewportWidth = Math.max(1, state.viewportWidth);
                int viewportHeight = Math.max(1, state.viewportHeight);

                int requestIndex = 0;
                while (requestIndex < blurRequests.size()) {
                    BlurRequest request = blurRequests.get(requestIndex);
                    if (!isDrawableBlurRequest(request)) {
                        requestIndex++;
                        continue;
                    }

                    float blurRadius = clamp(request.blurRadius, 0f, 24);
                    ensureBlurTexture(state, viewportWidth, viewportHeight, blurRadius);
                    requestIndex = drawBlurRequestBatch(
                            frameWidth,
                            frameHeight,
                            state,
                            blurRequests,
                            requestIndex,
                            blurRadius
                    );
                }
            }

            if (hasShadowRequests) {
                drawShadowRequests(frameWidth, frameHeight, shadowRequests);
            }
        } finally {
            state.restore();
        }
    }

    private void ensureSilhouetteTextures(int maskWidth, int maskHeight, int blurWidth, int blurHeight) {
        silhouetteMaskTexture.resize(maskWidth, maskHeight);
        silhouetteArmorMaskTexture.resize(maskWidth, maskHeight);
        silhouetteOccludedMaskTexture.resize(maskWidth, maskHeight);
        silhouetteSelfDepthTexture.resize(maskWidth, maskHeight);
        silhouetteArmorDepthTexture.resize(maskWidth, maskHeight);
        silhouetteBlurPingTexture.resize(blurWidth, blurHeight);
        silhouetteBlurPongTexture.resize(blurWidth, blurHeight);
    }

    private void renderSilhouetteMask(float frameWidth, float frameHeight, int vertexBuffer, int vertexCount, int viewportWidth, int viewportHeight, RenderTexture targetTexture) {
        renderSilhouetteMask(frameWidth, frameHeight, vertexBuffer, vertexCount, viewportWidth, viewportHeight, targetTexture, true);
    }

    private void renderSilhouetteMask(float frameWidth, float frameHeight, int vertexBuffer, int vertexCount, int viewportWidth, int viewportHeight, RenderTexture targetTexture, boolean clear) {
        prepareCommonState();
        GL11.glDisable(GL11.GL_BLEND);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetTexture.framebuffer);
        GL11.glViewport(0, 0, viewportWidth, viewportHeight);
        if (clear)
            clearColorAttachment();

        if (vertexBuffer == 0 || vertexCount < 3)
            return;

        GL20.glUseProgram(maskProgram);
        GL20.glUniform2f(maskScreenSizeUniform, frameWidth, frameHeight);
        setTriangleDepthTransform(maskDepthScaleUniform, maskDepthOffsetUniform, 1f, 0f);
        drawTriangleBuffer(vertexBuffer, vertexCount);
    }

    private void renderSelfDepthMask(float frameWidth, float frameHeight, int vertexBuffer, int vertexCount, int viewportWidth, int viewportHeight, boolean reversedDepth) {
        prepareCommonState();
        GL11.glDisable(GL11.GL_BLEND);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, silhouetteSelfDepthTexture.framebuffer);
        GL11.glViewport(0, 0, viewportWidth, viewportHeight);
        clearColorAttachment();
        clearDepthAttachment(reversedDepth ? 0f : 1f);

        if (vertexBuffer == 0 || vertexCount < 3)
            return;

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(reversedDepth ? GL11.GL_GREATER : GL11.GL_LESS);
        GL20.glUseProgram(maskProgram);
        GL20.glUniform2f(maskScreenSizeUniform, frameWidth, frameHeight);
        setTriangleDepthTransform(maskDepthScaleUniform, maskDepthOffsetUniform, 1f, 0f);
        drawTriangleBuffer(vertexBuffer, vertexCount);
    }

    private void renderArmorDepthMask(float frameWidth, float frameHeight, int armorVertexBuffer, int armorVertexCount, int viewportWidth, int viewportHeight, boolean reversedDepth) {
        prepareCommonState();
        GL11.glDisable(GL11.GL_BLEND);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, silhouetteArmorDepthTexture.framebuffer);
        GL11.glViewport(0, 0, viewportWidth, viewportHeight);
        clearColorAttachment();
        clearDepthAttachment(reversedDepth ? 0f : 1f);

        if (armorVertexBuffer == 0 || armorVertexCount < 3)
            return;

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(reversedDepth ? GL11.GL_GREATER : GL11.GL_LESS);
        GL20.glUseProgram(maskProgram);
        GL20.glUniform2f(maskScreenSizeUniform, frameWidth, frameHeight);
        setTriangleDepthTransform(maskDepthScaleUniform, maskDepthOffsetUniform, 4095f / 4096f, 0f);
        drawTriangleBuffer(armorVertexBuffer, armorVertexCount);
    }

    private void renderOccludedSilhouetteMask(float frameWidth, float frameHeight, int vertexBuffer, int vertexCount, int armorVertexBuffer, int armorVertexCount, int viewportWidth, int viewportHeight, boolean reversedDepth, int sceneDepthTexture) {
        prepareCommonState();
        GL11.glDisable(GL11.GL_BLEND);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, silhouetteOccludedMaskTexture.framebuffer);
        GL11.glViewport(0, 0, viewportWidth, viewportHeight);
        clearColorAttachment();

        if (sceneDepthTexture <= 0 || vertexCount < 3 && armorVertexCount < 3)
            return;

        GL20.glUseProgram(occludedMaskProgram);
        GL20.glUniform2f(occludedMaskScreenSizeUniform, frameWidth, frameHeight);
        setTriangleDepthTransform(occludedMaskDepthScaleUniform, occludedMaskDepthOffsetUniform, 1f, 0f);
        GL20.glUniform1i(occludedDepthTextureUniform, 0);
        GL20.glUniform1f(occludedDepthEpsilonUniform, 0.0005f);
        GL20.glUniform1i(occludedSelfDepthTextureUniform, 1);
        GL20.glUniform1f(occludedSelfDepthEpsilonUniform, 0.00075f);
        GL20.glUniform1i(occludedArmorDepthTextureUniform, 2);
        GL20.glUniform1f(occludedArmorDepthEpsilonUniform, 0.001f);
        GL20.glUniform1i(occludedArmorMaskTextureUniform, 3);
        GL20.glUniform1f(occludedArmorMaskDepthEpsilonUniform, 0.004f);
        GL20.glUniform1f(occludedArmorMaskPaddingPixelsUniform, 6f);
        GL20.glUniform1f(occludedReversedDepthUniform, reversedDepth ? 1f : 0f);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL33.glBindSampler(0, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneDepthTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL33.glBindSampler(1, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, silhouetteSelfDepthTexture.depthTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL33.glBindSampler(2, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, silhouetteArmorDepthTexture.depthTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        GL33.glBindSampler(3, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, silhouetteArmorMaskTexture.texture);
        drawTriangleBuffer(vertexBuffer, vertexCount);
        setTriangleDepthTransform(occludedMaskDepthScaleUniform, occludedMaskDepthOffsetUniform, 4095f / 4096f, 0f);
        drawTriangleBuffer(armorVertexBuffer, armorVertexCount);
    }

    private void setTriangleDepthTransform(int scaleUniform, int offsetUniform, float scale, float offset) {
        GL20.glUniform1f(scaleUniform, scale);
        GL20.glUniform1f(offsetUniform, offset);
    }

    private void uploadTriangleBuffer(FloatBuffer vertices, int vertexCount, int vertexBuffer) {
        if (vertices == null || vertexBuffer == 0 || vertexCount < 3)
            return;

        FloatBuffer uploadVertices = vertices.duplicate();
        uploadVertices.limit(uploadVertices.position() + vertexCount * 3);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, uploadVertices, GL15.GL_STREAM_DRAW);
    }

    private void drawTriangleBuffer(int vertexBuffer, int vertexCount) {
        if (vertexBuffer == 0 || vertexCount < 3)
            return;

        GL30.glBindVertexArray(triangleVertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0L);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
    }

    private void drawSilhouetteComposite(GLState state, float frameWidth, float frameHeight, float insetPixels, float lineAlpha, float armoredLineAlpha, float throughBlockLineAlpha, Color color) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, state.drawFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, state.readFramebuffer);
        GL11.glViewport(state.viewportX, state.viewportY, state.viewportWidth, state.viewportHeight);
        prepareCommonState();
        GL11.glEnable(GL11.GL_BLEND);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL20.glUseProgram(silhouetteCompositeProgram);
        GL20.glUniform1i(silhouetteBlurTextureUniform, 0);
        GL20.glUniform1i(silhouetteMaskTextureUniform, 1);
        GL20.glUniform1i(silhouetteArmorMaskTextureUniform, 2);
        GL20.glUniform1i(silhouetteOccludedMaskTextureUniform, 3);
        GL20.glUniform2f(silhouetteTexelSizeUniform, 1f / frameWidth, 1f / frameHeight);
        GL20.glUniform1f(silhouetteInsetPixelsUniform, insetPixels);
        GL20.glUniform1f(silhouetteLineAlphaUniform, lineAlpha);
        GL20.glUniform1f(silhouetteArmoredLineAlphaUniform, armoredLineAlpha);
        GL20.glUniform1f(silhouetteThroughBlockLineAlphaUniform, throughBlockLineAlpha);
        GL20.glUniform4f(
                silhouetteColorUniform,
                color.getRed() / 255f,
                color.getGreen() / 255f,
                color.getBlue() / 255f,
                color.getAlpha() / 255f
        );

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, silhouetteBlurPongTexture.texture);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL33.glBindSampler(1, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, silhouetteMaskTexture.texture);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL33.glBindSampler(2, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, silhouetteArmorMaskTexture.texture);
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        GL33.glBindSampler(3, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, silhouetteOccludedMaskTexture.texture);
        GL30.glBindVertexArray(vertexArray);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private static int silhouetteDownscale(float blurRadius) {
        if (blurRadius <= 24)
            return 1;

        return Math.max(1, Math.min(4, (int) Math.ceil(blurRadius / 24)));
    }

    private static FloatBuffer directFloatBuffer(float[] values) {
        if (values == null)
            return null;

        FloatBuffer buffer = BufferUtils.createFloatBuffer(values.length);
        buffer.put(values);
        buffer.flip();
        return buffer;
    }

    private void clearColorAttachment() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer clearColor = stack.floats(0f, 0f, 0f, 0f);
            GL30.glClearBufferfv(GL11.GL_COLOR, 0, clearColor);
        }
    }

    private void clearDepthAttachment(float depth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer clearDepth = stack.floats(depth);
            GL30.glClearBufferfv(GL11.GL_DEPTH, 0, clearDepth);
        }
    }

    private void drawShadowRequests(float frameWidth, float frameHeight, List<ShadowRequest> requests) {
        prepareCommonState();
        GL11.glEnable(GL11.GL_BLEND);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL20.glUseProgram(shadowProgram);
        GL20.glUniform2f(shadowScreenSizeUniform, frameWidth, frameHeight);
        GL30.glBindVertexArray(vertexArray);

        int requestIndex = 0;
        while (requestIndex < requests.size()) {
            clearEffectBuffers();
            int batchCount = 0;

            while (requestIndex < requests.size() && batchCount < EFFECT_BATCH_SIZE) {
                ShadowRequest request = requests.get(requestIndex++);
                if (!isDrawableShadowRequest(request))
                    continue;

                float blurRadius = Math.max(0f, request.blurRadius);
                float spread = Math.max(0f, request.spread);
                float padding = blurRadius + spread + 2f;
                putRect(
                        effectDrawRects,
                        request.x + request.offsetX - padding,
                        request.y + request.offsetY - padding,
                        request.width + padding * 2f,
                        request.height + padding * 2f
                );
                putRect(
                        effectBoxRects,
                        request.x + request.offsetX,
                        request.y + request.offsetY,
                        request.width,
                        request.height
                );
                effectParams.put(request.radius).put(blurRadius).put(spread).put(0f);
                Color color = request.color;
                effectColors.put(color.getRed() / 255f)
                        .put(color.getGreen() / 255f)
                        .put(color.getBlue() / 255f)
                        .put(color.getAlpha() / 255f);
                batchCount++;
            }

            if (batchCount > 0) {
                uploadEffectUniforms(
                        shadowDrawRectsUniform,
                        shadowBoxRectsUniform,
                        shadowEffectParamsUniform,
                        shadowColorsUniform
                );
                GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 6, batchCount);
            }
        }
    }

    private void ensureBlurTexture(GLState state, int viewportWidth, int viewportHeight, float blurRadius) {
        int blurDownscale = hudBlurDownscale(viewportWidth, viewportHeight);
        if (!blurCacheValid || cachedBlurWidth != viewportWidth || cachedBlurHeight != viewportHeight) {
            int blurWidth = Math.max(1, (viewportWidth + blurDownscale - 1) / blurDownscale);
            int blurHeight = Math.max(1, (viewportHeight + blurDownscale - 1) / blurDownscale);
            sourceTexture.resize(viewportWidth, viewportHeight);
            blurPingTexture.resize(blurWidth, blurHeight);
            blurPongTexture.resize(blurWidth, blurHeight);

            prepareCommonState();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, state.readFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, state.drawFramebuffer);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTexture.texture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, state.viewportX, state.viewportY, viewportWidth, viewportHeight);

            blurCacheValid = false;
            cachedBlurWidth = viewportWidth;
            cachedBlurHeight = viewportHeight;
            cachedBlurRadius = -1f;
        }

        if (!blurCacheValid || Math.abs(cachedBlurRadius - blurRadius) > 0.001f) {
            int blurWidth = Math.max(1, (viewportWidth + blurDownscale - 1) / blurDownscale);
            int blurHeight = Math.max(1, (viewportHeight + blurDownscale - 1) / blurDownscale);
            prepareCommonState();
            GL11.glDisable(GL11.GL_BLEND);
            renderBlurPass(
                    sourceTexture.texture,
                    blurPingTexture.framebuffer,
                    blurWidth,
                    blurHeight,
                    viewportWidth,
                    viewportHeight,
                    blurRadius,
                    1f,
                    0f
            );
            renderBlurPass(
                    blurPingTexture.texture,
                    blurPongTexture.framebuffer,
                    blurWidth,
                    blurHeight,
                    blurWidth,
                    blurHeight,
                    blurRadius / blurDownscale,
                    0f,
                    1f
            );

            blurCacheValid = true;
            cachedBlurRadius = blurRadius;
        }
    }

    private static int hudBlurDownscale(int viewportWidth, int viewportHeight) {
        long pixels = (long) viewportWidth * viewportHeight;
        if (pixels >= 7_000_000L)
            return 4;
        if (pixels >= 2_000_000L)
            return 3;
        return HUD_BLUR_BASE_DOWNSCALE;
    }

    private int drawBlurRequestBatch(float frameWidth, float frameHeight, GLState state,
                                     List<BlurRequest> requests, int requestIndex, float blurRadius) {
        clearEffectBuffers();
        int batchCount = 0;
        int index = requestIndex;

        while (index < requests.size() && batchCount < EFFECT_BATCH_SIZE) {
            BlurRequest request = requests.get(index);
            if (!isDrawableBlurRequest(request)) {
                index++;
                continue;
            }

            float requestBlurRadius = clamp(request.blurRadius, 0f, 24);
            if (Math.abs(requestBlurRadius - blurRadius) > 0.001f)
                break;

            putRect(effectDrawRects, request.x, request.y, request.width, request.height);
            putRect(effectBoxRects, request.x, request.y, request.width, request.height);
            effectParams.put(request.radius).put(clamp(request.alpha, 0f, 1f)).put(0f).put(0f);
            batchCount++;
            index++;
        }

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, state.drawFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, state.readFramebuffer);
        GL11.glViewport(state.viewportX, state.viewportY, state.viewportWidth, state.viewportHeight);
        GL11.glEnable(GL11.GL_BLEND);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL20.glUseProgram(roundedBlurProgram);
        GL20.glUniform2f(roundedBlurScreenSizeUniform, frameWidth, frameHeight);
        GL20.glUniform1i(roundedBlurTextureUniform, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, blurPongTexture.texture);
        GL30.glBindVertexArray(vertexArray);
        uploadEffectUniforms(
                roundedBlurDrawRectsUniform,
                roundedBlurBoxRectsUniform,
                roundedBlurEffectParamsUniform,
                -1
        );
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 6, batchCount);
        return index;
    }

    private static boolean isDrawableBlurRequest(BlurRequest request) {
        return request != null
                && request.width > 0f
                && request.height > 0f
                && request.alpha > 0f
                && clamp(request.blurRadius, 0f, 24) > 0.01f;
    }

    private static boolean isDrawableShadowRequest(ShadowRequest request) {
        return request != null
                && request.width > 0f
                && request.height > 0f
                && request.color != null
                && request.color.getAlpha() > 0;
    }

    private static boolean isDrawableIndicatorRequest(IndicatorRequest request) {
        return request != null
                && Float.isFinite(request.centerX)
                && Float.isFinite(request.centerY)
                && Float.isFinite(request.angle)
                && request.size > 0f
                && request.color != null
                && request.color.getAlpha() > 0;
    }

    private void clearEffectBuffers() {
        effectDrawRects.clear();
        effectBoxRects.clear();
        effectParams.clear();
        effectColors.clear();
    }

    private static void putRect(FloatBuffer buffer, float x, float y, float width, float height) {
        buffer.put(x).put(y).put(width).put(height);
    }

    private void uploadEffectUniforms(int drawRectsUniform, int boxRectsUniform,
                                      int paramsUniform, int colorsUniform) {
        effectDrawRects.flip();
        effectBoxRects.flip();
        effectParams.flip();
        GL20.glUniform4fv(drawRectsUniform, effectDrawRects);
        GL20.glUniform4fv(boxRectsUniform, effectBoxRects);
        GL20.glUniform4fv(paramsUniform, effectParams);
        if (colorsUniform >= 0) {
            effectColors.flip();
            GL20.glUniform4fv(colorsUniform, effectColors);
        }
    }

    public void destroy() {
        if (blurProgram != 0) {
            GL20.glDeleteProgram(blurProgram);
            blurProgram = 0;
        }
        if (roundedBlurProgram != 0) {
            GL20.glDeleteProgram(roundedBlurProgram);
            roundedBlurProgram = 0;
        }
        if (shadowProgram != 0) {
            GL20.glDeleteProgram(shadowProgram);
            shadowProgram = 0;
        }
        if (indicatorProgram != 0) {
            GL20.glDeleteProgram(indicatorProgram);
            indicatorProgram = 0;
        }
        if (maskProgram != 0) {
            GL20.glDeleteProgram(maskProgram);
            maskProgram = 0;
        }
        if (occludedMaskProgram != 0) {
            GL20.glDeleteProgram(occludedMaskProgram);
            occludedMaskProgram = 0;
        }
        if (silhouetteCompositeProgram != 0) {
            GL20.glDeleteProgram(silhouetteCompositeProgram);
            silhouetteCompositeProgram = 0;
        }
        resetUniformLocations();
        if (vertexArray != 0) {
            GL30.glDeleteVertexArrays(vertexArray);
            vertexArray = 0;
        }
        if (triangleVertexArray != 0) {
            GL30.glDeleteVertexArrays(triangleVertexArray);
            triangleVertexArray = 0;
        }
        if (triangleVertexBuffer != 0) {
            GL15.glDeleteBuffers(triangleVertexBuffer);
            triangleVertexBuffer = 0;
        }
        if (triangleArmorVertexBuffer != 0) {
            GL15.glDeleteBuffers(triangleArmorVertexBuffer);
            triangleArmorVertexBuffer = 0;
        }

        sourceTexture.destroy();
        blurPingTexture.destroy();
        blurPongTexture.destroy();
        silhouetteMaskTexture.destroy();
        silhouetteArmorMaskTexture.destroy();
        silhouetteOccludedMaskTexture.destroy();
        silhouetteSelfDepthTexture.destroy();
        silhouetteArmorDepthTexture.destroy();
        silhouetteBlurPingTexture.destroy();
        silhouetteBlurPongTexture.destroy();
        blurCacheValid = false;
        cachedBlurWidth = 0;
        cachedBlurHeight = 0;
        cachedBlurRadius = -1f;
    }

    private void renderBlurPass(int sourceTexture, int targetFramebuffer, int width, int height, float radius, float directionX, float directionY) {
        renderBlurPass(sourceTexture, targetFramebuffer, width, height, width, height, radius, directionX, directionY);
    }

    private void renderBlurPass(int sourceTexture, int targetFramebuffer, int width, int height,
                                int sourceWidth, int sourceHeight, float radius, float directionX, float directionY) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFramebuffer);
        GL11.glViewport(0, 0, width, height);
        GL20.glUseProgram(blurProgram);
        GL20.glUniform1i(blurTextureUniform, 0);
        GL20.glUniform2f(blurTexelSizeUniform, 1f / sourceWidth, 1f / sourceHeight);
        GL20.glUniform2f(blurDirectionUniform, directionX, directionY);
        GL20.glUniform1f(blurRadiusUniform, radius);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTexture);
        GL30.glBindVertexArray(vertexArray);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    private void prepareCommonState() {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL33.glBindSampler(0, 0);
        GL33.glBindSampler(1, 0);
        GL33.glBindSampler(2, 0);
        GL33.glBindSampler(3, 0);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void ensureInitialized() {
        if (vertexArray == 0) {
            vertexArray = GL30.glGenVertexArrays();
        }
        if (triangleVertexArray == 0) {
            triangleVertexArray = GL30.glGenVertexArrays();
        }
        if (triangleVertexBuffer == 0) {
            triangleVertexBuffer = GL15.glGenBuffers();
        }
        if (triangleArmorVertexBuffer == 0) {
            triangleArmorVertexBuffer = GL15.glGenBuffers();
        }
        if (blurProgram == 0) {
            blurProgram = createProgram(fullscreenVertexShader(), blurFragmentShader());
        }
        if (roundedBlurProgram == 0) {
            roundedBlurProgram = createProgram(rectVertexShader(), roundedBlurFragmentShader());
        }
        if (shadowProgram == 0) {
            shadowProgram = createProgram(rectVertexShader(), shadowFragmentShader());
        }
        if (indicatorProgram == 0) {
            indicatorProgram = createProgram(indicatorVertexShader(), indicatorFragmentShader());
        }
        if (maskProgram == 0) {
            maskProgram = createProgram(triangleMaskVertexShader(), maskFragmentShader());
        }
        if (occludedMaskProgram == 0) {
            occludedMaskProgram = createProgram(triangleMaskVertexShader(), occludedMaskFragmentShader());
        }
        if (silhouetteCompositeProgram == 0) {
            silhouetteCompositeProgram = createProgram(fullscreenVertexShader(), silhouetteCompositeFragmentShader());
        }
        if (blurTextureUniform < 0 || roundedBlurTextureUniform < 0 || shadowColorsUniform < 0
                || indicatorColorsUniform < 0 || occludedDepthTextureUniform < 0 || silhouetteColorUniform < 0) {
            loadUniformLocations();
        }
    }

    private void loadUniformLocations() {
        blurTextureUniform = uniform(blurProgram, "uTexture");
        blurTexelSizeUniform = uniform(blurProgram, "uTexelSize");
        blurDirectionUniform = uniform(blurProgram, "uDirection");
        blurRadiusUniform = uniform(blurProgram, "uRadius");

        roundedBlurDrawRectsUniform = uniform(roundedBlurProgram, "uDrawRects[0]");
        roundedBlurScreenSizeUniform = uniform(roundedBlurProgram, "uScreenSize");
        roundedBlurBoxRectsUniform = uniform(roundedBlurProgram, "uBoxRects[0]");
        roundedBlurEffectParamsUniform = uniform(roundedBlurProgram, "uEffectParams[0]");
        roundedBlurTextureUniform = uniform(roundedBlurProgram, "uTexture");

        shadowDrawRectsUniform = uniform(shadowProgram, "uDrawRects[0]");
        shadowScreenSizeUniform = uniform(shadowProgram, "uScreenSize");
        shadowBoxRectsUniform = uniform(shadowProgram, "uBoxRects[0]");
        shadowEffectParamsUniform = uniform(shadowProgram, "uEffectParams[0]");
        shadowColorsUniform = uniform(shadowProgram, "uColors[0]");

        indicatorTransformsUniform = uniform(indicatorProgram, "uIndicators[0]");
        indicatorScreenSizeUniform = uniform(indicatorProgram, "uScreenSize");
        indicatorParamsUniform = uniform(indicatorProgram, "uIndicatorParams[0]");
        indicatorColorsUniform = uniform(indicatorProgram, "uIndicatorColors[0]");

        maskScreenSizeUniform = uniform(maskProgram, "uScreenSize");
        maskDepthScaleUniform = uniform(maskProgram, "uDepthScale");
        maskDepthOffsetUniform = uniform(maskProgram, "uDepthOffset");

        occludedMaskScreenSizeUniform = uniform(occludedMaskProgram, "uScreenSize");
        occludedMaskDepthScaleUniform = uniform(occludedMaskProgram, "uDepthScale");
        occludedMaskDepthOffsetUniform = uniform(occludedMaskProgram, "uDepthOffset");
        occludedDepthTextureUniform = uniform(occludedMaskProgram, "uDepthTexture");
        occludedDepthEpsilonUniform = uniform(occludedMaskProgram, "uDepthEpsilon");
        occludedSelfDepthTextureUniform = uniform(occludedMaskProgram, "uSelfDepthTexture");
        occludedSelfDepthEpsilonUniform = uniform(occludedMaskProgram, "uSelfDepthEpsilon");
        occludedArmorDepthTextureUniform = uniform(occludedMaskProgram, "uArmorDepthTexture");
        occludedArmorDepthEpsilonUniform = uniform(occludedMaskProgram, "uArmorDepthEpsilon");
        occludedArmorMaskTextureUniform = uniform(occludedMaskProgram, "uArmorMaskTexture");
        occludedArmorMaskDepthEpsilonUniform = uniform(occludedMaskProgram, "uArmorMaskDepthEpsilon");
        occludedArmorMaskPaddingPixelsUniform = uniform(occludedMaskProgram, "uArmorMaskPaddingPixels");
        occludedReversedDepthUniform = uniform(occludedMaskProgram, "uReversedDepth");

        silhouetteBlurTextureUniform = uniform(silhouetteCompositeProgram, "uBlurTexture");
        silhouetteMaskTextureUniform = uniform(silhouetteCompositeProgram, "uMaskTexture");
        silhouetteArmorMaskTextureUniform = uniform(silhouetteCompositeProgram, "uArmorMaskTexture");
        silhouetteOccludedMaskTextureUniform = uniform(silhouetteCompositeProgram, "uOccludedMaskTexture");
        silhouetteColorUniform = uniform(silhouetteCompositeProgram, "uColor");
        silhouetteTexelSizeUniform = uniform(silhouetteCompositeProgram, "uTexelSize");
        silhouetteInsetPixelsUniform = uniform(silhouetteCompositeProgram, "uInsetPixels");
        silhouetteLineAlphaUniform = uniform(silhouetteCompositeProgram, "uLineAlpha");
        silhouetteArmoredLineAlphaUniform = uniform(silhouetteCompositeProgram, "uArmoredLineAlpha");
        silhouetteThroughBlockLineAlphaUniform = uniform(silhouetteCompositeProgram, "uThroughBlockLineAlpha");
    }

    private void resetUniformLocations() {
        blurTextureUniform = -1;
        blurTexelSizeUniform = -1;
        blurDirectionUniform = -1;
        blurRadiusUniform = -1;
        roundedBlurDrawRectsUniform = -1;
        roundedBlurScreenSizeUniform = -1;
        roundedBlurBoxRectsUniform = -1;
        roundedBlurEffectParamsUniform = -1;
        roundedBlurTextureUniform = -1;
        shadowDrawRectsUniform = -1;
        shadowScreenSizeUniform = -1;
        shadowBoxRectsUniform = -1;
        shadowEffectParamsUniform = -1;
        shadowColorsUniform = -1;
        indicatorTransformsUniform = -1;
        indicatorScreenSizeUniform = -1;
        indicatorParamsUniform = -1;
        indicatorColorsUniform = -1;
        maskScreenSizeUniform = -1;
        maskDepthScaleUniform = -1;
        maskDepthOffsetUniform = -1;
        occludedMaskScreenSizeUniform = -1;
        occludedMaskDepthScaleUniform = -1;
        occludedMaskDepthOffsetUniform = -1;
        occludedDepthTextureUniform = -1;
        occludedDepthEpsilonUniform = -1;
        occludedSelfDepthTextureUniform = -1;
        occludedSelfDepthEpsilonUniform = -1;
        occludedArmorDepthTextureUniform = -1;
        occludedArmorDepthEpsilonUniform = -1;
        occludedArmorMaskTextureUniform = -1;
        occludedArmorMaskDepthEpsilonUniform = -1;
        occludedArmorMaskPaddingPixelsUniform = -1;
        occludedReversedDepthUniform = -1;
        silhouetteBlurTextureUniform = -1;
        silhouetteMaskTextureUniform = -1;
        silhouetteArmorMaskTextureUniform = -1;
        silhouetteOccludedMaskTextureUniform = -1;
        silhouetteColorUniform = -1;
        silhouetteTexelSizeUniform = -1;
        silhouetteInsetPixelsUniform = -1;
        silhouetteLineAlphaUniform = -1;
        silhouetteArmoredLineAlphaUniform = -1;
        silhouetteThroughBlockLineAlphaUniform = -1;
    }

    private static int uniform(int program, String name) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location < 0)
            throw new IllegalStateException("Missing 2D shader uniform " + name);

        return location;
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GL20.glCreateProgram();

        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);

        int linkStatus = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        if (linkStatus == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new IllegalStateException("Failed to link 2D shader program: " + log);
        }

        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Failed to compile 2D shader: " + log);
        }

        return shader;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void restoreCapability(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    public static final class IndicatorRequest {
        public float centerX;
        public float centerY;
        public float angle;
        public float size;
        public float thickness;
        public float glowRadius;
        public float pulse;
        public Color color;

        public IndicatorRequest() {
        }

        public IndicatorRequest(float centerX, float centerY, float angle, float size,
                                float thickness, float glowRadius, float pulse, Color color) {
            set(centerX, centerY, angle, size, thickness, glowRadius, pulse, color);
        }

        public IndicatorRequest set(float centerX, float centerY, float angle, float size,
                                    float thickness, float glowRadius, float pulse, Color color) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.angle = angle;
            this.size = size;
            this.thickness = thickness;
            this.glowRadius = glowRadius;
            this.pulse = pulse;
            this.color = color;
            return this;
        }
    }

    public static final class BlurRequest {
        public float x;
        public float y;
        public float width;
        public float height;
        public float radius;
        public float blurRadius;
        public float alpha;

        public BlurRequest() {
        }

        public BlurRequest(float x, float y, float width, float height, float radius, float blurRadius, float alpha) {
            set(x, y, width, height, radius, blurRadius, alpha);
        }

        public BlurRequest set(float x, float y, float width, float height, float radius, float blurRadius, float alpha) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.radius = radius;
            this.blurRadius = blurRadius;
            this.alpha = alpha;
            return this;
        }
    }

    public static final class ShadowRequest {
        public float x;
        public float y;
        public float width;
        public float height;
        public float radius;
        public float blurRadius;
        public float spread;
        public float offsetX;
        public float offsetY;
        public Color color;

        public ShadowRequest() {
        }

        public ShadowRequest(float x, float y, float width, float height, float radius, float blurRadius, float spread, float offsetX, float offsetY, Color color) {
            set(x, y, width, height, radius, blurRadius, spread, offsetX, offsetY, color);
        }

        public ShadowRequest set(float x, float y, float width, float height, float radius, float blurRadius, float spread, float offsetX, float offsetY, Color color) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.radius = radius;
            this.blurRadius = blurRadius;
            this.spread = spread;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.color = color;
            return this;
        }
    }

    private static final class RenderTexture {
        private final boolean hasFramebuffer;
        private final boolean hasDepth;
        private int texture = 0;
        private int depthTexture = 0;
        private int framebuffer = 0;
        private int width = 0;
        private int height = 0;

        private RenderTexture(boolean hasFramebuffer) {
            this(hasFramebuffer, false);
        }

        private RenderTexture(boolean hasFramebuffer, boolean hasDepth) {
            this.hasFramebuffer = hasFramebuffer;
            this.hasDepth = hasDepth;
        }

        private void resize(int width, int height) {
            if (texture == 0) {
                texture = GL11.glGenTextures();
            }

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            boolean sizeChanged = this.width != width || this.height != height;
            if (sizeChanged) {
                this.width = width;
                this.height = height;
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
            }

            if (hasFramebuffer) {
                if (framebuffer == 0) {
                    framebuffer = GL30.glGenFramebuffers();
                }

                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);

                if (hasDepth) {
                    boolean newDepthTexture = depthTexture == 0;
                    if (depthTexture == 0) {
                        depthTexture = GL11.glGenTextures();
                    }

                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                    if (sizeChanged || newDepthTexture) {
                        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, 0L);
                    }
                    GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTexture, 0);
                }

                int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("Incomplete 2D blur framebuffer: " + status);
                }
            }
        }

        private void destroy() {
            if (framebuffer != 0) {
                GL30.glDeleteFramebuffers(framebuffer);
                framebuffer = 0;
            }
            if (texture != 0) {
                GL11.glDeleteTextures(texture);
                texture = 0;
            }
            if (depthTexture != 0) {
                GL11.glDeleteTextures(depthTexture);
                depthTexture = 0;
            }
            width = 0;
            height = 0;
        }
    }

    private static final class GLState {
        private final int activeTexture;
        private final int textureBinding;
        private final int sampler;
        private final int textureBinding1;
        private final int sampler1;
        private final int textureBinding2;
        private final int sampler2;
        private final int textureBinding3;
        private final int sampler3;
        private final int program;
        private final int vertexArray;
        private final int arrayBuffer;
        private final int elementArrayBuffer;
        private final int readFramebuffer;
        private final int drawFramebuffer;
        private final int blendSrcRgb;
        private final int blendDstRgb;
        private final int blendSrcAlpha;
        private final int blendDstAlpha;
        private final int blendEquationRgb;
        private final int blendEquationAlpha;
        private final int depthFunc;
        private final int viewportX;
        private final int viewportY;
        private final int viewportWidth;
        private final int viewportHeight;
        private final boolean blend;
        private final boolean depthTest;
        private final boolean cullFace;
        private final boolean scissorTest;
        private final boolean depthMask;

        private GLState(
                int activeTexture,
                int textureBinding,
                int sampler,
                int textureBinding1,
                int sampler1,
                int textureBinding2,
                int sampler2,
                int textureBinding3,
                int sampler3,
                int program,
                int vertexArray,
                int arrayBuffer,
                int elementArrayBuffer,
                int readFramebuffer,
                int drawFramebuffer,
                int blendSrcRgb,
                int blendDstRgb,
                int blendSrcAlpha,
                int blendDstAlpha,
                int blendEquationRgb,
                int blendEquationAlpha,
                int depthFunc,
                int viewportX,
                int viewportY,
                int viewportWidth,
                int viewportHeight,
                boolean blend,
                boolean depthTest,
                boolean cullFace,
                boolean scissorTest,
                boolean depthMask
        ) {
            this.activeTexture = activeTexture;
            this.textureBinding = textureBinding;
            this.sampler = sampler;
            this.textureBinding1 = textureBinding1;
            this.sampler1 = sampler1;
            this.textureBinding2 = textureBinding2;
            this.sampler2 = sampler2;
            this.textureBinding3 = textureBinding3;
            this.sampler3 = sampler3;
            this.program = program;
            this.vertexArray = vertexArray;
            this.arrayBuffer = arrayBuffer;
            this.elementArrayBuffer = elementArrayBuffer;
            this.readFramebuffer = readFramebuffer;
            this.drawFramebuffer = drawFramebuffer;
            this.blendSrcRgb = blendSrcRgb;
            this.blendDstRgb = blendDstRgb;
            this.blendSrcAlpha = blendSrcAlpha;
            this.blendDstAlpha = blendDstAlpha;
            this.blendEquationRgb = blendEquationRgb;
            this.blendEquationAlpha = blendEquationAlpha;
            this.depthFunc = depthFunc;
            this.viewportX = viewportX;
            this.viewportY = viewportY;
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
            this.blend = blend;
            this.depthTest = depthTest;
            this.cullFace = cullFace;
            this.scissorTest = scissorTest;
            this.depthMask = depthMask;
        }

        private static GLState capture() {
            int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int textureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int sampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            int textureBinding1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int sampler1 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            int textureBinding2 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int sampler2 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            GL13.glActiveTexture(GL13.GL_TEXTURE3);
            int textureBinding3 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int sampler3 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            GL13.glActiveTexture(activeTexture);

            int viewportX;
            int viewportY;
            int viewportWidth;
            int viewportHeight;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer viewport = stack.mallocInt(4);
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                viewportX = viewport.get(0);
                viewportY = viewport.get(1);
                viewportWidth = viewport.get(2);
                viewportHeight = viewport.get(3);
            }

            return new GLState(
                    activeTexture,
                    textureBinding,
                    sampler,
                    textureBinding1,
                    sampler1,
                    textureBinding2,
                    sampler2,
                    textureBinding3,
                    sampler3,
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                    GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                    GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    viewportX,
                    viewportY,
                    viewportWidth,
                    viewportHeight,
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
            );
        }

        private GLState withRenderTarget(int framebuffer, int width, int height) {
            return new GLState(
                    activeTexture,
                    textureBinding,
                    sampler,
                    textureBinding1,
                    sampler1,
                    textureBinding2,
                    sampler2,
                    textureBinding3,
                    sampler3,
                    program,
                    vertexArray,
                    arrayBuffer,
                    elementArrayBuffer,
                    framebuffer,
                    framebuffer,
                    blendSrcRgb,
                    blendDstRgb,
                    blendSrcAlpha,
                    blendDstAlpha,
                    blendEquationRgb,
                    blendEquationAlpha,
                    depthFunc,
                    0,
                    0,
                    width,
                    height,
                    blend,
                    depthTest,
                    cullFace,
                    scissorTest,
                    depthMask
            );
        }

        private void restore() {
            restoreCapability(GL11.GL_BLEND, blend);
            GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
            GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            restoreCapability(GL11.GL_DEPTH_TEST, depthTest);
            GL11.glDepthFunc(depthFunc);
            restoreCapability(GL11.GL_CULL_FACE, cullFace);
            restoreCapability(GL11.GL_SCISSOR_TEST, scissorTest);
            GL11.glDepthMask(depthMask);
            GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vertexArray);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementArrayBuffer);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL33.glBindSampler(0, sampler);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBinding);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL33.glBindSampler(1, sampler1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBinding1);
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL33.glBindSampler(2, sampler2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBinding2);
            GL13.glActiveTexture(GL13.GL_TEXTURE3);
            GL33.glBindSampler(3, sampler3);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBinding3);
            GL13.glActiveTexture(activeTexture);
        }
    }
}
