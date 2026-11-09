package com.freelabels;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a paginated PDF of the label grid, mirroring the "Generate PDF"
 * button (html2canvas + jsPDF) in camel.php. Each page is sized to the page
 * preset (mm) and labels are placed on a rows x cols grid with the same
 * margins/gaps used by the live preview and printer exports.
 */
public final class PdfExporter {

    private static final float MM_TO_PT = 72f / 25.4f;

    private PdfExporter() {}

    public static void export(File file, LayoutState state, List<Label> labels) throws IOException {
        int rows = Math.max(1, state.rows), cols = Math.max(1, state.cols);
        int perPage = rows * cols;
        int totalPages = Math.max(1, (int) Math.ceil(labels.size() / (double) perPage));

        float pageWpt = (float) state.pageWmm * MM_TO_PT;
        float pageHpt = (float) state.pageHmm * MM_TO_PT;

        try (PDDocument doc = new PDDocument()) {
            // Embedded Unicode TrueType fonts are required for ₹ and other non-WinAnsi
            // characters. Loading from the classpath keeps the exporter portable.
            PDFont font = loadFont(doc, "/fonts/DejaVuSans.ttf");
            PDFont fontBold = loadFont(doc, "/fonts/DejaVuSans-Bold.ttf");

            float left = (float) state.marginLeft * MM_TO_PT;
            float top = (float) state.marginTop * MM_TO_PT;
            float gapX = (float) state.gapX * MM_TO_PT;
            float gapY = (float) state.gapY * MM_TO_PT;
            float labelW = (pageWpt - left * 2 - gapX * (cols - 1)) / cols;
            float labelH = (pageHpt - top * 2 - gapY * (rows - 1)) / rows;

            for (int p = 0; p < totalPages; p++) {
                int base = p * perPage;
                PDPage page = new PDPage(new PDRectangle(pageWpt, pageHpt));
                doc.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    int idxInPage = 0;
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            int li = base + (idxInPage++);
                            if (li >= labels.size()) continue;
                            Label l = labels.get(li);

                            float bx = left + c * (labelW + gapX);
                            // PDF y-axis grows upward; page top starts at pageHpt
                            float byTop = pageHpt - top - r * (labelH + gapY);
                            float byBottom = byTop - labelH;

                            drawBackground(doc, cs, state, bx, byBottom, labelW, labelH);

                            cs.setStrokingColor(200, 200, 200);
                            cs.setLineWidth(0.5f);
                            cs.addRect(bx, byBottom, labelW, labelH);
                            cs.stroke();

                            drawLabel(doc, cs, l, bx, byTop, labelW, labelH, font, fontBold, state.labelIconImage, state.iconPosition);
                        }
                    }
                }
            }
            doc.save(file);
        }
    }

    /** Fills the label box with its configured solid color, or stretches the background image to fit it. */
    private static void drawBackground(PDDocument doc, PDPageContentStream cs, LayoutState state,
                                        float bx, float byBottom, float labelW, float labelH) throws IOException {
        if (state.backgroundMode == LayoutState.BackgroundMode.COLOR && state.backgroundColor != null) {
            java.awt.Color c = state.backgroundColor;
            cs.setNonStrokingColor(c.getRed(), c.getGreen(), c.getBlue());
            cs.addRect(bx, byBottom, labelW, labelH);
            cs.fill();
        } else if (state.backgroundMode == LayoutState.BackgroundMode.IMAGE && state.labelBackgroundImage != null) {
            PDImageXObject bgX = LosslessFactory.createFromImage(doc, state.labelBackgroundImage);
            cs.drawImage(bgX, bx, byBottom, labelW, labelH);
        }
    }

    private static void drawLabel(PDDocument doc, PDPageContentStream cs, Label l,
                                   float bx, float byTop, float labelW, float labelH,
                                   PDFont font, PDFont fontBold, BufferedImage iconImage,
                                   LayoutState.IconPosition iconPosition) throws IOException {
        float pad = 3 * MM_TO_PT / 3f; // small inner padding
        float innerX = bx + pad;
        float innerW = labelW - pad * 2;
        boolean hasQr = l.qr != null && !l.qr.trim().isEmpty();
        boolean hasBarcode = l.barcode != null && !l.barcode.trim().isEmpty();

        float qrSize = 0;
        if (hasQr) {
            qrSize = Math.min(innerW / 3f, labelH - pad * 2);
            BufferedImage qrImg = BarcodeUtil.renderQr(l.qr, Math.max(30, (int) qrSize));
            if (qrImg != null) {
                PDImageXObject qrX = LosslessFactory.createFromImage(doc, qrImg);
                cs.drawImage(qrX, innerX, byTop - pad - qrSize, qrSize, qrSize);
            }
        }

        float textX = innerX + (hasQr ? qrSize + 4 : 0);
        float textW = innerW - (hasQr ? qrSize + 4 : 0);
        if (textW <= 2) return;

        List<String> lines = new ArrayList<>();
        if (!l.customFields.isEmpty()) {
            float cy=byTop-pad-8;
            cs.setNonStrokingColor(0,0,0);
            for (Label.CustomField field:l.customFields) {
                if(field.value.isEmpty()) continue;
                if(field.type==Label.CustomField.Type.TEXT) {
                    List<String> wrapped = wrapText(font, 7f, normalizeText(field.value), innerW);
                    for (String wrappedLine : wrapped) {
                        if (cy < byTop - labelH + pad) break;
                        drawTextLine(cs, font, 7f, wrappedLine, innerX, cy);
                        cy -= 9f;
                    }
                } else {
                    BufferedImage image=field.type==Label.CustomField.Type.QR
                        ? BarcodeUtil.renderQr(field.value,Math.max(24,(int)Math.min(innerW, labelH/4)))
                        : BarcodeUtil.renderBarcode(field.value,BarcodeUtil.autoBarcodeType(field.value),Math.max(40,(int)innerW),18);
                    if (image != null) {
                        PDImageXObject obj = LosslessFactory.createFromImage(doc, image);
                        float iw = Math.min(innerW, field.type == Label.CustomField.Type.QR ? labelH / 4 : innerW);
                        float ih = field.type == Label.CustomField.Type.QR ? iw : 18f;

                        // Keep clear whitespace both before and after each barcode / QR image.
                        float imageGap = 6f;
                        cy -= imageGap;
                        cs.drawImage(obj, innerX, cy - ih, iw, ih);
                        cy -= ih + imageGap;
                    }
                }
                if(cy<byTop-labelH+pad) break;
            }
            lines.clear();
        }
        if (l.customFields.isEmpty()) {
        if (!l.brand.isEmpty()) lines.add(l.brand);
        if (!l.name.isEmpty()) lines.add(l.name);
        if (!l.sku.isEmpty() || !l.hsn.isEmpty()) lines.add((l.sku + "  " + l.hsn).trim());
        if (!l.price.isEmpty() || !l.gst.isEmpty()) lines.add((l.price + "  " + l.gst).trim());
        if (!l.date.isEmpty()) lines.add(l.date);
        }

        // Reserve a separate bottom band for the barcode and a visible gap above it.
        float barcodeGap = hasBarcode ? 6f : 0f;
        float barcodeBottomGap = hasBarcode ? 4f : 0f;
        float barcodeH = hasBarcode ? Math.min(20f, Math.max(15f, labelH * 0.3f)) : 0f;
        float textBottomLimit = byTop - labelH + pad + barcodeBottomGap + barcodeH + barcodeGap;
        float fontSize = Math.max(5f, Math.min(9f,
                (labelH - pad * 2 - barcodeH - barcodeGap - barcodeBottomGap)
                        / Math.max(1, lines.size()) / 1.6f));

        // IMPORTANT: drawBackground() may have left the fill (non-stroking) color set to the
        // label's background color — text glyphs are painted with that same fill color via
        // showText(), so without resetting it here the text would render in the background
        // color and be invisible against it (or against the previous label's leftover color).
        cs.setNonStrokingColor(0, 0, 0);

        float ty = byTop - pad - fontSize;
        for (int i = 0; i < lines.size(); i++) {
            PDFont f = i == 0 ? fontBold : font;
            List<String> wrappedLines = wrapText(f, fontSize, normalizeText(lines.get(i)), textW);
            for (String text : wrappedLines) {
                if (ty < textBottomLimit) break;
                drawTextLine(cs, f, fontSize, text, textX, ty);
                ty -= fontSize * 1.35f;
            }
            if (ty < textBottomLimit) break;
        }

        if (hasBarcode) {
            BarcodeUtil.Type type = BarcodeUtil.autoBarcodeType(l.barcode);
            int bcW = (int) Math.max(30, textW);
            int bcH = (int) Math.max(15, barcodeH);
            BufferedImage bcImg = BarcodeUtil.renderBarcode(l.barcode, type, bcW, bcH);
            if (bcImg != null) {
                PDImageXObject bcX = LosslessFactory.createFromImage(doc, bcImg);
                float barY = byTop - labelH + pad + barcodeBottomGap;
                cs.drawImage(bcX, textX, barY, bcW, bcH);
            }
        }

        if (iconImage != null) {
            float iconSize = Math.min(labelW, labelH) * 0.22f;
            float ix = (iconPosition==LayoutState.IconPosition.TOP_LEFT || iconPosition==LayoutState.IconPosition.BOTTOM_LEFT) ? bx+3 : bx+labelW-iconSize-3;
            float iy = (iconPosition==LayoutState.IconPosition.TOP_LEFT || iconPosition==LayoutState.IconPosition.TOP_RIGHT) ? byTop-iconSize-3 : byTop-labelH+3;
            PDImageXObject iconX = LosslessFactory.createFromImage(doc, iconImage);
            cs.drawImage(iconX, ix, iy, iconSize, iconSize);
        }
    }

    /**
     * Loads and embeds a TrueType font in the PDF. Font resources are bundled
     * in src/main/resources/fonts so generated PDFs do not depend on fonts
     * installed on the host operating system.
     */
    private static PDFont loadFont(PDDocument document, String resourcePath) throws IOException {
        InputStream stream = PdfExporter.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IOException("Required PDF font resource is missing: " + resourcePath);
        }
        try (InputStream in = stream) {
            return PDType0Font.load(document, in);
        }
    }

    /**
     * Draws exactly one logical line. beginText/endText are paired in a
     * finally block so an exception cannot leave the content stream in text mode.
     * Control characters are removed before PDFBox sees the string.
     */
    private static void drawTextLine(PDPageContentStream stream, PDFont font,
                                     float size, String text, float x, float y)
            throws IOException {
        String safe = printableText(text);
        boolean started = false;
        try {
            stream.beginText();
            started = true;
            stream.setFont(font, size);
            stream.newLineAtOffset(x, y);
            stream.showText(replaceUnsupported(font, safe));
        } finally {
            if (started) {
                stream.endText();
            }
        }
    }

    /** Normalize all supported line endings without passing LF/CR to showText(). */
    private static String normalizeText(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String printableText(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\t') {
                out.append("    ");
            } else if (cp == '\n' || cp == '\r') {
                // A logical line must never contain a line separator.
                out.append(' ');
            } else if (!Character.isISOControl(cp)) {
                out.appendCodePoint(cp);
            }
        }
        return out.toString();
    }

    /**
     * DejaVu Sans covers the Rupee sign and common Latin/extended characters.
     * If a particular glyph is absent, replace only that code point with U+FFFD
     * (or '?' if the font does not contain U+FFFD), rather than failing the PDF.
     */
    private static String replaceUnsupported(PDFont font, String value) throws IOException {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            String glyph = new String(Character.toChars(cp));
            i += Character.charCount(cp);
            try {
                font.getStringWidth(glyph);
                out.append(glyph);
            } catch (IllegalArgumentException ex) {
                String replacement = "\uFFFD";
                try {
                    font.getStringWidth(replacement);
                    out.append(replacement);
                } catch (IllegalArgumentException ignored) {
                    out.append('?');
                }
            }
        }
        return out.toString();
    }

    /** Wraps at whitespace; long unbroken tokens are split on Unicode code-point boundaries. */
    private static List<String> wrapText(PDFont font, float size, String value,
                                         float maxWidth) throws IOException {
        List<String> result = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            result.add("");
            return result;
        }
        String[] paragraphs = value.split("\\n", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                result.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (width(font, size, candidate) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (line.length() > 0) {
                    result.add(line.toString());
                    line.setLength(0);
                }
                // Split a single token wider than the available line width safely.
                StringBuilder tokenPart = new StringBuilder();
                for (int i = 0; i < word.length();) {
                    int cp = word.codePointAt(i);
                    String glyph = new String(Character.toChars(cp));
                    i += Character.charCount(cp);
                    if (tokenPart.length() > 0 &&
                            width(font, size, tokenPart.toString() + glyph) > maxWidth) {
                        result.add(tokenPart.toString());
                        tokenPart.setLength(0);
                    }
                    tokenPart.append(glyph);
                }
                line.append(tokenPart);
            }
            if (line.length() > 0) result.add(line.toString());
        }
        return result;
    }

    private static float width(PDFont font, float size, String value) throws IOException {
        return font.getStringWidth(replaceUnsupported(font, printableText(value))) / 1000f * size;
    }
}
