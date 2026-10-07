package com.instrumentalist.krs.utils;

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

    private static final float FONT_SIZE = 15f;
    private static final float LINE_HEIGHT = 16f;
    private static final float ROW_HEIGHT = 22f;
    private static final float PAD = 12f;
    private static final float TITLE_STATUS_GAP = 6f;
    private static final float MAX_PANEL_WIDTH = 972f;
    private static final Color PROGRESS_FILL = new Color(255, 255, 255, 230);

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

        render(vg, current, "Connecting to server", hideAddress, true);
    }

    public static void renderConnected(NVGU vg, boolean hideAddress) {
        ConnectionSnapshot current = snapshot;
        if (current == null)
            return;

        render(vg, current, "Connected to server", hideAddress, false);
    }

    public static void renderDisconnected(NVGU vg, boolean hideAddress, Component reason) {
        ConnectionSnapshot current = snapshot;
        if (current == null)
            return;

        ConnectionSnapshot disconnectedSnapshot = current.withMessage(reason, "Disconnected");
        render(vg, disconnectedSnapshot, "Disconnected from server", hideAddress, false);
    }

    public static boolean hasSnapshot() {
        return snapshot != null;
    }

    private static void render(NVGU vg, ConnectionSnapshot current, String title, boolean hideAddress, boolean animatedProgress) {
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        if (!(screenWidth > 1f) || !(screenHeight > 1f))
            return;

        List<ConnectionInfoRow> rows = buildConnectionRows(current, hideAddress);

        boolean disconnectedScreen = Objects.equals(title, "Disconnected from server");
        String statusText = disconnectedScreen && (current.message.toString().toLowerCase().contains("banned") || current.message.toString().toLowerCase().contains("blocked"))
                ? "You got banned? Quit cheating now!"
                : disconnectedScreen ? "Something went wrong"
                : componentToString(current.message, "Waiting for server");

        PanelLayout layout = chooseLayout(rows, title, statusText, screenWidth, screenHeight);
        float panelX = (screenWidth - layout.width) / 2f;
        float panelY = Math.max(8f, Math.min(10f, screenHeight - layout.height - 8f));

        vg.beginEffectBatch();
        NanoVGTheme.renderPanelEffects(vg, panelX, panelY, layout.width, layout.height, NanoVGTheme.RADIUS_HUD, 1f);
        vg.flushEffectBatch();
        NanoVGTheme.renderPanel(vg, panelX, panelY, layout.width, layout.height, NanoVGTheme.RADIUS_HUD, 1f);

        float textX = panelX + PAD;
        drawLines(vg, layout.titleLines, textX, panelY + 6f, NanoVGTheme.TEXT, NVGFonts.INTER_MEDIUM);
        drawLines(vg, layout.statusLines, textX, panelY + 6f + layout.titleLines.size() * LINE_HEIGHT + TITLE_STATUS_GAP, NanoVGTheme.MUTED, NVGFonts.INTER);

        float elapsedSeconds = current.elapsedMs() / 1000f;
        float progressWidth = layout.width - PAD * 2f;
        float progressX = textX;
        float progressY = panelY + layout.progressY;
        float trackHeight = 4f;
        float markerWidth = Math.max(48f, progressWidth * 0.18f);
        float markerX = animatedProgress ? progressX + ((elapsedSeconds * 56f) % (progressWidth + markerWidth)) - markerWidth : progressX;
        float drawMarkerWidth = animatedProgress ? markerWidth : progressWidth;
        if (progressWidth > 0f && trackHeight > 0f) {
            vg.roundedRectangle(progressX, progressY, progressWidth, trackHeight, 2f, NanoVGTheme.base(90));
            vg.pushScissor(progressX, progressY - 2f, progressWidth, trackHeight + 4f);
            if (drawMarkerWidth > 0f)
                vg.roundedRectangle(markerX, progressY, drawMarkerWidth, trackHeight, 2f, PROGRESS_FILL);
            vg.popScissor();
        }

        for (PlacedRow row : layout.rows) {
            float x = textX + row.column * (layout.columnWidth + layout.columnGap);
            float y = panelY + row.y;
            NVGFonts.INTER.drawText(row.label, x, y + 3f, FONT_SIZE, NanoVGTheme.MUTED, Alignment.LEFT_TOP, false);
            float valueY = row.stacked ? y + 3f + LINE_HEIGHT : y + 3f;
            drawLines(vg, row.valueLines, x + row.valueX, valueY, NanoVGTheme.TEXT, NVGFonts.INTER);
        }
    }

    private static PanelLayout chooseLayout(List<ConnectionInfoRow> rows, String title, String status, float screenWidth, float screenHeight) {
        float maxPanelWidth = Math.min(Math.max(240f, screenWidth - 32f), MAX_PANEL_WIDTH);
        float maxPanelHeight = Math.max(80f, screenHeight - 16f);
        float[] widths = {240f, 360f, 480f, 640f, 800f, MAX_PANEL_WIDTH};
        PanelLayout widest = null;
        float lastWidth = -1f;
        for (float requested : widths) {
            float width = Math.min(requested, maxPanelWidth);
            if (Math.abs(width - lastWidth) < 0.5f)
                continue;
            lastWidth = width;

            PanelLayout single = layoutPanel(rows, title, status, width, false);
            widest = single;
            if (!wraps(single) && single.height <= maxPanelHeight)
                return single;
        }

        if (widest != null && widest.height > maxPanelHeight && maxPanelWidth >= 800f && rows.size() > 4) {
            PanelLayout dual = layoutPanel(rows, title, status, maxPanelWidth, true);
            if (dual.height < widest.height)
                return dual;
        }
        return widest;
    }

    private static boolean wraps(PanelLayout layout) {
        if (layout.titleLines.size() > 1 || layout.statusLines.size() > 1)
            return true;

        for (PlacedRow row : layout.rows) {
            if (row.stacked || row.valueLines.size() > 1)
                return true;
        }
        return false;
    }

    private static PanelLayout layoutPanel(List<ConnectionInfoRow> rows, String title, String status, float panelWidth, boolean twoColumns) {
        float columnGap = 6f;
        int columns = twoColumns ? 2 : 1;
        float innerWidth = panelWidth - PAD * 2f;
        float columnWidth = columns == 2 ? (innerWidth - columnGap) / 2f : innerWidth;
        List<String> titleLines = wrapText(title, innerWidth, NVGFonts.INTER_MEDIUM);
        List<String> statusLines = wrapText(status, innerWidth, NVGFonts.INTER);
        float progressY = 6f + titleLines.size() * LINE_HEIGHT + TITLE_STATUS_GAP + statusLines.size() * LINE_HEIGHT + 8f;
        float contentTop = progressY + 14f;

        int split = columns == 2 ? (rows.size() + 1) / 2 : rows.size();
        float[] labelWidths = new float[columns];
        for (int i = 0; i < rows.size(); i++) {
            int column = i < split ? 0 : 1;
            labelWidths[column] = Math.max(labelWidths[column], NVGFonts.INTER.getWidth(rows.get(i).label, FONT_SIZE));
        }

        float[] columnHeight = new float[columns];
        List<PlacedRow> placed = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            int column = i < split ? 0 : 1;
            ConnectionInfoRow row = rows.get(i);
            float valueMaxWidth = columnWidth - labelWidths[column] - 8f;
            boolean stacked = valueMaxWidth < 72f;
            List<String> valueLines = wrapText(row.value, stacked ? columnWidth : valueMaxWidth, NVGFonts.INTER);
            float height = stacked
                    ? 6f + LINE_HEIGHT + valueLines.size() * LINE_HEIGHT
                    : Math.max(ROW_HEIGHT, 6f + valueLines.size() * LINE_HEIGHT);
            placed.add(new PlacedRow(
                    row.label,
                    valueLines,
                    column,
                    contentTop + columnHeight[column],
                    stacked,
                    stacked ? 0f : labelWidths[column] + 8f
            ));
            columnHeight[column] += height;
        }

        float columnsHeight = 0f;
        for (float height : columnHeight)
            columnsHeight = Math.max(columnsHeight, height);

        return new PanelLayout(
                panelWidth,
                contentTop + columnsHeight + 6f,
                columnWidth,
                columnGap,
                titleLines,
                statusLines,
                progressY,
                placed
        );
    }

    private static void drawLines(NVGU vg, List<String> lines, float x, float y, Color color, NVGFont font) {
        for (int i = 0; i < lines.size(); i++)
            font.drawText(lines.get(i), x, y + i * LINE_HEIGHT, FONT_SIZE, color, Alignment.LEFT_TOP, false);
    }

    private static List<String> wrapText(String text, float maxWidth, NVGFont font) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        int index = 0;
        while (index <= normalized.length()) {
            int lineBreak = normalized.indexOf('\n', index);
            if (lineBreak < 0)
                lineBreak = normalized.length();
            wrapParagraph(normalized.substring(index, lineBreak), maxWidth, font, lines);
            if (lineBreak >= normalized.length())
                break;
            index = lineBreak + 1;
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static void wrapParagraph(String paragraph, float maxWidth, NVGFont font, List<String> lines) {
        if (paragraph.isEmpty()) {
            lines.add("");
            return;
        }
        if (maxWidth <= 1f || font.getWidth(paragraph, FONT_SIZE) <= maxWidth) {
            lines.add(paragraph);
            return;
        }

        int start = 0;
        while (start < paragraph.length()) {
            int end = findWrapEnd(paragraph, start, maxWidth, font);
            lines.add(paragraph.substring(start, end).strip());
            start = end;
            while (start < paragraph.length() && Character.isWhitespace(paragraph.charAt(start)))
                start++;
        }
    }

    private static int findWrapEnd(String text, int start, float maxWidth, NVGFont font) {
        int bestFit = Math.min(text.length(), start + 1);
        int lastWhitespace = -1;
        for (int end = start + 1; end <= text.length(); end++) {
            if (font.getWidth(text.substring(start, end), FONT_SIZE) > maxWidth)
                break;

            bestFit = end;
            if (Character.isWhitespace(text.charAt(end - 1)))
                lastWhitespace = end;
        }

        if (bestFit >= text.length())
            return text.length();
        if (lastWhitespace > start)
            return lastWhitespace;
        return bestFit;
    }

    private static List<ConnectionInfoRow> buildConnectionRows(ConnectionSnapshot current, boolean hideAddress) {
        List<ConnectionInfoRow> rows = new ArrayList<>(10);
        rows.add(new ConnectionInfoRow("Elapsed", String.format(Locale.ROOT, "%.1fs", current.elapsedMs() / 1000f)));

        if (current.serverData != null)
            rows.add(new ConnectionInfoRow("Address", hideAddress ? "Hidden" : blankToUnknown(current.serverData.ip)));

        if (current.serverAddress != null)
            rows.add(new ConnectionInfoRow("Port", hideAddress ? "Hidden" : Integer.toString(current.serverAddress.getPort())));

        rows.add(new ConnectionInfoRow("Remote", hideAddress ? "Hidden" : blankToUnknown(current.remoteAddress)));
        rows.add(new ConnectionInfoRow("Packets", String.format(Locale.ROOT, "%.1f in / %.1f out", current.averageReceivedPackets, current.averageSentPackets)));

        if (current.serverData != null) {
            rows.add(new ConnectionInfoRow("Version", componentToString(current.serverData.version, "Unknown")));
            rows.add(new ConnectionInfoRow("Encrypted", current.encrypted ? "Yes" : "No"));
            rows.add(new ConnectionInfoRow("Players", formatPlayers(current.serverData)));
            rows.add(new ConnectionInfoRow("Type", formatEnumName(current.serverData.type())));
            rows.add(new ConnectionInfoRow("List state", formatEnumName(current.serverData.state())));
            rows.add(new ConnectionInfoRow("Last ping", formatPing(current.serverData.ping)));
        } else {
            rows.add(new ConnectionInfoRow("Encrypted", current.encrypted ? "Yes" : "No"));
        }

        rows.add(new ConnectionInfoRow("Transfer", current.transferState == null ? "None" : "Present"));
        return rows;
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

    private record ConnectionInfoRow(String label, String value) {
    }

    private record PlacedRow(String label, List<String> valueLines, int column, float y, boolean stacked, float valueX) {
    }

    private record PanelLayout(
            float width,
            float height,
            float columnWidth,
            float columnGap,
            List<String> titleLines,
            List<String> statusLines,
            float progressY,
            List<PlacedRow> rows
    ) {
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
