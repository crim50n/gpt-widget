package dev.local.usagewidget;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class RedemptionStoreTest {
    @Test public void attemptSurvivesReopenAndAuthCacheClear() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        Redemption.Attempt first = new RedemptionStore(c).begin("account-a","credit-1");
        c.getSharedPreferences("usage",0).edit().clear().commit();
        Redemption.Attempt retry = new RedemptionStore(c).begin("account-a","credit-1");
        assertEquals(first,retry);
        assertNotEquals(first,new RedemptionStore(c).begin("account-b","credit-1"));
    }
    @Test public void anotherCreditCannotReplaceUncertainOperation() throws Exception {
        RedemptionStore store = new RedemptionStore(RuntimeEnvironment.getApplication());
        Redemption.Attempt first = store.begin("account-a","credit-1");
        try { store.begin("account-a","credit-2"); fail("Must keep the pending attempt"); }
        catch (IllegalStateException expected) { }
        assertEquals(first,store.pending("account-a"));
        store.complete("account-a",new Redemption.Attempt("credit-1","different-request"));
        assertEquals(first,store.pending("account-a"));
        store.complete("account-a",first);
        assertNull(store.pending("account-a"));
        assertNotEquals(first,store.begin("account-a","credit-2"));
    }
}
