package dev.local.usagewidget;

import static dev.local.usagewidget.UiText.t;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class ResetService {
    private static final AtomicBoolean busy = new AtomicBoolean();
    static boolean isBusy() { return busy.get(); }
    static void consume(Context context, String account, String credit, Consumer<String> finished) {
        if (!busy.compareAndSet(false,true)) return;
        Context app = context.getApplicationContext();
        Refresh.EXECUTOR.execute(() -> {
            String message;
            try {
                Store store = new Store(app);
                Store.Credentials auth = AuthSession.current(app);
                if (auth == null || account.isEmpty() || !account.equals(auth.account()))
                    throw new Api.Failure(t(R.string.error_account_missing));
                RedemptionStore attempts = new RedemptionStore(app);
                Redemption.Attempt pending = attempts.pending(account);
                boolean retry = pending != null;
                if (retry && !pending.creditId().equals(credit)) throw new Api.Failure(t(R.string.error_pending_reset));
                if (!retry) {
                    AuthSession.Read read = AuthSession.read(app,"rate-limit-reset-credits");
                    auth = read.auth();
                    if (!account.equals(auth.account())) throw new Api.Failure(t(R.string.error_account_changed_reset));
                    Api.Response response = read.response();
                    Models.Resets fresh = Models.parseResets(response.json());
                    store.success("resets",fresh.json(),response.offset(),auth.revision());
                    Models.Ticket ticket = fresh.tickets().stream().filter(t -> credit.equals(t.id())).findFirst().orElse(null);
                    if (ticket == null || !"available".equals(ticket.status()) || !ticket.supported()
                            || (ticket.expires() != null && ticket.expires().toEpochMilli() <= store.now("resets")))
                        throw new Api.Failure(t(R.string.error_reset_unavailable));
                }
                if (!store.revision().equals(auth.revision())) throw new Api.Failure(t(R.string.error_connection_changed_reset));
                Redemption.Attempt attempt = attempts.begin(account,credit);
                try {
                    Api.ConsumeResponse response = Api.consume(auth,attempt.body());
                    Redemption.Outcome outcome = Redemption.interpret(response.status(),response.json(),retry);
                    message = outcome.message();
                    if (outcome.definitive()) attempts.complete(account,attempt);
                } catch (Exception e) {
                    message = t(R.string.reset_unconfirmed);
                }
                // Read-only reconciliation; never retry the POST automatically.
                Refresh.run(app);
            } catch (Exception e) {
                message = e instanceof Api.Failure ? e.getMessage() : t(R.string.reset_prepare_failed);
            } finally { busy.set(false); UsageWidget.updateAll(app); }
            String result = message;
            new Handler(Looper.getMainLooper()).post(() -> finished.accept(result));
        });
    }
}
