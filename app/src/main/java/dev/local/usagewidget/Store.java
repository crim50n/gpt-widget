package dev.local.usagewidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Store {
    static final Object LOCK = new Object();
    private static final String ALIAS = "usage-widget-session-v1";
    private final SharedPreferences prefs;
    private final SecretKey suppliedKey;
    Store(Context context) { this(context,null); }
    // Package-private key injection permits storage tests without exporting a production key.
    Store(Context context, SecretKey key) {
        prefs = context.getSharedPreferences("usage", Context.MODE_PRIVATE); suppliedKey = key;
    }
    record Credentials(String token, String account, String revision, String refreshToken, long expiresAt, String method) {
        Credentials(String token, String account, String revision) { this(token,account,revision,"",0,"webview"); }
        boolean renewable() { return "device".equals(method) && !refreshToken.isEmpty(); }
        @Override public String toString() { return "Credentials[redacted]"; }
    }

    boolean connected() { return prefs.contains("auth"); }
    String revision() { return prefs.getString("revision", ""); }
    String account() {
        try { Credentials auth = credentials(); return auth == null ? "" : auth.account(); }
        catch (Exception ignored) { return ""; }
    }
    private SecretKey key() throws Exception {
        if (suppliedKey != null) return suppliedKey;
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return gen.generateKey();
    }
    void saveCredentials(String token, String account) throws Exception {
        OAuthClient.checkSecret(token);
        if (account == null || !account.matches("[A-Za-z0-9_-]{1,200}")) account = "";
        synchronized (LOCK) {
            writeCredentials(new Credentials(token,account,UUID.randomUUID().toString()),true,null);
        }
    }
    private void writeCredentials(Credentials auth, boolean clear, AuthSession.Verified verified) throws Exception {
        OAuthClient.checkSecret(auth.token());
        if (auth.renewable()) OAuthClient.checkSecret(auth.refreshToken());
        JSONObject payload = new JSONObject().put("token",auth.token()).put("account",auth.account())
                .put("refresh",auth.refreshToken()).put("expires",auth.expiresAt()).put("method",auth.method());
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted = cipher.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));
        SharedPreferences.Editor edit = prefs.edit(); if (clear) edit.clear();
        edit.putString("auth",Base64.encodeToString(encrypted,Base64.NO_WRAP))
                .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
                .putString("revision",auth.revision()).putString("method_ui",auth.method()).remove("reauth_required");
        if (verified != null) {
            edit.putString("usage",verified.usage().toString()).putLong("usage_time",verified.usageTime())
                    .putLong("usage_offset",verified.usageOffset())
                    .putString("resets",verified.resets().toString()).putLong("resets_time",verified.resetsTime())
                    .putLong("resets_offset",verified.resetsOffset());
        }
        if (!edit.commit()) throw new IllegalStateException("Storage failed");
    }
    boolean activate(AuthSession.Verified verified, String expectedRevision) throws Exception {
        synchronized (LOCK) {
            if (!revision().equals(expectedRevision)) return false;
            Credentials candidate = verified.auth();
            writeCredentials(new Credentials(candidate.token(),candidate.account(),UUID.randomUUID().toString(),
                    candidate.refreshToken(),candidate.expiresAt(),candidate.method()),true,verified);
            return true;
        }
    }
    boolean rotate(Credentials expected, OAuthClient.Tokens tokens) throws Exception {
        synchronized (LOCK) {
            Credentials current = credentials();
            if (current == null || !current.revision().equals(expected.revision()) || !current.token().equals(expected.token())
                    || !current.refreshToken().equals(expected.refreshToken())) return false;
            writeCredentials(new Credentials(tokens.access(),current.account(),current.revision(),tokens.refresh(),tokens.expiresAt(),"device"),false,null);
            return true;
        }
    }
    boolean reauthRequired() { return prefs.getBoolean("reauth_required",false); }
    void requireReauth(String revision) {
        synchronized (LOCK) {
            if (revision().equals(revision)) prefs.edit().putBoolean("reauth_required",true).apply();
        }
    }
    Credentials credentials() throws Exception {
        synchronized (LOCK) {
            if (!connected()) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128,
                    Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)));
            JSONObject obj = new JSONObject(new String(cipher.doFinal(Base64.decode(prefs.getString("auth", ""), Base64.NO_WRAP)), StandardCharsets.UTF_8));
            return new Credentials(obj.getString("token"), obj.optString("account"), prefs.getString("revision", ""),
                    obj.optString("refresh",""),obj.optLong("expires",0),obj.optString("method","webview"));
        }
    }
    @android.annotation.SuppressLint("ApplySharedPref") // Persist logout before returning to the UI.
    void clear() {
        synchronized (LOCK) {
            prefs.edit().clear().putString("revision",UUID.randomUUID().toString()).commit();
            if (suppliedKey != null) return;
            try { KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null); ks.deleteEntry(ALIAS); }
            catch (Exception ignored) { }
        }
    }
    void success(String group, JSONObject data, long offset, String revision) {
        synchronized (LOCK) {
            if (!revision.equals(prefs.getString("revision", ""))) return;
            prefs.edit().putString(group, data.toString()).putLong(group + "_time", System.currentTimeMillis())
                    .putLong(group + "_offset", offset).remove(group + "_error").apply();
        }
    }
    void error(String group, String message, String revision) {
        synchronized (LOCK) {
            if (revision.equals(prefs.getString("revision", ""))) prefs.edit().putString(group + "_error", message).apply();
        }
    }
    JSONObject data(String group) {
        try { return new JSONObject(prefs.getString(group, "")); } catch (Exception e) { return null; }
    }
    long time(String group) { return prefs.getLong(group + "_time", 0); }
    long now(String group) { return System.currentTimeMillis() + prefs.getLong(group + "_offset", 0); }
    String error(String group) { return prefs.getString(group + "_error", ""); }
}
