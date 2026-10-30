package dev.local.usagewidget;

/** Conservative vertical budget in dp, including wrapped dates and enlarged fonts. */
final class WidgetSizing {
    enum Kind { MINI, STRIP, RAIL, PANEL }
    static Kind kind(int width, int height) {
        if (height < 140 && width >= 140) return Kind.STRIP;
        if (width < 140 && height >= 120) return Kind.RAIL;
        if (width < 140 || height < 140) return Kind.MINI;
        return Kind.PANEL;
    }
    record Layout(int padding, int percentSp, boolean label, boolean progress,
                  boolean stats, boolean expiry, boolean status, boolean tickets) { }
    static Layout choose(int width, int height, float fontScale) {
        float scale = Math.max(1f, fontScale);
        boolean narrow = width < 230;
        int padding = narrow ? 12 : 16;
        int percent = narrow ? 38 : height >= 350 ? 56 : 42;
        float used = padding * 2 + 40 + 6 + percent * scale * 1.2f + 9 + 30 * scale;
        float labelHeight = (narrow ? 32 : 20) * scale;
        boolean label = used + labelHeight <= height;
        if (label) used += labelHeight;
        boolean progress = used + 18 <= height;
        if (progress) used += 18;
        boolean stats = width >= 140 && used + 32 + 40 * scale <= height;
        if (stats) used += 32 + 40 * scale;
        boolean expiry = stats && used + 10 + 32 * scale <= height;
        if (expiry) used += 10 + 32 * scale;
        boolean status = used + 10 + 28 * scale <= height;
        if (status) used += 10 + 28 * scale;
        boolean tickets = expiry && used + 14 + 120 * scale <= height;
        return new Layout(padding, percent, label, progress, stats, expiry, status, tickets);
    }
}
