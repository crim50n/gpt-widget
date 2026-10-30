package dev.local.usagewidget;

import static dev.local.usagewidget.UiText.t;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.Instant;

public class MainActivity extends Activity {
    private Appearance.Palette palette;
    private LinearLayout content, overview, preview;
    private int size = 2;
    private android.widget.ImageButton refreshButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String signature = "";
    private final Runnable repaint = new Runnable() {
        @Override public void run() {
            updateRefreshButton();
            Store s = new Store(MainActivity.this);
            String next = s.connected() + ":" + s.time("usage") + ":" + s.time("resets") + s.error("usage") + s.error("resets") + ResetService.isBusy();
            if (!next.equals(signature)) { signature = next; renderOverview(); renderPreview(); }
            handler.postDelayed(this, 2000);
        }
    };
    private int dp(float value) { return Appearance.dp(this, value); }
    @Override public void onCreate(Bundle state) {
        setTheme(Appearance.palette(this).dark() ? R.style.Theme_Usage_Dark : R.style.Theme_Usage_Light);
        super.onCreate(state);
        if (state != null) {
            size = Math.max(0, Math.min(3, state.getInt("size", 2)));
        }
        build();
        if (new Store(this).connected()) RefreshJob.schedule(this);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("size", size); super.onSaveInstanceState(state);
    }
    private void build() {
        palette = Appearance.palette(this);
        getWindow().setStatusBarColor(palette.background());
        getWindow().setNavigationBarColor(palette.background());
        getWindow().getDecorView().setSystemUiVisibility(palette.dark() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(palette.background());
        content = column(); content.setPadding(dp(22), dp(24), dp(22), dp(32)); scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout heading = row(); content.addView(heading);
        TextView title = text(t(R.string.usage_title), 26, palette.text(), true);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        refreshButton = new android.widget.ImageButton(this);
        refreshButton.setImageResource(R.drawable.ic_refresh); refreshButton.setColorFilter(palette.accent());
        refreshButton.setBackground(Appearance.control(this, palette.inset(), 24, true)); refreshButton.setPadding(dp(12),dp(12),dp(12),dp(12));
        refreshButton.setContentDescription(t(R.string.refresh_now)); refreshButton.setTooltipText(t(R.string.refresh_now)); refreshButton.setTag("refresh-now");
        heading.addView(refreshButton, new LinearLayout.LayoutParams(dp(48),dp(48)));
        refreshButton.setOnClickListener(v -> {
            if (!new Store(this).connected()) { startActivity(new Intent(this, BrowserLoginActivity.class)); return; }
            Refresh.request(this); updateRefreshButton();
            AppNotice.show(this,t(R.string.refreshing_all));
        });
        overview = column(); margin(content, overview, 24);
        section(t(R.string.your_widget), t(R.string.choose_widget_size));
        LinearLayout sizes = segmentedRow(); margin(content, sizes, 14);
        String[] names = {"1 × 1", "2 × 2", "4 × 3", "4 × 4"};
        for (int i = 0; i < names.length; i++) {
            final int selected = i;
            TextView choice = pill(names[i], i == size);
            choice.setTag("widget-size-" + i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1); lp.setMargins(i == 0 ? 0 : dp(6), 0, 0, 0);
            sizes.addView(choice, lp);
            choice.setOnClickListener(v -> {
                size = selected;
                for (int j = 0; j < sizes.getChildCount(); j++) stylePill((TextView) sizes.getChildAt(j), j == size);
                renderPreview();
            });
        }
        preview = column(); margin(content, preview, 16); renderPreview();
        margin(content, action(t(R.string.add_widget_action), true, this::pin), 14);
        section(t(R.string.appearance), t(R.string.appearance_description));
        LinearLayout themes = segmentedRow(); margin(content, themes, 14);
        String[] labels = {t(R.string.theme_system), t(R.string.theme_light), t(R.string.theme_dark)};
        String[] modes = {"system", "light", "dark"};
        for (int i = 0; i < 3; i++) {
            final String mode = modes[i];
            TextView choice = pill(labels[i], Appearance.mode(this).equals(mode));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1); lp.setMargins(i == 0 ? 0 : dp(6), 0, 0, 0);
            themes.addView(choice, lp);
            choice.setOnClickListener(v -> {
                int y = scroll.getScrollY(); Appearance.mode(this, mode);
                setTheme(Appearance.palette(this).dark() ? R.style.Theme_Usage_Dark : R.style.Theme_Usage_Light);
                build();
                content.post(() -> ((ScrollView) content.getParent()).scrollTo(0, y));
            });
        }
        section(t(R.string.auto_refresh), t(R.string.auto_refresh_description));
        choiceSetting("interval", t(R.string.interval), AppSettings.intervalLabels(this), AppSettings.intervalIndex(this), index -> AppSettings.interval(this,index));
        section(t(R.string.date_and_time), t(R.string.date_time_description));
        choiceSetting("date", t(R.string.date_format), AppSettings.dateLabels(this), AppSettings.dateIndex(this), index -> AppSettings.format(this,"date",index));
        choiceSetting("time", t(R.string.time_format), AppSettings.timeLabels(this), AppSettings.timeIndex(this), index -> AppSettings.format(this,"time",index));
        section(t(R.string.account), t(R.string.account_description));
        LinearLayout settings = card(); margin(content, settings, 14);
        Store connection = new Store(this);
        if (connection.connected()) settings.addView(text(t(R.string.account_connected),12,palette.muted(),false));
        setting(settings, t(R.string.sign_in), t(R.string.sign_in_description), () -> startActivity(new Intent(this, BrowserLoginActivity.class)));
        setting(settings, t(R.string.disconnect_account), t(R.string.disconnect_description), () ->
                AppDialogs.confirm(this,t(R.string.disconnect_account),t(R.string.disconnect_confirm),t(R.string.disconnect),this::disconnect));
        margin(content, text(t(R.string.pool_description), 12, palette.muted(), false), 24);
        setContentView(scroll); renderOverview();
    }
    private void renderOverview() {
        if (overview == null) return;
        overview.removeAllViews(); Store store = new Store(this);
        LinearLayout hero = card(); overview.addView(hero);
        hero.addView(text(t(store.connected() ? R.string.weekly_limit : R.string.account_connection), 14, palette.muted(), false));
        try {
            Models.Usage usage = store.data("usage") == null ? null : Models.cachedUsage(store.data("usage"));
            Models.Window week = usage == null ? null : usage.week();
            TextView number = text(week == null ? "—" : week.remaining() + "%", 64, palette.accent(), true);
            margin(hero, number, 8);
            if (week == null) hero.addView(text(t(store.connected() ? R.string.waiting_first_data : R.string.connect_to_see_usage), 14, palette.muted(), false));
            if (week != null) {
                android.widget.ProgressBar bar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                bar.setProgressTintList(android.content.res.ColorStateList.valueOf(palette.accent()));
                bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(palette.inset())); bar.setProgress(week.remaining());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(8)); lp.topMargin = dp(18); hero.addView(bar, lp);
                margin(hero, text(Display.countdown(week.resetAt(), store.now("usage")), 14, palette.text(), false), 14);
                margin(hero, text(t(R.string.restores_at, AppSettings.dateTime(this, Instant.ofEpochSecond(week.resetAt()))), 12, palette.muted(), false), 4);
                if (usage.reached() || Boolean.FALSE.equals(usage.allowed())) margin(hero, text(t(R.string.server_restricted), 13, palette.accent(), true), 10);
            }
            if (!store.connected()) margin(hero, action(t(R.string.connect_account), true, () -> startActivity(new Intent(this, BrowserLoginActivity.class))), 16);
            if (usage != null) for (Models.Window window : usage.windows()) if (window.duration() != 604800)
                margin(hero, text(t(R.string.usage_window, window.duration()/3600, window.remaining()), 13, palette.text(), false), 10);
            if (store.connected()) margin(hero, text(Display.usageStatus(store), 11, palette.muted(), false), 14);
            Models.Resets resets = store.data("resets") == null ? null : Models.parseResets(store.data("resets"));
            LinearLayout stats = row(); margin(overview, stats, 10);
            metric(stats, resets == null ? "—" : String.valueOf(resets.count()), t(R.string.available_resets), false);
            metric(stats, usage == null ? "—" : usage.unlimited() ? "∞" : usage.creditLabel(), t(R.string.credits), true);
            if (resets != null) {
                LinearLayout tickets = card(); margin(overview, tickets, 10);
                tickets.addView(text(t(R.string.reset_limits), 16, palette.text(), true));
                String account = store.account();
                Redemption.Attempt pending = null;
                boolean readable = true;
                if (!account.isEmpty()) {
                    try { pending = new RedemptionStore(this).pending(account); }
                    catch (Exception e) { readable = false; margin(tickets,text(t(R.string.pending_reset_unreadable),12,palette.muted(),false),10); }
                }
                if (pending != null) {
                    margin(tickets,text(t(R.string.pending_reset_description),13,palette.muted(),false),12);
                    String creditId = pending.creditId();
                    TextView retry = action(t(ResetService.isBusy() ? R.string.applying : R.string.retry_attempt), true,
                            () -> confirmReset(account,creditId,"",true));
                    retry.setEnabled(!ResetService.isBusy()); margin(tickets,retry,10);
                }
                for (Models.Ticket ticket : resets.tickets()) if ("available".equals(ticket.status())) {
                    String ticketTitle = UiText.ticketTitle(ticket);
                    margin(tickets, text(ticket.supported() ? ticketTitle : t(R.string.unsupported_plan_title,ticketTitle), 14, palette.text(), true), 14);
                    String expiry = ticket.expires() == null ? t(R.string.expiry_unknown) : t(R.string.until_date, AppSettings.dateTime(this, ticket.expires()));
                    if (ticket.expires() != null && ticket.expires().toEpochMilli() <= store.now("resets")) expiry = t(R.string.refresh_list_suffix,expiry);
                    margin(tickets, text(expiry, 12, palette.muted(), false), 3);
                    boolean active = ticket.supported() && (ticket.expires() == null || ticket.expires().toEpochMilli() > store.now("resets"));
                    if (active && !ticket.id().isEmpty() && !account.isEmpty() && readable && pending == null) {
                        TextView consume = action(t(ResetService.isBusy() ? R.string.applying : R.string.use_reset), false,
                                () -> confirmReset(account,ticket.id(),ticketTitle,false));
                        consume.setEnabled(!ResetService.isBusy()); margin(tickets,consume,8);
                    } else if (active && ticket.id().isEmpty()) margin(tickets,text(t(R.string.refresh_before_reset),12,palette.muted(),false),6);
                }
                margin(tickets, text(Display.resetStatus(store), 11, palette.muted(), false), 14);
            } else if (!store.error("resets").isEmpty()) margin(overview, text(Display.resetStatus(store), 12, palette.muted(), false), 10);
        } catch (Exception ignored) { hero.addView(text(t(R.string.refresh_to_continue), 14, palette.muted(), false)); }
    }
    private void renderPreview() {
        if (preview == null) return;
        preview.removeAllViews();
        int available = Math.max(140, Math.round(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density) - 44);
        int width = size == 0 ? 76 : size == 1 ? Math.min(170, available) : available;
        int height = size == 0 ? 76 : size == 1 ? 180 : size == 2 ? 280 : 380;
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(dp(1), dp(1), dp(1), dp(1));
        View rendered = UsageWidget.render(this, width, height, true).apply(this, frame);
        frame.addView(rendered, new FrameLayout.LayoutParams(-1, -1));
        // Preview is display-only, not an interactive widget instance.
        View cover = new View(this); cover.setClickable(true); frame.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(width), dp(height)); lp.gravity = Gravity.CENTER_HORIZONTAL;
        preview.addView(frame, lp);
        TextView caption = text(t(new int[]{R.string.preview_mini,R.string.preview_compact,R.string.preview_overview,R.string.preview_detailed}[size]), 12, palette.muted(), false);
        caption.setGravity(Gravity.CENTER); margin(preview, caption, 10);
    }
    private void updateRefreshButton() {
        if (refreshButton == null) return;
        boolean busy = Refresh.isRunning() || ResetService.isBusy(); refreshButton.setEnabled(!busy); refreshButton.setAlpha(busy ? .4f : 1f);
        refreshButton.setContentDescription(t(busy ? R.string.refreshing : R.string.refresh_now));
    }
    private void confirmReset(String account, String id, String title, boolean retry) {
        if (ResetService.isBusy()) return;
        String message = retry ? t(R.string.retry_reset_confirmation) : t(R.string.apply_reset_confirmation,title);
        AppDialogs.confirm(this,t(retry ? R.string.retry_attempt : R.string.confirm_reset),message,t(retry ? R.string.retry : R.string.apply),() -> {
                    ResetService.consume(this,account,id,result -> {
                        if (isFinishing() || isDestroyed() || !account.equals(new Store(this).account())) return;
                        renderOverview(); renderPreview(); updateRefreshButton();
                        AppDialogs.message(this,t(R.string.reset_generic),result);
                    });
                    renderOverview(); updateRefreshButton();
                });
    }
    private void choiceSetting(String key, String title, String[] choices, int initial, java.util.function.IntConsumer save) {
        TextView button = action(t(R.string.setting_value,title,choices[initial]), false, () -> {});
        button.setTag("setting-" + key);
        final int[] current = {initial};
        button.setOnClickListener(v -> AppDialogs.choose(this,title,choices,current[0],index -> {
                    save.accept(index); current[0] = index; button.setText(t(R.string.setting_value,title,choices[index]));
                    renderOverview(); renderPreview();
                }));
        margin(content,button,12);
    }
    private void pin() {
        Class<?> provider = size == 0 ? TinyWidget.class : size == 1 ? CompactWidget.class : size == 2 ? UsageWidget.class : LargeWidget.class;
        AppWidgetManager manager = getSystemService(AppWidgetManager.class);
        if (manager.isRequestPinAppWidgetSupported()) manager.requestPinAppWidget(new ComponentName(this, provider), null, null);
        else AppDialogs.message(this,t(R.string.add_widget),t(R.string.widget_picker_help));
    }
    private void disconnect() {
        new PendingLoginStore(this).clear();
        new Store(this).clear(); RefreshJob.cancel(this);
        UsageWidget.updateAll(this); renderOverview(); renderPreview();
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout segmentedRow() {
        LinearLayout l = row(); l.setPadding(dp(4),dp(4),dp(4),dp(4));
        l.setBackground(Appearance.shape(this,palette.inset(),30)); return l;
    }
    private LinearLayout card() {
        LinearLayout l = column(); l.setBackground(Appearance.outlined(this, palette.surface(), 28)); l.setPadding(dp(20), dp(20), dp(20), dp(20)); return l;
    }
    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color); t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL)); t.setLineSpacing(dp(3), 1); return t;
    }
    private void margin(LinearLayout parent, View view, int top) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(top); parent.addView(view, lp); }
    private void section(String title, String subtitle) { margin(content, text(title, 21, palette.text(), true), 30); margin(content, text(subtitle, 13, palette.muted(), false), 6); }
    private TextView pill(String value, boolean selected) { TextView t = text(value, 13, palette.text(), true); t.setGravity(Gravity.CENTER); stylePill(t, selected); return t; }
    private void stylePill(TextView t, boolean selected) {
        t.setBackground(Appearance.control(this, selected ? palette.surface() : android.graphics.Color.TRANSPARENT, 26, false));
        t.setTextColor(selected ? palette.text() : palette.muted()); t.setSelected(selected);
    }
    private TextView action(String title, boolean primary, Runnable callback) {
        TextView t = text(title, 15, primary ? palette.accentText() : palette.text(), true);
        t.setGravity(Gravity.CENTER); t.setPadding(dp(12), dp(16), dp(12), dp(16)); t.setMinHeight(dp(52));
        t.setBackground(Appearance.control(this, primary ? palette.accent() : palette.surface(), 28, !primary)); t.setOnClickListener(v -> callback.run()); return t;
    }
    private void setting(LinearLayout parent, String title, String subtitle, Runnable callback) {
        LinearLayout item = row(); item.setPadding(0, dp(12), 0, dp(12)); item.setMinimumHeight(dp(68));
        LinearLayout labels = column(); labels.addView(text(title, 15, palette.text(), true)); margin(labels, text(subtitle, 12, palette.muted(), false), 5);
        item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1)); TextView arrow = text("›", 24, palette.muted(), false); arrow.setPadding(dp(16), 0, 0, 0); item.addView(arrow);
        item.setOnClickListener(v -> callback.run()); parent.addView(item);
    }
    private void metric(LinearLayout parent, String value, String label, boolean gap) {
        LinearLayout card = card(); card.addView(text(value, 28, palette.text(), true)); margin(card, text(label, 11, palette.muted(), false), 6);
        card.setMinimumHeight(dp(100));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); if (gap) lp.leftMargin = dp(10); parent.addView(card, lp);
    }
    @Override protected void onResume() {
        super.onResume(); handler.post(repaint);
        if (new PendingLoginStore(this).exists()) startActivity(new Intent(this,BrowserLoginActivity.class).putExtra("resume_pending",true));
        else showConnectionNotice();
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); showConnectionNotice(); }
    private void showConnectionNotice() {
        if (!getIntent().getBooleanExtra("connection_success",false)) return;
        getIntent().removeExtra("connection_success");
        if (new Store(this).connected()) content.post(() -> AppNotice.show(this,t(R.string.account_connected)));
    }
    @Override protected void onPause() { handler.removeCallbacks(repaint); super.onPause(); }
    @Override protected void onDestroy() { AppDialogs.dismiss(this); AppNotice.clear(this); super.onDestroy(); }
}
