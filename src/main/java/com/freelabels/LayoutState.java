package com.freelabels;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Mirrors the JS:
 *   const state = {
 *     page: { wmm: 210, hmm: 297 },
 *     grid: { rows:8, cols:3, marginTop:10, marginLeft:10, gapX:2, gapY:2 },
 *     zoom: 1.0,
 *   };
 * Extended (beyond the original PHP/JS) with a per-label background and an
 * optional icon/logo image, plus template save/load support (see
 * TemplateManager).
 */
public class LayoutState {
    public double pageWmm = 210, pageHmm = 297;
    public int rows = 8, cols = 3;
    public double marginTop = 10, marginLeft = 10, gapX = 2, gapY = 2;
    public double zoom = 1.0;

    /** How each label's background is painted. */
    public enum BackgroundMode { NONE, COLOR, IMAGE }

    public BackgroundMode backgroundMode = BackgroundMode.NONE;
    /** Used when backgroundMode == COLOR. */
    public Color backgroundColor = null;
    /** Used when backgroundMode == IMAGE; stretched to fill each label box. */
    public BufferedImage labelBackgroundImage = null;

    /** Optional logo/icon stamped on every label (e.g. top-right corner). Independent of backgroundMode. */
    public BufferedImage labelIconImage = null;
    public enum IconPosition { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    public IconPosition iconPosition = IconPosition.TOP_LEFT;

    public enum Preset {
        A4("A4 (210\u00d7297 mm)"),
        A5("A5 (148\u00d7210 mm)"),
        THERMAL_50x30("50\u00d730 mm (Thermal)"),
        AVERY_L7160("Avery L7160 (3\u00d78)"),
        AVERY_5163("Avery 5163 (2\u00d74)"),
        AVERY_3422("Avery 3422 (2\u00d77)"),
        CUSTOM("Custom\u2026");

        public final String label;
        Preset(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    /** Mirrors applyPreset() in the JS. Returns false for CUSTOM (caller should just reveal W/H fields). */
    public boolean applyPreset(Preset p) {
        switch (p) {
            case A4:            pageWmm = 210; pageHmm = 297; rows = 8; cols = 3; return true;
            case A5:            pageWmm = 148; pageHmm = 210; rows = 6; cols = 2; return true;
            case THERMAL_50x30: pageWmm = 50;  pageHmm = 30;  rows = 1; cols = 1; return true;
            case AVERY_L7160:   pageWmm = 210; pageHmm = 297; rows = 8; cols = 3; return true;
            case AVERY_5163:    pageWmm = 215.9; pageHmm = 279.4; rows = 4; cols = 2; return true;
            case AVERY_3422:    pageWmm = 210; pageHmm = 297; rows = 7; cols = 2; return true;
            case CUSTOM:
            default:
                return false;
        }
    }

    /** mm -> px at 96dpi (screen), same constant used as mmToPx() in the JS (96/25.4). */
    public static double mmToPx(double mm) {
        return mm * (96.0 / 25.4);
    }

    public static double pxToMm(double px) {
        return px / (96.0 / 25.4);
    }
}
