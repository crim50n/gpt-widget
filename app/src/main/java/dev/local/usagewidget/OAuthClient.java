package dev.local.usagewidget;

import static dev.local.usagewidget.UiText.t;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;

/** Codex legacy device authorization, kept separate from dynamic SIWC credentials. */
final class OAuthClient {
    static final String CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann";
    static final String VERIFY_URL = "https://auth.openai.com/codex/device";
    static final String USER_CODE = "https://auth.openai.com/api/accounts/deviceauth/usercode";
    static final String POLL = "https://auth.openai.com/api/accounts/deviceauth/token";
    static final String TOKEN = "https://auth.openai.com/oauth/token";
    record Device(String authId, String code, int intervalSeconds) {
        @Override public String toString() { return "DeviceAuthorization[redacted]"; }
    }
    record Tokens(String access, String refresh, long expiresAt, String accountHint) {
        @Override public String toString() { return "OAuthTokens[redacted]"; }
    }
    record Reply(int status, JSONObject json) {
        @Override public String toString() { return "OAuthReply[status=" + status + "]"; }
    }
    static class Failure extends Api.Failure {
        final boolean permanent;
        Failure(String message, boolean permanent) { super(message); this.permanent = permanent; }
    }
    static class SlowDown extends Exception { }
    interface Transport { Reply post(String endpoint, boolean form, Map<String,String> fields) throws Exception; }
    private final Transport transport;
    OAuthClient() { this(OAuthClient::post); }
    OAuthClient(Transport transport) { this.transport = transport; }

