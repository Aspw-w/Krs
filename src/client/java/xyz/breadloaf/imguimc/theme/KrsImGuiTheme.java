package xyz.breadloaf.imguimc.theme;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;

final class KrsImGuiTheme {

    private KrsImGuiTheme() {
    }

    static void applyClassic() {
        ImGuiStyle style = ImGui.getStyle();
        applyLayout(style);
        applyDarkColors(style, 112, 124, 248, 236, 88, 74);
    }

    static void applyDark() {
        ImGuiStyle style = ImGui.getStyle();
        applyLayout(style);
        applyDarkColors(style, 138, 150, 255, 255, 108, 82);
    }

    static void applyLight() {
        ImGuiStyle style = ImGui.getStyle();
        applyLayout(style);

        style.setColor(ImGuiCol.Text, 22, 21, 30, 255);
        style.setColor(ImGuiCol.TextDisabled, 104, 103, 122, 230);
        style.setColor(ImGuiCol.WindowBg, 246, 246, 248, 252);
        style.setColor(ImGuiCol.ChildBg, 255, 255, 255, 245);
        style.setColor(ImGuiCol.PopupBg, 255, 255, 255, 250);
        style.setColor(ImGuiCol.Border, 86, 90, 140, 78);
        style.setColor(ImGuiCol.BorderShadow, 0, 0, 0, 0);
        style.setColor(ImGuiCol.FrameBg, 240, 241, 250, 255);
        style.setColor(ImGuiCol.FrameBgHovered, 224, 227, 255, 255);
        style.setColor(ImGuiCol.FrameBgActive, 208, 213, 255, 255);
        style.setColor(ImGuiCol.TitleBg, 232, 233, 244, 255);
        style.setColor(ImGuiCol.TitleBgActive, 220, 223, 246, 255);
        style.setColor(ImGuiCol.TitleBgCollapsed, 232, 233, 244, 240);
        style.setColor(ImGuiCol.MenuBarBg, 236, 237, 247, 252);
        style.setColor(ImGuiCol.ScrollbarBg, 50, 54, 110, 24);
        style.setColor(ImGuiCol.ScrollbarGrab, 82, 90, 170, 105);
        style.setColor(ImGuiCol.ScrollbarGrabHovered, 88, 100, 232, 150);
        style.setColor(ImGuiCol.ScrollbarGrabActive, 224, 84, 62, 190);
        style.setColor(ImGuiCol.CheckMark, 76, 88, 224, 255);
        style.setColor(ImGuiCol.SliderGrab, 76, 88, 224, 230);
        style.setColor(ImGuiCol.SliderGrabActive, 224, 84, 62, 255);
        style.setColor(ImGuiCol.Button, 224, 227, 255, 255);
        style.setColor(ImGuiCol.ButtonHovered, 208, 213, 255, 255);
        style.setColor(ImGuiCol.ButtonActive, 190, 197, 252, 255);
        style.setColor(ImGuiCol.Header, 234, 236, 252, 255);
        style.setColor(ImGuiCol.HeaderHovered, 218, 222, 255, 255);
        style.setColor(ImGuiCol.HeaderActive, 200, 206, 252, 255);
        style.setColor(ImGuiCol.Separator, 86, 90, 140, 68);
        style.setColor(ImGuiCol.SeparatorHovered, 88, 100, 232, 150);
        style.setColor(ImGuiCol.SeparatorActive, 224, 84, 62, 190);
        style.setColor(ImGuiCol.ResizeGrip, 88, 100, 232, 82);
        style.setColor(ImGuiCol.ResizeGripHovered, 88, 100, 232, 132);
        style.setColor(ImGuiCol.ResizeGripActive, 224, 84, 62, 175);
        style.setColor(ImGuiCol.Tab, 234, 235, 247, 255);
        style.setColor(ImGuiCol.TabHovered, 216, 220, 255, 255);
        style.setColor(ImGuiCol.TabActive, 204, 210, 252, 255);
        style.setColor(ImGuiCol.TabUnfocused, 228, 229, 240, 240);
        style.setColor(ImGuiCol.TabUnfocusedActive, 218, 222, 248, 255);
        style.setColor(ImGuiCol.DockingPreview, 88, 100, 232, 150);
        style.setColor(ImGuiCol.DockingEmptyBg, 236, 237, 247, 230);
        style.setColor(ImGuiCol.PlotLines, 76, 88, 224, 230);
        style.setColor(ImGuiCol.PlotLinesHovered, 224, 84, 62, 255);
        style.setColor(ImGuiCol.PlotHistogram, 76, 88, 224, 220);
        style.setColor(ImGuiCol.PlotHistogramHovered, 224, 84, 62, 250);
        style.setColor(ImGuiCol.TableHeaderBg, 234, 235, 247, 255);
        style.setColor(ImGuiCol.TableBorderStrong, 86, 90, 140, 82);
        style.setColor(ImGuiCol.TableBorderLight, 86, 90, 140, 48);
        style.setColor(ImGuiCol.TableRowBg, 255, 255, 255, 0);
        style.setColor(ImGuiCol.TableRowBgAlt, 50, 54, 120, 16);
        style.setColor(ImGuiCol.TextSelectedBg, 88, 100, 232, 96);
        style.setColor(ImGuiCol.DragDropTarget, 224, 84, 62, 210);
        style.setColor(ImGuiCol.NavHighlight, 88, 100, 232, 170);
        style.setColor(ImGuiCol.NavWindowingHighlight, 255, 255, 255, 180);
        style.setColor(ImGuiCol.NavWindowingDimBg, 20, 19, 30, 72);
        style.setColor(ImGuiCol.ModalWindowDimBg, 20, 19, 30, 120);
    }

