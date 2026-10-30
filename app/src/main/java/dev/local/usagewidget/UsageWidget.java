package dev.local.usagewidget;

import static dev.local.usagewidget.UiText.t;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Build;
import android.util.SizeF;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class UsageWidget extends AppWidgetProvider {
    private static final String REFRESH = "dev.local.usagewidget.REFRESH";
    static final Class<?>[] PROVIDERS = {UsageWidget.class, CompactWidget.class, LargeWidget.class, TinyWidget.class};
    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (REFRESH.equals(intent.getAction())) {
            if (Refresh.isRunning()) return;
            RefreshJob.schedule(context); RefreshJob.requestNow(context); updateAll(context);
        }
    }
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        updateAll(context); RefreshJob.schedule(context);
        if (AppSettings.intervalMillis(context) > 0 && new Store(context).time("usage") == 0) RefreshJob.requestNow(context);
    }
    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) { updateAll(context); }
    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (Class<?> provider : PROVIDERS) for (int id : manager.getAppWidgetIds(new ComponentName(context, provider))) {
            Bundle options = manager.getAppWidgetOptions(id);
            if (Build.VERSION.SDK_INT >= 31) {
                java.util.ArrayList<SizeF> sizes = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
                if (sizes != null && !sizes.isEmpty() && sizes.size() <= 16) {
                    java.util.Map<SizeF, RemoteViews> layouts = new java.util.LinkedHashMap<>();
                    for (SizeF size : sizes) layouts.put(size, render(context, (int) size.getWidth(), (int) size.getHeight(), false));
                    manager.updateAppWidget(id, new RemoteViews(layouts));
                    continue;
                }
            }
            int width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, provider == TinyWidget.class ? 70 : provider == CompactWidget.class ? 150 : 300);
            int height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, provider == TinyWidget.class ? 70 : provider == LargeWidget.class ? 330 : provider == CompactWidget.class ? 160 : 240);
            int landscapeWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width);
            int landscapeHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height);
            manager.updateAppWidget(id, new RemoteViews(render(context, landscapeWidth, landscapeHeight, false),
                    render(context, width, height, false)));
        }
    }
    static RemoteViews render(Context context, int width, int height, boolean preview) {
        RemoteViews views = renderContent(context,width,height,preview);
        boolean loading = !preview && Refresh.isFetching();
        boolean dark = Appearance.palette(context).dark();
        views.setViewVisibility(R.id.loading_container, loading ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.spinner_dark, loading && dark ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.spinner_light, loading && !dark ? View.VISIBLE : View.GONE);
        if (views.getLayoutId() != R.layout.widget_tiny)
            views.setViewVisibility(R.id.refresh, loading ? View.INVISIBLE : View.VISIBLE);
        if (views.getLayoutId() == R.layout.widget_rail)
            views.setViewVisibility(R.id.remaining, loading ? View.INVISIBLE : View.VISIBLE);
        return views;
    }
    private static RemoteViews renderContent(Context context, int width, int height, boolean preview) {
        WidgetSizing.Kind kind = WidgetSizing.kind(width, height);
        if (kind == WidgetSizing.Kind.MINI) return renderTiny(context, preview);
        if (kind != WidgetSizing.Kind.PANEL) return renderCompact(context, width, height, preview, kind);
        Appearance.Palette p = Appearance.palette(context);
        WidgetSizing.Layout layout = WidgetSizing.choose(width, height, context.getResources().getConfiguration().fontScale);
        Store store = new Store(context);
        boolean wide = width >= 230 && height < 360;
        RemoteViews v = new RemoteViews(context.getPackageName(), wide ? R.layout.widget_wide : R.layout.usage_widget);
        PendingIntent open = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent refresh = PendingIntent.getBroadcast(context, 1, new Intent(context, UsageWidget.class).setAction(REFRESH), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root, open);
        v.setOnClickPendingIntent(R.id.refresh, store.connected() ? refresh : open);
        v.setInt(R.id.widget_root, "setBackgroundResource", p.dark() ? R.drawable.widget_background : R.drawable.widget_background_light);
        for (int id : new int[]{R.id.reset_card, R.id.credit_card}) v.setInt(id, "setBackgroundResource", p.dark() ? R.drawable.widget_inset : R.drawable.widget_inset_light);
        for (int id : new int[]{R.id.brand, R.id.resets, R.id.credits, R.id.stats_summary}) v.setTextColor(id, p.text());
        for (int id : new int[]{R.id.remaining_label, R.id.reset_time, R.id.resets_label, R.id.credits_label, R.id.expiry, R.id.status, R.id.tickets}) v.setTextColor(id, p.muted());
        v.setTextColor(R.id.remaining, p.accent()); v.setInt(R.id.refresh, "setColorFilter", p.accent());
        int pad = Appearance.dp(context, wide && width < 300 ? 12 : layout.padding());
        v.setViewPadding(R.id.widget_root, pad, pad, pad, pad);
        v.setTextViewTextSize(R.id.remaining, TypedValue.COMPLEX_UNIT_SP, layout.percentSp());
        v.setTextViewText(R.id.brand, "ChatGPT");
        v.setViewVisibility(R.id.remaining_label, wide || layout.label() ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.progress, wide || layout.progress() ? View.VISIBLE : View.GONE);
        {
            int inset = Appearance.dp(context, 8);
            v.setViewPadding(R.id.reset_card, inset, inset, inset, inset);
            v.setViewPadding(R.id.credit_card, inset, inset, inset, inset);
            v.setTextViewTextSize(R.id.resets, TypedValue.COMPLEX_UNIT_SP, 20);
            v.setTextViewTextSize(R.id.credits, TypedValue.COMPLEX_UNIT_SP, 20);
        }
        boolean summary = width / context.getResources().getConfiguration().fontScale < 160;
        v.setViewVisibility(R.id.stats, !wide && layout.stats() && !summary ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.stats_summary, wide || layout.stats() && summary ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.expiry, (wide ? height >= 220 : layout.expiry()) ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.status, (wide ? height >= 270 : layout.status()) ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.tickets, View.GONE);
        String remaining = "—", label = t(store.connected() ? R.string.usage_not_loaded : R.string.connect_account);
        String reset = t(R.string.tap_to_open), count = "—", credit = "—", expiry = t(R.string.expiry_not_loaded);
        int percent = 0;
        try {
            if (store.data("usage") != null) {
                Models.Usage usage = Models.cachedUsage(store.data("usage"));
                Models.Window week = usage.week();
                if (week != null) {
                    percent = week.remaining(); remaining = percent + "%"; label = "";
                    reset = t(R.string.widget_restores, AppSettings.dateTime(context, Instant.ofEpochSecond(week.resetAt())));
                    if (week.resetAt() * 1000 <= store.now("usage")) reset = t(R.string.widget_due);
                } else label = t(R.string.no_weekly_window);
                if (usage.reached() || Boolean.FALSE.equals(usage.allowed())) { label = t(R.string.usage_restricted); reset = label; }
                credit = usage.unlimited() ? "∞" : usage.creditLabel();
            }
            if (store.data("resets") != null) {
                Models.Resets resets = Models.parseResets(store.data("resets")); count = String.valueOf(resets.count());
                Instant date = resets.nearest();
                expiry = date == null ? t(R.string.no_expiring_resets) : t(R.string.widget_reset_expires,AppSettings.dateTime(context, date));
                if (date != null && date.toEpochMilli() <= store.now("resets")) expiry = t(R.string.reset_expiry_due);
                if (layout.tickets()) {
                    StringBuilder details = new StringBuilder(t(R.string.available_resets));
                    int shown = 0;
                    for (Models.Ticket ticket : resets.tickets()) {
                        if (!"available".equals(ticket.status()) || !ticket.supported() || shown >= 3) continue;
                        details.append("\n").append(UiText.ticketTitle(ticket)).append("\n")
                                .append(ticket.expires() == null ? t(R.string.expiry_unknown) : t(R.string.until_date,AppSettings.dateTime(context, ticket.expires())));
                        shown++;
                    }
                    if (shown > 0) { v.setTextViewText(R.id.tickets, details.toString()); v.setViewVisibility(R.id.tickets, View.VISIBLE); }
                }
            }
        } catch (Exception ignored) { label = t(R.string.refresh_data); }
        if (preview && store.data("usage") == null) {
            percent = 73; remaining = "73%"; label = "";
            reset = t(R.string.widget_restores,AppSettings.dateTime(context, Instant.parse("2026-10-30T20:24:00Z"))); count = "2"; credit = "0";
            expiry = t(R.string.widget_reset_expires,AppSettings.dateTime(context, Instant.parse("2026-10-29T19:11:00Z")));
        }
        if (remaining.equals("—")) reset = t(store.connected() ? R.string.open_to_refresh : R.string.connect_account);
        if (!store.error("usage").isEmpty() || !store.error("resets").isEmpty()) reset = t(R.string.refresh_failed_open);
        else if (store.time("usage") > 0 && System.currentTimeMillis() - store.time("usage") > 86400000L)
            reset = t(R.string.stale_data);
        v.setTextViewText(R.id.remaining, remaining); v.setTextViewText(R.id.remaining_label, label);
        if (label.isEmpty()) v.setViewVisibility(R.id.remaining_label, View.GONE);
        v.setTextViewText(R.id.reset_time, reset); v.setTextViewText(R.id.resets, count);
        v.setTextViewText(R.id.resets_label, t(R.string.resets)); v.setTextViewText(R.id.credits, credit);
        v.setTextViewText(R.id.stats_summary, t(R.string.widget_stats,count,credit));
        if (wide && width >= 400 && height < 200) {
            v.setTextViewText(R.id.reset_time, reset.replace("\n", ": "));
            v.setTextViewText(R.id.stats_summary, t(R.string.widget_stats_inline,count,credit));
            v.setTextViewTextSize(R.id.reset_time, TypedValue.COMPLEX_UNIT_SP, 15);
            v.setTextViewTextSize(R.id.stats_summary, TypedValue.COMPLEX_UNIT_SP, 16);
        }
        v.setTextViewText(R.id.expiry, expiry);
        String status = t(R.string.widget_timestamps,AppSettings.stamp(context, store.time("usage")),AppSettings.stamp(context, store.time("resets")));
        if (!store.error("usage").isEmpty() || !store.error("resets").isEmpty()) status = t(R.string.not_updated_details);
        else if (store.time("usage") != 0 && System.currentTimeMillis() - store.time("usage") > 86400000L) status = t(R.string.stale_data);
        if (preview && store.data("usage") == null) status = t(R.string.preview_example);
        v.setTextViewText(R.id.status, status);
        Bitmap bar = Bitmap.createBitmap(600, 12, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bar); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(p.inset()); canvas.drawRoundRect(0, 0, 600, 12, 6, 6, paint);
        paint.setColor(p.accent()); if (percent > 0) canvas.drawRoundRect(0, 0, 6 * percent, 12, 6, 6, paint);
        v.setImageViewBitmap(R.id.progress, bar);
        return fit(context, v, width, height, preview);
    }
    /** Measure Android's actual font/layout metrics, not only a guessed dp budget. */
    private static RemoteViews fit(Context context, RemoteViews views, int width, int height, boolean preview) {
        android.widget.FrameLayout host = new android.widget.FrameLayout(context);
        View tree = views.apply(context, host);
        View content = tree.findViewById(R.id.widget_root);
        int widthPx = Appearance.dp(context, width), heightPx = Appearance.dp(context, height);
        int[] optional = {R.id.tickets, R.id.expiry, R.id.status, R.id.stats_summary, R.id.stats, R.id.remaining_label, R.id.progress};
        int step = 0;
        while (true) {
            content.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            if (content.getMeasuredHeight() <= heightPx) return fillAvailableSpace(context, views, width, height);
            if (step < optional.length) {
                int id = optional[step++];
                views.setViewVisibility(id, View.GONE); tree.findViewById(id).setVisibility(View.GONE);
            } else if (step == optional.length) {
                step++;
                views.setTextViewTextSize(R.id.remaining, TypedValue.COMPLEX_UNIT_SP, 28);
                ((android.widget.TextView) tree.findViewById(R.id.remaining)).setTextSize(28);
                views.setViewPadding(R.id.widget_root, 8, 8, 8, 8);
                content.setPadding(8, 8, 8, 8);
            } else return renderTiny(context, preview);
        }
    }
    private static RemoteViews renderTiny(Context context, boolean preview) {
        Appearance.Palette p = Appearance.palette(context);
        Store store = new Store(context);
        String percent = "—", label = store.connected() ? "" : t(R.string.sign_in);
        try {
            if (store.data("usage") != null) {
                Models.Usage usage = Models.cachedUsage(store.data("usage"));
                Models.Window week = usage.week();
                if (week != null) {
                    percent = week.remaining() + "%";
                    label = "";
                    if (week.resetAt() * 1000 <= store.now("usage")) label = t(R.string.refresh);
                }
                if (usage.reached() || Boolean.FALSE.equals(usage.allowed())) label = t(R.string.limit_short);
            } else if (preview) { percent = "73%"; label = ""; }
        } catch (Exception ignored) { label = t(R.string.refresh); }
        if (!store.error("usage").isEmpty() || (store.time("usage") > 0 && System.currentTimeMillis() - store.time("usage") > 86400000L)) label = t(R.string.refresh_warning);
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_tiny);
        v.setInt(R.id.widget_root, "setBackgroundResource", p.dark() ? R.drawable.widget_background : R.drawable.widget_background_light);
        v.setTextViewText(R.id.remaining, percent); v.setTextViewText(R.id.remaining_label, label);
        v.setViewVisibility(R.id.remaining_label, label.isEmpty() ? View.GONE : View.VISIBLE);
        v.setTextColor(R.id.remaining, p.accent()); v.setTextColor(R.id.remaining_label, p.muted());
        v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        v.setContentDescription(R.id.widget_root, t(R.string.widget_accessibility,percent,label));
        return v;
    }
    /** Increase readable content, not empty spacers, when a host grants extra room. */
    private static RemoteViews fillAvailableSpace(Context context, RemoteViews views, int width, int height) {
        View tree = views.apply(context, new android.widget.FrameLayout(context));
        View content = tree.findViewById(R.id.widget_root);
        int w = Appearance.dp(context, width), h = Appearance.dp(context, height);
        int[] ids = {R.id.remaining, R.id.remaining_label, R.id.reset_time, R.id.stats_summary, R.id.expiry, R.id.tickets, R.id.status, R.id.resets, R.id.credits};
        java.util.Map<Integer, Float> base = new java.util.HashMap<>();
        for (int id : ids) {
            View node = tree.findViewById(id);
            if (node instanceof android.widget.TextView text && text.getVisibility() == View.VISIBLE && text.getAutoSizeTextType() == android.widget.TextView.AUTO_SIZE_TEXT_TYPE_NONE)
                base.put(id, text.getTextSize());
        }
        content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        for (float factor = 1.05f; factor <= 2.51f && content.getMeasuredHeight() < h * .94f; factor += .05f) {
            for (java.util.Map.Entry<Integer, Float> entry : base.entrySet()) {
                android.widget.TextView text = tree.findViewById(entry.getKey());
                float size = entry.getValue() * factor;
                if (text.getText().length() > 0) {
                    android.graphics.Paint paint = new android.graphics.Paint(text.getPaint()); paint.setTextSize(size);
                    float required = 0;
                    String[] chunks = text.getMaxLines() == 1 ? new String[]{text.getText().toString()} : text.getText().toString().split("\\s+");
                    for (String chunk : chunks) required = Math.max(required, paint.measureText(chunk));
                    int available = text.getMeasuredWidth() - text.getPaddingLeft() - text.getPaddingRight() - Appearance.dp(context, 2);
                    if (required > available && available > 0) size *= available / required;
                }
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
            }
            content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            if (content.getMeasuredHeight() > h - Appearance.dp(context, 4)) break;
            for (int id : base.keySet()) views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, ((android.widget.TextView) tree.findViewById(id)).getTextSize());
        }
        return views;
    }
    private static RemoteViews renderCompact(Context context, int width, int height, boolean preview, WidgetSizing.Kind kind) {
        boolean rail = kind == WidgetSizing.Kind.RAIL;
        Appearance.Palette p = Appearance.palette(context);
        Store store = new Store(context);
        String percent = "—", label = "", reset = t(R.string.open_app), stats = t(R.string.no_data), expiry = "";
        java.time.format.DateTimeFormatter date = AppSettings.dateFormatter(context);
        java.time.format.DateTimeFormatter time = AppSettings.timeFormatter(context);
        try {
            if (store.data("usage") != null) {
                Models.Usage usage = Models.cachedUsage(store.data("usage")); Models.Window week = usage.week();
                if (week != null) {
                    percent = week.remaining() + "%";
                    Instant when = Instant.ofEpochSecond(week.resetAt());
                    reset = t(R.string.reset_at_compact,date.format(when) + (rail ? "\n" : " ") + time.format(when));
                    if (week.resetAt() * 1000 <= store.now("usage")) label = t(R.string.refresh);
                }
                if (usage.reached() || Boolean.FALSE.equals(usage.allowed())) label = t(R.string.limit_short);
                String count = "—";
                if (store.data("resets") != null) {
                    Models.Resets resets = Models.parseResets(store.data("resets")); count = String.valueOf(resets.count());
                    if (resets.nearest() != null) expiry = t(R.string.expires_compact,date.format(resets.nearest()) + "\n" + time.format(resets.nearest()));
                }
                stats = rail ? t(R.string.rail_stats,count,usage.creditLabel()) : width>=280 ? t(R.string.widget_stats_inline,count,usage.creditLabel()) : t(R.string.reset_count_inline,count);
            } else if (preview) {
                Instant sample = Instant.parse("2026-10-30T20:24:00Z");
                percent = "73%"; label = t(R.string.sample); reset = t(R.string.reset_at_compact,date.format(sample) + (rail ? "\n" : " ") + time.format(sample));
                stats = rail ? t(R.string.rail_stats,"2","0") : width >= 280 ? t(R.string.widget_stats_inline,"2","0") : t(R.string.reset_count_inline,"2");
                sample = Instant.parse("2026-10-29T19:11:00Z"); expiry = t(R.string.expires_compact,date.format(sample) + "\n" + time.format(sample));
            }
        } catch (Exception ignored) { label = t(R.string.refresh); }
        if (!store.error("usage").isEmpty() || !store.error("resets").isEmpty()
                || (store.time("usage") > 0 && System.currentTimeMillis() - store.time("usage") > 86400000L)) label = t(R.string.refresh);
        RemoteViews v = new RemoteViews(context.getPackageName(), rail ? R.layout.widget_rail : R.layout.widget_strip);
        v.setInt(R.id.widget_root, "setBackgroundResource", p.dark() ? R.drawable.widget_background : R.drawable.widget_background_light);
        v.setTextViewText(R.id.remaining, percent); v.setTextColor(R.id.remaining, p.accent());
        v.setTextViewText(R.id.remaining_label, label); v.setTextColor(R.id.remaining_label, p.muted());
        if (label.isEmpty()) v.setViewVisibility(R.id.remaining_label, View.GONE);
        if (!rail && width < 180 && context.getResources().getConfiguration().fontScale > 1.1f)
            v.setViewVisibility(R.id.remaining_label, View.GONE);
        v.setContentDescription(R.id.widget_root, t(R.string.widget_accessibility,percent,label));
        v.setTextViewText(R.id.reset_time, reset); v.setTextColor(R.id.reset_time, p.muted());
        v.setTextViewText(R.id.stats_summary, stats); v.setTextColor(R.id.stats_summary, p.text());
        v.setViewVisibility(R.id.stats_summary, rail ? (height >= 230 ? View.VISIBLE : View.GONE) : (height >= 64 && width >= 220 ? View.VISIBLE : View.GONE));
        v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        v.setInt(R.id.refresh, "setColorFilter", p.accent());
        v.setOnClickPendingIntent(R.id.refresh, store.connected()
                ? PendingIntent.getBroadcast(context, 1, new Intent(context, UsageWidget.class).setAction(REFRESH), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        if (rail) {
            android.graphics.Paint number = new android.graphics.Paint();
            number.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            number.setTextSize(Appearance.dp(context, 36));
            float size = number.getTextSize() * Math.min(1f, Appearance.dp(context, width - 16) / Math.max(1f, number.measureText(percent)));
            v.setTextViewTextSize(R.id.remaining, TypedValue.COMPLEX_UNIT_PX, size);
            v.setTextColor(R.id.brand, p.text());
            v.setTextViewText(R.id.expiry, expiry); v.setTextColor(R.id.expiry, p.muted());
            v.setViewVisibility(R.id.expiry, height >= 310 && !expiry.isEmpty() ? View.VISIBLE : View.GONE);
            v.setViewVisibility(R.id.refresh_control, height >= 230 ? View.VISIBLE : View.GONE);
            v.setInt(R.id.refresh, "setColorFilter", p.accent());
            View tree = v.apply(context, new android.widget.FrameLayout(context));
            for (int hide : new int[]{R.id.expiry, R.id.refresh_control, R.id.stats_summary, R.id.brand, R.id.reset_time}) {
                tree.measure(View.MeasureSpec.makeMeasureSpec(Appearance.dp(context, width), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                if (tree.getMeasuredHeight() <= Appearance.dp(context, height)) break;
                v.setViewVisibility(hide, View.GONE); tree.findViewById(hide).setVisibility(View.GONE);
            }
        }
        if (rail && height >= 400) {
            Bitmap meter = Bitmap.createBitmap(12, 400, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(meter); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(p.inset()); canvas.drawRoundRect(0,0,12,400,6,6,paint);
            float amount = 0;
            try { amount = Float.parseFloat(percent.replace("%", "")) / 100f; } catch (NumberFormatException ignored) { }
            paint.setColor(p.accent());
            if (amount > 0) canvas.drawRoundRect(0, 400 * (1 - amount), 12, 400, 6, 6, paint);
            v.setImageViewBitmap(R.id.vertical_meter, meter);
            v.setViewVisibility(R.id.vertical_meter, View.VISIBLE);
            return v;
        }
        return rail ? fillAvailableSpace(context, v, width, height) : v;
    }
}
