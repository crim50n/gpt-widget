package dev.local.usagewidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Durable authorization draft, isolated from the active session and never stored in Activity state. */
final class PendingLoginStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences prefs;
    private final SecretKey suppliedKey;
    record Draft(String id, String revision, OAuthClient.Device device, long expiresAt,
                 String phase, OAuthClient.Tokens tokens) {
        Draft phase(String next, OAuthClient.Tokens value) { return new Draft(id,revision,device,expiresAt,next,value); }
        @Override public String toString() { return "PendingLogin[redacted]"; }
    }
    PendingLoginStore(Context context) { this(context,null); }
    PendingLoginStore(Context context, SecretKey key) { prefs=context.getSharedPreferences("pending-browser-login",0); suppliedKey=key; }
    boolean exists() { return prefs.contains("encrypted"); }
    private SecretKey key() throws Exception {
        if (suppliedKey != null) return suppliedKey;
        String alias="usage-widget-device-login-v1";
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(alias)) return (SecretKey) store.getKey(alias,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    Draft load() throws Exception {
        synchronized (LOCK) {
            if (!exists()) return null;
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
            JSONObject json=new JSONObject(new String(cipher.doFinal(Base64.decode(prefs.getString("encrypted",""),Base64.NO_WRAP)),StandardCharsets.UTF_8));
            OAuthClient.Device device=new OAuthClient.Device(json.getString("device_id"),json.getString("code"),json.getInt("interval"));
            OAuthClient.Tokens tokens=null;
            JSONObject t=json.optJSONObject("tokens");
            if (t != null) tokens=new OAuthClient.Tokens(t.getString("access"),t.getString("refresh"),t.getLong("expires"),t.getString("account"));
            return new Draft(json.getString("id"),json.getString("revision"),device,json.getLong("deadline"),json.getString("phase"),tokens);
        }
    }
    void create(Draft draft) throws Exception { synchronized (LOCK) { write(draft); } }
    boolean update(Draft draft) throws Exception {
        synchronized (LOCK) {
            Draft current=load();
            if (current==null || !current.id().equals(draft.id())) return false;
            write(draft); return true;
        }
    }
    private void write(Draft draft) throws Exception {
        JSONObject json=new JSONObject().put("id",draft.id()).put("revision",draft.revision())
                .put("device_id",draft.device().authId()).put("code",draft.device().code()).put("interval",draft.device().intervalSeconds())
                .put("deadline",draft.expiresAt()).put("phase",draft.phase());
        if (draft.tokens()!=null) {
            OAuthClient.Tokens t=draft.tokens();
            json.put("tokens",new JSONObject().put("access",t.access()).put("refresh",t.refresh()).put("expires",t.expiresAt()).put("account",t.accountHint()));
        }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8));
        if (!prefs.edit().putString("encrypted",Base64.encodeToString(encrypted,Base64.NO_WRAP))
                .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).commit()) throw new IllegalStateException("Pending login storage failed");
    }
    void clear(String id) throws Exception {
        synchronized (LOCK) {
            Draft current=load();
            if (current!=null && current.id().equals(id)) prefs.edit().clear().commit();
        }
    }
    void clear() { synchronized (LOCK) { prefs.edit().clear().commit(); } }
}
