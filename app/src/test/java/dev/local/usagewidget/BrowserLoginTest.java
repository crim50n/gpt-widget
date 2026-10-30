package dev.local.usagewidget;

import android.content.Context;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import javax.crypto.spec.SecretKeySpec;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35})
public class BrowserLoginTest {
    @Test public void successfulLoginShowsFeedbackOnMainScreen() {
        try (org.robolectric.android.controller.ActivityController<BrowserLoginActivity> activity = Robolectric.buildActivity(BrowserLoginActivity.class).setup()) {
            BrowserLoginActivity.Controller controller=(BrowserLoginActivity.Controller)activity.get().onRetainNonConfigurationInstance();
            controller.stage=BrowserLoginActivity.Stage.SUCCESS;
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
            android.content.Intent launched=org.robolectric.Shadows.shadowOf(activity.get()).getNextStartedActivity();
            assertNotNull(launched); assertEquals(MainActivity.class.getName(),launched.getComponent().getClassName());
            assertTrue(launched.getBooleanExtra("connection_success",false)); assertTrue(activity.get().isFinishing());
        }
    }
    @Test public void screenDoesNotBeginAuthorizationWithoutUserAction() {
        try (org.robolectric.android.controller.ActivityController<BrowserLoginActivity> activity = Robolectric.buildActivity(BrowserLoginActivity.class).setup()) {
            BrowserLoginActivity.Controller controller = (BrowserLoginActivity.Controller) activity.get().onRetainNonConfigurationInstance();
            assertEquals(BrowserLoginActivity.Stage.IDLE,controller.stage);
            assertEquals("",controller.code);
            assertEquals(0,activity.get().getWindow().getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
    @Test public void cancellationWhileCheckingUsagePreservesActiveSession() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        Store store=new Store(context,new SecretKeySpec(new byte[32],"AES"));
        store.saveCredentials("existing-token","existing-account"); String revision=store.revision();
        OAuthClient oauth = new OAuthClient((url,form,fields) -> {
            if (url.equals(OAuthClient.USER_CODE)) return new OAuthClient.Reply(200,new JSONObject("{\"device_auth_id\":\"device\",\"user_code\":\"ABCD-EFGH\",\"interval\":3}"));
            if (url.equals(OAuthClient.POLL)) return new OAuthClient.Reply(200,new JSONObject().put("authorization_code","code").put("code_verifier","A".repeat(43)));
            return new OAuthClient.Reply(200,new JSONObject("{\"access_token\":\"new-token\",\"refresh_token\":\"new-refresh\",\"expires_in\":3600}"));
        });
        CountDownLatch reading = new CountDownLatch(1), stopped = new CountDownLatch(1);
        BrowserLoginActivity.Controller controller = new BrowserLoginActivity.Controller(context,store,oauth,(path,auth) -> {
            reading.countDown();
            try { new CountDownLatch(1).await(20,TimeUnit.SECONDS); throw new AssertionError("Cancellation should interrupt"); }
            finally { stopped.countDown(); }
        },new PendingLoginStore(context,new SecretKeySpec(new byte[32],"AES")));
        try {
            controller.start();
            assertTrue(reading.await(10,TimeUnit.SECONDS));
            controller.cancel(); assertTrue(stopped.await(5,TimeUnit.SECONDS));
            assertEquals(BrowserLoginActivity.Stage.CANCELLED,controller.stage);
            assertEquals("existing-token",store.credentials().token()); assertEquals(revision,store.revision());
        } finally { controller.cancel(); }
    }
}
