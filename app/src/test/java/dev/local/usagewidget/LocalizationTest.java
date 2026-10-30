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
public class LocalizationTest {
    @Test @Config(qualifiers="en-rUS") public void englishIsTheBaseLanguage() {
        Context c=RuntimeEnvironment.getApplication();
        assertEquals("Sign in",UiText.t(R.string.sign_in));
        assertEquals("Resets\nOctober 30",UiText.t(R.string.reset_at_compact,"October 30"));
        assertEquals("Full reset",UiText.ticketTitle(new Models.Ticket("id","Старое название","codex_rate_limits","available",true,null)));
        assertEquals("Every 15 minutes",AppSettings.intervalLabels(c)[1]);
        assertTrue(AppSettings.dateLabels(c)[0].contains("Oct"));
    }
    @Test @Config(qualifiers="ru-rRU") public void russianTranslationIncludesBackgroundMessagesAndDateNames() {
        Context c=RuntimeEnvironment.getApplication();
        assertEquals("Войти в аккаунт",UiText.t(R.string.sign_in));
        assertEquals("Полный сброс",UiText.ticketTitle(new Models.Ticket("id","Full reset","codex_rate_limits","available",true,null)));
        assertTrue(UiText.t(R.string.oauth_server_error,503).contains("503"));
        assertEquals("Каждые 15 минут",AppSettings.intervalLabels(c)[1]);
        assertTrue(AppSettings.dateLabels(c)[0].contains("окт"));
    }
    @Test @Config(qualifiers="de-rDE") public void unsupportedLanguageFallsBackToEnglish() {
        assertEquals("Sign in",UiText.t(R.string.sign_in));
        assertEquals("Refresh now",UiText.t(R.string.refresh_now));
    }
}
