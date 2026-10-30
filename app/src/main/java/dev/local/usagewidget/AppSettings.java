package dev.local.usagewidget;

import android.content.Context;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class AppSettings {
    static final int[] INTERVALS = {0,15,30,60,180,360};
    // Preserve the first three indices so existing preferences keep their meaning.
    private static final String[] DATE_PATTERNS = {
            "d MMM", "dd.MM", "yyyy-MM-dd", "dd.MM.yyyy", "dd.MM.yy",
            "d MMMM", "d MMMM yyyy", "EEE, d MMM", "EEEE, d MMMM",
            "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy", "yyyy/MM/dd"
    };
    static String[] intervalLabels(Context c) { return c.getResources().getStringArray(R.array.refresh_intervals); }
    static String[] timeLabels(Context c) { return c.getResources().getStringArray(R.array.time_formats); }
    static String[] dateLabels(Context c) {
        String[] labels=new String[DATE_PATTERNS.length];
        java.time.LocalDate sample=java.time.LocalDate.of(2026,10,30);
        Locale locale=c.getResources().getConfiguration().getLocales().get(0);
        for (int i=0;i<labels.length;i++) labels[i]=sample.format(DateTimeFormatter.ofPattern(DATE_PATTERNS[i],locale));
        labels[9]=c.getString(R.string.date_order_dmy,labels[9]);
        labels[10]=c.getString(R.string.date_order_mdy,labels[10]);
        return labels;
    }
    private static int get(Context c, String key, int def) { return c.getSharedPreferences("settings",0).getInt(key,def); }
    static int intervalIndex(Context c) { return Math.max(0,Math.min(INTERVALS.length-1,get(c,"interval",1))); }
    static long intervalMillis(Context c) { return INTERVALS[intervalIndex(c)] * 60000L; }
    static void interval(Context c, int value) {
        c.getSharedPreferences("settings",0).edit().putInt("interval",value).apply(); RefreshJob.schedule(c);
    }
    static int dateIndex(Context c) { return Math.max(0, Math.min(DATE_PATTERNS.length-1,get(c,"date",0))); }
    static int timeIndex(Context c) { return get(c,"time",0) == 1 ? 1 : 0; }
    static void format(Context c, String key, int index) {
        c.getSharedPreferences("settings",0).edit().putInt(key,index).apply(); UsageWidget.updateAll(c);
    }
    static DateTimeFormatter dateFormatter(Context c) {
        return DateTimeFormatter.ofPattern(DATE_PATTERNS[dateIndex(c)],c.getResources().getConfiguration().getLocales().get(0)).withZone(ZoneId.systemDefault());
    }
    static DateTimeFormatter timeFormatter(Context c) {
        return DateTimeFormatter.ofPattern(timeIndex(c) == 1 ? "h:mm a" : "HH:mm",Locale.US).withZone(ZoneId.systemDefault());
    }
    static String dateTime(Context c, Instant instant) { return dateFormatter(c).format(instant) + " " + timeFormatter(c).format(instant); }
    static String stamp(Context c, long millis) { return millis == 0 ? "—" : timeFormatter(c).format(Instant.ofEpochMilli(millis)); }
}
