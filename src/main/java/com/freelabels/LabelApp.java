package com.freelabels;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer; 

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Java Swing port of camel.php (FreeLabels — Free Cloud-Based Label Creation Platform).
 * Reproduces: page/template presets, grid (rows/cols/margins/gaps) settings, a quick
 * single-label entry form, bulk .xlsx import, a live preview of the label sheet
 * (QR + barcode + text), an editable labels table, and PDF / ZPL / TSPL export.
 */
public class LabelApp extends JFrame {

    private final LayoutState state = new LayoutState();
    private final List<Label> labels = new ArrayList<>();

    private final PreviewPanel previewPanel = new PreviewPanel(state, labels);
    private final LabelTableModel tableModel = new LabelTableModel(labels);
    private JTable labelsTable;

    // Settings fields
    private JComboBox<LayoutState.Preset> presetCombo;
    private JSpinner pageWField, pageHField;
    private JPanel customSizeRow;
    private JSpinner rowsField, colsField, marginTopField, marginLeftField, gapXField, gapYField;
    private JCheckBox toggleGridBox;
    private JLabel zoomLabel;

    // Background / icon / template fields
    private JLabel bgStatusLabel;
    private JLabel iconPreviewLabel;

    // Quick add fields
    private JTextField fName, fSKU, fPrice, fBarcode, fQR, fDate, fGST, fHSN, fBrand;

    private static final FileNameExtensionFilter IMAGE_FILTER =
            new FileNameExtensionFilter("Image files (*.png, *.jpg, *.jpeg, *.gif, *.bmp)",
                    "png", "jpg", "jpeg", "gif", "bmp");
    private static final FileNameExtensionFilter TEMPLATE_FILTER =
            new FileNameExtensionFilter("FreeLabels template (*.fltemplate)", "fltemplate");

