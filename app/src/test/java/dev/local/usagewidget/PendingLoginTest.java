package dev.local.usagewidget;

import android.content.Context;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import javax.crypto.spec.SecretKeySpec;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class PendingLoginTest {
    private final SecretKeySpec key=new SecretKeySpec(new byte[32],"AES");
    private Context context() { return RuntimeEnvironment.getApplication(); }
    private PendingLoginStore pending() { return new PendingLoginStore(context(),key); }
    private PendingLoginStore.Draft draft(Store active, String phase, OAuthClient.Tokens tokens) {
        return new PendingLoginStore.Draft("attempt-a",active.revision(),new OAuthClient.Device("private-device","ABCD-EFGH",3),
                System.currentTimeMillis()+900000,phase,tokens);
    }
    private OAuthClient.Tokens tokens() { return new OAuthClient.Tokens("private-access","private-refresh",System.currentTimeMillis()+3600000,""); }
    private Api.Response response(String path, Store.Credentials auth) throws Exception {
        if (path.equals("usage")) return new Api.Response(new JSONObject("{\"account_id\":\"acct-a\",\"rate_limit\":{\"allowed\":true,\"primary_window\":{\"used_percent\":21,\"limit_window_seconds\":604800,\"reset_at\":1993294786}},\"credits\":{\"balance\":\"0\"}}"),0);
        return new Api.Response(new JSONObject("{\"available_count\":0,\"credits\":[]}"),0);
    }
    private OAuthClient noExchange() { return new OAuthClient((url,form,fields) -> { throw new AssertionError("Must not exchange an already received token"); }); }
    private void await(BrowserLoginActivity.Controller c, BrowserLoginActivity.Stage expected) throws Exception {
        long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (c.stage!=expected && System.nanoTime()<until) Thread.sleep(10);
        assertEquals(c.message,expected,c.stage);
    }
    @Test public void draftIsEncryptedAndSurvivesRecreation() throws Exception {
        Store active=new Store(context(),key);
        PendingLoginStore.Draft d=draft(active,"checking",tokens()); pending().create(d);
        assertEquals(d,pending().load());
        String disk=context().getSharedPreferences("pending-browser-login",0).getAll().toString();
        for (String secret : new String[]{"private-device","ABCD-EFGH","private-access","private-refresh"}) assertFalse(disk.contains(secret));
        PendingLoginStore.Draft replacement=new PendingLoginStore.Draft("attempt-b",active.revision(),d.device(),d.expiresAt(),"waiting",null);
        pending().create(replacement);
        assertFalse(pending().update(d)); pending().clear(d.id()); assertEquals(replacement,pending().load());
    }
    @Test public void receivedTokensResumeCheckingWithoutAnotherOAuthExchange() throws Exception {
        Store active=new Store(context(),key); active.saveCredentials("old-access","old-account");
        pending().create(draft(active,"checking",tokens()));
        BrowserLoginActivity.Controller recovered=new BrowserLoginActivity.Controller(context(),active,noExchange(),this::response,pending());
        try {
            assertEquals(BrowserLoginActivity.Stage.CHECKING,recovered.stage);
            recovered.resume(false); await(recovered,BrowserLoginActivity.Stage.SUCCESS);
            assertEquals("private-access",active.credentials().token()); assertEquals("acct-a",active.credentials().account());
            assertFalse(pending().exists());
        } finally { recovered.cancel(); }
    }
    @Test public void waitingDraftResumesTheSameDeviceCode() throws Exception {
        Store active=new Store(context(),key);
        pending().create(draft(active,"waiting",null));
        java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
        OAuthClient oauth=new OAuthClient((url,form,fields) -> {
            requests.incrementAndGet();
            assertNotEquals(OAuthClient.USER_CODE,url);
            if (url.equals(OAuthClient.POLL)) {
                assertEquals("private-device",fields.get("device_auth_id")); assertEquals("ABCD-EFGH",fields.get("user_code"));
                return new OAuthClient.Reply(200,new JSONObject().put("authorization_code","approved").put("code_verifier","A".repeat(43)));
            }
            return new OAuthClient.Reply(200,new JSONObject("{\"access_token\":\"private-access\",\"refresh_token\":\"private-refresh\",\"expires_in\":3600}"));
        });
        BrowserLoginActivity.Controller recovered=new BrowserLoginActivity.Controller(context(),active,oauth,this::response,pending());
        try {
            assertEquals("ABCD-EFGH",recovered.code); recovered.resume(false); await(recovered,BrowserLoginActivity.Stage.SUCCESS);
            assertEquals(2,requests.get()); assertFalse(pending().exists());
        } finally { recovered.cancel(); }
    }
    @Test public void failedApiCheckRetainsTokensAndExistingSessionForRetry() throws Exception {
        Store active=new Store(context(),key); active.saveCredentials("old-access","old-account");
        pending().create(draft(active,"checking",tokens()));
        BrowserLoginActivity.Controller failed=new BrowserLoginActivity.Controller(context(),active,noExchange(),(path,auth) -> {
            throw new Api.Failure("Доступ запрещён (403)",403);
        },pending());
        failed.resume(false); await(failed,BrowserLoginActivity.Stage.ERROR);
        assertEquals("old-access",active.credentials().token()); assertEquals("private-access",pending().load().tokens().access());
        assertTrue(failed.message.contains("3/4")); assertTrue(failed.message.contains("403"));
        BrowserLoginActivity.Controller retry=new BrowserLoginActivity.Controller(context(),active,noExchange(),this::response,pending());
        try { retry.resume(false); await(retry,BrowserLoginActivity.Stage.SUCCESS); }
        finally { retry.cancel(); }
    }
    @Test public void interruptedExchangeRequiresNewCodeRatherThanReusingIt() throws Exception {
        Store active=new Store(context(),key);
        pending().create(draft(active,"exchanging",null));
        BrowserLoginActivity.Controller recovered=new BrowserLoginActivity.Controller(context(),active,noExchange(),this::response,pending());
        assertEquals(BrowserLoginActivity.Stage.ERROR,recovered.stage);
        assertFalse(recovered.canResume()); recovered.resume(true);
        assertFalse(active.connected()); recovered.cancel(); assertFalse(pending().exists());
    }
    @Test public void changedAccountDiscardsStaleAttempt() throws Exception {
        Store active=new Store(context(),key); pending().create(draft(active,"checking",tokens()));
        active.saveCredentials("other-access","other-account");
        BrowserLoginActivity.Controller recovered=new BrowserLoginActivity.Controller(context(),active,noExchange(),this::response,pending());
        assertEquals(BrowserLoginActivity.Stage.ERROR,recovered.stage); assertFalse(pending().exists());
        assertEquals("other-access",active.credentials().token());
    }
    @Test public void transientVerificationFailureRetriesWithoutButtonPress() throws Exception {
        Store active=new Store(context(),key); active.saveCredentials("old-access","old-account");
        pending().create(draft(active,"checking",tokens()));
        java.util.concurrent.atomic.AtomicInteger usageReads=new java.util.concurrent.atomic.AtomicInteger();
        BrowserLoginActivity.Controller controller=new BrowserLoginActivity.Controller(context(),active,noExchange(),(path,auth) -> {
            if (path.equals("usage") && usageReads.incrementAndGet()==1) throw new Api.Failure("Temporary access delay",403);
            return response(path,auth);
        },pending());
        try {
            controller.resume(false); await(controller,BrowserLoginActivity.Stage.SUCCESS);
            assertEquals(2,usageReads.get()); assertEquals("private-access",active.credentials().token());
        } finally { controller.cancel(); }
    }
    @Test public void returningToExistingErrorStateResumesAutomatically() throws Exception {
        Store active=new Store(context(),key); active.saveCredentials("old-access","old-account");
        pending().create(draft(active,"checking",tokens()));
        java.util.concurrent.atomic.AtomicBoolean offline=new java.util.concurrent.atomic.AtomicBoolean(true);
        BrowserLoginActivity.Controller controller=new BrowserLoginActivity.Controller(context(),active,noExchange(),(path,auth) -> {
            if (offline.get()) throw new Api.Failure("Unavailable",503);
            return response(path,auth);
        },pending());
        try {
            controller.resume(false); await(controller,BrowserLoginActivity.Stage.ERROR);
            assertEquals("old-access",active.credentials().token());
            offline.set(false);
            controller.resume(false); // Same call as Activity.onResume(), no explicit/manual flag.
            await(controller,BrowserLoginActivity.Stage.SUCCESS);
            assertFalse(pending().exists());
        } finally { controller.cancel(); }
    }
    @Test public void returnDuringRunningCheckIsNotLostIfThatCheckFails() throws Exception {
        Store active=new Store(context(),key); pending().create(draft(active,"checking",tokens()));
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1), release=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean first=new java.util.concurrent.atomic.AtomicBoolean(true);
        BrowserLoginActivity.Controller controller=new BrowserLoginActivity.Controller(context(),active,noExchange(),(path,auth) -> {
            if (first.compareAndSet(true,false)) {
                entered.countDown(); release.await(5,java.util.concurrent.TimeUnit.SECONDS);
                throw new Api.Failure("First check did not complete");
            }
            return response(path,auth);
        },pending());
        try {
            controller.resume(false); assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
            controller.resume(false); // User returned while the first request was still running.
            release.countDown(); await(controller,BrowserLoginActivity.Stage.SUCCESS);
        } finally { release.countDown(); controller.cancel(); }
    }
}
