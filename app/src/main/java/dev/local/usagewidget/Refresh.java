package dev.local.usagewidget;

import static dev.local.usagewidget.UiText.t;

import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class Refresh {
    static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean queued = new AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicInteger active = new java.util.concurrent.atomic.AtomicInteger();
    static boolean isRunning() { return queued.get() || isFetching(); }
    static boolean isFetching() { return active.get() > 0; }
    static void begin(Context context) { active.incrementAndGet(); UsageWidget.updateAll(context); }
    static void end(Context context) { active.decrementAndGet(); UsageWidget.updateAll(context); }

    static void request(Context context) {
        Context app = context.getApplicationContext();
        if (!queued.compareAndSet(false, true)) return;
        EXECUTOR.execute(() -> {
            try { run(app); } finally { queued.set(false); }
        });
    }

    static void run(Context context) {
        try {
            begin(context);
            fetch(context);
        } finally { end(context); }
    }

    private static void fetch(Context context) {
        Store store = new Store(context);
        String revision = store.revision();
        Store.Credentials auth;
        try { auth = store.credentials(); }
        catch (Exception e) {
            store.error("usage", t(R.string.error_saved_session), revision);
            return;
        }
        if (auth == null) return;
        for (String group : new String[]{"usage", "resets"}) {
            if (Thread.currentThread().isInterrupted()) break;
            try {
                AuthSession.Read read = AuthSession.read(context,group.equals("usage") ? "usage" : "rate-limit-reset-credits");
                Api.Response result = read.response();
                store.success(group, group.equals("usage") ? Models.parseUsage(result.json()).json()
                        : Models.parseResets(result.json()).json(), result.offset(), read.auth().revision());
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) break;
                String message = e instanceof Api.Failure ? e.getMessage()
                        : e instanceof org.json.JSONException ? t(R.string.error_data_format)
                        : t(R.string.error_network);
                store.error(group, message, auth.revision());
            }
        }
    }
}
