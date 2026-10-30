package dev.local.usagewidget;

import android.content.Context;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import javax.crypto.spec.SecretKeySpec;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class AuthSessionTest {
    private Store store() { return new Store(RuntimeEnvironment.getApplication(),new SecretKeySpec(new byte[32],"AES")); }
    private Api.Response reply(String path, Store.Credentials auth) throws Exception {
        if (path.equals("usage")) return new Api.Response(new JSONObject("{\"account_id\":\"acct-a\",\"email\":\"private@example.test\",\"rate_limit\":{\"allowed\":true,\"primary_window\":{\"used_percent\":21,\"limit_window_seconds\":604800,\"reset_at\":1993294786}},\"credits\":{\"balance\":\"0\"}}"),1000);
        assertEquals("acct-a",auth.account());
        return new Api.Response(new JSONObject("{\"available_count\":0,\"credits\":[]}"),2000);
    }
    private AuthSession.Verified verified(long expiry) throws Exception {
        return AuthSession.verify(new Store.Credentials("new-access","","","refresh-a",expiry,"device"),this::reply);
    }
    @Test public void validationDoesNotTouchActiveAccountUntilBothGetsSucceed() throws Exception {
        Store store = store(); store.saveCredentials("old-access","old-account");
        String revision = store.revision();
        try {
            AuthSession.verify(new Store.Credentials("new-access","","","refresh-a",9999999999999L,"device"),(path,auth) -> {
                if (!path.equals("usage")) throw new Api.Failure("Denied",403);
                return reply(path,auth);
            }); fail();
        } catch (Api.Failure expected) { }
        assertEquals("old-access",store.credentials().token()); assertEquals(revision,store.revision());
        AuthSession.Verified candidate = verified(System.currentTimeMillis()+3600000);
        assertEquals("old-access",store.credentials().token());
        assertTrue(store.activate(candidate,revision));
        assertEquals("new-access",store.credentials().token()); assertEquals("acct-a",store.credentials().account());
        assertTrue(store.credentials().renewable());
        assertFalse(store.data("usage").toString().contains("private@example"));
        assertNotNull(store.data("resets"));
    }
    @Test public void accountHintMustMatchAuthenticatedUsage() throws Exception {
        try { AuthSession.verify(new Store.Credentials("test","wrong-account",""),this::reply); fail(); }
        catch (Api.Failure expected) { }
    }
    @Test public void logoutAndAnotherLoginInvalidatePendingActivation() throws Exception {
        Store store=store(); String revision=store.revision();
        AuthSession.Verified candidate=verified(System.currentTimeMillis()+3600000);
        store.clear(); assertFalse(store.activate(candidate,revision)); assertFalse(store.connected());
        revision=store.revision(); store.saveCredentials("other-token","other-account");
        assertFalse(store.activate(candidate,revision)); assertEquals("other-account",store.credentials().account());
    }
    @Test public void rotationKeepsCacheAndAccountButReplacesBothSecrets() throws Exception {
        Store store=store(); store.activate(verified(1),store.revision());
        Store.Credentials before=store.credentials(); String cache=store.data("usage").toString();
        assertTrue(store.rotate(before,new OAuthClient.Tokens("rotated-access","rotated-refresh",9999999999999L,"acct-a")));
        Store.Credentials after=store.credentials();
        assertEquals(before.revision(),after.revision()); assertEquals("rotated-refresh",after.refreshToken());
        assertEquals(cache,store.data("usage").toString());
        assertFalse(store.rotate(before,new OAuthClient.Tokens("stale","stale",9999999999999L,"acct-a")));
        String stored=RuntimeEnvironment.getApplication().getSharedPreferences("usage",0).getAll().toString();
        assertFalse(stored.contains("rotated-access")); assertFalse(stored.contains("rotated-refresh"));
    }
    @Test public void concurrentRefreshesUseSingleRotatingTokenExchange() throws Exception {
        Store store=store(); store.activate(verified(1),store.revision()); AtomicInteger calls=new AtomicInteger();
        OAuthClient client=new OAuthClient((url,form,fields) -> {
            calls.incrementAndGet();
            return new OAuthClient.Reply(200,new JSONObject("{\"access_token\":\"rotated-access\",\"refresh_token\":\"rotated-refresh\",\"expires_in\":3600}"));
        });
        java.util.concurrent.ExecutorService threads=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<Store.Credentials> first=threads.submit(() -> AuthSession.current(store,client,null));
            java.util.concurrent.Future<Store.Credentials> second=threads.submit(() -> AuthSession.current(store,client,null));
            assertEquals("rotated-access",first.get().token()); assertEquals("rotated-access",second.get().token());
            assertEquals(1,calls.get());
        } finally { threads.shutdownNow(); }
    }
    @Test public void permanentRefreshFailurePreservesCacheAndStopsRepeatedRefresh() throws Exception {
        Store store=store(); store.activate(verified(1),store.revision()); AtomicInteger calls=new AtomicInteger();
        OAuthClient client=new OAuthClient((url,form,fields) -> { calls.incrementAndGet(); return new OAuthClient.Reply(401,new JSONObject()); });
        for (int i=0;i<2;i++) {
            try { AuthSession.current(store,client,null); fail(); } catch (Api.Failure expected) { }
        }
        assertEquals(1,calls.get()); assertTrue(store.connected()); assertNotNull(store.data("usage"));
        assertTrue(store.reauthRequired());
    }
    @Test public void webViewCredentialsDoNotRefresh() throws Exception {
        Store store=store(); store.saveCredentials("web-access","acct-a");
        OAuthClient client=new OAuthClient((url,form,fields) -> { throw new AssertionError("Unexpected OAuth call"); });
        assertEquals("web-access",AuthSession.current(store,client,null).token());
        assertFalse(store.credentials().renewable());
    }
}