    Device start() throws Exception {
        Reply response = transport.post(USER_CODE,false,fields("client_id",CLIENT_ID));
        if (response.status() != 200) throw failure(response.status(),false);
        JSONObject json = response.json();
        String id = json.getString("device_auth_id");
        String code = json.optString("user_code",json.optString("usercode",""));
        if (id.isEmpty() || id.length() > 4096 || !code.matches("[A-Za-z0-9 -]{3,64}"))
            throw new Failure(t(R.string.oauth_unknown_code),false);
        int interval;
        try { interval = Integer.parseInt(json.optString("interval","5").trim()); }
        catch (NumberFormatException e) { throw new Failure(t(R.string.oauth_unknown_interval),false); }
        if (interval < 0 || interval > 900) throw new Failure(t(R.string.oauth_invalid_interval),false);
        return new Device(id,code,Math.max(3,interval));
    }
    Tokens poll(Device device) throws Exception {
        return poll(device,() -> {});
    }
    interface BeforeExchange { void run() throws Exception; }
    Tokens poll(Device device, BeforeExchange beforeExchange) throws Exception {
        Reply response = transport.post(POLL,false,fields("device_auth_id",device.authId(),"user_code",device.code()));
        // These waiting statuses belong ONLY to the Codex device-auth endpoint.
        if (response.status() == 403 || response.status() == 404) return null;
        if (response.status() == 429) throw new SlowDown();
        if (response.status() != 200) throw failure(response.status(),false);
        String code = response.json().getString("authorization_code");
        String verifier = response.json().getString("code_verifier");
        if (code.isEmpty() || code.length() > 65536 || !verifier.matches("[A-Za-z0-9._~-]{43,128}"))
            throw new Failure(t(R.string.oauth_invalid_response),false);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        beforeExchange.run();
        Reply exchanged = transport.post(TOKEN,true,fields(
                "grant_type","authorization_code", "client_id",CLIENT_ID, "code",code,
                "code_verifier",verifier, "redirect_uri","https://auth.openai.com/deviceauth/callback"));
        if (exchanged.status() != 200) throw failure(exchanged.status(),true);
        return tokens(exchanged.json(),System.currentTimeMillis(),"");
    }
    Tokens refresh(String refreshToken) throws Exception {
        checkSecret(refreshToken);
        Reply response = transport.post(TOKEN,true,fields("grant_type","refresh_token", "client_id",CLIENT_ID,"refresh_token",refreshToken));
        if (response.status() != 200) throw failure(response.status(),true);
        return tokens(response.json(),System.currentTimeMillis(),refreshToken);
    }
    static Tokens tokens(JSONObject json, long now, String previousRefresh) throws Exception {
        String access = json.getString("access_token");
        String refresh = json.optString("refresh_token",previousRefresh);
        checkSecret(access); checkSecret(refresh);
        long expiry;
        if (json.has("expires_in")) {
            long seconds = json.getLong("expires_in");
            if (seconds <= 0 || seconds > 365L*86400) throw new Failure(t(R.string.oauth_invalid_expiry),false);
            expiry = now + seconds * 1000;
        } else {
            long exp = claims(access).optLong("exp",0);
            expiry = exp > 0 && exp < Long.MAX_VALUE/1000 ? exp*1000 : now + 3600000;
        }
        if (expiry <= now) throw new Failure(t(R.string.oauth_expired_token),true);
        String hint = accountHint(claims(json.optString("id_token","")));
        if (hint.isEmpty()) hint = accountHint(claims(access));
        return new Tokens(access,refresh,expiry,hint);
    }
    // Claims are hints only. Account identity is confirmed by authenticated /wham/usage.
    private static JSONObject claims(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length != 3 || parts[1].length() > 65536) return new JSONObject();
            return new JSONObject(new String(Base64.getUrlDecoder().decode(parts[1]),StandardCharsets.UTF_8));
        } catch (Exception ignored) { return new JSONObject(); }
    }
    private static String accountHint(JSONObject claims) {
        JSONObject auth = claims.optJSONObject("https://api.openai.com/auth");
        String id = auth == null ? claims.optString("chatgpt_account_id","") : auth.optString("chatgpt_account_id","");
        return validAccount(id) ? id : "";
    }
    static boolean validAccount(String id) { return id != null && id.matches("[A-Za-z0-9_-]{1,200}"); }
    static void checkSecret(String value) {
        if (value == null || value.isEmpty() || value.length() > 65536 || !value.matches("[\\x21-\\x7E]+"))
            throw new IllegalArgumentException("Invalid credential");
    }
    private static Map<String,String> fields(String... values) {
        Map<String,String> fields = new LinkedHashMap<>();
        for (int i=0;i<values.length;i+=2) fields.put(values[i],values[i+1]);
        return fields;
    }
    private static Failure failure(int status, boolean tokenExchange) {
        if (status == 400 || status == 401)
            return new Failure(t(R.string.oauth_rejected),tokenExchange);
        if (status == 403 || status == 404)
            return new Failure(t(R.string.oauth_device_unavailable),tokenExchange);
        if (status == 429) return new Failure(t(R.string.oauth_too_many_attempts),false);
        return new Failure(t(R.string.oauth_server_error,status),false);
    }
    private static Reply post(String endpoint, boolean form, Map<String,String> fields) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (!endpoint.equals(USER_CODE) && !endpoint.equals(POLL) && !endpoint.equals(TOKEN)) throw new IllegalArgumentException("Unknown auth endpoint");
        HttpsURLConnection connection = (HttpsURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setRequestMethod("POST"); connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("User-Agent","UsageWidget Android");
            connection.setRequestProperty("Content-Type",form ? "application/x-www-form-urlencoded" : "application/json");
            String body;
            if (form) {
                StringBuilder encoded = new StringBuilder();
                for (Map.Entry<String,String> entry : fields.entrySet()) {
                    if (encoded.length()>0) encoded.append('&');
                    encoded.append(URLEncoder.encode(entry.getKey(),"UTF-8")).append('=')
                            .append(URLEncoder.encode(entry.getValue(),"UTF-8"));
                }
                body = encoded.toString();
            } else body = new JSONObject(new LinkedHashMap<>(fields)).toString();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true); connection.setFixedLengthStreamingMode(bytes.length);
            try (java.io.OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            int status = connection.getResponseCode();
            // Error bodies can echo credentials: never expose or log them.
            if (status != 200) return new Reply(status,new JSONObject());
            String type = connection.getContentType();
            if (type == null || !type.toLowerCase(java.util.Locale.ROOT).contains("application/json"))
                throw new Failure(t(R.string.oauth_html_response),false);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count=in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (out.size()+count>1024*1024) throw new Failure(t(R.string.oauth_response_too_large),false);
                    out.write(buffer,0,count);
                }
            }
            return new Reply(status,new JSONObject(out.toString("UTF-8")));
        } finally { connection.disconnect(); }
    }
}
