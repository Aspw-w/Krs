package com.instrumentalist.krs.utils.render;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class IrisCompat {
    private static boolean lookedUp;
    private static Object api;
    private static Method shaderPackInUse;
    private static Method renderingShadowPass;
    private static Object renderingState;
    private static Method gbufferProjection;
    private static Method gbufferModelView;

    private IrisCompat() {
    }

    private static void lookUp() {
        if (lookedUp)
            return;
        lookedUp = true;
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = apiClass.getMethod("getInstance").invoke(null);
            shaderPackInUse = apiClass.getMethod("isShaderPackInUse");
            renderingShadowPass = apiClass.getMethod("isRenderingShadowPass");

            Class<?> stateClass = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
            Field instance = stateClass.getField("INSTANCE");
            renderingState = instance.get(null);
            gbufferProjection = stateClass.getMethod("getGbufferProjection");
            gbufferModelView = stateClass.getMethod("getGbufferModelView");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            api = null;
        }
    }

    public static boolean isShaderPackInUse() {
        lookUp();
        return call(api, shaderPackInUse);
    }

    public static boolean isRenderingShadowPass() {
        lookUp();
        return call(api, renderingShadowPass);
    }

    public static boolean gbufferMatrices(Matrix4f projectionDest, Matrix4f viewProjectionDest) {
        lookUp();
        if (renderingState == null || gbufferProjection == null || gbufferModelView == null)
            return false;
        try {
            Matrix4fc projection = (Matrix4fc) gbufferProjection.invoke(renderingState);
            Matrix4fc modelView = (Matrix4fc) gbufferModelView.invoke(renderingState);
            if (projection == null || modelView == null)
                return false;
            projectionDest.set(projection);
            viewProjectionDest.set(projection).mul(modelView);
            return true;
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return false;
        }
    }

    private static boolean call(Object target, Method method) {
        if (target == null || method == null)
            return false;
        try {
            return (Boolean) method.invoke(target);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return false;
        }
    }
}
