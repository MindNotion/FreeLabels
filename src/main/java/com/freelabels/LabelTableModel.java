package com.freelabels;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/** Backs the "Labels Data" JTable: #, Name, SKU, Price, Barcode, QR/EPC, Date, GST, HSN, Brand. */
public class LabelTableModel extends AbstractTableModel {

    private static final String[] COLS = {
            "#", "Name", "SKU", "Price", "Barcode", "QR/EPC", "Date", "GST", "HSN", "Brand"
    };

    private final List<Label> labels;

    public LabelTableModel(List<Label> labels) {
        this.labels = labels;
    }

    @Override public int getRowCount() { return labels.size(); }
    @Override public int getColumnCount() { return COLS.length; }
    @Override public String getColumnName(int column) { return COLS[column]; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Label l = labels.get(rowIndex);
        switch (columnIndex) {
            case 0: return rowIndex + 1;
            case 1: return l.name;
            case 2: return l.sku;
            case 3: return l.price;
            case 4: return l.barcode;
            case 5: return l.qr;
            case 6: return l.date;
            case 7: return l.gst;
            case 8: return l.hsn;
            case 9: return l.brand;
            default: return "";
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex != 0;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        Label l = labels.get(rowIndex);
        String v = aValue == null ? "" : aValue.toString();
        switch (columnIndex) {
            case 1: l.name = v; break;
            case 2: l.sku = v; break;
            case 3: l.price = v; break;
            case 4: l.barcode = v; break;
            case 5: l.qr = v; break;
            case 6: l.date = v; break;
            case 7: l.gst = v; break;
            case 8: l.hsn = v; break;
            case 9: l.brand = v; break;
            default: return;
        }
        fireTableCellUpdated(rowIndex, columnIndex);
    }

    public void removeRow(int rowIndex) {
        labels.remove(rowIndex);
        fireTableRowsDeleted(rowIndex, rowIndex);
        fireTableDataChanged(); // re-number the "#" column
    }

    public void refresh() { fireTableDataChanged(); }
}
