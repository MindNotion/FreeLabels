package com.freelabels;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.oned.EAN13Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Mirrors the JS helper:
 *   function autoBarcodeType(value){
 *     if (/^\d{12,13}$/.test(value)) return 'EAN13';
 *     if (/^[0-9A-Fa-f]{24}$/.test(value)) return 'EPC';
 *     return 'CODE128';
 *   }
 * plus rendering via JsBarcode / QRCode.js equivalents (ZXing here).
 */
public final class BarcodeUtil {

    public enum Type { EAN13, EPC, CODE128 }

    private BarcodeUtil() {}

    public static Type autoBarcodeType(String value) {
        if (value == null) return Type.CODE128;
        String v = value.trim();
        if (v.matches("\\d{12,13}")) return Type.EAN13;
        if (v.matches("[0-9A-Fa-f]{24}")) return Type.EPC;
        return Type.CODE128;
    }

    /** Renders a 1D barcode (EAN13 or Code128) to a BufferedImage sized widthPx x heightPx. */
    public static BufferedImage renderBarcode(String value, Type type, int widthPx, int heightPx) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            BitMatrix matrix;
            if (type == Type.EAN13) {
                String padded = padEnd(value, 12, '0').substring(0, 12);
                matrix = new EAN13Writer().encode(padded, BarcodeFormat.EAN_13, widthPx, heightPx);
            } else {
                // CODE128 also used for EPC (JsBarcode falls back to code128 for EPC values too)
                matrix = new Code128Writer().encode(value, BarcodeFormat.CODE_128, widthPx, heightPx);
            }
            return toImage(matrix);
        } catch (Exception e) {
            // Covers WriterException (checked) and IllegalArgumentException (e.g. bad EAN-13 checksum input)
            return null;
        }
    }

    /** Renders a QR code to a BufferedImage sized sizePx x sizePx (medium error correction, like QRCode.js CorrectLevel.M). */
    public static BufferedImage renderQr(String text, int sizePx) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            return toImage(matrix);
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage toImage(BitMatrix matrix) {
        int w = matrix.getWidth(), h = matrix.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        return img;
    }

    private static String padEnd(String s, int len, char c) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < len) sb.append(c);
        return sb.toString();
    }
}
