package xyz.breadloaf.imguimc.font;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class FontExtractor {

    private static File fontDir;

    public static String getFontPath(String fontNameTtf) {
        if (fontDir == null) {
            fontDir = new File(System.getProperty("java.io.tmpdir"), "krs_imguimc_fonts");
        }
        return new File(fontDir, fontNameTtf).getAbsolutePath();
    }
    public static void extractFont() throws IOException {
        if (fontDir == null) {
            fontDir = new File(System.getProperty("java.io.tmpdir"), "krs_imguimc_fonts");
        }
        File fontFile = new File(fontDir, "arial.ttf");
        if (fontFile.exists() && fontFile.length() > 0) {
            return;
        }

        InputStream in = FontExtractor.class.getClassLoader()
                .getResourceAsStream("assets/krs/arial.ttf");

        if (in == null) {
            in = FontExtractor.class.getResourceAsStream("/assets/krs/arial.ttf");
        }

        if (in == null) {
            throw new IOException("Could not find font resource: assets/krs/arial.ttf");
        }

        try (in) {
            Files.createDirectories(fontDir.toPath());
            Files.copy(in, fontFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}