package dev.local.usagewidget;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},qualifiers="en-rUS-w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppDialogsTest {
    @Test public void choiceDialogsMatchThemeAndScrollLongLists() throws Exception {
        for (String mode : new String[]{"dark","light"}) {
            RuntimeEnvironment.getApplication().getSharedPreferences("appearance",0).edit().putString("theme",mode).commit();
            try (org.robolectric.android.controller.ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup()) {
                Activity activity=owner.get(); Appearance.Palette p=Appearance.palette(activity);
                AtomicInteger selection=new AtomicInteger(-1);
                Dialog dialog=AppDialogs.choose(activity,UiText.t(R.string.date_format),AppSettings.dateLabels(activity),2,selection::set);
                View root=dialog.getWindow().getDecorView().findViewWithTag("app-dialog");
                assertEquals(p.surface(),((GradientDrawable)root.getBackground()).getColor().getDefaultColor());
                assertTrue(root.findViewWithTag("dialog-option-2").isSelected());
                root.measure(View.MeasureSpec.makeMeasureSpec(328,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                root.layout(0,0,root.getMeasuredWidth(),root.getMeasuredHeight());
                assertTrue("Dialog should fit the screen",root.getHeight()<=640);
                File folder=new File("build/widget-previews"); folder.mkdirs();
                Bitmap image=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888); root.draw(new Canvas(image));
                try (FileOutputStream out=new FileOutputStream(new File(folder,"dialog-"+mode+".png"))) { image.compress(Bitmap.CompressFormat.PNG,100,out); }
                root.findViewWithTag("dialog-option-12").performClick();
                assertEquals(12,selection.get()); assertFalse(dialog.isShowing());
                AppDialogs.dismiss(activity);
            }
        }
    }
    @Test public void cancelDoesNotExecuteConfirmationAndNewDialogReplacesOld() {
        try (org.robolectric.android.controller.ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity=owner.get(); AtomicInteger calls=new AtomicInteger();
            Dialog first=AppDialogs.confirm(activity,"Подтвердить сброс","Использовать один сброс?","Применить",calls::incrementAndGet);
            first.getWindow().getDecorView().findViewWithTag("dialog-negative").performClick();
            assertEquals(0,calls.get()); assertFalse(first.isShowing());
            Dialog second=AppDialogs.message(activity,"Сброс лимита","Данные обновлены");
            Dialog third=AppDialogs.confirm(activity,"Подтвердить сброс","Использовать один сброс?","Применить",calls::incrementAndGet);
            assertFalse(second.isShowing());
            third.getWindow().getDecorView().findViewWithTag("dialog-positive").performClick(); assertEquals(1,calls.get());
            AppNotice.show(activity,"Код скопирован");
            TextView notice=activity.getWindow().getDecorView().findViewWithTag("usage-inline-notice");
            assertNotNull(notice); assertEquals(Appearance.palette(activity).text(),notice.getCurrentTextColor());
            AppNotice.clear(activity); AppDialogs.dismiss(activity);
        }
    }
}
