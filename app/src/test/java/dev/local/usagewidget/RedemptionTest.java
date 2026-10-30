package dev.local.usagewidget;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class RedemptionTest {
    @Test public void requestUsesBodyIdempotencyAndSelectedCredit() throws Exception {
        JSONObject body = new Redemption.Attempt("credit-1","request-1").body();
        assertEquals("credit-1",body.getString("credit_id"));
        assertEquals("request-1",body.getString("redeem_request_id"));
        assertEquals(2,body.length());
    }
    @Test public void alreadyRedeemedIsSuccessOnlyForSamePendingAttempt() throws Exception {
        JSONObject body = new JSONObject("{\"code\":\"already_redeemed\"}");
        assertFalse(Redemption.interpret(200,body,false).success());
        assertTrue(Redemption.interpret(200,body,true).success());
        assertTrue(Redemption.interpret(409,body,true).success());
    }
    @Test public void onlyResetIsDirectSuccess() throws Exception {
        assertTrue(Redemption.interpret(200,new JSONObject("{\"code\":\"reset\"}"),false).success());
        assertFalse(Redemption.interpret(200,new JSONObject(),false).definitive());
        assertFalse(Redemption.interpret(200,new JSONObject("{\"code\":\"processing\"}"),false).definitive());
        assertFalse(Redemption.interpret(503,new JSONObject("{\"code\":\"reset\"}"),true).success());
    }
    @Test public void knownRejectionsAreDefinitiveWithoutSuccess() throws Exception {
        for (String code : new String[]{"no_credit","nothing_to_reset"}) {
            Redemption.Outcome outcome = Redemption.interpret(200,new JSONObject().put("code",code),false);
            assertTrue(outcome.definitive()); assertFalse(outcome.success());
        }
        for (int status : new int[]{401,403,429,500})
            assertFalse(Redemption.interpret(status,new JSONObject(),true).definitive());
    }
    @Test public void ticketIdsSurviveCacheRoundTrip() throws Exception {
        JSONObject root = new JSONObject("{\"available_count\":1,\"credits\":[{\"id\":\"credit-1\",\"reset_type\":\"codex_rate_limits\",\"status\":\"available\",\"is_supported_by_plan\":true}]}");
        assertEquals("credit-1",Models.parseResets(Models.parseResets(root).json()).tickets().get(0).id());
    }
}
