package dev.local.usagewidget;

import static dev.local.usagewidget.UiText.t;

final class Display {
    static String age(long time) {
        if (time == 0) return t(R.string.not_received);
        long minutes = Math.max(0, (System.currentTimeMillis() - time) / 60000);
        if (minutes < 1) return t(R.string.just_now);
        if (minutes < 60) return t(R.string.minutes_ago,minutes);
        if (minutes < 1440) return t(R.string.hours_ago,minutes/60);
        return t(R.string.days_ago,minutes/1440);
    }
    static String countdown(long epochSeconds, long nowMillis) {
        long seconds = epochSeconds - nowMillis / 1000;
        if (seconds <= 0) return t(R.string.restoration_due);
        long minutes = (seconds + 59) / 60;
        long days = minutes / 1440;
        long hours = minutes % 1440 / 60;
        return days>0 ? t(R.string.countdown_days,days,hours,minutes%60) : t(R.string.countdown_hours,hours,minutes%60);
    }
    static String usageStatus(Store store) {
        return withError(t(R.string.usage_status,age(store.time("usage"))),store.error("usage"));
    }
    static String resetStatus(Store store) {
        return withError(t(R.string.reset_status,age(store.time("resets"))),store.error("resets"));
    }
    private static String withError(String value,String error) { return error.isEmpty() ? value : t(R.string.status_error,value,error); }
}
