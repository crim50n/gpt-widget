package dev.local.usagewidget;

import android.content.Context;
import java.util.Locale;

/** Resource access for UI-facing messages produced by background and domain components. */
final class UiText {
    private static Context application;
    static void initialize(Context context) { application=context.getApplicationContext(); }
    static String t(int id, Object... args) {
        if (application==null) throw new IllegalStateException("Application resources are not initialized");
        return args.length==0 ? application.getString(id) : application.getString(id,args);
    }
    static Locale locale() {
        return application==null ? Locale.getDefault() : application.getResources().getConfiguration().getLocales().get(0);
    }
    static String ticketTitle(Models.Ticket ticket) {
        return switch (ticket.type()) {
            case "codex_rate_limits" -> t(R.string.reset_full);
            case "codex_weekly" -> t(R.string.reset_weekly);
            case "codex_five_hour" -> t(R.string.reset_five_hour);
            default -> ticket.title().isEmpty() ? t(R.string.reset_generic) : ticket.title();
        };
    }
}
