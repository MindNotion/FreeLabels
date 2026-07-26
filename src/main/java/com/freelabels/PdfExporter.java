package com.freelabels;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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
            PDFont font = PDType1Font.HELVETICA;
            PDFont fontBold = PDType1Font.HELVETICA_BOLD;

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

                            drawLabel(doc, cs, l, bx, byTop, labelW, labelH, font, fontBold, state.labelIconImage);
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
                                   PDFont font, PDFont fontBold, BufferedImage iconImage) throws IOException {
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
        if (!l.brand.isEmpty()) lines.add(l.brand);
        if (!l.name.isEmpty()) lines.add(l.name);
        if (!l.sku.isEmpty() || !l.hsn.isEmpty()) lines.add((l.sku + "  " + l.hsn).trim());
        if (!l.price.isEmpty() || !l.gst.isEmpty()) lines.add((l.price + "  " + l.gst).trim());
        if (!l.date.isEmpty()) lines.add(l.date);

        float barcodeH = hasBarcode ? Math.min(20f, labelH * 0.3f) : 0;
        float fontSize = Math.max(5f, Math.min(9f, (labelH - pad * 2 - barcodeH) / Math.max(1, lines.size()) / 1.6f));

        // IMPORTANT: drawBackground() may have left the fill (non-stroking) color set to the
        // label's background color — text glyphs are painted with that same fill color via
        // showText(), so without resetting it here the text would render in the background
        // color and be invisible against it (or against the previous label's leftover color).
        cs.setNonStrokingColor(0, 0, 0);

        float ty = byTop - pad - fontSize;
        for (int i = 0; i < lines.size(); i++) {
            PDFont f = i == 0 ? fontBold : font;
            String text = clip(f, fontSize, lines.get(i), textW);
            cs.beginText();
            cs.setFont(f, fontSize);
            cs.newLineAtOffset(textX, ty);
            cs.showText(text);
            cs.endText();
            ty -= fontSize * 1.35f;
            if (ty < byTop - labelH + barcodeH) break;
        }

        if (hasBarcode) {
            BarcodeUtil.Type type = BarcodeUtil.autoBarcodeType(l.barcode);
            int bcW = (int) Math.max(30, textW);
            int bcH = (int) Math.max(15, barcodeH);
            BufferedImage bcImg = BarcodeUtil.renderBarcode(l.barcode, type, bcW, bcH);
            if (bcImg != null) {
                PDImageXObject bcX = LosslessFactory.createFromImage(doc, bcImg);
                float barY = byTop - labelH + pad;
                cs.drawImage(bcX, textX, barY, bcW, bcH);
            }
        }

        if (iconImage != null) {
            float iconSize = Math.min(labelW, labelH) * 0.22f;
            float ix = bx + labelW - iconSize - 3;
            float iy = byTop - iconSize - 3;
            PDImageXObject iconX = LosslessFactory.createFromImage(doc, iconImage);
            cs.drawImage(iconX, ix, iy, iconSize, iconSize);
        }
    }

    private static String clip(PDFont font, float size, String text, float maxWidth) throws IOException {
        if (font.getStringWidth(text) / 1000f * size <= maxWidth) return text;
        String t = text;
        while (t.length() > 1) {
            t = t.substring(0, t.length() - 1);
            String candidate = t + "\u2026";
            if (font.getStringWidth(sanitize(candidate)) / 1000f * size <= maxWidth) return sanitize(candidate);
        }
        return sanitize(t);
    }

    /** PDType1Font/Helvetica cannot encode arbitrary unicode; fall back to "..." if needed. */
    private static String sanitize(String s) {
        try {
            PDType1Font.HELVETICA.getStringWidth(s);
            return s;
        } catch (Exception e) {
            return s.replace("\u2026", "...");
        }
    }
}
