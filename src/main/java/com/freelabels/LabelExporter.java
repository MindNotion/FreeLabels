package com.freelabels;

import java.util.List;

/**
 * Mirrors buildZPL() / buildTSPL() from camel.php: paginates the label list
 * over rows x cols pages and emits printer-language commands with a QR
 * (left) followed by a text block and a barcode.
 */
public final class LabelExporter {

    private LabelExporter() {}

    private static final double DPMM = 8; // 203dpi thermal printers -> ~8 dots/mm

    public static String buildZPL(LayoutState state, List<Label> labels) {
        int rows = state.rows, cols = state.cols;
        double left = state.marginLeft, top = state.marginTop;
        double gapX = state.gapX, gapY = state.gapY;
        double labelWmm = (state.pageWmm - left * 2 - gapX * (cols - 1)) / cols;
        double labelHmm = (state.pageHmm - top * 2 - gapY * (rows - 1)) / rows;

        int perPage = rows * cols;
        int totalPages = Math.max(1, (int) Math.ceil(labels.size() / (double) perPage));

        StringBuilder out = new StringBuilder();
        for (int p = 0; p < totalPages; p++) {
            int base = p * perPage;
            out.append("^XA\n");
            out.append(String.format("^PW%d\n", Math.round(labelWmm * cols * DPMM)));

            int idxInPage = 0;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int li = base + (idxInPage++);
                    if (li >= labels.size()) continue;
                    Label l = labels.get(li);

                    int x = (int) Math.round((left + c * (labelWmm + gapX)) * DPMM);
                    int y = (int) Math.round((top + r * (labelHmm + gapY)) * DPMM);

                    int qrWidth = 0;
                    if (!isEmpty(l.qr)) {
                        out.append(String.format("^FO%d,%d^BQN,2,5^FDLA,%s^FS%n", x, y, l.qr));
                        qrWidth = 160;
                    }

                    int tx = x + qrWidth + 10;
                    int ty = y;
                    if (!l.customFields.isEmpty()) {
                        for (Label.CustomField field : l.customFields) {
                            if (field.value.isEmpty()) continue;
                            if (field.type == Label.CustomField.Type.QR) {
                                out.append(String.format("^FO%d,%d^BQN,2,4^FDLA,%s^FS%n", tx, ty, field.value));
                                ty += 120;
                            } else if (field.type == Label.CustomField.Type.BARCODE) {
                                out.append(String.format("^FO%d,%d^BCN,55,Y,N,N^FD%s^FS%n", tx, ty, field.value));
                                ty += 75;
                            } else {
                                out.append(String.format("^FO%d,%d^A0N,20,20^FD%s^FS%n", tx, ty, zplEscape(field.value)));
                                ty += 25;
                            }
                        }
                    }
                    out.append(String.format("^FO%d,%d^A0N,24,24^FD%s^FS%n", tx, ty, slice(l.brand, 28)));
                    ty += 30;
                    out.append(String.format("^FO%d,%d^A0N,20,20^FD%s^FS%n", tx, ty, slice(l.name, 32)));
                    ty += 28;
                    out.append(String.format("^FO%d,%d^A0N,18,18^FD%s %s^FS%n", tx, ty, l.sku, l.hsn));
                    ty += 24;
                    out.append(String.format("^FO%d,%d^A0N,18,18^FD%s %s^FS%n", tx, ty, l.price, l.gst));
                    ty += 24;
                    out.append(String.format("^FO%d,%d^A0N,18,18^FD%s^FS%n", tx, ty, l.date));
                    ty += 34;

                    if (!isEmpty(l.barcode)) {
                        BarcodeUtil.Type t = BarcodeUtil.autoBarcodeType(l.barcode);
                        if (t == BarcodeUtil.Type.EAN13) {
                            out.append(String.format("^FO%d,%d^BEN,70,Y,N^FD%s^FS%n", tx, ty,
                                    padEnd(l.barcode, 12, '0').substring(0, 12)));
                        } else {
                            out.append(String.format("^FO%d,%d^BCN,70,Y,N,N^FD%s^FS%n", tx, ty, l.barcode));
                            if (t == BarcodeUtil.Type.EPC) {
                                out.append(String.format("^FO%d,%d^A0N,16,16^FDEPC / GID-96^FS%n", tx, ty + 80));
                            }
                        }
                    }
                }
            }
            out.append("^XZ\n");
        }
        return out.toString();
    }

    public static String buildTSPL(LayoutState state, List<Label> labels) {
        int rows = state.rows, cols = state.cols;
        double left = state.marginLeft, top = state.marginTop;
        double gapX = state.gapX, gapY = state.gapY;
        double labelWmm = (state.pageWmm - left * 2 - gapX * (cols - 1)) / cols;
        double labelHmm = (state.pageHmm - top * 2 - gapY * (rows - 1)) / rows;
        int perPage = rows * cols;
        int totalPages = Math.max(1, (int) Math.ceil(labels.size() / (double) perPage));

        StringBuilder out = new StringBuilder();
        for (int p = 0; p < totalPages; p++) {
            int base = p * perPage;
            out.append(String.format("SIZE %.1f mm, %.1f mm%n", labelWmm, labelHmm));
            out.append(String.format("GAP %.1f mm, 0 mm%n", gapY));
            out.append("DIRECTION 1\nCLS\n");

            int idxInPage = 0;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int li = base + (idxInPage++);
                    if (li >= labels.size()) continue;
                    Label l = labels.get(li);

                    double xmm = left + c * (labelWmm + gapX);
                    double ymm = top + r * (labelHmm + gapY);

                    double qrOffset = 0;
                    if (!isEmpty(l.qr)) {
                        out.append(String.format("QRCODE %.1f,%.1f,L,5,A,0,\"%s\"%n", xmm, ymm, l.qr));
                        qrOffset = 20;
                    }

                    double tx = xmm + qrOffset + 2;
                    double ty = ymm;
                    if (!l.customFields.isEmpty()) {
                        for (Label.CustomField field : l.customFields) {
                            if (field.value.isEmpty()) continue;
                            if (field.type == Label.CustomField.Type.QR) {
                                out.append(String.format("QRCODE %.1f,%.1f,L,4,A,0,\"%s\"%n", tx, ty, tsplEscape(field.value)));
                                ty += 22;
                            } else if (field.type == Label.CustomField.Type.BARCODE) {
                                out.append(String.format("BARCODE %.1f,%.1f,\"128\",55,1,0,2,2,\"%s\"%n", tx, ty, tsplEscape(field.value)));
                                ty += 16;
                            } else {
                                out.append(String.format("TEXT %.1f,%.1f,\"0\",0,1,1,\"%s\"%n", tx, ty, tsplEscape(field.value)));
                                ty += 5;
                            }
                        }
                    }
                    out.append(String.format("TEXT %.1f,%.1f,\"0\",0,1,1,\"%s\"%n", tx, ty, slice(l.brand, 30)));
                    ty += 5;
                    out.append(String.format("TEXT %.1f,%.1f,\"0\",0,1,1,\"%s\"%n", tx, ty, slice(l.name, 34)));
                    ty += 5;
                    out.append(String.format("TEXT %.1f,%.1f,\"0\",0,1,1,\"%s  %s\"%n", tx, ty, l.sku, l.hsn));
                    ty += 5;
                    out.append(String.format("TEXT %.1f,%.1f,\"0\",0,1,1,\"%s  %s\"%n", tx, ty, l.price, l.gst));
                    ty += 5;
                    out.append(String.format("TEXT %.1f,%.1f,\"0\",0,1,1,\"%s\"%n", tx, ty, l.date));
                    ty += 20;

                    if (!isEmpty(l.barcode)) {
                        BarcodeUtil.Type t = BarcodeUtil.autoBarcodeType(l.barcode);
                        if (t == BarcodeUtil.Type.EAN13) {
                            out.append(String.format("BARCODE %.1f,%.1f,\"EAN13\",70,1,0,2,2,\"%s\"%n", tx, ty,
                                    padEnd(l.barcode, 12, '0').substring(0, 12)));
                        } else {
                            out.append(String.format("BARCODE %.1f,%.1f,\"128\",70,1,0,2,2,\"%s\"%n", tx, ty, l.barcode));
                            if (t == BarcodeUtil.Type.EPC) {
                                out.append(String.format("TEXT %.1f,%.1f,\"0\",0,1,1,\"EPC / GID-96\"%n", tx, ty + 70));
                            }
                        }
                    }
                }
            }
            out.append("PRINT 1,1\n");
        }
        return out.toString();
    }

    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    private static String slice(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String zplEscape(String s) {
        return s == null ? "" : s.replace("^", " ").replace("~", " ").replace("\r", " ").replace("\n", " ");
    }
    private static String tsplEscape(String s) {
        return s == null ? "" : s.replace("\\", "\\").replace("\"", "\\\"").replace("\r", " ").replace("\n", " ");
    }

    private static String padEnd(String s, int len, char c) {
        StringBuilder sb = new StringBuilder(s == null ? "" : s);
        while (sb.length() < len) sb.append(c);
        return sb.toString();
    }
}
