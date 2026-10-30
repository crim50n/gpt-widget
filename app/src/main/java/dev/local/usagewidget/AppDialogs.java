package dev.local.usagewidget;

import static dev.local.usagewidget.UiText.t;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.WeakHashMap;
import java.util.function.IntConsumer;

/** Shared themed content; no platform AlertDialog widgets or OEM button styling. */
final class AppDialogs {
    private static final WeakHashMap<Activity,Dialog> open = new WeakHashMap<>();
    static Dialog message(Activity owner, String title, String message) {
        return show(owner,title,message,null,0,null,t(R.string.got_it),null,null);
    }
    static Dialog confirm(Activity owner, String title, String message, String positive, Runnable action) {
        return show(owner,title,message,null,0,null,positive,action,t(R.string.cancel));
    }
    static Dialog choose(Activity owner, String title, String[] options, int selected, IntConsumer action) {
        return show(owner,title,null,options,selected,action,null,null,t(R.string.cancel));
    }
    static void dismiss(Activity owner) { Dialog dialog=open.remove(owner); if (dialog!=null) dialog.dismiss(); }
    private static Dialog show(Activity owner, String title, String message, String[] choices, int selected,
                               IntConsumer choose, String positive, Runnable action, String negative) {
        if (owner.isFinishing() || owner.isDestroyed()) return null;
        dismiss(owner);
        Appearance.Palette p=Appearance.palette(owner);
        Dialog dialog=new Dialog(owner); dialog.setOwnerActivity(owner); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=new LinearLayout(owner); root.setOrientation(LinearLayout.VERTICAL); root.setTag("app-dialog");
        root.setPadding(dp(owner,24),dp(owner,24),dp(owner,24),dp(owner,20));
        root.setBackground(Appearance.outlined(owner,p.surface(),28));
        TextView heading=text(owner,title,21,p.text(),true); root.addView(heading);
        ScrollView scroll=new ScrollView(owner); scroll.setFillViewport(false); scroll.setClipToPadding(false);
        LinearLayout body=new LinearLayout(owner); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
        LinearLayout.LayoutParams scrollParams=new LinearLayout.LayoutParams(-1,-2); scrollParams.topMargin=dp(owner,16); root.addView(scroll,scrollParams);
        if (message!=null) body.addView(text(owner,message,15,p.muted(),false));
        if (choices!=null) for (int i=0;i<choices.length;i++) {
            final int index=i;
            boolean checked=i==selected;
            LinearLayout item=new LinearLayout(owner); item.setGravity(Gravity.CENTER_VERTICAL);
            item.setMinimumHeight(dp(owner,48)); item.setPadding(dp(owner,12),dp(owner,12),dp(owner,12),dp(owner,12));
            item.setBackground(Appearance.control(owner,checked?p.inset():Color.TRANSPARENT,16,false));
            item.setSelected(checked); item.setTag("dialog-option-"+i);
            TextView label=text(owner,choices[i],15,p.text(),checked);
            item.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            TextView mark=text(owner,checked?"●":"○",20,checked?p.text():p.muted(),false);
            mark.setPadding(dp(owner,12),0,0,0); mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); item.addView(mark);
            item.setContentDescription(checked?t(R.string.selected_description,choices[i]):choices[i]);
            item.setOnClickListener(v -> { dialog.dismiss(); choose.accept(index); });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(owner,4); body.addView(item,lp);
        }
        if (positive!=null) {
            TextView button=button(owner,positive,true); button.setTag("dialog-positive");
            button.setOnClickListener(v -> { dialog.dismiss(); if (action!=null) action.run(); });
            addButton(owner,root,button,20);
        }
        if (negative!=null) {
            TextView button=button(owner,negative,false); button.setTag("dialog-negative");
            button.setOnClickListener(v -> dialog.dismiss()); addButton(owner,root,button,positive==null?16:8);
        }
        int width=Math.min(dp(owner,420),owner.getResources().getDisplayMetrics().widthPixels-dp(owner,32));
        int maxHeight=(int)(owner.getResources().getDisplayMetrics().heightPixels*.82f);
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        if (root.getMeasuredHeight()>maxHeight) {
            scrollParams.height=Math.max(dp(owner,48),maxHeight-root.getMeasuredHeight()+scroll.getMeasuredHeight());
            scroll.setLayoutParams(scrollParams);
        }
        dialog.setContentView(root); dialog.setCanceledOnTouchOutside(true);
        Window window=dialog.getWindow();
        if (window!=null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes=window.getAttributes(); attributes.dimAmount=.6f; attributes.gravity=Gravity.CENTER;
            window.setAttributes(attributes);
        }
        dialog.setOnDismissListener(d -> { if (open.get(owner)==dialog) open.remove(owner); });
        open.put(owner,dialog); dialog.show();
        if (window!=null) window.setLayout(width,WindowManager.LayoutParams.WRAP_CONTENT);
        return dialog;
    }
    private static void addButton(Activity owner, LinearLayout root, View button, int margin) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(owner,margin); root.addView(button,lp);
    }
    private static TextView button(Activity owner, String label, boolean primary) {
        Appearance.Palette p=Appearance.palette(owner);
        TextView view=text(owner,label,15,primary?p.accentText():p.text(),true); view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(owner,48)); view.setPadding(dp(owner,14),dp(owner,14),dp(owner,14),dp(owner,14));
        view.setBackground(Appearance.control(owner,primary?p.accent():p.surface(),26,!primary)); return view;
    }
    private static TextView text(Activity owner, String label, int size, int color, boolean medium) {
        TextView view=new TextView(owner); view.setText(label); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(Typeface.create(medium?"sans-serif-medium":"sans-serif",Typeface.NORMAL)); view.setLineSpacing(dp(owner,3),1); return view;
    }
    private static int dp(Activity activity,int value) { return Appearance.dp(activity,value); }
}
