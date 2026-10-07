package com.instrumentalist.krs.utils.render;

public final class GuiEntityRenderGuard {
    private static int depth = 0;

    private GuiEntityRenderGuard() {
    }

    public static void begin() {
        depth++;
    }

    public static void end() {
        if (depth > 0)
            depth--;
    }

    public static boolean isActive() {
        return depth > 0;
    }

    public static void run(Runnable action) {
        begin();
        try {
            action.run();
        } finally {
            end();
        }
    }
}