    public LabelApp() {
        super("🏷 FreeLabels V1.3 — Label Creation Platform");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildSettingsPanel(), buildPreviewPanel());
        topSplit.setResizeWeight(0.0);
        topSplit.setDividerLocation(549);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                topSplit, buildLabelsTablePanel());
        mainSplit.setResizeWeight(0.72);

        add(mainSplit, BorderLayout.CENTER);

        applyPreset(LayoutState.Preset.A4);
        refreshAll();

        setSize(1400, 900);
        setLocationRelativeTo(null);
    }

    // ---------------------------------------------------------------- settings

    private JComponent buildSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel header = new JPanel(new BorderLayout());
        header.add(new JLabel("<html><b>Input &amp; Settings</b></html>"), BorderLayout.WEST);
        JButton resetBtn = new JButton("Reset");
        resetBtn.addActionListener(this::onReset);
        header.add(resetBtn, BorderLayout.EAST);
        panel.add(header);
        panel.add(Box.createVerticalStrut(8));

        panel.add(labeled("Choose Page / Template", presetCombo = new JComboBox<>(LayoutState.Preset.values())));
        presetCombo.addActionListener(e -> applyPreset((LayoutState.Preset) presetCombo.getSelectedItem()));

        pageWField = new JSpinner(new SpinnerNumberModel(210.0, 1.0, 2000.0, 1.0));
        pageHField = new JSpinner(new SpinnerNumberModel(297.0, 1.0, 2000.0, 1.0));
        customSizeRow = row(labeled("Width (mm)", pageWField), labeled("Height (mm)", pageHField));
        customSizeRow.setVisible(false);
        pageWField.addChangeListener(e -> { state.pageWmm = (double) pageWField.getValue(); refreshAll(); });
        pageHField.addChangeListener(e -> { state.pageHmm = (double) pageHField.getValue(); refreshAll(); });
        panel.add(customSizeRow);

        rowsField = intSpinner(state.rows, 1, 100);
        colsField = intSpinner(state.cols, 1, 100);
        panel.add(row(labeled("Rows", rowsField), labeled("Cols", colsField)));

        marginTopField = mmSpinner(state.marginTop);
        marginLeftField = mmSpinner(state.marginLeft);
        panel.add(row(labeled("Top Margin (mm)", marginTopField), labeled("Left Margin (mm)", marginLeftField)));

        gapXField = mmSpinner(state.gapX);
        gapYField = mmSpinner(state.gapY);
        panel.add(row(labeled("Horizontal Gap (mm)", gapXField), labeled("Vertical Gap (mm)", gapYField)));

        for (JSpinner sp : new JSpinner[]{rowsField, colsField, marginTopField, marginLeftField, gapXField, gapYField}) {
            sp.addChangeListener(e -> {
                state.rows = (int) rowsField.getValue();
                state.cols = (int) colsField.getValue();
                state.marginTop = (double) marginTopField.getValue();
                state.marginLeft = (double) marginLeftField.getValue();
                state.gapX = (double) gapXField.getValue();
                state.gapY = (double) gapYField.getValue();
                refreshAll();
            });
        }

        toggleGridBox = new JCheckBox("5 mm Grid Overlay", true);
        toggleGridBox.addActionListener(e -> { previewPanel.setShowGrid(toggleGridBox.isSelected()); previewPanel.repaint(); });
        panel.add(toggleGridBox);

        panel.add(new JSeparator());
        panel.add(Box.createVerticalStrut(6));
        panel.add(new JLabel("<html><b>Label Background</b></html>"));
        bgStatusLabel = new JLabel("None");
        bgStatusLabel.setFont(bgStatusLabel.getFont().deriveFont(Font.ITALIC, 11f));
        panel.add(bgStatusLabel);
        JPanel bgRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton bgColorBtn = new JButton("Choose Color\u2026");
        JButton bgImageBtn = new JButton("Choose Image\u2026");
        JButton bgClearBtn = new JButton("Clear");
        bgColorBtn.addActionListener(this::onChooseBackgroundColor);
        bgImageBtn.addActionListener(this::onChooseBackgroundImage);
        bgClearBtn.addActionListener(this::onClearBackground);
        bgRow.add(bgColorBtn); bgRow.add(bgImageBtn); bgRow.add(bgClearBtn);
        panel.add(bgRow);

        panel.add(new JSeparator());
        panel.add(Box.createVerticalStrut(6));
        panel.add(new JLabel("<html><b>Label Icon / Logo</b></html>"));
        JPanel iconRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        iconPreviewLabel = new JLabel();
        iconPreviewLabel.setPreferredSize(new Dimension(32, 32));
        iconPreviewLabel.setBorder(BorderFactory.createDashedBorder(Color.GRAY));
        JButton iconChooseBtn = new JButton("Choose Icon\u2026");
        JButton iconClearBtn = new JButton("Clear");
        iconChooseBtn.addActionListener(this::onChooseIcon);
        iconClearBtn.addActionListener(this::onClearIcon);
        iconRow.add(iconPreviewLabel); iconRow.add(iconChooseBtn); iconRow.add(iconClearBtn);
        panel.add(iconRow);
        panel.add(new JLabel("<html><small>Stamped in the top-right corner of every label.</small></html>"));

        panel.add(new JSeparator());
        panel.add(Box.createVerticalStrut(6));
        panel.add(new JLabel("<html><b>Template</b></html>"));
        JPanel templateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton saveTemplateBtn = new JButton("Save Template\u2026");
        JButton loadTemplateBtn = new JButton("Load Template\u2026");
        saveTemplateBtn.addActionListener(this::onSaveTemplate);
        loadTemplateBtn.addActionListener(this::onLoadTemplate);
        templateRow.add(saveTemplateBtn); templateRow.add(loadTemplateBtn);
        panel.add(templateRow);
        panel.add(new JLabel("<html><small>Saves page/grid size, background, and icon (not label data) for reuse.</small></html>"));

        panel.add(new JSeparator());
        panel.add(Box.createVerticalStrut(6));
        panel.add(new JLabel("<html><b>Single Label Data (for quick test)</b></html>"));

        fName = textField(); fSKU = textField(); fPrice = textField(); fBarcode = textField();
        fQR = textField(); fDate = textField(); fGST = textField(); fHSN = textField(); fBrand = textField();
        panel.add(labeled("Product Name", fName));
        panel.add(row(labeled("SKU", fSKU), labeled("Price", fPrice)));
        panel.add(labeled("Barcode (auto: EAN-13 / EPC / Code128)", fBarcode));
        panel.add(labeled("QR text/URL or EPC", fQR));
        panel.add(labeled("Exp/Manf Date", fDate));
        panel.add(row(labeled("GST %", fGST), labeled("HSN", fHSN)));
        panel.add(labeled("Brand", fBrand));

        JButton addBtn = new JButton("+ Add to Grid");
        addBtn.addActionListener(this::onAddSingle);
        panel.add(addBtn);

        panel.add(new JSeparator());
        panel.add(Box.createVerticalStrut(6));

        // Create label with HTML header and anchor tag
        JLabel headerLabel = new JLabel("<html><b>Bulk Import</b>&nbsp;&nbsp;&nbsp;<u style='color:#ce1212'>📥 Download sample xlsx file</u></html>");
        headerLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        headerLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI("https://www.freelabels.in/sample_bulk_file.xlsx"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        panel.add(headerLabel);
        JButton importBtn = new JButton("Choose File\u2026");
        importBtn.addActionListener(this::onImportXlsx);
        panel.add(importBtn);
        panel.add(new JLabel("<html><small>A: Name, B: SKU, C: Price, D: Barcode, E: QR/EPC,<br>"
                + "F: Expiry/Manufacturing, G: GST, H: HSN, I: Brand</small></html>"));

        panel.add(Box.createVerticalStrut(8));
        JPanel zoomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton zoomInBtn = new JButton("Zoom In");
        JButton zoomOutBtn = new JButton("Zoom Out");
        zoomLabel = new JLabel("100%");
        zoomInBtn.addActionListener(e -> setZoom(state.zoom + 0.1));
        zoomOutBtn.addActionListener(e -> setZoom(state.zoom - 0.1));
        zoomRow.add(zoomInBtn); zoomRow.add(zoomOutBtn); zoomRow.add(zoomLabel);
        panel.add(zoomRow);

        panel.add(new JSeparator());
        panel.add(Box.createVerticalStrut(6));
        JPanel exportRow = new JPanel(new GridLayout(0, 1, 4, 4));
        JButton pdfBtn = new JButton("Generate PDF");
        JButton zplBtn = new JButton("Export ZPL");
        JButton tsplBtn = new JButton("Export TSPL");
        pdfBtn.addActionListener(this::onGeneratePdf);
        zplBtn.addActionListener(this::onExportZpl);
        tsplBtn.addActionListener(this::onExportTspl);
        exportRow.add(pdfBtn); exportRow.add(zplBtn); exportRow.add(tsplBtn);
        panel.add(exportRow);

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JComponent buildPreviewPanel() {
        JPanel wrap = new JPanel(new BorderLayout());
        JPanel header = new JPanel(new BorderLayout());
        // Left panel
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

        leftPanel.add(new JLabel("<html><b>Live Preview</b></html>"));
        JButton freelabelBtn = new JButton("<html><u><b>Watch Tutorial</b></u></html>");
        freelabelBtn.setBorderPainted(false);
        freelabelBtn.setContentAreaFilled(false);
        freelabelBtn.setFocusPainted(false);
        freelabelBtn.setOpaque(false);
        freelabelBtn.setForeground(new Color(0, 102, 204));
        freelabelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        freelabelBtn.setToolTipText("Open Tutorial");

        freelabelBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI("https://www.youtube.com/watch?v=SejHWA1MOBA"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        leftPanel.add(freelabelBtn);

        JButton websiteBtn = new JButton("<html><u><b>Powered By MindNotion</b></u></html>");
        websiteBtn.setBorderPainted(false);
        websiteBtn.setContentAreaFilled(false);
        websiteBtn.setFocusPainted(false);
        websiteBtn.setOpaque(false);
        websiteBtn.setForeground(new Color(206, 18, 18));
        websiteBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        websiteBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI("https://www.mindnotion.in/"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        websiteBtn.setToolTipText("Open MindNotion website");

        leftPanel.add(websiteBtn);

        

        header.add(leftPanel, BorderLayout.WEST);


        JButton refillBtn = new JButton("Fill Grid with Current Labels");
        refillBtn.addActionListener(e -> { previewPanel.setForceFill(true); refreshAll(); });
        header.add(refillBtn, BorderLayout.EAST);
        wrap.add(header, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(previewPanel);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);
        wrap.add(scroll, BorderLayout.CENTER);
        wrap.add(new JLabel(" Zoom affects preview only. Edit rows/cols/margins/gaps on the left to adjust the grid."),
                BorderLayout.SOUTH);
        return wrap;
    }

    private JComponent buildLabelsTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(6, 10, 10, 10));

        JPanel header = new JPanel(new BorderLayout());
        header.add(new JLabel("<html><b>Labels Data</b></html>"), BorderLayout.WEST);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton clearBtn = new JButton("Clear");
        JButton duplicateBtn = new JButton("Duplicate to Fill");
        JButton downloadJsonBtn = new JButton("Download JSON");
        JButton deleteRowBtn = new JButton("Delete Selected Row");
        clearBtn.addActionListener(this::onClearLabels);
        duplicateBtn.addActionListener(this::onDuplicateToFill);
        downloadJsonBtn.addActionListener(this::onDownloadJson);
        deleteRowBtn.addActionListener(this::onDeleteSelectedRow);
        btns.add(deleteRowBtn); btns.add(duplicateBtn); btns.add(clearBtn); btns.add(downloadJsonBtn);
        header.add(btns, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        labelsTable = new JTable(tableModel);
        labelsTable.setFillsViewportHeight(true);
        labelsTable.setRowHeight(22);
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        labelsTable.getColumnModel().getColumn(0).setMaxWidth(50);
        labelsTable.getColumnModel().getColumn(0).setCellRenderer(center);

        panel.add(new JScrollPane(labelsTable), BorderLayout.CENTER);
        return panel;
    }

    // ---------------------------------------------------------------- helpers

    private static JPanel labeled(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setBorder(new EmptyBorder(3, 0, 3, 0));
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 11f));
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private static JPanel row(JComponent a, JComponent b) {
        JPanel p = new JPanel(new GridLayout(1, 2, 8, 0));
        p.add(a); p.add(b);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private static JSpinner intSpinner(int value, int min, int max) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, 1));
    }

    private static JSpinner mmSpinner(double value) {
        return new JSpinner(new SpinnerNumberModel(value, 0.0, 500.0, 0.5));
    }

    private static JTextField textField() {
        JTextField tf = new JTextField();
        tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        return tf;
    }

    private void applyPreset(LayoutState.Preset preset) {
        boolean known = state.applyPreset(preset);
        customSizeRow.setVisible(!known);
        if (!known) {
            pageWField.setValue(state.pageWmm);
            pageHField.setValue(state.pageHmm);
            return;
        }
        rowsField.setValue(state.rows);
        colsField.setValue(state.cols);
        refreshAll();
    }

    private void setZoom(double z) {
        state.zoom = Math.max(0.3, Math.min(3.0, z));
        zoomLabel.setText(Math.round(state.zoom * 100) + "%");
        refreshAll();
    }

    private void refreshAll() {
        previewPanel.setLabels(labels);
        previewPanel.refresh();
        tableModel.refresh();
    }

    // ---------------------------------------------------------------- actions

    private void onAddSingle(ActionEvent e) {
        Label l = new Label(fName.getText(), fSKU.getText(), fPrice.getText(), fBarcode.getText(),
                fQR.getText(), fDate.getText(), fGST.getText(), fHSN.getText(), fBrand.getText());
        labels.add(l);
        refreshAll();
    }

    private void onImportXlsx(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Excel files (*.xlsx, *.xls)", "xlsx", "xls"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        setBusy(true);
        SwingWorker<List<Label>, Void> worker = new SwingWorker<>() {
            @Override protected List<Label> doInBackground() throws Exception {
                return XlsxImporter.importFile(f);
            }
            @Override protected void done() {
                setBusy(false);
                try {
                    List<Label> imported = get();
                    labels.addAll(imported);
                    refreshAll();
                    JOptionPane.showMessageDialog(LabelApp.this,
                            imported.size() + " label(s) imported.", "Bulk Import", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(LabelApp.this,
                            "Error processing file: " + ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void onReset(ActionEvent e) {
        int choice = JOptionPane.showConfirmDialog(this, "Reset all fields, labels, and preview?",
                "Confirm Reset", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) return;
        fName.setText(""); fSKU.setText(""); fPrice.setText(""); fBarcode.setText("");
        fQR.setText(""); fDate.setText(""); fGST.setText(""); fHSN.setText(""); fBrand.setText("");
        labels.clear();
        state.zoom = 1.0;
        zoomLabel.setText("100%");
        state.backgroundMode = LayoutState.BackgroundMode.NONE;
        state.backgroundColor = null;
        state.labelBackgroundImage = null;
        state.labelIconImage = null;
        updateBackgroundStatusLabel();
        updateIconPreview();
        presetCombo.setSelectedItem(LayoutState.Preset.A4);
        applyPreset(LayoutState.Preset.A4);
        previewPanel.setForceFill(false);
        refreshAll();
    }

    // ---------------------------------------------------------------- background / icon / template

    private void onChooseBackgroundColor(ActionEvent e) {
        Color initial = state.backgroundColor != null ? state.backgroundColor : Color.WHITE;
        Color chosen = JColorChooser.showDialog(this, "Choose Label Background Color", initial);
        if (chosen == null) return;
        state.backgroundColor = chosen;
        state.backgroundMode = LayoutState.BackgroundMode.COLOR;
        updateBackgroundStatusLabel();
        refreshAll();
    }

    private void onChooseBackgroundImage(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(IMAGE_FILTER);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) { error("That file doesn't look like a supported image."); return; }
            state.labelBackgroundImage = img;
            state.backgroundMode = LayoutState.BackgroundMode.IMAGE;
            updateBackgroundStatusLabel();
            refreshAll();
        } catch (IOException ex) {
            error("Failed to load image: " + ex.getMessage());
        }
    }

    private void onClearBackground(ActionEvent e) {
        state.backgroundMode = LayoutState.BackgroundMode.NONE;
        state.backgroundColor = null;
        state.labelBackgroundImage = null;
        updateBackgroundStatusLabel();
        refreshAll();
    }

    private void updateBackgroundStatusLabel() {
        switch (state.backgroundMode) {
            case COLOR:
                bgStatusLabel.setText(String.format("Solid color (#%06X)", state.backgroundColor.getRGB() & 0xFFFFFF));
                break;
            case IMAGE:
                bgStatusLabel.setText("Image (" + state.labelBackgroundImage.getWidth()
                        + "\u00d7" + state.labelBackgroundImage.getHeight() + "px)");
                break;
            case NONE:
            default:
                bgStatusLabel.setText("None");
        }
    }

    private void onChooseIcon(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(IMAGE_FILTER);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) { error("That file doesn't look like a supported image."); return; }
            state.labelIconImage = img;
            updateIconPreview();
            refreshAll();
        } catch (IOException ex) {
            error("Failed to load icon image: " + ex.getMessage());
        }
    }

    private void onClearIcon(ActionEvent e) {
        state.labelIconImage = null;
        updateIconPreview();
        refreshAll();
    }

    private void updateIconPreview() {
        if (state.labelIconImage == null) {
            iconPreviewLabel.setIcon(null);
        } else {
            Image scaled = state.labelIconImage.getScaledInstance(28, 28, Image.SCALE_SMOOTH);
            iconPreviewLabel.setIcon(new ImageIcon(scaled));
        }
    }

    private void onSaveTemplate(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(TEMPLATE_FILTER);
        chooser.setSelectedFile(new File("my-template.fltemplate"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = ensureExt(chooser.getSelectedFile(), ".fltemplate");
        try {
            TemplateManager.save(f, state);
            info("Template saved to " + f.getAbsolutePath());
        } catch (IOException ex) {
            error("Failed to save template: " + ex.getMessage());
        }
    }

    private void onLoadTemplate(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(TEMPLATE_FILTER);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        try {
            TemplateManager.load(f, state);
            // Reflect the loaded page size as "Custom" since we don't persist which preset produced it.
            presetCombo.setSelectedItem(LayoutState.Preset.CUSTOM);
            customSizeRow.setVisible(true);
            pageWField.setValue(state.pageWmm);
            pageHField.setValue(state.pageHmm);
            rowsField.setValue(state.rows);
            colsField.setValue(state.cols);
            marginTopField.setValue(state.marginTop);
            marginLeftField.setValue(state.marginLeft);
            gapXField.setValue(state.gapX);
            gapYField.setValue(state.gapY);
            updateBackgroundStatusLabel();
            updateIconPreview();
            refreshAll();
            info("Template loaded from " + f.getAbsolutePath());
        } catch (IOException ex) {
            error("Failed to load template: " + ex.getMessage());
        }
    }

    private void onDuplicateToFill(ActionEvent e) {
        if (labels.isEmpty()) return;
        int needed = Math.max(0, state.rows * state.cols - labels.size());
        int originalSize = labels.size();
        for (int i = 0; i < needed; i++) labels.add(labels.get(i % originalSize).copy());
        refreshAll();
    }

    private void onClearLabels(ActionEvent e) {
        labels.clear();
        refreshAll();
    }

    private void onDeleteSelectedRow(ActionEvent e) {
        int row = labelsTable.getSelectedRow();
        if (row < 0) return;
        tableModel.removeRow(row);
        refreshAll();
    }

    private void onDownloadJson(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("labels.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        try (Writer w = new FileWriter(f, StandardCharsets.UTF_8)) {
            w.write("[\n");
            for (int i = 0; i < labels.size(); i++) {
                w.write(labels.get(i).toJson());
                w.write(i < labels.size() - 1 ? ",\n" : "\n");
            }
            w.write("]\n");
            info("Saved to " + f.getAbsolutePath());
        } catch (IOException ex) {
            error("Failed to write JSON: " + ex.getMessage());
        }
    }

    private void onGeneratePdf(ActionEvent e) {
        if (labels.isEmpty()) { error("Add at least one label first."); return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("labels.pdf"));
        chooser.setFileFilter(new FileNameExtensionFilter("PDF files (*.pdf)", "pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = ensureExt(chooser.getSelectedFile(), ".pdf");
        setBusy(true);
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                PdfExporter.export(f, state, labels);
                return null;
            }
            @Override protected void done() {
                setBusy(false);
                try { get(); info("PDF saved to " + f.getAbsolutePath()); }
                catch (Exception ex) { error("Failed to generate PDF: " + ex.getCause()); }
            }
        };
        worker.execute();
    }

    private void onExportZpl(ActionEvent e) {
        if (labels.isEmpty()) { error("Add at least one label first."); return; }
        saveText(LabelExporter.buildZPL(state, labels), "labels.zpl", "ZPL files (*.zpl)", "zpl");
    }

    private void onExportTspl(ActionEvent e) {
        if (labels.isEmpty()) { error("Add at least one label first."); return; }
        saveText(LabelExporter.buildTSPL(state, labels), "labels.tspl", "TSPL files (*.tspl)", "tspl");
    }

    private void saveText(String content, String defaultName, String filterDesc, String ext) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(defaultName));
        chooser.setFileFilter(new FileNameExtensionFilter(filterDesc, ext));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = ensureExt(chooser.getSelectedFile(), "." + ext);
        try (Writer w = new FileWriter(f, StandardCharsets.UTF_8)) {
            w.write(content);
            info("Saved to " + f.getAbsolutePath());
        } catch (IOException ex) {
            error("Failed to write file: " + ex.getMessage());
        }
    }

    private static File ensureExt(File f, String ext) {
        return f.getName().toLowerCase().endsWith(ext) ? f : new File(f.getParentFile(), f.getName() + ext);
    }

    private void setBusy(boolean busy) {
        setCursor(Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void info(String msg) {
        JOptionPane.showMessageDialog(this, msg, "FreeLabels", JOptionPane.INFORMATION_MESSAGE);
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "FreeLabels", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new LabelApp().setVisible(true));
    }
}
