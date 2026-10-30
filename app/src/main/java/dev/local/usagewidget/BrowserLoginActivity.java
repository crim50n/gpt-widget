package dev.local.usagewidget;

import static dev.local.usagewidget.UiText.t;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A fresh device authorization; never imports credentials from another installed app. */
public class BrowserLoginActivity extends Activity {
    private Controller controller;
    private TextView status, code, remaining, start, open, copy, check;
    private final Runnable updateUi=this::render;
    private Appearance.Palette palette;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable repaint = new Runnable() {
        @Override public void run() { render(); handler.postDelayed(this,1000); }
    };
    private int dp(int value) { return Appearance.dp(this,value); }
    @Override public void onCreate(Bundle saved) {
        palette = Appearance.palette(this);
        setTheme(palette.dark() ? R.style.Theme_Usage_Dark : R.style.Theme_Usage_Light);
        super.onCreate(saved);
        getWindow().getDecorView().setSystemUiVisibility(palette.dark() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        Object retained = getLastNonConfigurationInstance();
        boolean resumePending=getIntent().getBooleanExtra("resume_pending",false);
        controller = retained instanceof Controller ? (Controller) retained : Controller.shared(getApplicationContext(),resumePending);
        if (resumePending && controller.stage==Stage.IDLE && !new PendingLoginStore(this).exists() && new Store(this).connected()) controller.stage=Stage.SUCCESS;
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(palette.background());
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(22),dp(24),dp(22),dp(24));
        scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((v,insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets;
        });
        add(root,text(t(R.string.sign_in),26,palette.text()),0);
        add(root,text(t(R.string.signin_intro),14,palette.muted()),12);
        status = text(t(R.string.signin_preserve_session),14,palette.muted());
        add(root,status,22);
        code = text("",32,palette.text()); code.setGravity(Gravity.CENTER); code.setTypeface(android.graphics.Typeface.MONOSPACE);
        code.setPadding(dp(12),dp(22),dp(12),dp(22)); code.setBackground(Appearance.outlined(this,palette.surface(),24));
        add(root,code,18);
        remaining = text("",12,palette.muted()); remaining.setGravity(Gravity.CENTER); add(root,remaining,8);
        start = button(t(R.string.get_code),true,() -> { controller.start(); render(); }); add(root,start,20);
        copy = button(t(R.string.copy_code),false,() -> {
            if (!controller.code.isEmpty()) {
                getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText(t(R.string.clipboard_code_label),controller.code));
                AppNotice.show(this,t(R.string.code_copied));
            }
        }); add(root,copy,10);
        open = button(t(R.string.open_signin_page),true,() -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(OAuthClient.VERIFY_URL)).addCategory(Intent.CATEGORY_BROWSABLE)); }
            catch (android.content.ActivityNotFoundException e) { status.setText(t(R.string.no_browser)); }
        }); add(root,open,10);
        check=button(t(R.string.check_signin),false,() -> { controller.resume(true); render(); }); add(root,check,10);
        add(root,button(t(R.string.cancel_signin),false,() -> { controller.cancel(); finish(); }),10);
        add(root,text(t(R.string.device_auth_help),12,palette.muted()),24);
        setContentView(scroll); render();
    }
    private TextView text(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(dp(3),1); return view;
    }
    private TextView button(String title, boolean primary, Runnable action) {
        TextView view = text(title,15,primary ? palette.accentText() : palette.text()); view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14),dp(14),dp(14),dp(14)); view.setMinHeight(dp(48));
        view.setBackground(Appearance.control(this,primary ? palette.accent() : palette.surface(),26,!primary));
        view.setOnClickListener(v -> action.run()); return view;
    }
    private void add(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(margin); parent.addView(child,lp);
    }
    private void render() {
        if (controller == null || isDestroyed() || isFinishing()) return;
        Stage stage = controller.stage;
        if (stage == Stage.SUCCESS) {
            startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("connection_success",true));
            finish(); return;
        }
        boolean waiting = stage == Stage.WAITING;
        boolean busy = waiting || stage == Stage.REQUESTING || stage == Stage.EXCHANGING || stage == Stage.CHECKING;
        if (stage != Stage.IDLE) status.setText(controller.message);
        code.setVisibility(waiting ? View.VISIBLE : View.GONE); code.setText(controller.code);
        copy.setVisibility(waiting ? View.VISIBLE : View.GONE); open.setVisibility(waiting ? View.VISIBLE : View.GONE);
        start.setVisibility(busy ? View.GONE : View.VISIBLE);
        start.setText(t(stage == Stage.ERROR || stage == Stage.CANCELLED ? R.string.new_code : R.string.get_code));
        check.setVisibility(controller.canResume() && (waiting || stage==Stage.ERROR) ? View.VISIBLE : View.GONE);
        check.setText(t(stage==Stage.ERROR ? R.string.retry_signin : R.string.check_signin));
        remaining.setVisibility(waiting ? View.VISIBLE : View.GONE);
        if (waiting) {
            long seconds = Math.max(0,(controller.deadline-SystemClock.elapsedRealtime())/1000);
            remaining.setText(t(R.string.code_expires_in,seconds/60,seconds%60));
        }
    }
    @Override protected void onResume() { super.onResume(); controller.listener=updateUi; controller.resume(false); handler.post(repaint); }
    @Override protected void onPause() { if (controller.listener==updateUi) controller.listener=null; handler.removeCallbacks(repaint); super.onPause(); }
    @Override public Object onRetainNonConfigurationInstance() { return controller; }
    @Override protected void onDestroy() {
        AppNotice.clear(this);
        handler.removeCallbacksAndMessages(null);
        if (controller.listener==updateUi) controller.listener=null;
        // A browser can outlive this Activity. Only an explicit cancellation discards its draft.
        super.onDestroy();
    }
    @Override public void onBackPressed() { controller.cancel(); super.onBackPressed(); }

    enum Stage { IDLE, REQUESTING, WAITING, EXCHANGING, CHECKING, SUCCESS, ERROR, CANCELLED }
    static final class Controller {
        private static Controller shared;
        static synchronized Controller shared(Context context, boolean resume) {
            if (shared==null || (!resume && shared.stage==Stage.SUCCESS) || shared.stage==Stage.CANCELLED) shared=new Controller(context);
            return shared;
        }
        private final Context app;
        private final Store store;
        private final OAuthClient oauth;
        private final AuthSession.Reader reader;
        private final PendingLoginStore drafts;
        private PendingLoginStore.Draft draft;
        private final Handler main = new Handler(Looper.getMainLooper());
        volatile Stage stage = Stage.IDLE;
        volatile String message = "", code = "";
        volatile long deadline;
        volatile Runnable listener;
        private int generation;
        private Thread worker;
        private boolean resumeRequested;
        Controller(Context app) { this(app,new Store(app),new OAuthClient(),Api::get,new PendingLoginStore(app)); }
        Controller(Context app, Store store, OAuthClient oauth, AuthSession.Reader reader, PendingLoginStore drafts) {
            this.app=app.getApplicationContext(); this.store=store; this.oauth=oauth; this.reader=reader; this.drafts=drafts;
            try {
                draft=drafts.load();
                if (draft!=null) {
                    if (!store.revision().equals(draft.revision())) {
                        Store.Credentials active=store.credentials();
                        boolean applied=active!=null && draft.tokens()!=null && active.token().equals(draft.tokens().access());
                        drafts.clear(draft.id()); draft=null;
                        stage=applied ? Stage.SUCCESS : Stage.ERROR;
                        message=t(applied ? R.string.connection_restored : R.string.account_changed_signin);
                    } else if (draft.tokens()!=null) {
                        stage=Stage.CHECKING; message=t(R.string.resuming_verification);
                    } else if (draft.phase().equals("exchanging")) {
                        stage=Stage.ERROR; message=t(R.string.exchange_interrupted);
                    } else {
                        code=draft.device().code(); deadline=SystemClock.elapsedRealtime()+Math.max(0,draft.expiresAt()-System.currentTimeMillis());
                        stage=Stage.WAITING; message=t(R.string.restored_attempt);
                    }
                }
            } catch (Exception e) { drafts.clear(); draft=null; stage=Stage.ERROR; message=t(R.string.restore_attempt_failed); }
        }
        synchronized void start() {
            if (worker!=null && worker.isAlive() || stage==Stage.SUCCESS) return;
            int id=++generation; String expectedRevision = store.revision(); resumeRequested=false;
            drafts.clear(); draft=null;
            stage=Stage.REQUESTING; message=t(R.string.requesting_code); code=""; notifyUi();
            worker = new Thread(() -> run(id,expectedRevision),"usage-browser-login"); worker.start();
        }
        synchronized boolean canResume() { return draft!=null && (draft.tokens()!=null || draft.phase().equals("waiting")); }
        synchronized void resume(boolean explicit) {
            if (!canResume() || stage==Stage.SUCCESS || stage==Stage.CANCELLED) return;
            if (worker!=null && worker.isAlive()) {
                // Remember the return even if an in-flight check fails just after onResume.
                resumeRequested=true;
                if (explicit) { message=t(R.string.check_in_progress); notifyUi(); }
                return;
            }
            resumeRequested=false;
            int id=++generation; String revision=draft.revision();
            stage=draft.tokens()!=null ? Stage.CHECKING : Stage.WAITING;
            worker=new Thread(() -> run(id,revision),"usage-browser-login-resume"); worker.start();
        }
        private void run(int id, String expectedRevision) {
            try {
                PendingLoginStore.Draft saved;
                synchronized (this) { saved=draft; }
                OAuthClient.Device device = saved==null ? oauth.start() : saved.device();
                synchronized (this) {
                    if (!current(id)) return;
                    if (saved==null) {
                        draft=new PendingLoginStore.Draft(java.util.UUID.randomUUID().toString(),expectedRevision,device,System.currentTimeMillis()+15*60*1000L,"waiting",null);
                        drafts.create(draft);
                    }
                    code=device.code(); deadline=SystemClock.elapsedRealtime()+Math.max(0,draft.expiresAt()-System.currentTimeMillis());
                    if (draft.tokens()==null) { stage=Stage.WAITING; message=t(R.string.signin_step_wait); }
                    notifyUi();
                }
                long interval = device.intervalSeconds()*1000L;
                OAuthClient.Tokens tokens;
                synchronized (this) { tokens=draft.tokens(); }
                int checks=0;
                while (tokens==null && current(id) && SystemClock.elapsedRealtime()<deadline) {
                    Thread.sleep(Math.min(interval,Math.max(1,deadline-SystemClock.elapsedRealtime())));
                    if (!current(id) || SystemClock.elapsedRealtime()>=deadline) break;
                    try { tokens=oauth.poll(device,() -> {
                        synchronized (Controller.this) {
                            if (!current(id)) throw new InterruptedException();
                            draft=draft.phase("exchanging",null);
                            if (!drafts.update(draft)) throw new Api.Failure(t(R.string.signin_attempt_changed));
                            stage=Stage.EXCHANGING; message=t(R.string.signin_step_exchange); notifyUi();
                        }
                    }); }
                    catch (OAuthClient.SlowDown e) { interval=Math.min(900000,interval+5000); continue; }
                    synchronized (this) {
                        if (!current(id)) return;
                        if (tokens==null) { checks++; message=t(R.string.signin_wait_checks,checks,AppSettings.stamp(app,System.currentTimeMillis())); notifyUi(); }
                    }
                    if (tokens != null) break;
                }
                if (!current(id)) return;
                if (tokens == null) throw new Api.Failure(t(R.string.code_expired));
                synchronized (this) {
                    if (!current(id)) return;
                    draft=draft.phase("checking",tokens);
                    if (!drafts.update(draft)) throw new Api.Failure(t(R.string.signin_cancelled));
                    stage=Stage.CHECKING; code=""; message=t(R.string.signin_step_tokens); notifyUi();
                }
                if (tokens.expiresAt() <= System.currentTimeMillis()+60000) {
                    tokens=oauth.refresh(tokens.refresh());
                    synchronized (this) {
                        if (!current(id)) return;
                        draft=draft.phase("checking",tokens);
                        if (!drafts.update(draft)) throw new Api.Failure(t(R.string.signin_cancelled));
                    }
                }
                AuthSession.Verified verified = verifyWithRetry(id,new Store.Credentials(tokens.access(),tokens.accountHint(),"",
                        tokens.refresh(),tokens.expiresAt(),"device"));
                synchronized (this) {
                    if (!current(id)) return;
                    if (!store.activate(verified,expectedRevision)) throw new Api.Failure(t(R.string.connection_changed_signin));
                    drafts.clear(draft.id()); draft=null;
                    stage=Stage.SUCCESS; message=t(R.string.connection_verified); notifyUi();
                }
                RefreshJob.schedule(app); UsageWidget.updateAll(app);
            } catch (Exception e) {
                synchronized (this) {
                    if (!current(id)) return;
                    String at=message;
                    stage=Stage.ERROR; code="";
                    message=t(R.string.signin_failed_preserved,at,e instanceof Api.Failure ? e.getMessage() : t(R.string.signin_step_failed));
                    notifyUi();
                }
            } finally {
                synchronized (this) {
                    if (generation==id) {
                        worker=null;
                        if (resumeRequested && stage==Stage.ERROR) { resumeRequested=false; resume(false); }
                    }
                }
            }
        }
        private AuthSession.Verified verifyWithRetry(int id, Store.Credentials auth) throws Exception {
            for (int attempt=0; ; attempt++) {
                if (!current(id)) throw new InterruptedException();
                try {
                    return AuthSession.verify(auth,(path,credentials) -> {
                            synchronized (Controller.this) {
                                if (!current(id)) throw new InterruptedException();
                                message=t(path.equals("usage") ? R.string.signin_step_usage : R.string.signin_step_resets); notifyUi();
                            }
                            return reader.get(path,credentials);
                    });
                } catch (Exception failure) {
                    if (!current(id) || attempt>=2 || !retryableVerification(failure)) throw failure;
                    synchronized (this) {
                        message=t(R.string.signin_auto_retry,attempt+2); notifyUi();
                    }
                    Thread.sleep((attempt+1)*2000L);
                }
            }
        }
        private static boolean retryableVerification(Exception failure) {
            if (failure instanceof java.io.IOException) return true;
            if (!(failure instanceof Api.Failure)) return false;
            int status=((Api.Failure)failure).status;
            return status==401 || status==403 || status==408 || status==425 || status==429 || status>=500;
        }
        private synchronized boolean current(int id) { return generation == id && !Thread.currentThread().isInterrupted(); }
        synchronized void cancel() {
            if (stage == Stage.SUCCESS) return;
            generation++; stage=Stage.CANCELLED; code=""; resumeRequested=false;
            try { if (draft!=null) drafts.clear(draft.id()); } catch (Exception ignored) { }
            draft=null;
            if (worker != null) worker.interrupt(); notifyUi();
        }
        private void notifyUi() { main.post(() -> { Runnable callback=listener; if (callback!=null) callback.run(); }); }
    }
}
