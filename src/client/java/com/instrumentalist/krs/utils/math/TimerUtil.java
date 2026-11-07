package com.instrumentalist.krs.utils.math;

public class TimerUtil {

    public static float timerSpeed = 0f;

    public static void reset() {
        timerSpeed = 0f;
    }

    public static boolean isActive() {
        return timerSpeed > 0f && timerSpeed != 1f;
    }

    public static float realTickStep() {
        return isActive() ? 1f / timerSpeed : 1f;
    }
}