    private static void applyLayout(ImGuiStyle style) {
        float scale = getScale();

        style.setAlpha(1.0f);
        style.setDisabledAlpha(0.58f);
        style.setWindowPadding(14f * scale, 11f * scale);
        style.setFramePadding(9f * scale, 6f * scale);
        style.setCellPadding(6f * scale, 4f * scale);
        style.setItemSpacing(9f * scale, 7f * scale);
        style.setItemInnerSpacing(7f * scale, 5f * scale);
        style.setIndentSpacing(18f * scale);
        style.setScrollbarSize(14f * scale);
        style.setGrabMinSize(10f * scale);
        style.setWindowRounding(8f * scale);
        style.setChildRounding(6f * scale);
        style.setFrameRounding(5f * scale);
        style.setPopupRounding(6f * scale);
        style.setScrollbarRounding(7f * scale);
        style.setGrabRounding(5f * scale);
        style.setTabRounding(6f * scale);
        style.setWindowBorderSize(1f * scale);
        style.setChildBorderSize(1f * scale);
        style.setPopupBorderSize(1f * scale);
        style.setFrameBorderSize(1f * scale);
        style.setTabBorderSize(0f);
        style.setWindowTitleAlign(0.5f, 0.5f);
        style.setAntiAliasedLines(true);
        style.setAntiAliasedFill(true);
    }

