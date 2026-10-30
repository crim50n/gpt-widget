package dev.local.usagewidget;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native JobScheduler keeps the app dependency-free and obeys Android background limits. */
public class RefreshJob extends JobService {
    private static final int PERIODIC = 1201;
    private static final int MANUAL = 1202;
    private final java.util.Map<Integer, Task> tasks = new java.util.HashMap<>();
    private static class Task {
        final AtomicBoolean stopped = new AtomicBoolean();
        Future<?> future;
    }
    static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        long interval = AppSettings.intervalMillis(context);
        if (!new Store(context).connected() || interval == 0) { scheduler.cancel(PERIODIC); return; }
        JobInfo current = scheduler.getPendingJob(PERIODIC);
        if (current == null || current.getIntervalMillis() != interval) scheduler.schedule(new JobInfo.Builder(PERIODIC,
                new ComponentName(context, RefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
                .setPeriodic(interval).build());
    }
    static void requestNow(Context context) {
        if (!new Store(context).connected()) return;
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler.getPendingJob(MANUAL) == null) scheduler.schedule(new JobInfo.Builder(MANUAL,
                new ComponentName(context, RefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(1).build());
    }
    static void cancel(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        scheduler.cancel(PERIODIC); scheduler.cancel(MANUAL);
    }
    @Override public boolean onStartJob(JobParameters params) {
        Task task = new Task();
        tasks.put(params.getJobId(), task);
        task.future = Refresh.EXECUTOR.submit(() -> {
            try {
                if (!task.stopped.get()) Refresh.run(getApplicationContext());
            } finally {
                new android.os.Handler(getMainLooper()).post(() -> {
                    if (!task.stopped.get()) {
                        tasks.remove(params.getJobId());
                        jobFinished(params, false);
                    }
                });
            }
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        Task task = tasks.remove(params.getJobId());
        if (task != null) {
            task.stopped.set(true);
            if (task.future != null) task.future.cancel(true);
        }
        return false;
    }
}
