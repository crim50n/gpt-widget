package dev.local.usagewidget;

import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.time.Instant;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="en-rUS")
public class ModelsTest {
    private JSONObject usage(String window, String credits) throws Exception {
        return new JSONObject("{\"email\":\"private@example.test\",\"rate_limit\":{\"allowed\":true,"
                + "\"limit_reached\":false,\"primary_window\":" + window + ",\"secondary_window\":null},\"credits\":" + credits + "}");
    }
    private final String week = "{\"used_percent\":21,\"limit_window_seconds\":604800,\"reset_at\":1793294786}";

    @Test public void primaryCanBeWeeklyAndCacheOmitsPrivateData() throws Exception {
        Models.Usage parsed = Models.parseUsage(usage(week, "{\"balance\":\"0\"}"));
        assertEquals(79, parsed.week().remaining());
        assertEquals(1793294786L, parsed.week().resetAt());
        assertFalse(parsed.json().toString().contains("private@example"));
        assertEquals(79, Models.cachedUsage(parsed.json()).week().remaining());
    }
    @Test public void missingWindowDoesNotBecomeFullOrEmpty() throws Exception {
        Models.Usage parsed = Models.parseUsage(usage("null", "null"));
        assertNull(parsed.week());
        assertTrue(parsed.windows().isEmpty());
        assertEquals("Unavailable", parsed.creditLabel());
    }
    @Test public void weeklyWindowCanBeSecondary() throws Exception {
        JSONObject root = usage("{\"used_percent\":35,\"limit_window_seconds\":18000,\"reset_at\":1793294786}", "null");
        root.getJSONObject("rate_limit").put("secondary_window", new JSONObject(week));
        assertEquals(79, Models.parseUsage(root).week().remaining());
        assertEquals(2, Models.parseUsage(root).windows().size());
    }
    @Test public void monetaryCreditsUseCeilingAndPreserveUnknown() throws Exception {
        assertEquals("2", Models.parseUsage(usage(week, "{\"balance\":\"1.01\"}")).creditLabel());
        assertEquals("0", Models.parseUsage(usage(week, "{\"balance\":\"-1.2\"}")).creditLabel());
        assertEquals("Unavailable", Models.parseUsage(usage(week, "{\"balance\":\"NaN\"}")).creditLabel());
        assertEquals("Unlimited", Models.parseUsage(usage(week, "{\"unlimited\":true}")).creditLabel());
    }
    @Test public void roundedHundredDoesNotOverrideServerState() throws Exception {
        JSONObject root = usage(week, "null");
        root.getJSONObject("rate_limit").getJSONObject("primary_window").put("used_percent", 100);
        Models.Usage parsed = Models.parseUsage(root);
        assertEquals(0, parsed.week().remaining());
        assertFalse(parsed.reached());
        assertEquals(Boolean.TRUE, parsed.allowed());
    }
    @Test(expected = JSONException.class) public void invalidWindowIsNotCachedAsZero() throws Exception {
        Models.parseUsage(usage("{\"limit_window_seconds\":604800,\"reset_at\":1793294786}", "null"));
    }
    @Test public void resetsUseServerCountAndParseMicroseconds() throws Exception {
        JSONObject root = new JSONObject("{\"available_count\":3,\"applicable_available_count\":0,\"credits\":["
                + "{\"reset_type\":\"codex_rate_limits\",\"status\":\"available\",\"is_supported_by_plan\":true,"
                + "\"expires_at\":\"2026-10-22T20:45:18.706624Z\"},"
                + "{\"reset_type\":\"codex_weekly\",\"status\":\"available\",\"is_supported_by_plan\":false,"
                + "\"expires_at\":\"2026-10-21T20:45:18Z\"},"
                + "{\"reset_type\":\"codex_rate_limits\",\"status\":\"used\",\"is_supported_by_plan\":true,"
                + "\"expires_at\":\"2026-10-20T20:45:18Z\"}]} ");
        Models.Resets resets = Models.parseResets(root);
        assertEquals(3, resets.count());
        assertEquals(Instant.parse("2026-10-22T20:45:18.706624Z"), resets.nearest());
        assertEquals(resets.nearest(), Models.parseResets(resets.json()).nearest());
    }
}
