package com.freelabels;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;

/**
 * Saves/loads a complete label template — page size, grid layout, label
 * background (solid color or image), and an optional icon/logo image — to
 * a single self-contained ".fltemplate" text file. Images are embedded as
 * base64-encoded PNG data so the template file can be shared or reopened
 * without depending on the original image files still being on disk.
 *
 * File format (simple "key=value" lines, one per setting):
 *   FreeLabelsTemplate v1
 *   pageWmm=210.0
 *   pageHmm=297.0
 *   rows=8
 *   cols=3
 *   marginTop=10.0
 *   marginLeft=10.0
 *   gapX=2.0
 *   gapY=2.0
 *   backgroundMode=COLOR
 *   backgroundColorRGB=-1
 *   backgroundImageBase64=iVBORw0KG...
 *   iconImageBase64=iVBORw0KG...
 */
public final class TemplateManager {

    private static final String HEADER = "FreeLabelsTemplate v1";

    private TemplateManager() {}

    public static void save(File file, LayoutState state) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        sb.append("pageWmm=").append(state.pageWmm).append('\n');
        sb.append("pageHmm=").append(state.pageHmm).append('\n');
        sb.append("rows=").append(state.rows).append('\n');
        sb.append("cols=").append(state.cols).append('\n');
        sb.append("marginTop=").append(state.marginTop).append('\n');
        sb.append("marginLeft=").append(state.marginLeft).append('\n');
        sb.append("gapX=").append(state.gapX).append('\n');
        sb.append("gapY=").append(state.gapY).append('\n');
        sb.append("backgroundMode=").append(state.backgroundMode.name()).append('\n');
        if (state.backgroundColor != null) {
            sb.append("backgroundColorRGB=").append(state.backgroundColor.getRGB()).append('\n');
        }
        if (state.labelBackgroundImage != null) {
            sb.append("backgroundImageBase64=").append(toBase64(state.labelBackgroundImage)).append('\n');
        }
        if (state.labelIconImage != null) {
            sb.append("iconImageBase64=").append(toBase64(state.labelIconImage)).append('\n');
        }
        Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Applies the saved settings onto the given (already-constructed) LayoutState. */
    public static void load(File file, LayoutState state) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).startsWith("FreeLabelsTemplate")) {
            throw new IOException("Not a valid FreeLabels template file: " + file.getName());
        }

        // Reset background/icon before applying, so a template that omits them clears any previous state.
        state.backgroundMode = LayoutState.BackgroundMode.NONE;
        state.backgroundColor = null;
        state.labelBackgroundImage = null;
        state.labelIconImage = null;

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq);
            String value = line.substring(eq + 1);
            try {
                switch (key) {
                    case "pageWmm": state.pageWmm = Double.parseDouble(value); break;
                    case "pageHmm": state.pageHmm = Double.parseDouble(value); break;
                    case "rows": state.rows = Integer.parseInt(value); break;
                    case "cols": state.cols = Integer.parseInt(value); break;
                    case "marginTop": state.marginTop = Double.parseDouble(value); break;
                    case "marginLeft": state.marginLeft = Double.parseDouble(value); break;
                    case "gapX": state.gapX = Double.parseDouble(value); break;
                    case "gapY": state.gapY = Double.parseDouble(value); break;
                    case "backgroundMode": state.backgroundMode = LayoutState.BackgroundMode.valueOf(value); break;
                    case "backgroundColorRGB": state.backgroundColor = new Color(Integer.parseInt(value), true); break;
                    case "backgroundImageBase64": state.labelBackgroundImage = fromBase64(value); break;
                    case "iconImageBase64": state.labelIconImage = fromBase64(value); break;
                    default: /* ignore unknown keys, forward-compatible */ break;
                }
            } catch (IllegalArgumentException ex) {
                throw new IOException("Malformed template line: " + line, ex);
            }
        }
    }

    private static String toBase64(BufferedImage img) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    private static BufferedImage fromBase64(String b64) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(b64);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) throw new IOException("Could not decode embedded template image.");
        return img;
    }
}
