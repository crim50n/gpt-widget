package dev.local.usagewidget;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class OAuthClientTest {
    @Test public void codeAndIntervalParsingAcceptServerAliases() throws Exception {
        OAuthClient client = new OAuthClient((url,form,fields) -> {
            assertEquals(OAuthClient.USER_CODE,url); assertFalse(form);
            assertEquals(OAuthClient.CLIENT_ID,fields.get("client_id"));
            return new OAuthClient.Reply(200,new JSONObject("{\"device_auth_id\":\"device\",\"usercode\":\"ABCD-EFGH\",\"interval\":\"5\"}"));
        });
        OAuthClient.Device device = client.start();
        assertEquals("ABCD-EFGH",device.code()); assertEquals(5,device.intervalSeconds());
        assertFalse(device.toString().contains(device.code()));
    }
    @Test public void waitingDoesNotExchangeTokens() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        OAuthClient client = new OAuthClient((url,form,fields) -> {
            calls.incrementAndGet(); assertEquals(OAuthClient.POLL,url);
            return new OAuthClient.Reply(403,new JSONObject());
        });
        assertNull(client.poll(new OAuthClient.Device("device","ABCD-EFGH",5)));
        assertEquals(1,calls.get());
    }
    @Test public void deviceApprovalExchangesUsingExactRedirectAndVerifier() throws Exception {
        String verifier = "A".repeat(43);
        AtomicInteger calls = new AtomicInteger();
        OAuthClient client = new OAuthClient((url,form,fields) -> {
            if (calls.getAndIncrement() == 0) {
                assertEquals(OAuthClient.POLL,url); assertFalse(form);
                assertEquals("device",fields.get("device_auth_id"));
                return new OAuthClient.Reply(200,new JSONObject().put("authorization_code","code").put("code_verifier",verifier));
            }
            assertEquals(OAuthClient.TOKEN,url); assertTrue(form);
            assertEquals("authorization_code",fields.get("grant_type"));
            assertEquals(verifier,fields.get("code_verifier"));
            assertEquals("https://auth.openai.com/deviceauth/callback",fields.get("redirect_uri"));
            return new OAuthClient.Reply(200,new JSONObject("{\"access_token\":\"test-access\",\"refresh_token\":\"test-refresh\",\"expires_in\":3600}"));
        });
        OAuthClient.Tokens tokens = client.poll(new OAuthClient.Device("device","ABCD-EFGH",5));
        assertEquals(2,calls.get()); assertEquals("test-refresh",tokens.refresh());
        assertTrue(tokens.expiresAt() > System.currentTimeMillis()+3500000);
        assertFalse(tokens.toString().contains("test-access"));
    }
    @Test public void refreshUsesPreviousTokenIfServerOmitsReplacement() throws Exception {
        OAuthClient client = new OAuthClient((url,form,fields) -> {
            assertTrue(form); assertEquals("refresh_token",fields.get("grant_type"));
            assertEquals("refresh-old",fields.get("refresh_token"));
            return new OAuthClient.Reply(200,new JSONObject("{\"access_token\":\"next-access\",\"expires_in\":3600}"));
        });
        assertEquals("refresh-old",client.refresh("refresh-old").refresh());
    }
    @Test public void refreshFailureClassifiesRevokedVsTemporary() throws Exception {
        for (int status : new int[]{400,401,429,503}) {
            OAuthClient client = new OAuthClient((url,form,fields) -> new OAuthClient.Reply(status,new JSONObject()));
            try { client.refresh("refresh-token"); fail(); }
            catch (OAuthClient.Failure e) { assertEquals(status==400 || status==401,e.permanent); }
        }
    }
    @Test public void malformedVerifierNeverReachesExchange() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        OAuthClient client = new OAuthClient((url,form,fields) -> {
            calls.incrementAndGet();
            return new OAuthClient.Reply(200,new JSONObject("{\"authorization_code\":\"code\",\"code_verifier\":\"bad\"}"));
        });
        try { client.poll(new OAuthClient.Device("device","ABCD-EFGH",5)); fail(); }
        catch (OAuthClient.Failure expected) { }
        assertEquals(1,calls.get());
    }
    @Test(expected=IllegalArgumentException.class) public void bearerCannotInjectHeaders() {
        OAuthClient.checkSecret("access\r\nCookie: injected");
    }
}
