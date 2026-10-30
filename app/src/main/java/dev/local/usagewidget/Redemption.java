package dev.local.usagewidget;

import static dev.local.usagewidget.UiText.t;

import org.json.JSONException;
import org.json.JSONObject;

/** Only codes confirmed in the web client are treated as definitive outcomes. */
final class Redemption {
    record Attempt(String creditId, String requestId) {
        JSONObject body() throws JSONException {
            return new JSONObject().put("credit_id", creditId).put("redeem_request_id", requestId);
        }
    }
    record Outcome(boolean success, boolean definitive, String message) { }
    static Outcome interpret(int http, JSONObject body, boolean retry) {
        String code = body.optString("code", "");
        if (http >= 200 && http < 500 && http != 401 && http != 403 && http != 429) {
            if ("reset".equals(code) && http < 300)
                return new Outcome(true,true,t(R.string.reset_success));
            if ("already_redeemed".equals(code))
                return retry ? new Outcome(true,true,t(R.string.reset_previous_confirmed))
                        : new Outcome(false,true,t(R.string.reset_already_used));
            if ("no_credit".equals(code)) return new Outcome(false,true,t(R.string.reset_none_available));
            if ("nothing_to_reset".equals(code)) return new Outcome(false,true,t(R.string.reset_nothing_to_reset));
        }
        String reason = http == 401 ? t(R.string.error_session_expired)
                : http == 403 ? t(R.string.error_server_denied)
                : http == 429 ? t(R.string.error_rate_limited)
                : t(R.string.reset_unknown_response,http);
        return new Outcome(false,false,t(R.string.reset_retry_same_id,reason));
    }
}
