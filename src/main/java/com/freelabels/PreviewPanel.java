package com.freelabels;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Mirrors drawPreview() in camel.php: lays labels out in a rows x cols grid
 * on a page of state.page.wmm x state.page.hmm, honoring margins/gaps/zoom,
 * and renders brand/name/sku/hsn/price/gst/date text plus an optional QR
 * (left) and an optional barcode (bottom of the text block).
 */
public class PreviewPanel extends JPanel {

    private final LayoutState state;
    private List<Label> labels;
    private boolean showGrid = true;
    private boolean forceFill = false;

    public PreviewPanel(LayoutState state, List<Label> labels) {
        this.state = state;
        this.labels = labels;
        setBackground(Color.WHITE);
    }

    public void setLabels(List<Label> labels) { this.labels = labels; }
    public void setShowGrid(boolean showGrid) { this.showGrid = showGrid; }
    public void setForceFill(boolean forceFill) { this.forceFill = forceFill; }

    /** Recomputes preferred size from page dims + zoom, then repaints. Call after any state change. */
    public void refresh() {
        int pxW = (int) Math.round(LayoutState.mmToPx(state.pageWmm) * state.zoom);
        int pxH = (int) Math.round(LayoutState.mmToPx(state.pageHmm) * state.zoom);
        setPreferredSize(new Dimension(Math.max(pxW, 50), Math.max(pxH, 50)));
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        double zoom = state.zoom;
        double pxW = LayoutState.mmToPx(state.pageWmm) * zoom;
        double pxH = LayoutState.mmToPx(state.pageHmm) * zoom;

        // page background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, (int) pxW, (int) pxH);
        g.setColor(new Color(221, 221, 221));
        g.drawRect(0, 0, (int) pxW - 1, (int) pxH - 1);

        if (showGrid) paintGridOverlay(g, pxW, pxH, zoom);

        int rows = Math.max(1, state.rows), cols = Math.max(1, state.cols);
        double left = LayoutState.mmToPx(state.marginLeft);
        double top = LayoutState.mmToPx(state.marginTop);
        double gapX = LayoutState.mmToPx(state.gapX);
        double gapY = LayoutState.mmToPx(state.gapY);
        double fullW = LayoutState.mmToPx(state.pageWmm);
        double fullH = LayoutState.mmToPx(state.pageHmm);
        double labelW = (fullW - left * 2 - gapX * (cols - 1)) / cols;
        double labelH = (fullH - top * 2 - gapY * (rows - 1)) / rows;

        int toPlace = rows * cols;
        java.util.List<Label> data = new java.util.ArrayList<>();
        int n = Math.min(toPlace, labels.size());
        for (int i = 0; i < n; i++) data.add(labels.get(i));
        if (forceFill && !labels.isEmpty()) {
            int i = data.size();
            while (data.size() < toPlace) {
                data.add(labels.get(i % labels.size()));
                i++;
            }
        }
        if (data.isEmpty()) { g.dispose(); return; }

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                double bx = (left + c * (labelW + gapX)) * zoom;
                double by = (top + r * (labelH + gapY)) * zoom;
                double bw = labelW * zoom;
                double bh = labelH * zoom;

                paintLabelBackground(g, bx, by, bw, bh);

                // label-box border
                g.setColor(new Color(0, 0, 0, 46));
                Stroke old = g.getStroke();
                g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1,
                        new float[]{3, 3}, 0));
                g.drawRect((int) bx, (int) by, (int) bw, (int) bh);
                g.setStroke(old);

                if (idx < data.size()) {
                    paintLabelContent(g, data.get(idx), bx, by, bw, bh);
                }
            }
        }
        g.dispose();
    }

    /** Fills a label box with its configured solid color or stretches the background image to fit. */
    private void paintLabelBackground(Graphics2D g, double bx, double by, double bw, double bh) {
        if (state.backgroundMode == LayoutState.BackgroundMode.COLOR && state.backgroundColor != null) {
            g.setColor(state.backgroundColor);
            g.fillRect((int) bx, (int) by, (int) Math.ceil(bw), (int) Math.ceil(bh));
        } else if (state.backgroundMode == LayoutState.BackgroundMode.IMAGE && state.labelBackgroundImage != null) {
            g.drawImage(state.labelBackgroundImage, (int) bx, (int) by, (int) Math.ceil(bw), (int) Math.ceil(bh), null);
        }
    }

    private void paintGridOverlay(Graphics2D g, double pxW, double pxH, double zoom) {
        g.setColor(new Color(0, 0, 0, 15));
        double step = LayoutState.mmToPx(5) * zoom;
        for (double x = 0; x < pxW; x += step) g.drawLine((int) x, 0, (int) x, (int) pxH);
        for (double y = 0; y < pxH; y += step) g.drawLine(0, (int) y, (int) pxW, (int) y);
    }

    /**
     * Unified content layout: QR (if present) on the left third, text block + barcode
     * (if present) on the remaining space. This mirrors the combined visual outcome of
     * the several near-duplicate branches in the original drawPreview() JS function.
     */
    private void paintLabelContent(Graphics2D g, Label l, double bx, double by, double bw, double bh) {
        double pad = 4;
        double innerX = bx + pad, innerY = by + pad;
        double innerW = bw - pad * 2, innerH = bh - pad * 2;
        if (innerW <= 4 || innerH <= 4) return;

        double qrSize = 0;
        boolean hasQr = l.qr != null && !l.qr.trim().isEmpty();
        if (hasQr) {
            qrSize = Math.min(innerW / 3.0, innerH);
            BufferedImage qrImg = BarcodeUtil.renderQr(l.qr, Math.max(20, (int) qrSize));
            if (qrImg != null) {
                g.drawImage(qrImg, (int) innerX, (int) (innerY + (innerH - qrSize) / 2),
                        (int) qrSize, (int) qrSize, null);
            }
        }

        double textX = innerX + (hasQr ? qrSize + 6 : 0);
        double textW = innerW - (hasQr ? qrSize + 6 : 0);
        if (textW <= 4) return;

        java.util.List<String> lines = new java.util.ArrayList<>();
        if (!l.brand.isEmpty()) lines.add(l.brand);
        if (!l.name.isEmpty()) lines.add(l.name);
        if (!l.sku.isEmpty() || !l.hsn.isEmpty()) lines.add(join(l.sku, l.hsn));
        if (!l.price.isEmpty() || !l.gst.isEmpty()) lines.add(join(l.price, l.gst));
        if (!l.date.isEmpty()) lines.add(l.date);

        if (!l.customFields.isEmpty()) {
            double cy=innerY+9;
            for (Label.CustomField field:l.customFields) {
                if (field.value.isEmpty()) continue;
                if (field.type==Label.CustomField.Type.QR) {
                    int size=(int)Math.min( Math.min(innerW*0.28, innerH*0.28), 72);
                    BufferedImage img=BarcodeUtil.renderQr(field.value,Math.max(20,size));
                    if(img!=null){
                        double imageGap = 6;
                        cy += imageGap;
                        g.drawImage(img,(int)innerX,(int)cy,size,size,null);
                        cy += size + imageGap;
                    }
                } else if(field.type==Label.CustomField.Type.BARCODE) {
                    int h=(int)Math.max(14,Math.min(32,innerH*0.22));
                    BufferedImage img=BarcodeUtil.renderBarcode(field.value,BarcodeUtil.autoBarcodeType(field.value),(int)innerW,h);
                    if(img!=null){
                        double imageGap = 6;
                        cy += imageGap;
                        g.drawImage(img,(int)innerX,(int)cy,(int)innerW,h,null);
                        cy += h + imageGap;
                    }
                } else {
                    g.setColor(Color.BLACK); g.setFont(new Font("SansSerif",Font.PLAIN,Math.max(7,Math.min(11,(int)(innerH/Math.max(2,l.customFields.size()+1))))));
                    FontMetrics fm=g.getFontMetrics();
                    for(String wrapped:wrapText(field.value,fm,(int)innerW)){
                        if(cy>innerY+innerH-2) break;
                        g.drawString(wrapped,(float)innerX,(float)cy); cy+=fm.getHeight();
                    }
                }
                if(cy>innerY+innerH) break;
            }
        } else {
        boolean hasBarcode = l.barcode != null && !l.barcode.trim().isEmpty();
        double barcodeH = hasBarcode ? Math.min(28, innerH * 0.35) : 0;
        double barcodeGap = hasBarcode ? 6 : 0;
        double barcodeBottomGap = hasBarcode ? 4 : 0;
        double textAreaH = innerH - barcodeH - barcodeGap - barcodeBottomGap;

        float fontSize = (float) Math.max(6, Math.min(11, textAreaH / Math.max(1, lines.size()) / 1.6));
        g.setColor(Color.BLACK);
        Font brandFont = new Font("SansSerif", Font.BOLD, Math.round(fontSize));
        Font metaFont = new Font("SansSerif", Font.PLAIN, Math.round(fontSize));

        double ty = innerY + fontSize;
        for (int i = 0; i < lines.size(); i++) {
            g.setFont(i == 0 ? brandFont : metaFont);
            String text = clip(g, lines.get(i), textW);
            g.drawString(text, (float) textX, (float) ty);
            ty += fontSize * 1.35;
            if (ty > innerY + textAreaH) break;
        }

        if (hasBarcode) {
            BarcodeUtil.Type type = BarcodeUtil.autoBarcodeType(l.barcode);
            int bcW = (int) Math.max(20, textW);
            int bcH = (int) Math.max(10, barcodeH);
            BufferedImage bcImg = BarcodeUtil.renderBarcode(l.barcode, type, bcW, bcH);
            double barY = innerY + innerH - barcodeH - barcodeBottomGap;
            if (bcImg != null) {
                g.drawImage(bcImg, (int) textX, (int) barY, bcW, bcH, null);
            }
            if (type == BarcodeUtil.Type.EPC) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 8));
                g.drawString("EPC / GID-96", (float) textX, (float) (barY + bcH + 9));
            }
        }

        }
        if (state.labelIconImage != null) {
            double iconSize = Math.min(bw, bh) * 0.22;
            double ix = (state.iconPosition==LayoutState.IconPosition.TOP_LEFT || state.iconPosition==LayoutState.IconPosition.BOTTOM_LEFT) ? bx+3 : bx+bw-iconSize-3;
            double iy = (state.iconPosition==LayoutState.IconPosition.TOP_LEFT || state.iconPosition==LayoutState.IconPosition.TOP_RIGHT) ? by+3 : by+bh-iconSize-3;
            g.drawImage(state.labelIconImage, (int) ix, (int) iy, (int) iconSize, (int) iconSize, null);
        }
    }

    private static java.util.List<String> wrapText(String text, FontMetrics fm, int width) {
        java.util.List<String> out=new java.util.ArrayList<>();
        StringBuilder line=new StringBuilder();
        for(String word:text.split("\\s+")){
            String candidate=line.length()==0?word:line+" "+word;
            if(line.length()>0 && fm.stringWidth(candidate)>width){out.add(line.toString());line.setLength(0);}
            if(fm.stringWidth(word)>width){
                if(line.length()>0){out.add(line.toString());line.setLength(0);}
                StringBuilder part=new StringBuilder();
                for(int i=0;i<word.length();){
                    int cp=word.codePointAt(i); String ch=new String(Character.toChars(cp));
                    if(part.length()>0 && fm.stringWidth(part.toString()+ch)>width){out.add(part.toString());part.setLength(0);}
                    part.append(ch); i+=Character.charCount(cp);
                }
                if(part.length()>0)line.append(part);
            } else {if(line.length()>0)line.append(' ');line.append(word);}
        }
        if(line.length()>0)out.add(line.toString());
        return out;
    }

    private static String join(String a, String b) {
        a = a == null ? "" : a; b = b == null ? "" : b;
        if (!a.isEmpty() && !b.isEmpty()) return a + "  " + b;
        return a + b;
    }

    private static String clip(Graphics2D g, String text, double maxWidth) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) return text;
        String t = text;
        while (t.length() > 1 && fm.stringWidth(t + "\u2026") > maxWidth) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "\u2026";
    }
}
