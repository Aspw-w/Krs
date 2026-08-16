package com.instrumentalist.krs.utils;

import com.instrumentalist.krs.utils.nanovg.MaterialIcon;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.network.IConnection;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.NVGFont;

import java.awt.Color;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ConnectionDetailsPanel {

    private static ConnectionSnapshot snapshot;

    private ConnectionDetailsPanel() {
    }

    public static void captureTarget(ServerAddress serverAddress, ServerData serverData, TransferState transferState) {
        long now = System.currentTimeMillis();
        snapshot = new ConnectionSnapshot(
                serverAddress,
                serverData,
                transferState,
                now,
                now,
                null,
                null,
                false,
                "Resolving",
                "Unknown",
                0f,
                0f,
                false
        );
    }

    public static void captureConnection(Connection connection, Component status, boolean aborted) {
        ConnectionSnapshot current = snapshot;
        if (current == null)
            return;

        snapshot = current.withConnection(connection, status, aborted);
    }

    public static void captureConnected(Connection connection, ServerData serverData) {
        ConnectionSnapshot current = snapshot;
        if (current == null) {
            long now = System.currentTimeMillis();
            snapshot = new ConnectionSnapshot(
                    serverAddressFromData(serverData),
                    serverData,
                    null,
                    now,
                    now,
                    Component.literal("Connected"),
                    connection == null ? null : connection.getRemoteAddress(),
                    false,
                    "Connected",
                    connection == null ? "Unknown" : formatSocketAddress(connection.getRemoteAddress()),
                    connection == null ? 0f : connection.getAverageReceivedPackets(),
                    connection == null ? 0f : connection.getAverageSentPackets(),
                    connection != null && ((IConnection) connection).krs$isEncrypted()
            );
            return;
        }

        if (serverData != null && current.serverData == null)
            current = current.withServerData(serverData, serverAddressFromData(serverData));

        snapshot = current.withConnection(connection, Component.literal("Connected"), false);
    }

    public static void renderConnecting(NVGU vg, boolean hideAddress) {
        ConnectionSnapshot current = snapshot;
        if (current == null)
            return;

        render(vg, current, "Connecting to server", hideAddress, true, new Color(74, 189, 255, 230));
    }

    public static void renderConnected(NVGU vg, boolean hideAddress) {
        ConnectionSnapshot current = snapshot;
        if (current == null)
            return;

        render(vg, current, "Connected to server", hideAddress, false, new Color(79, 220, 138, 230));
    }

    public static void renderDisconnected(NVGU vg, boolean hideAddress, Component reason) {
        ConnectionSnapshot current = snapshot;
        if (current == null)
            return;

        ConnectionSnapshot disconnectedSnapshot = current.withMessage(reason, "Disconnected");
        render(vg, disconnectedSnapshot, "Disconnected from server", hideAddress, false, new Color(255, 110, 98, 230));
    }

    public static boolean hasSnapshot() {
        return snapshot != null;
    }

    private static void render(NVGU vg, ConnectionSnapshot current, String title, boolean hideAddress, boolean animatedProgress, Color accentColor) {
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        float panelWidth = Math.min(620f, Math.max(340f, screenWidth - 32f));
        boolean twoColumns = panelWidth >= 500f && screenHeight >= 420f;
        float panelHeight = screenHeight < 420f ? 154f : twoColumns ? 260f : 276f;
        float panelX = (screenWidth - panelWidth) / 2f;
        float panelY = Math.max(3f, Math.min(5f, screenHeight - panelHeight - 3f));

        List<ConnectionInfoRow> rows = buildConnectionRows(current, hideAddress);

        vg.beginEffectBatch();
        NanoVGTheme.renderPanelEffects(vg, panelX, panelY, panelWidth, panelHeight, 8f, 1f);
        vg.flushEffectBatch();

        NanoVGTheme.renderPanel(vg, panelX, panelY, panelWidth, panelHeight, 8f, 1f);

        float elapsedSeconds = current.elapsedMs() / 1000f;
        float pulse = (float) ((Math.sin(elapsedSeconds * 3.4f) + 1.0f) * 0.5f);
        Color pulseColor = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int) (120f + pulse * 90f));
        vg.circle(panelX + 24f, panelY + 28f, 5f + pulse * 1.4f, pulseColor);
        vg.circle(panelX + 24f, panelY + 28f, 3.2f, accentColor);

        NVGFonts.INTER_MEDIUM.drawText(title, panelX + 38f, panelY + 18f, 18f, new Color(255, 255, 255, 245), Alignment.LEFT_TOP, true);

        boolean disconnectedScreen = Objects.equals(title, "Disconnected from server");
        NVGFonts.INTER.drawText(disconnectedScreen && (current.message.toString().toLowerCase().contains("banned") || current.message.toString().toLowerCase().contains("blocked")) ? "You got banned? Quit cheating now!" : disconnectedScreen ? "Something went wrong" : componentToString(current.message, "Waiting for server"), panelX + 38f, panelY + 41f, 12f, new Color(155, 166, 182, 220), Alignment.LEFT_TOP, true);

        float progressWidth = panelWidth - 36f;
        float progressX = panelX + 18f;
        float progressY = panelY + 68f;
        float markerWidth = Math.max(48f, progressWidth * 0.18f);
        float markerX = animatedProgress ? progressX + ((elapsedSeconds * 56f) % (progressWidth + markerWidth)) - markerWidth : progressX;
        float drawMarkerWidth = animatedProgress ? markerWidth : progressWidth;
        vg.roundedRectangle(progressX, progressY, progressWidth, 3f, 2f, new Color(255, 255, 255, 30));
        vg.pushScissor(progressX, progressY - 2f, progressWidth, 7f);
        vg.roundedRectangle(markerX, progressY, drawMarkerWidth, 3f, 2f, accentColor);
        vg.popScissor();

        float contentTop = panelY + 88f;
        float rowHeight = screenHeight < 420f ? 20f : 22f;
        int rowsPerColumn = Math.max(3, (int) ((panelY + panelHeight - contentTop - 12f) / rowHeight));
        int maxRows = Math.min(rows.size(), twoColumns ? rowsPerColumn * 2 : rowsPerColumn);
        float firstColumnX = panelX + 22f;
        float columnGap = 18f;
        float columnWidth = twoColumns ? (panelWidth - 44f - columnGap) / 2f : panelWidth - 44f;

        for (int i = 0; i < maxRows; i++) {
            ConnectionInfoRow row = rows.get(i);
            int column = twoColumns ? i / rowsPerColumn : 0;
            int rowIndex = twoColumns ? i % rowsPerColumn : i;
            float x = firstColumnX + column * (columnWidth + columnGap);
            float y = contentTop + rowIndex * rowHeight;
            float valueX = x + Math.min(124f, columnWidth * 0.42f);
            float maxValueWidth = Math.max(44f, columnWidth - (valueX - x));
            drawIcon(row.icon, x, y - 2f, row.accent ? accentColor : new Color(155, 166, 182, 220));
            NVGFonts.INTER_MEDIUM.drawText(row.label, x + 22f, y, 12f, new Color(155, 166, 182, 220), Alignment.LEFT_TOP, true);
            NVGFonts.INTER.drawText(fitText(row.value, NVGFonts.INTER, 12f, maxValueWidth), valueX, y, 12f, row.accent ? accentColor : new Color(222, 228, 238, 232), Alignment.LEFT_TOP, true);
        }
    }

    private static List<ConnectionInfoRow> buildConnectionRows(ConnectionSnapshot current, boolean hideAddress) {
        List<ConnectionInfoRow> rows = new ArrayList<>(10);
        rows.add(new ConnectionInfoRow(MaterialIcon.CLOCK, "Elapsed", String.format(Locale.ROOT, "%.1fs", current.elapsedMs() / 1000f), false));

        if (current.serverData != null)
            rows.add(new ConnectionInfoRow(MaterialIcon.LAN, "Address", hideAddress ? "Hidden" : blankToUnknown(current.serverData.ip), false));

        if (current.serverAddress != null)
            rows.add(new ConnectionInfoRow(MaterialIcon.TUNE, "Port", hideAddress ? "Hidden" : Integer.toString(current.serverAddress.getPort()), false));

        rows.add(new ConnectionInfoRow(MaterialIcon.OPEN_IN_BROWSER, "Remote", hideAddress ? "Hidden" : blankToUnknown(current.remoteAddress), false));
        rows.add(new ConnectionInfoRow(MaterialIcon.SIGNAL, "Packets", String.format(Locale.ROOT, "%.1f in / %.1f out", current.averageReceivedPackets, current.averageSentPackets), false));

        if (current.serverData != null) {
            rows.add(new ConnectionInfoRow(MaterialIcon.READER, "Version", componentToString(current.serverData.version, "Unknown"), false));
            rows.add(new ConnectionInfoRow(MaterialIcon.KEY, "Encrypted", current.encrypted ? "Yes" : "No", false));
            rows.add(new ConnectionInfoRow(MaterialIcon.PERSON, "Players", formatPlayers(current.serverData), false));
            rows.add(new ConnectionInfoRow(MaterialIcon.PUBLIC, "Type", formatEnumName(current.serverData.type()), false));
            rows.add(new ConnectionInfoRow(MaterialIcon.SEARCH, "List state", formatEnumName(current.serverData.state()), false));
            rows.add(new ConnectionInfoRow(MaterialIcon.SIGNAL, "Last ping", formatPing(current.serverData.ping), false));
        } else {
            rows.add(new ConnectionInfoRow(MaterialIcon.KEY, "Encrypted", current.encrypted ? "Yes" : "No", false));
        }

        rows.add(new ConnectionInfoRow(MaterialIcon.RELOAD, "Transfer", current.transferState == null ? "None" : "Present", false));
        return rows;
    }

    private static void drawIcon(String icon, float x, float y, Color color) {
        NVGFonts.ICON.drawText(icon, x, y, 15f, color, Alignment.LEFT_TOP, true);
    }

    private static String connectionState(Connection connection, boolean aborted) {
        if (aborted)
            return "Aborted";

        if (connection == null)
            return "Resolving";

        if (connection.isConnected())
            return "Connected";

        if (connection.isConnecting())
            return "Connecting";

        return "Waiting";
    }

    private static String formatSocketAddress(SocketAddress address) {
        if (address == null)
            return "Unknown";

        return address.toString();
    }

    private static ServerAddress serverAddressFromData(ServerData serverData) {
        if (serverData == null || serverData.ip == null || serverData.ip.isBlank())
            return null;

        return ServerAddress.parseString(serverData.ip);
    }

    private static String formatPing(long ping) {
        if (ping < 0L)
            return "Unknown";

        return ping + " ms";
    }

    private static String formatPlayers(ServerData serverData) {
        if (serverData == null || serverData.players == null)
            return "Unknown";

        return serverData.players.online() + " / " + serverData.players.max();
    }

    private static String componentToString(Component component, String fallback) {
        if (component == null)
            return fallback;

        String value = component.getString();
        return blankToFallback(value, fallback);
    }

    private static String blankToUnknown(String value) {
        return blankToFallback(value, "Unknown");
    }

    private static String blankToFallback(String value, String fallback) {
        if (value == null || value.isBlank())
            return fallback;

        return value;
    }

    private static String formatEnumName(Enum<?> value) {
        if (value == null)
            return "Unknown";

        String name = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder builder = new StringBuilder(name.length());
        boolean capitalize = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isWhitespace(c)) {
                builder.append(c);
                capitalize = true;
                continue;
            }

            builder.append(capitalize ? Character.toUpperCase(c) : c);
            capitalize = false;
        }

        return builder.toString();
    }

    private static String fitText(String text, NVGFont font, float size, float maxWidth) {
        if (text == null)
            return "";

        if (font.getWidth(text, size) <= maxWidth)
            return text;

        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && font.getWidth(text.substring(0, end) + ellipsis, size) > maxWidth) {
            end--;
        }

        return end <= 0 ? ellipsis : text.substring(0, end) + ellipsis;
    }

    private record ConnectionInfoRow(String icon, String label, String value, boolean accent) {
    }

    private record ConnectionSnapshot(
            ServerAddress serverAddress,
            ServerData serverData,
            TransferState transferState,
            long startedAtMs,
            long updatedAtMs,
            Component message,
            SocketAddress remoteSocketAddress,
            boolean aborted,
            String connectionState,
            String remoteAddress,
            float averageReceivedPackets,
            float averageSentPackets,
            boolean encrypted
    ) {
        private long elapsedMs() {
            long endMs = Math.max(updatedAtMs, startedAtMs);
            return Math.max(0L, endMs - startedAtMs);
        }

        private ConnectionSnapshot withConnection(Connection connection, Component status, boolean aborted) {
            SocketAddress socketAddress = connection == null ? remoteSocketAddress : connection.getRemoteAddress();
            return new ConnectionSnapshot(
                    serverAddress,
                    serverData,
                    transferState,
                    startedAtMs,
                    System.currentTimeMillis(),
                    status,
                    socketAddress,
                    aborted,
                    ConnectionDetailsPanel.connectionState(connection, aborted),
                    connection == null ? remoteAddress : formatSocketAddress(socketAddress),
                    connection == null ? averageReceivedPackets : connection.getAverageReceivedPackets(),
                    connection == null ? averageSentPackets : connection.getAverageSentPackets(),
                    connection != null && ((IConnection) connection).krs$isEncrypted()
            );
        }

        private ConnectionSnapshot withMessage(Component status, String fallbackState) {
            return new ConnectionSnapshot(
                    serverAddress,
                    serverData,
                    transferState,
                    startedAtMs,
                    updatedAtMs,
                    status == null ? message : status,
                    remoteSocketAddress,
                    aborted,
                    fallbackState,
                    remoteAddress,
                    averageReceivedPackets,
                    averageSentPackets,
                    encrypted
            );
        }

        private ConnectionSnapshot withServerData(ServerData updatedServerData, ServerAddress updatedServerAddress) {
            return new ConnectionSnapshot(
                    updatedServerAddress == null ? serverAddress : updatedServerAddress,
                    updatedServerData,
                    transferState,
                    startedAtMs,
                    updatedAtMs,
                    message,
                    remoteSocketAddress,
                    aborted,
                    connectionState,
                    remoteAddress,
                    averageReceivedPackets,
                    averageSentPackets,
                    encrypted
            );
        }
    }
}
