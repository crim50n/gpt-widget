package dev.local.usagewidget;

import static dev.local.usagewidget.UiText.t;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

final class Api {
    record Response(JSONObject json, long offset) { }
    static class Failure extends Exception {
        final int status;
        Failure(String safeMessage) { this(safeMessage,0); }
        Failure(String safeMessage, int status) { super(safeMessage); this.status = status; }
    }
    record ConsumeResponse(int status, JSONObject json) { }
    static ConsumeResponse consume(Store.Credentials auth, JSONObject body) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL("https://chatgpt.com/backend-api/wham/rate-limit-reset-credits/consume").openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Authorization", "Bearer " + auth.token());
            if (!auth.account().isEmpty()) connection.setRequestProperty("ChatGPT-Account-ID", auth.account());
            connection.setRequestProperty("OAI-Language", UiText.locale().toLanguageTag());
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true); connection.setFixedLengthStreamingMode(bytes.length);
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int status = connection.getResponseCode();
            String type = connection.getContentType();
            if (type == null || !type.toLowerCase(java.util.Locale.ROOT).contains("application/json"))
                throw new Failure(t(R.string.error_not_json_http,status));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) throw new Failure(t(R.string.error_empty_response));
            try (InputStream input = stream) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 1024 * 1024) throw new Failure(t(R.string.error_response_too_large));
                    output.write(buffer,0,count);
                }
            }
            return new ConsumeResponse(status, new JSONObject(output.toString(StandardCharsets.UTF_8.name())));
        } finally { connection.disconnect(); }
    }
    static Response get(String path, Store.Credentials auth) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL("https://chatgpt.com/backend-api/wham/" + path).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + auth.token());
            if (!auth.account().isEmpty()) connection.setRequestProperty("ChatGPT-Account-ID", auth.account());
            connection.setRequestProperty("OAI-Language", UiText.locale().toLanguageTag());
            int code = connection.getResponseCode();
            if (code == 401) throw new Failure(t(R.string.error_session_expired),401);
            if (code == 403) throw new Failure(t(R.string.error_forbidden),403);
            if (code == 429) throw new Failure(t(R.string.error_rate_limited),429);
            if (code != 200) throw new Failure(t(R.string.error_server_http,code),code);
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).contains("application/json"))
                throw new Failure(t(R.string.error_not_json));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (bytes.size() + n > 1024 * 1024) throw new Failure(t(R.string.error_response_too_large));
                    bytes.write(buffer, 0, n);
                }
            }
            long server = connection.getDate();
            long offset = server > 0 ? server - System.currentTimeMillis() : 0;
            return new Response(new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())), offset);
        } finally { connection.disconnect(); }
    }
}
