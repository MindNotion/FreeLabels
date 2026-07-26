package com.freelabels;

import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors the xlsxInput 'change' handler in camel.php:
 * Column A: Name, B: SKU, C: Price, D: Barcode, E: QR/EPC,
 * F: Expiry/Manufacturing, G: GST, H: HSN, I: Brand.
 * Skips a header row if row 0's first cell contains "name".
 */
public final class XlsxImporter {

    private XlsxImporter() {}

    public static List<Label> importFile(File file) throws IOException {
        List<Label> out = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            for (Row row : sheet) {
                if (row == null) continue;
                int idx = row.getRowNum();
                String c0 = cellText(row, 0, fmt);
                if (idx == 0 && c0 != null && c0.toLowerCase().contains("name")) {
                    continue; // header row
                }
                boolean allBlank = true;
                for (int i = 0; i < 9; i++) {
                    if (!isBlank(cellText(row, i, fmt))) { allBlank = false; break; }
                }
                if (allBlank) continue;

                Label l = new Label(
                        cellText(row, 0, fmt), // name
                        cellText(row, 1, fmt), // sku
                        cellText(row, 2, fmt), // price
                        cellText(row, 3, fmt), // barcode
                        cellText(row, 4, fmt), // qr
                        cellText(row, 5, fmt), // date
                        cellText(row, 6, fmt), // gst
                        cellText(row, 7, fmt), // hsn
                        cellText(row, 8, fmt)  // brand
                );
                out.add(l);
            }
        }
        return out;
    }

    private static String cellText(Row row, int i, DataFormatter fmt) {
        Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return fmt.formatCellValue(cell).trim();
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
