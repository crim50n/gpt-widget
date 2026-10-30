package dev.local.usagewidget;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

final class Appearance {
    record Palette(int background, int surface, int inset, int text, int muted, int accent, int accentText, int line, boolean dark) { }
    static String mode(Context c) { return c.getSharedPreferences("appearance", 0).getString("theme", "system"); }
    static void mode(Context c, String mode) {
        c.getSharedPreferences("appearance", 0).edit().putString("theme", mode).apply();
        UsageWidget.updateAll(c);
    }
    static Palette palette(Context c) {
        String mode = mode(c);
        boolean dark = mode.equals("dark") || (mode.equals("system") &&
                (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        return dark ? new Palette(Color.BLACK, hex("#212121"), hex("#303030"), hex("#F5F5F5"), hex("#B4B4B4"), Color.WHITE, Color.BLACK, hex("#454545"), true)
                : new Palette(Color.WHITE, hex("#F7F7F7"), hex("#ECECEC"), hex("#171717"), hex("#676767"), hex("#171717"), Color.WHITE, hex("#D9D9D9"), false);
    }
    static int hex(String color) { return Color.parseColor(color); }
    static int dp(Context c, float value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c, int color, int radius) {
        GradientDrawable result = new GradientDrawable(); result.setColor(color);
        result.setCornerRadius(dp(c, radius)); return result;
    }
    static GradientDrawable outlined(Context c, int color, int radius) {
        GradientDrawable result = shape(c,color,radius);
        result.setStroke(dp(c,1),palette(c).line()); return result;
    }
    static android.graphics.drawable.Drawable control(Context c, int color, int radius, boolean border) {
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(palette(c).dark() ? 0x33FFFFFF : 0x22000000),
                border ? outlined(c,color,radius) : shape(c,color,radius),shape(c,Color.WHITE,radius));
    }
}
