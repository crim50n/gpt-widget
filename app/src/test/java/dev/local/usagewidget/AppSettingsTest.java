package dev.local.usagewidget;

import android.app.job.JobScheduler;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.time.Instant;
import java.util.TimeZone;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class AppSettingsTest {
    @Test public void changingIntervalReplacesPeriodicJobAndManualModeCancelsOnlyPeriodic() {
        Context c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences("usage",0).edit().putString("auth","test-presence-only").commit();
        JobScheduler scheduler = c.getSystemService(JobScheduler.class);
        RefreshJob.schedule(c);
        assertEquals(900000L,scheduler.getPendingJob(1201).getIntervalMillis());
        AppSettings.interval(c,3);
        assertEquals(3600000L,scheduler.getPendingJob(1201).getIntervalMillis());
        RefreshJob.requestNow(c);
        AppSettings.interval(c,0);
        assertNull(scheduler.getPendingJob(1201));
        assertNotNull(scheduler.getPendingJob(1202));
        scheduler.cancelAll();
        new UsageWidget().onUpdate(c,android.appwidget.AppWidgetManager.getInstance(c),new int[0]);
        assertTrue(scheduler.getAllPendingJobs().isEmpty());
    }
    @Test public void dateAndClockFormatsAreIndependent() {
        Context c = RuntimeEnvironment.getApplication(); TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC")); Instant sample = Instant.parse("2026-10-30T23:45:00Z");
            AppSettings.format(c,"date",1);
            assertEquals("30.10 23:45",AppSettings.dateTime(c,sample));
            AppSettings.format(c,"time",1);
            assertEquals("30.10 11:45 PM",AppSettings.dateTime(c,sample));
            AppSettings.format(c,"date",2);
            assertEquals("2026-10-30 11:45 PM",AppSettings.dateTime(c,sample));
        } finally { TimeZone.setDefault(original); }
    }
}
