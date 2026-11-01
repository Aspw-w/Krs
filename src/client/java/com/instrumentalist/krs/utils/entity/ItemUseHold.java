package com.instrumentalist.krs.utils.entity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class ItemUseHold {
    private static final Set<Object> HOLDERS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean releasing;

    private ItemUseHold() {
    }

    public static void hold(Object owner) {
        HOLDERS.add(owner);
    }

    public static void release(Object owner) {
        HOLDERS.remove(owner);
    }

    public static boolean isHeld(Object owner) {
        return HOLDERS.contains(owner);
    }

    public static boolean shouldBlockRelease() {
        return !releasing && !HOLDERS.isEmpty();
    }

    public static void forceRelease(Runnable release) {
        releasing = true;
        try {
            release.run();
        } finally {
            releasing = false;
        }
    }
}
