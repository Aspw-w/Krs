package com.instrumentalist.krs.utils;

import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;

import java.awt.*;
import java.util.ArrayList;

public class NotificationManager {
    private static final int MAX_NOTIFICATIONS = 32;
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_MESSAGE_LENGTH = 512;
    private static final Object NOTIFICATION_LOCK = new Object();
    private static final ArrayList<Notification> notifications = new ArrayList<>(MAX_NOTIFICATIONS);
    private final ArrayList<NotificationRenderEntry> renderEntries = new ArrayList<>(MAX_NOTIFICATIONS);
    private final ArrayList<NotificationRenderEntry> renderEntryPool = new ArrayList<>(MAX_NOTIFICATIONS);

    public void addNotification(String title, String message) {
        Notification notification = new Notification(
                sanitizeText(title, MAX_TITLE_LENGTH),
                sanitizeText(message, MAX_MESSAGE_LENGTH)
        );
        synchronized (NOTIFICATION_LOCK) {
            while (notifications.size() >= MAX_NOTIFICATIONS)
                notifications.removeFirst();
            notifications.add(notification);
            updateTargetOffsetsLocked();
        }
    }

    public void prepareNotifications() {
        renderEntries.clear();
        synchronized (NOTIFICATION_LOCK) {
            if (notifications.isEmpty()) return;

            long now = System.nanoTime();
            float screenWidth = NanoVGManager.getScaledScreenWidth();
            float screenHeight = NanoVGManager.getScaledScreenHeight();
            float startY = screenHeight - 90;
            boolean anyExpired = false;

            for (int index = 0; index < notifications.size(); ) {
                Notification notification = notifications.get(index);
                notification.update(now);

                if (notification.isExpired()) {
                    notifications.remove(index);
                    anyExpired = true;
                    continue;
                }

                int renderIndex = renderEntries.size();
                while (renderEntryPool.size() <= renderIndex)
                    renderEntryPool.add(new NotificationRenderEntry());

                float yPos = startY - notification.getYOffset();
                NotificationRenderEntry renderEntry = renderEntryPool.get(renderIndex);
                renderEntry.update(notification, screenWidth - 65, yPos);
                renderEntries.add(renderEntry);
                startY -= 34;
                index++;
            }

            if (anyExpired)
                updateTargetOffsetsLocked();
        }
    }

    public void clear() {
        synchronized (NOTIFICATION_LOCK) {
            notifications.clear();
        }
        renderEntries.clear();
    }

    public void renderNotificationEffects(NVGU vg) {
        for (NotificationRenderEntry entry : renderEntries) {
            entry.notification.renderEffects(vg, entry.x, entry.y);
        }
    }

    public void renderNotificationBodies(NVGU vg) {
        for (NotificationRenderEntry entry : renderEntries) {
            entry.notification.renderBody(vg, entry.x, entry.y);
        }
    }

    public void renderNotifications(NVGU vg) {
        prepareNotifications();
        if (renderEntries.isEmpty()) return;

        vg.beginEffectBatch();
        renderNotificationEffects(vg);
        vg.flushEffectBatch();
        renderNotificationBodies(vg);
    }

    private static void updateTargetOffsetsLocked() {
        float offset = 0;
        for (Notification notification : notifications) {
            notification.setTargetYOffset(offset);
            offset += 34;
        }
    }

    private static String sanitizeText(String value, int maxLength) {
        if (value == null || value.isEmpty())
            return "";
        if (value.length() <= maxLength)
            return value;

        int end = maxLength;
        if (Character.isHighSurrogate(value.charAt(end - 1)))
            end--;
        return value.substring(0, end);
    }

    private static class NotificationRenderEntry {
        Notification notification;
        float x;
        float y;

        void update(Notification notification, float x, float y) {
            this.notification = notification;
            this.x = x;
            this.y = y;
        }
    }
}

class Notification {
    private final String title;
    private final String message;
    private final long startTimeNanos;
    private float fadeProgress = 0.0f;
    private float yOffset = 0;
    private float targetYOffset = 0;
    private long lastUpdateNanos;
    private long elapsedMillis;
    private float cachedWidth = -1f;

    public Notification(String title, String message) {
        this.title = title;
        this.message = message;
        this.startTimeNanos = System.nanoTime();
        this.lastUpdateNanos = this.startTimeNanos;
    }

    private static Color alphaColor(int red, int green, int blue, int alpha) {
        return new Color(red, green, blue, Math.min(255, Math.max(0, alpha)));
    }

    public void update(long nowNanos) {
        long deltaNanos = nowNanos - lastUpdateNanos;
        float deltaTime = deltaNanos <= 0L ? 0.0f : deltaNanos / 1_000_000.0f;
        lastUpdateNanos = nowNanos;

        long ageNanos = nowNanos - startTimeNanos;
        elapsedMillis = ageNanos <= 0L ? 0L : ageNanos / 1_000_000L;

        if (elapsedMillis < 1200) {
            fadeProgress = Math.min(1.0f, fadeProgress + 0.006f * deltaTime);
        } else if (elapsedMillis > 6000) {
            fadeProgress = Math.max(0.0f, fadeProgress - 0.006f * deltaTime);
        }

        float smoothing = Math.min(1.0f, 0.03f * deltaTime);
        yOffset += (targetYOffset - yOffset) * smoothing;
    }

    public void setTargetYOffset(float targetYOffset) {
        this.targetYOffset = targetYOffset;
    }

    public float getYOffset() {
        return yOffset;
    }

    public boolean isExpired() {
        return fadeProgress == 0.0f && elapsedMillis > 6000;
    }

    public void renderEffects(NVGU vg, float x, float y) {
        float preferWidth = getPreferredWidth();
        float width = 28f + preferWidth;

        NanoVGTheme.renderPanelEffects(vg, x - preferWidth, y - 120, width, 58, 8f, fadeProgress);
    }

    public void renderBody(NVGU vg, float x, float y) {
        int textAlpha = Math.min(255, (int) (fadeProgress * 255));
        Color textColor = alphaColor(255, 255, 255, textAlpha);

        float preferWidth = getPreferredWidth();
        float width = 28f + preferWidth;

        NanoVGTheme.renderPanel(vg, x - preferWidth, y - 120, width, 58, 8f, fadeProgress);

        if (fadeProgress > 0.1) {
            NVGFonts.INTER_MEDIUM.drawText(title, x + 14 - preferWidth, y - 114, 24f, textColor, Alignment.LEFT_TOP, true);
            NVGFonts.INTER.drawText(message, x + 14 - preferWidth, y - 88, 19f, textColor, Alignment.LEFT_TOP, true);

            float progress = Math.max(0, Math.min(1, (6000 - elapsedMillis) / 6000f));
            float barWidth = progress * width - 8f;
            if (barWidth > 0) {
                Color faded = Interface.getFadedColor(0, 1);
                Color barColor = new Color(faded.getRed(), faded.getGreen(), faded.getBlue(), textAlpha);
                vg.roundedRectangle(x - preferWidth + 3.5f, y - 62, barWidth, 4f, 2f, barColor);
            }
        }
    }

    private float getPreferredWidth() {
        if (cachedWidth < 0f) {
            float messageWidth = NVGFonts.INTER.getWidth(message, 19f);
            float titleWidth = NVGFonts.INTER_MEDIUM.getWidth(title, 24f);
            cachedWidth = Math.max(messageWidth, titleWidth);
        }

        return cachedWidth;
    }
}
