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
    private JTextField customValueField;
    private JComboBox<Label.CustomField.Type> customTypeCombo;
    private final java.util.List<Label.CustomField> pendingCustomFields = new ArrayList<>();
    private DefaultListModel<String> customFieldListModel;
    private JRadioButton[] iconPositionRadios;

    private static final FileNameExtensionFilter IMAGE_FILTER =
            new FileNameExtensionFilter("Image files (*.png, *.jpg, *.jpeg, *.gif, *.bmp)",
                    "png", "jpg", "jpeg", "gif", "bmp");
    private static final FileNameExtensionFilter TEMPLATE_FILTER =
            new FileNameExtensionFilter("FreeLabels template (*.fltemplate)", "fltemplate");

    public LabelApp() {
        super("🏷 FreeLabels V2 — Label Creation Platform");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildSettingsPanel(), buildPreviewPanel());
        topSplit.setResizeWeight(0.0);
        topSplit.setDividerLocation(749);

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
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(14, 14, 14, 14));
        content.setBackground(new Color(245, 247, 250));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        JLabel title = new JLabel("Input & Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        JButton resetBtn = new JButton("Reset");
        resetBtn.setBackground(new Color(223, 111, 99));
        resetBtn.setForeground(Color.BLACK);
        resetBtn.setOpaque(true);
        resetBtn.setBorderPainted(false);
        resetBtn.setFocusPainted(false);
        resetBtn.setFont(resetBtn.getFont().deriveFont(Font.BOLD, 12f));
        resetBtn.setMargin(new Insets(7, 18, 7, 18));
        resetBtn.addActionListener(this::onReset);

        header.add(title, BorderLayout.WEST);
        header.add(resetBtn, BorderLayout.EAST);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(header);
        content.add(Box.createVerticalStrut(12));

        // Page and grid settings
        JPanel pageSection = settingsSection("PAGE & GRID SETTINGS");
        presetCombo = new JComboBox<>(LayoutState.Preset.values());
        presetCombo.addActionListener(e -> applyPreset((LayoutState.Preset) presetCombo.getSelectedItem()));
        pageSection.add(labeled("Choose Page / Template", presetCombo));

        pageWField = new JSpinner(new SpinnerNumberModel(210.0, 1.0, 2000.0, 1.0));
        pageHField = new JSpinner(new SpinnerNumberModel(297.0, 1.0, 2000.0, 1.0));
        customSizeRow = row(labeled("Width (mm)", pageWField), labeled("Height (mm)", pageHField));
        customSizeRow.setVisible(false);
        pageWField.addChangeListener(e -> { state.pageWmm = (double) pageWField.getValue(); refreshAll(); });
        pageHField.addChangeListener(e -> { state.pageHmm = (double) pageHField.getValue(); refreshAll(); });
        pageSection.add(customSizeRow);

        rowsField = intSpinner(state.rows, 1, 100);
        colsField = intSpinner(state.cols, 1, 100);
        pageSection.add(row(labeled("Rows", rowsField), labeled("Columns", colsField)));
        marginTopField = mmSpinner(state.marginTop);
        marginLeftField = mmSpinner(state.marginLeft);
        pageSection.add(row(labeled("Top Margin (mm)", marginTopField), labeled("Left Margin (mm)", marginLeftField)));
        gapXField = mmSpinner(state.gapX);
        gapYField = mmSpinner(state.gapY);
        pageSection.add(row(labeled("Horizontal Gap (mm)", gapXField), labeled("Vertical Gap (mm)", gapYField)));

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
        toggleGridBox.setOpaque(false);
        toggleGridBox.addActionListener(e -> { previewPanel.setShowGrid(toggleGridBox.isSelected()); previewPanel.repaint(); });
        pageSection.add(toggleGridBox);
        addSection(content, pageSection);

        // Label background
        JPanel backgroundSection = settingsSection("LABEL BACKGROUND");
        bgStatusLabel = new JLabel("None");
        bgStatusLabel.setFont(bgStatusLabel.getFont().deriveFont(Font.ITALIC, 11f));
        backgroundSection.add(bgStatusLabel);
        JPanel bgRow = leftFlowPanel();
        JButton bgColorBtn = new JButton("Choose Color…");
        JButton bgImageBtn = new JButton("Choose Image…");
        JButton bgClearBtn = new JButton("Clear");
        bgColorBtn.addActionListener(this::onChooseBackgroundColor);
        bgImageBtn.addActionListener(this::onChooseBackgroundImage);
        bgClearBtn.addActionListener(this::onClearBackground);
        bgRow.add(bgColorBtn); bgRow.add(bgImageBtn); bgRow.add(bgClearBtn);
        backgroundSection.add(bgRow);
        addSection(content, backgroundSection);

        // Logo and position
        JPanel logoSection = settingsSection("LABEL ICON / LOGO");
        JPanel iconRow = leftFlowPanel();
        iconPreviewLabel = new JLabel();
        iconPreviewLabel.setPreferredSize(new Dimension(36, 36));
        iconPreviewLabel.setBorder(BorderFactory.createDashedBorder(new Color(145, 155, 168)));
        JButton iconChooseBtn = new JButton("Choose Icon…");
        JButton iconClearBtn = new JButton("Clear");
        iconChooseBtn.addActionListener(this::onChooseIcon);
        iconClearBtn.addActionListener(this::onClearIcon);
        iconRow.add(iconPreviewLabel); iconRow.add(iconChooseBtn); iconRow.add(iconClearBtn);
        logoSection.add(iconRow);
        ButtonGroup iconPositions = new ButtonGroup();
        JPanel positionPanel = new JPanel(new GridLayout(2, 2, 8, 5));
        positionPanel.setOpaque(false);
        String[] positionNames = {"Top left corner", "Top right corner", "Bottom left corner", "Bottom right corner"};
        LayoutState.IconPosition[] positions = LayoutState.IconPosition.values();
        iconPositionRadios = new JRadioButton[positions.length];
        for (int i = 0; i < positions.length; i++) {
            final LayoutState.IconPosition pos = positions[i];
            JRadioButton radio = new JRadioButton(positionNames[i], i == 1);
            radio.setOpaque(false);
            iconPositions.add(radio);
            positionPanel.add(radio);
            iconPositionRadios[i] = radio;
            radio.addActionListener(e -> { state.iconPosition = pos; refreshAll(); });
            if (i == 1) state.iconPosition = pos;
        }
        logoSection.add(labeled("Logo position", positionPanel));
        addSection(content, logoSection);

        // Templates
        JPanel templateSection = settingsSection("TEMPLATES");
        JPanel templateRow = leftFlowPanel();
        JButton saveTemplateBtn = new JButton("Save Template…");
        JButton loadTemplateBtn = new JButton("Load Template…");
        saveTemplateBtn.addActionListener(this::onSaveTemplate);
        loadTemplateBtn.addActionListener(this::onLoadTemplate);
        templateRow.add(saveTemplateBtn); templateRow.add(loadTemplateBtn);
        templateSection.add(templateRow);
        templateSection.add(helpText("Saves page/grid size, background, and icon (not label data) for reuse."));
        addSection(content, templateSection);

        // Custom fields
        JPanel customSection = settingsSection("CUSTOM FIELDS");
        customFieldListModel = new DefaultListModel<>();
        JList<String> customFieldList = new JList<>(customFieldListModel);
        customFieldList.setVisibleRowCount(3);
        JScrollPane customListScroll = new JScrollPane(customFieldList);
        customListScroll.setPreferredSize(new Dimension(250, 76));
        customSection.add(customListScroll);
        customValueField = textField();
        customTypeCombo = new JComboBox<>(Label.CustomField.Type.values());
        customSection.add(labeled("Field value", customValueField));
        customSection.add(labeled("Content type", customTypeCombo));
        // Custom-field actions: two compact buttons on row one, wide Add Label on row two.
        JPanel customButtons = new JPanel(new GridLayout(2, 1, 0, 8));
        customButtons.setOpaque(false);
        customButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        customButtons.setBorder(new EmptyBorder(6, 0, 6, 0));

        JPanel fieldActionRow = new JPanel(new GridLayout(1, 2, 14, 0));
        fieldActionRow.setOpaque(false);
        JButton addFieldBtn = new JButton("Add Field");
        addFieldBtn.setBackground(new Color(214, 237, 23));
        addFieldBtn.setForeground(Color.black);
        addFieldBtn.setOpaque(true);
        addFieldBtn.setBorderPainted(false);
        addFieldBtn.setFocusPainted(false);

        JButton removeFieldBtn = new JButton("Remove Selected");
        removeFieldBtn.setBackground(new Color(206, 74, 126));
        removeFieldBtn.setForeground(Color.black);
        removeFieldBtn.setOpaque(true);
        removeFieldBtn.setBorderPainted(false);
        removeFieldBtn.setFocusPainted(false);

        addFieldBtn.addActionListener(e -> {
            String v = customValueField.getText();
            if (v.trim().isEmpty()) { error("Enter a value for the custom field."); return; }
            Label.CustomField.Type t = (Label.CustomField.Type) customTypeCombo.getSelectedItem();
            pendingCustomFields.add(new Label.CustomField(v, t));
            customFieldListModel.addElement(t + ": " + v);
            customValueField.setText("");
        });
        removeFieldBtn.addActionListener(e -> {
            int i = customFieldList.getSelectedIndex();
            if (i >= 0) { pendingCustomFields.remove(i); customFieldListModel.remove(i); }
        });
        fieldActionRow.add(addFieldBtn);
        fieldActionRow.add(removeFieldBtn);

        JButton addBtn = new JButton("+ Add Label");
        addBtn.addActionListener(this::onAddSingle);
        addBtn.setBackground(new Color(254, 231, 21));
        addBtn.setForeground(Color.black);
        addBtn.setOpaque(true);
        addBtn.setBorderPainted(false);
        addBtn.setFocusPainted(false);
        addBtn.setFont(addBtn.getFont().deriveFont(Font.BOLD, 14f));
        addBtn.setMargin(new Insets(12, 18, 12, 18));
        addBtn.setPreferredSize(new Dimension(0, 48));

        customButtons.add(fieldActionRow);
        customButtons.add(addBtn);
        customSection.add(customButtons);
        addSection(content, customSection);

        // Bulk import
        JPanel importSection = settingsSection("BULK IMPORT");
        JLabel headerLabel = new JLabel("Download sample XLSX file");
        headerLabel.setForeground(new Color(190, 35, 35));
        headerLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        headerLabel.setToolTipText("Download sample Excel file");
        headerLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                try { Desktop.getDesktop().browse(new URI("https://www.freelabels.in/sample_bulk_file.xlsx")); }
                catch (Exception ex) { ex.printStackTrace(); }
            }
        });
        headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        importSection.add(headerLabel);
        JButton importBtn = new JButton("Choose File…");
        importBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        importBtn.addActionListener(this::onImportXlsx);
        importSection.add(importBtn);
        importSection.add(helpText("Each row creates one label. Each column creates one ordered line. Use barcode=value or qr_code=value for machine-readable fields."));
        addSection(content, importSection);

        // Preview zoom and exports
        JPanel outputSection = settingsSection("PREVIEW & EXPORT");
        JPanel zoomRow = leftFlowPanel();
        JButton zoomInBtn = new JButton("Zoom In");
        JButton zoomOutBtn = new JButton("Zoom Out");
        zoomLabel = new JLabel("100%");
        zoomInBtn.addActionListener(e -> setZoom(state.zoom + 0.1));
        zoomOutBtn.addActionListener(e -> setZoom(state.zoom - 0.1));
        zoomRow.add(zoomInBtn); zoomRow.add(zoomOutBtn); zoomRow.add(zoomLabel);
        outputSection.add(zoomRow);
        JPanel exportRow = new JPanel(new GridLayout(0, 1, 6, 6));
        exportRow.setOpaque(false);
        JButton pdfBtn = new JButton("Generate PDF");
        pdfBtn.setBackground(new Color(249, 87, 0));
        pdfBtn.setForeground(new Color(59, 24, 119));
        pdfBtn.setOpaque(true);
        pdfBtn.setBorderPainted(false);
        pdfBtn.setFocusPainted(false);
        pdfBtn.setFont(addBtn.getFont().deriveFont(Font.BOLD, 14f));
        pdfBtn.setMargin(new Insets(12, 18, 12, 18));
        pdfBtn.setPreferredSize(new Dimension(0, 48));

        JButton zplBtn = new JButton("Export ZPL");
        zplBtn.setBackground(new Color(151, 188, 98));
        zplBtn.setForeground(new Color(44, 95, 45));
        zplBtn.setOpaque(true);
        zplBtn.setBorderPainted(false);
        zplBtn.setFocusPainted(false);
        zplBtn.setFont(addBtn.getFont().deriveFont(Font.BOLD, 14f));
        zplBtn.setMargin(new Insets(12, 18, 12, 18));
        zplBtn.setPreferredSize(new Dimension(0, 48));

        JButton tsplBtn = new JButton("Export TSPL");
        tsplBtn.setBackground(new Color(253, 210, 14));
        tsplBtn.setForeground(new Color(199, 45, 27));
        tsplBtn.setOpaque(true);
        tsplBtn.setBorderPainted(false);
        tsplBtn.setFocusPainted(false);
        tsplBtn.setFont(addBtn.getFont().deriveFont(Font.BOLD, 14f));
        tsplBtn.setMargin(new Insets(12, 18, 12, 18));
        tsplBtn.setPreferredSize(new Dimension(0, 48));

        pdfBtn.addActionListener(this::onGeneratePdf);
        zplBtn.addActionListener(this::onExportZpl);
        tsplBtn.addActionListener(this::onExportTspl);
        exportRow.add(pdfBtn); exportRow.add(zplBtn); exportRow.add(tsplBtn);
        outputSection.add(exportRow);
        addSection(content, outputSection);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(new Color(245, 247, 250));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static JPanel settingsSection(String title) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(Color.WHITE);
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(155, 169, 185), 1),
                        BorderFactory.createMatteBorder(3, 0, 0, 0, new Color(45, 91, 135))),
                new EmptyBorder(11, 12, 12, 12)));
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 11.5f));
        heading.setForeground(new Color(45, 72, 98));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(heading);
        section.add(Box.createVerticalStrut(9));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return section;
    }

    private static void addSection(JPanel parent, JPanel section) {
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(section);
        parent.add(Box.createVerticalStrut(12));
    }

    private static JPanel leftFlowPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static JLabel helpText(String text) {
        JLabel label = new JLabel("<html><span style='color:#596579'>" + text + "</span></html>");
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
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
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(4, 0, 5, 0));
        JLabel l = new JLabel(label);
        l.setHorizontalAlignment(SwingConstants.LEFT);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 12f));
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private static JPanel row(JComponent a, JComponent b) {
        JPanel p = new JPanel(new GridLayout(1, 2, 12, 0));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(2, 0, 2, 0));
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
        tf.setPreferredSize(new Dimension(180, 28));
        tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
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
        Label l = new Label();
        l.customFields.addAll(pendingCustomFields);
        labels.add(l);
        refreshAll();
    }

    private void onImportXlsx(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Excel files (*.xlsx)", "xlsx"));
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
        pendingCustomFields.clear();
        if(customFieldListModel!=null) customFieldListModel.clear();
        if(customValueField!=null) customValueField.setText("");
        labels.clear();
        state.zoom = 1.0;
        zoomLabel.setText("100%");
        state.backgroundMode = LayoutState.BackgroundMode.NONE;
        state.backgroundColor = null;
        state.labelBackgroundImage = null;
        state.labelIconImage = null;
        state.iconPosition = LayoutState.IconPosition.TOP_RIGHT;
        if(iconPositionRadios!=null && iconPositionRadios.length>1) iconPositionRadios[1].setSelected(true);
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
            if(iconPositionRadios!=null) iconPositionRadios[state.iconPosition.ordinal()].setSelected(true);
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
