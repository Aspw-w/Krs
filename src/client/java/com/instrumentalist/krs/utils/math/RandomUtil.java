package com.instrumentalist.krs.utils.math;

import java.util.Random;

public final class RandomUtil {

    private static final Random RANDOM = new Random();

    private RandomUtil() {
    }

    public static int nextInt(int startInclusive, int endExclusive) {
        return (endExclusive - startInclusive <= 0) ? startInclusive : startInclusive + RANDOM.nextInt(endExclusive - startInclusive);
    }

    public static double nextDouble(double startInclusive, double endInclusive) {
        return (startInclusive == endInclusive || endInclusive - startInclusive <= 0.0)
                ? startInclusive
                : startInclusive + (endInclusive - startInclusive) * RANDOM.nextDouble();
    }

    public static float nextFloat(float startInclusive, float endInclusive) {
        return (startInclusive == endInclusive || endInclusive - startInclusive <= 0f)
                ? startInclusive
                : startInclusive + (endInclusive - startInclusive) * RANDOM.nextFloat();
    }

    public static String randomString(int length) {
        return random(length, "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
    }

    private static String random(int length, String chars) {
        return random(length, chars.toCharArray());
    }

    private static String random(int length, char[] chars) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            stringBuilder.append(chars[RANDOM.nextInt(chars.length)]);
        }
        return stringBuilder.toString();
    }
}
