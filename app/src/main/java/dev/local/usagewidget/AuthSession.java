package dev.local.usagewidget;

import static dev.local.usagewidget.UiText.t;

import android.content.Context;
import org.json.JSONObject;

final class AuthSession {
    private static final Object REFRESH_LOCK = new Object();
    record Verified(Store.Credentials auth, JSONObject usage, JSONObject resets, long usageOffset,
                    long resetsOffset, long usageTime, long resetsTime) {
        @Override public String toString() { return "VerifiedSession[redacted]"; }
    }
    record Read(Api.Response response, Store.Credentials auth) { }
    interface Reader { Api.Response get(String path, Store.Credentials auth) throws Exception; }

    static Verified verify(Store.Credentials candidate) throws Exception { return verify(candidate,Api::get); }
    static Verified verify(Store.Credentials candidate, Reader reader) throws Exception {
        Api.Response usage = reader.get("usage",candidate);
        long usageTime = System.currentTimeMillis();
        JSONObject cleanUsage = Models.parseUsage(usage.json()).json();
        String account = usage.json().optString("account_id","");
        if (!OAuthClient.validAccount(account) || (!candidate.account().isEmpty() && !candidate.account().equals(account)))
            throw new Api.Failure(t(R.string.error_account_verification));
        Store.Credentials verified = new Store.Credentials(candidate.token(),account,candidate.revision(),
                candidate.refreshToken(),candidate.expiresAt(),candidate.method());
        Api.Response resets = reader.get("rate-limit-reset-credits",verified);
        long resetsTime = System.currentTimeMillis();
        return new Verified(verified,cleanUsage,Models.parseResets(resets.json()).json(),usage.offset(),resets.offset(),usageTime,resetsTime);
    }
    static Store.Credentials current(Context context) throws Exception { return current(context,null); }
    private static Store.Credentials current(Context context, String rejectedToken) throws Exception {
        return current(new Store(context),new OAuthClient(),rejectedToken);
    }
    static Store.Credentials current(Store store, OAuthClient oauth, String rejectedToken) throws Exception {
        synchronized (REFRESH_LOCK) {
            Store.Credentials auth = store.credentials();
            if (auth == null || !auth.renewable()) return auth;
            if (store.reauthRequired()) throw new Api.Failure(t(R.string.error_session_expired));
            boolean rejected = rejectedToken != null && rejectedToken.equals(auth.token());
            if (!rejected && auth.expiresAt() > System.currentTimeMillis()+60000) return auth;
            try {
                OAuthClient.Tokens next = oauth.refresh(auth.refreshToken());
                if (!next.accountHint().isEmpty() && !next.accountHint().equals(auth.account())) {
                    store.requireReauth(auth.revision());
                    throw new Api.Failure(t(R.string.error_account_changed_refresh));
                }
                if (!store.rotate(auth,next)) throw new Api.Failure(t(R.string.error_connection_changed_refresh));
                return store.credentials();
            } catch (OAuthClient.Failure e) {
                if (e.permanent) store.requireReauth(auth.revision());
                throw e;
            }
        }
    }
    static Read read(Context context, String path) throws Exception {
        Store.Credentials auth = current(context);
        if (auth == null) throw new Api.Failure(t(R.string.error_connect_account));
        Api.Response response;
        try { response = Api.get(path,auth); }
        catch (Api.Failure failure) {
            if (failure.status != 401 || !auth.renewable()) throw failure;
            Store.Credentials renewed = current(context,auth.token());
            if (renewed == null || !renewed.revision().equals(auth.revision())) throw new Api.Failure(t(R.string.error_connection_changed));
            auth = renewed;
            try { response = Api.get(path,auth); } // One read-only retry; never automatically repeat consume.
            catch (Api.Failure retryFailure) {
                if (retryFailure.status == 401) new Store(context).requireReauth(auth.revision());
                throw retryFailure;
            }
        }
        String account = response.json().optString("account_id","");
        if (!account.isEmpty() && !auth.account().isEmpty() && !account.equals(auth.account()))
            throw new Api.Failure(t(R.string.error_wrong_account));
        return new Read(response,auth);
    }
}
