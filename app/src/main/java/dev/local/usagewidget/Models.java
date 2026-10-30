package dev.local.usagewidget;

import static dev.local.usagewidget.UiText.t;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Only these whitelisted fields are cached; never the complete API response. */
final class Models {
    record Window(double used, long duration, long resetAt) {
        int remaining() { return (int) Math.round(Math.max(0, Math.min(100, 100 - used))); }
    }
    record Usage(List<Window> windows, boolean reached, Boolean allowed, String balance,
                 boolean unlimited) {
        Window week() { return windows.stream().filter(w -> w.duration == 604800).findFirst().orElse(null); }
        JSONObject json() throws JSONException {
            JSONArray array = new JSONArray();
            for (Window w : windows) array.put(new JSONObject().put("used_percent", w.used)
                    .put("limit_window_seconds", w.duration).put("reset_at", w.resetAt));
            return new JSONObject().put("windows", array).put("reached", reached)
                    .put("allowed", allowed == null ? JSONObject.NULL : allowed)
                    .put("balance", balance == null ? JSONObject.NULL : balance).put("unlimited", unlimited);
        }
        String creditLabel() {
            if (unlimited) return t(R.string.unlimited);
            try { return new BigDecimal(balance).max(BigDecimal.ZERO).setScale(0, RoundingMode.CEILING).toPlainString(); }
            catch (Exception e) { return t(R.string.unavailable); }
        }
    }
    record Ticket(String id, String title, String type, String status, boolean supported, Instant expires) { }
    record Resets(int count, List<Ticket> tickets) {
        Instant nearest() {
            return tickets.stream().filter(t -> t.supported && "available".equals(t.status))
                    .map(Ticket::expires).filter(t -> t != null).min(Comparator.naturalOrder()).orElse(null);
        }
        JSONObject json() throws JSONException {
            JSONArray array = new JSONArray();
            for (Ticket t : tickets) array.put(new JSONObject().put("id", t.id).put("title", t.title).put("reset_type", t.type)
                    .put("status", t.status).put("is_supported_by_plan", t.supported)
                    .put("expires_at", t.expires == null ? JSONObject.NULL : t.expires.toString()));
            return new JSONObject().put("available_count", count).put("credits", array);
        }
    }

    static Usage parseUsage(JSONObject root) throws JSONException {
        JSONObject rate = root.getJSONObject("rate_limit");
        List<Window> windows = new ArrayList<>();
        addWindow(windows, rate.optJSONObject("primary_window"));
        addWindow(windows, rate.optJSONObject("secondary_window"));
        JSONObject credits = root.optJSONObject("credits");
        return new Usage(windows, rate.optBoolean("limit_reached", false),
                rate.isNull("allowed") ? null : rate.getBoolean("allowed"),
                credits == null || credits.isNull("balance") ? null : credits.getString("balance"),
                credits != null && credits.optBoolean("unlimited", false));
    }

    static Usage cachedUsage(JSONObject root) throws JSONException {
        List<Window> windows = new ArrayList<>();
        JSONArray array = root.getJSONArray("windows");
        for (int i = 0; i < array.length(); i++) addWindow(windows, array.getJSONObject(i));
        return new Usage(windows, root.getBoolean("reached"), root.isNull("allowed") ? null : root.getBoolean("allowed"),
                root.isNull("balance") ? null : root.getString("balance"), root.getBoolean("unlimited"));
    }

    private static void addWindow(List<Window> windows, JSONObject w) throws JSONException {
        if (w == null) return;
        double used = w.getDouble("used_percent");
        long duration = w.getLong("limit_window_seconds");
        long reset = w.getLong("reset_at");
        if (!Double.isFinite(used) || duration <= 0 || reset <= 0) throw new JSONException("Invalid window");
        windows.add(new Window(used, duration, reset));
    }

    static Resets parseResets(JSONObject root) throws JSONException {
        int count = root.getInt("available_count");
        if (count < 0) throw new JSONException("Invalid count");
        JSONArray array = root.getJSONArray("credits");
        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject t = array.getJSONObject(i);
            Instant expires = null;
            if (!t.isNull("expires_at")) {
                try { expires = Instant.parse(t.getString("expires_at")); }
                catch (RuntimeException e) { throw new JSONException("Invalid expiry"); }
            }
            tickets.add(new Ticket(t.optString("id", ""), t.optString("title", ""), t.getString("reset_type"),
                    t.getString("status"), t.optBoolean("is_supported_by_plan", false), expires));
        }
        return new Resets(count, tickets);
    }
}
