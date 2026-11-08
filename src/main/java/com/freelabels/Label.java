package com.freelabels;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain data holder for a single label's fields.
 * Mirrors the JS object used in camel.php:
 * { name, sku, price, barcode, qr, date, gst, hsn, brand }
 */
public class Label {
    public String name = "";
    public String sku = "";
    public String price = "";
    public String barcode = "";
    public String qr = "";
    public String date = "";
    public String gst = "";
    public String hsn = "";
    public String brand = "";
    /** Ordered user-defined rows imported from arbitrary spreadsheet columns. */
    public final List<CustomField> customFields = new ArrayList<>();
    public static final class CustomField {
        public enum Type { TEXT, BARCODE, QR }
        public final String value; public final Type type;
        public CustomField(String value, Type type) { this.value=value==null?"":value; this.type=type; }
    }

    public Label() {}

    public Label(String name, String sku, String price, String barcode, String qr,
                 String date, String gst, String hsn, String brand) {
        this.name = nz(name);
        this.sku = nz(sku);
        this.price = nz(price);
        this.barcode = nz(barcode);
        this.qr = nz(qr);
        this.date = nz(date);
        this.gst = nz(gst);
        this.hsn = nz(hsn);
        this.brand = nz(brand);
    }

    /** Deep-ish copy (all fields are immutable Strings). */
    public Label copy() {
        Label copy = new Label(name, sku, price, barcode, qr, date, gst, hsn, brand);
        copy.customFields.addAll(customFields); return copy;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public boolean hasAnyTextField() {
        return !name.isEmpty() || !sku.isEmpty() || !price.isEmpty()
                || !brand.isEmpty() || !hsn.isEmpty() || !gst.isEmpty() || !date.isEmpty();
    }

    /** Minimal JSON-escaping serialization, used by "Download JSON". */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("  {\n");
        sb.append("    \"name\": ").append(esc(name)).append(",\n");
        sb.append("    \"sku\": ").append(esc(sku)).append(",\n");
        sb.append("    \"price\": ").append(esc(price)).append(",\n");
        sb.append("    \"barcode\": ").append(esc(barcode)).append(",\n");
        sb.append("    \"qr\": ").append(esc(qr)).append(",\n");
        sb.append("    \"date\": ").append(esc(date)).append(",\n");
        sb.append("    \"gst\": ").append(esc(gst)).append(",\n");
        sb.append("    \"hsn\": ").append(esc(hsn)).append(",\n");
        sb.append("    \"brand\": ").append(esc(brand)).append("\n");
        sb.append("  }");
        return sb.toString();
    }

    private static String esc(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
