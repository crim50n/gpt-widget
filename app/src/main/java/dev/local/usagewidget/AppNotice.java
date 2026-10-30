package dev.local.usagewidget;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.TextView;

/** In-app feedback follows our selected theme rather than the system Toast appearance. */
final class AppNotice {
    private static final String TAG="usage-inline-notice";
    static void show(Activity owner,String message) {
        if (owner.isDestroyed() || owner.isFinishing()) return;
        if (!(owner.getWindow().getDecorView() instanceof FrameLayout)) return;
        FrameLayout root=(FrameLayout)owner.getWindow().getDecorView(); clear(owner);
        Appearance.Palette p=Appearance.palette(owner);
        TextView text=new TextView(owner); text.setText(message); text.setTextSize(14); text.setTextColor(p.text());
        text.setTag(TAG); text.setGravity(Gravity.CENTER); text.setPadding(dp(owner,20),dp(owner,14),dp(owner,20),dp(owner,14));
        text.setBackground(Appearance.outlined(owner,p.inset(),24)); text.setElevation(dp(owner,6));
        text.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        WindowInsets insets=root.getRootWindowInsets();
        lp.setMargins(dp(owner,20),dp(owner,16),dp(owner,20),dp(owner,24)+(insets==null?0:insets.getSystemWindowInsetBottom()));
        root.addView(text,lp); text.setOnClickListener(v -> root.removeView(text));
        new Handler(Looper.getMainLooper()).postDelayed(() -> { if (text.getParent()==root) root.removeView(text); },3500);
    }
    static void clear(Activity owner) {
        View root=owner.getWindow().getDecorView(); View notice=root.findViewWithTag(TAG);
        if (notice!=null && notice.getParent() instanceof ViewGroup) ((ViewGroup)notice.getParent()).removeView(notice);
    }
    private static int dp(Activity owner,int value) { return Appearance.dp(owner,value); }
}
