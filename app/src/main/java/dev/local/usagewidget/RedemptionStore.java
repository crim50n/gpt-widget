package dev.local.usagewidget;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/** Separate from the auth cache: a new login must not discard an uncertain request ID. */
final class RedemptionStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences prefs;
    RedemptionStore(Context context) { prefs = context.getSharedPreferences("redemption-attempts",0); }
    private String key(String account) {
        if (account == null || account.isEmpty()) throw new IllegalArgumentException("Account required");
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(account.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte b : hash) key.append(String.format(java.util.Locale.ROOT,"%02x", b & 255));
            return key.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    Redemption.Attempt pending(String account) {
        synchronized (LOCK) {
            String value = prefs.getString(key(account),null);
            if (value == null) return null;
            try {
                JSONObject json = new JSONObject(value);
                return new Redemption.Attempt(json.getString("credit_id"),json.getString("redeem_request_id"));
            } catch (Exception e) { throw new IllegalStateException("Unreadable pending operation",e); }
        }
    }
    Redemption.Attempt begin(String account, String credit) throws Exception {
        synchronized (LOCK) {
            Redemption.Attempt existing = pending(account);
            if (existing != null) {
                if (!existing.creditId().equals(credit)) throw new IllegalStateException("Another operation is pending");
                return existing;
            }
            Redemption.Attempt attempt = new Redemption.Attempt(credit,UUID.randomUUID().toString());
            if (!prefs.edit().putString(key(account),attempt.body().toString()).commit())
                throw new IllegalStateException("Cannot persist operation");
            return attempt;
        }
    }
    void complete(String account, Redemption.Attempt attempt) {
        synchronized (LOCK) {
            Redemption.Attempt stored = pending(account);
            if (stored != null && stored.equals(attempt) && !prefs.edit().remove(key(account)).commit())
                throw new IllegalStateException("Cannot persist result");
        }
    }
}