    private static void applyDarkColors(ImGuiStyle style, int accentRed, int accentGreen, int accentBlue, int glowRed, int glowGreen, int glowBlue) {
        style.setColor(ImGuiCol.Text, 245, 244, 242, 255);
        style.setColor(ImGuiCol.TextDisabled, 159, 157, 176, 215);
        style.setColor(ImGuiCol.WindowBg, 19, 18, 29, 242);
        style.setColor(ImGuiCol.ChildBg, 25, 24, 38, 228);
        style.setColor(ImGuiCol.PopupBg, 22, 21, 34, 248);
        style.setColor(ImGuiCol.Border, 255, 255, 255, 68);
        style.setColor(ImGuiCol.BorderShadow, 0, 0, 0, 0);
        style.setColor(ImGuiCol.FrameBg, 40, 39, 58, 235);
        style.setColor(ImGuiCol.FrameBgHovered, accentRed, accentGreen, accentBlue, 76);
        style.setColor(ImGuiCol.FrameBgActive, glowRed, glowGreen, glowBlue, 104);
        style.setColor(ImGuiCol.TitleBg, 14, 13, 22, 255);
        style.setColor(ImGuiCol.TitleBgActive, 27, 26, 42, 255);
        style.setColor(ImGuiCol.TitleBgCollapsed, 14, 13, 22, 235);
        style.setColor(ImGuiCol.MenuBarBg, 27, 26, 42, 245);
        style.setColor(ImGuiCol.ScrollbarBg, 0, 0, 0, 38);
        style.setColor(ImGuiCol.ScrollbarGrab, 255, 255, 255, 90);
        style.setColor(ImGuiCol.ScrollbarGrabHovered, accentRed, accentGreen, accentBlue, 125);
        style.setColor(ImGuiCol.ScrollbarGrabActive, glowRed, glowGreen, glowBlue, 165);
        style.setColor(ImGuiCol.CheckMark, accentRed, accentGreen, accentBlue, 255);
        style.setColor(ImGuiCol.SliderGrab, accentRed, accentGreen, accentBlue, 225);
        style.setColor(ImGuiCol.SliderGrabActive, glowRed, glowGreen, glowBlue, 255);
        style.setColor(ImGuiCol.Button, accentRed, accentGreen, accentBlue, 78);
        style.setColor(ImGuiCol.ButtonHovered, accentRed, accentGreen, accentBlue, 128);
        style.setColor(ImGuiCol.ButtonActive, glowRed, glowGreen, glowBlue, 158);
        style.setColor(ImGuiCol.Header, accentRed, accentGreen, accentBlue, 42);
        style.setColor(ImGuiCol.HeaderHovered, accentRed, accentGreen, accentBlue, 84);
        style.setColor(ImGuiCol.HeaderActive, glowRed, glowGreen, glowBlue, 118);
        style.setColor(ImGuiCol.Separator, 255, 255, 255, 68);
        style.setColor(ImGuiCol.SeparatorHovered, accentRed, accentGreen, accentBlue, 130);
        style.setColor(ImGuiCol.SeparatorActive, glowRed, glowGreen, glowBlue, 170);
        style.setColor(ImGuiCol.ResizeGrip, accentRed, accentGreen, accentBlue, 60);
        style.setColor(ImGuiCol.ResizeGripHovered, accentRed, accentGreen, accentBlue, 118);
        style.setColor(ImGuiCol.ResizeGripActive, glowRed, glowGreen, glowBlue, 160);
        style.setColor(ImGuiCol.Tab, 32, 31, 48, 245);
        style.setColor(ImGuiCol.TabHovered, accentRed, accentGreen, accentBlue, 130);
        style.setColor(ImGuiCol.TabActive, glowRed, glowGreen, glowBlue, 150);
        style.setColor(ImGuiCol.TabUnfocused, 24, 23, 36, 220);
        style.setColor(ImGuiCol.TabUnfocusedActive, glowRed, glowGreen, glowBlue, 105);
        style.setColor(ImGuiCol.DockingPreview, accentRed, accentGreen, accentBlue, 145);
        style.setColor(ImGuiCol.DockingEmptyBg, 19, 18, 29, 210);
        style.setColor(ImGuiCol.PlotLines, accentRed, accentGreen, accentBlue, 225);
        style.setColor(ImGuiCol.PlotLinesHovered, glowRed, glowGreen, glowBlue, 255);
        style.setColor(ImGuiCol.PlotHistogram, accentRed, accentGreen, accentBlue, 210);
        style.setColor(ImGuiCol.PlotHistogramHovered, glowRed, glowGreen, glowBlue, 245);
        style.setColor(ImGuiCol.TableHeaderBg, 36, 35, 54, 245);
        style.setColor(ImGuiCol.TableBorderStrong, 255, 255, 255, 72);
        style.setColor(ImGuiCol.TableBorderLight, 255, 255, 255, 44);
        style.setColor(ImGuiCol.TableRowBg, 255, 255, 255, 0);
        style.setColor(ImGuiCol.TableRowBgAlt, 255, 255, 255, 24);
        style.setColor(ImGuiCol.TextSelectedBg, accentRed, accentGreen, accentBlue, 76);
        style.setColor(ImGuiCol.DragDropTarget, glowRed, glowGreen, glowBlue, 210);
        style.setColor(ImGuiCol.NavHighlight, accentRed, accentGreen, accentBlue, 165);
        style.setColor(ImGuiCol.NavWindowingHighlight, 255, 255, 255, 180);
        style.setColor(ImGuiCol.NavWindowingDimBg, 0, 0, 0, 80);
        style.setColor(ImGuiCol.ModalWindowDimBg, 0, 0, 0, 110);
    }

    private static float getScale() {
        ImGuiIO io = ImGui.getIO();
        float scale = Math.max(io.getDisplayFramebufferScaleX(), io.getDisplayFramebufferScaleY());

        if (!Float.isFinite(scale) || scale < 1.0f)
            return 1.0f;

        return scale;
    }
}
