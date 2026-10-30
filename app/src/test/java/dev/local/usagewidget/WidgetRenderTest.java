package dev.local.usagewidget;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, qualifiers = "en-rUS-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetRenderTest {
    @Test @Config(qualifiers="ru-rRU-mdpi") public void russianLayoutsHaveNoLargeEmptyMargins() throws Exception {
        gridAndTransitionsHaveNoLargeEmptyMargins();
    }
    @Test public void refreshIndicatorsFollowNetworkStateAndTheme() {
        Context context = RuntimeEnvironment.getApplication();
        int[][] sizes = {{64,64},{76,76},{76,170},{76,350},{166,76},{250,200},{350,380}};
        for (String theme : new String[]{"light","dark"}) {
            context.getSharedPreferences("appearance",0).edit().putString("theme",theme).commit();
            try {
                Refresh.begin(context);
                for (int[] size : sizes) {
                    FrameLayout host = new FrameLayout(context);
                    View view = UsageWidget.render(context,size[0],size[1],false).apply(context,host);
                    host.addView(view,new FrameLayout.LayoutParams(-1,-1));
                    host.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));
                    host.layout(0,0,size[0],size[1]);
                    android.widget.ProgressBar spinner = view.findViewById(theme.equals("dark") ? R.id.spinner_dark : R.id.spinner_light);
                    for (View node = spinner; node != null; node = node.getParent() instanceof View ? (View) node.getParent() : null)
                        assertEquals("Loading ancestor hidden at " + size[0] + "x" + size[1],View.VISIBLE,node.getVisibility());
                    assertTrue("Spinner must be indeterminate",spinner.isIndeterminate());
                    assertNotNull(spinner.getIndeterminateDrawable());
                    check(view,"refresh " + size[0] + "x" + size[1]);
                    View preview = UsageWidget.render(context,size[0],size[1],true).apply(context,new FrameLayout(context));
                    assertEquals(View.GONE,preview.findViewById(R.id.loading_container).getVisibility());
                }
            } finally { Refresh.end(context); }
            assertFalse(Refresh.isFetching());
            for (int[] size : sizes) {
                View view = UsageWidget.render(context,size[0],size[1],false).apply(context,new FrameLayout(context));
                assertEquals(View.GONE,view.findViewById(R.id.loading_container).getVisibility());
            }
        }
    }
    @Test public void invalidSessionStopsRefreshIndicator() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("usage",0).edit().putString("auth","invalid").commit();
        Refresh.run(context);
        assertFalse(Refresh.isFetching());
    }
    @Test public void stripsHaveIndependentRefreshAction() {
        Context context = RuntimeEnvironment.getApplication();
        assertNull(UsageWidget.render(context,76,76,true).apply(context,new FrameLayout(context)).findViewById(R.id.refresh));
        for (int width : new int[]{166,256,346,436}) {
            View view = UsageWidget.render(context, width, 76, true).apply(context, new FrameLayout(context));
            View refresh = view.findViewById(R.id.refresh);
            assertNotNull(refresh);
            assertEquals(View.VISIBLE, refresh.getVisibility());
            assertTrue(refresh.hasOnClickListeners());
        }
    }
    @Test public void tinyIgnoresOldCaptionPreferencesAndShowsErrors() {
        Context context = RuntimeEnvironment.getApplication();
        for (int choice = 0; choice < 4; choice++) {
            context.getSharedPreferences("appearance",0).edit().putInt("tinyLabel",choice).commit();
            View view = UsageWidget.render(context,76,76,true).apply(context,new FrameLayout(context));
            TextView label = view.findViewById(R.id.remaining_label);
            assertEquals("", label.getText().toString());
            assertEquals(View.GONE, label.getVisibility());
        }
        context.getSharedPreferences("usage",0).edit().putString("usage_error","Сессия истекла").commit();
        View view = UsageWidget.render(context,76,76,true).apply(context,new FrameLayout(context));
        assertEquals(View.VISIBLE, view.findViewById(R.id.remaining_label).getVisibility());
        assertEquals(UiText.t(R.string.refresh_warning), ((TextView) view.findViewById(R.id.remaining_label)).getText().toString());
        context.getSharedPreferences("usage",0).edit().clear().commit();
    }
    @Test public void gridAndTransitionsHaveNoLargeEmptyMargins() throws Exception {
        java.util.List<int[]> sizes = new java.util.ArrayList<>();
        for (int columns = 1; columns <= 6; columns++) for (int rows = 1; rows <= 6; rows++)
            sizes.add(new int[]{76 + 90 * (columns - 1), 76 + 90 * (rows - 1)});
        for (int width : new int[]{139,140,229,230}) for (int height : new int[]{119,120,139,140,359,360}) sizes.add(new int[]{width,height});
        java.util.List<String> failures = new java.util.ArrayList<>();
        for (String theme : new String[]{"light", "dark"}) for (float scale : new float[]{1f,1.3f,1.6f}) {
            Configuration config = new Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration()); config.fontScale = scale;
            Context context = RuntimeEnvironment.getApplication().createConfigurationContext(config);
            context.getSharedPreferences("appearance", 0).edit().putString("theme", theme).commit();
            for (int[] size : sizes) {
                FrameLayout host = new FrameLayout(context);
                View view = UsageWidget.render(context, size[0], size[1], true).apply(context, host);
                host.addView(view, new FrameLayout.LayoutParams(-1,-1));
                host.measure(View.MeasureSpec.makeMeasureSpec(size[0], View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(size[1], View.MeasureSpec.EXACTLY));
                host.layout(0,0,size[0],size[1]);
                String name = theme + " " + scale + " " + size[0] + "x" + size[1];
                try { check(view, name); } catch (AssertionError e) { failures.add(e.getMessage()); }
                android.graphics.Rect bounds = new android.graphics.Rect();
                contentBounds(view, host, bounds);
                int margin = Math.max(bounds.top, size[1] - bounds.bottom);
                if (margin > Math.max(28, size[1] * .15)) failures.add(name + " empty margin=" + margin);
                java.util.List<android.graphics.Rect> regions = new java.util.ArrayList<>();
                contentRegions(view, host, regions);
                regions.sort(java.util.Comparator.comparingInt(r -> r.top));
                int bottom = regions.isEmpty() ? 0 : regions.get(0).bottom;
                for (android.graphics.Rect region : regions) {
                    if (region.top - bottom > Math.max(24, size[1] * .12)) failures.add(name + " internal gap=" + (region.top - bottom));
                    bottom = Math.max(bottom, region.bottom);
                }
                if (scale == 1f) {
                    File folder = new File("build/widget-grid"); folder.mkdirs();
                    Bitmap bitmap = Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888); host.draw(new Canvas(bitmap));
                    try (FileOutputStream out = new FileOutputStream(new File(folder, theme + "-" + size[0] + "x" + size[1] + ".png"))) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                }
            }
        }
        assertTrue(String.join("\n", failures), failures.isEmpty());
    }
    private void contentBounds(View view, ViewGroup host, android.graphics.Rect result) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) contentBounds(group.getChildAt(i), host, result);
        } else if ((view instanceof TextView text && text.getText().length() > 0) || view instanceof android.widget.ImageView) {
            android.graphics.Rect rect = new android.graphics.Rect(0,0,view.getWidth(),view.getHeight());
            host.offsetDescendantRectToMyCoords(view, rect); result.union(rect);
        }
    }
    private void contentRegions(View view, ViewGroup host, java.util.List<android.graphics.Rect> result) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof ViewGroup group) {
            for (int i=0;i<group.getChildCount();i++) contentRegions(group.getChildAt(i),host,result);
        } else if ((view instanceof TextView text && text.getText().length()>0) || view instanceof android.widget.ImageView) {
            android.graphics.Rect r = new android.graphics.Rect(0,0,view.getWidth(),view.getHeight()); host.offsetDescendantRectToMyCoords(view,r); result.add(r);
        }
    }
    @Test public void selectedSizePreviewAndHeaderRefresh() {
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller = org.robolectric.Robolectric.buildActivity(MainActivity.class).setup()) {
            View root = controller.get().findViewById(android.R.id.content);
            assertNotNull(root.findViewWithTag("refresh-now"));
            assertNull(root.findViewWithTag("height-1"));
            assertNotNull(root.findViewWithTag("setting-interval"));
            assertNotNull(root.findViewWithTag("setting-date"));
            assertNotNull(root.findViewWithTag("setting-time"));
            for (int size = 0; size < 4; size++) {
                View choice = root.findViewWithTag("widget-size-" + size);
                choice.performClick();
                assertTrue(choice.isSelected());
            }
        }
    }
    @Test public void dateFormatsFitCompactWidgets() {
        Context context = RuntimeEnvironment.getApplication();
        for (int date=0;date<AppSettings.dateLabels(context).length;date++) for (int time=0;time<2;time++) {
            AppSettings.format(context,"date",date); AppSettings.format(context,"time",time);
            for (int[] size : new int[][]{{76,170},{76,350},{166,76},{256,76},{250,200},{350,380}}) {
                FrameLayout host = new FrameLayout(context);
                View view = UsageWidget.render(context,size[0],size[1],true).apply(context,host);
                host.addView(view,new FrameLayout.LayoutParams(-1,-1));
                host.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));
                host.layout(0,0,size[0],size[1]);
                check(view,"date="+date+" time="+time+" "+size[0]+"x"+size[1]);
            }
        }
    }
    @Test public void wideShortWidgetUsesBothColumns() {
        Context context = RuntimeEnvironment.getApplication();
        for (int width : new int[]{250, 350}) {
            FrameLayout host = new FrameLayout(context);
            View view = UsageWidget.render(context, width, 200, true).apply(context, host);
            host.addView(view, new FrameLayout.LayoutParams(-1, -1));
            host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
            host.layout(0, 0, width, 200);
            assertEquals(View.VISIBLE, view.findViewById(R.id.stats_summary).getVisibility());
            assertEquals(View.VISIBLE, view.findViewById(R.id.reset_time).getVisibility());
            android.graphics.Rect percent = new android.graphics.Rect(), stats = new android.graphics.Rect();
            view.findViewById(R.id.remaining).getGlobalVisibleRect(percent);
            view.findViewById(R.id.stats_summary).getGlobalVisibleRect(stats);
            assertTrue("Metrics should occupy the right column", stats.left >= percent.right);
            check(view, "wide " + width);
        }
    }
    @Test @Config(sdk = 35) public void exportLauncherPreviews() throws Exception {
        if (!Boolean.parseBoolean(System.getProperty("updateWidgetPreviews", "false"))) return;
        String[] names = {"tiny", "compact", "overview", "large"};
        int[][] sizes = {{76,76},{170,180},{350,280},{350,380}};
        for (String language : new String[]{"en", "ru"}) {
        RuntimeEnvironment.setQualifiers(language.equals("ru") ? "ru-rRU-mdpi" : "en-rUS-mdpi");
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("usage", 0).edit().clear().commit();
        for (String theme : new String[]{"light", "dark"}) {
            context.getSharedPreferences("appearance", 0).edit().putString("theme", theme).commit();
            for (int i = 0; i < sizes.length; i++) {
                int width = sizes[i][0], height = sizes[i][1];
                FrameLayout host = new FrameLayout(context);
                View view = UsageWidget.render(context, width, height, true).apply(context, host);
                // Picker previews use fixed sample values, not a signed-in user's data.
                view.findViewById(R.id.remaining_label).setVisibility(View.GONE);
                View status = view.findViewById(R.id.status);
                if (status != null) status.setVisibility(View.GONE);
                host.addView(view, new FrameLayout.LayoutParams(-1, -1));
                host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                host.layout(0, 0, width, height);
                Bitmap image = Bitmap.createBitmap(width * 3, height * 3, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(image); canvas.scale(3, 3); host.draw(canvas);
                File folder = new File("src/main/res/drawable" + (language.equals("ru") ? "-ru" : "") + (theme.equals("dark") ? "-night" : "") + "-nodpi"); folder.mkdirs();
                try (FileOutputStream out = new FileOutputStream(new File(folder, "preview_" + names[i] + ".png"))) {
                    image.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
            }
        }
        }
    }
    @Test public void backgroundRefreshCanBuildRemoteViews() throws Exception {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            assertNotNull(executor.submit(() -> UsageWidget.render(RuntimeEnvironment.getApplication(), 300, 280, true)).get(15, java.util.concurrent.TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
    @Test public void settingsActivityRendersBothThemes() throws Exception {
        for (String theme : new String[]{"light", "dark"}) {
            RuntimeEnvironment.getApplication().getSharedPreferences("appearance", 0).edit().putString("theme", theme).commit();
            try (org.robolectric.android.controller.ActivityController<MainActivity> controller = org.robolectric.Robolectric.buildActivity(MainActivity.class).setup()) {
                MainActivity activity = controller.get();
                View view = activity.findViewById(android.R.id.content);
                view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(780, View.MeasureSpec.EXACTLY));
                view.layout(0, 0, 360, 780);
                Bitmap bitmap = Bitmap.createBitmap(360, 780, Bitmap.Config.ARGB_8888);
                view.draw(new Canvas(bitmap));
                File folder = new File("build/widget-previews"); folder.mkdirs();
                try (FileOutputStream out = new FileOutputStream(new File(folder, "settings-" + theme + ".png"))) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                assertNotNull(activity.findViewById(android.R.id.content));
            }
        }
    }
    @Test public void dataStatesAndLargeValues() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        int[][] sizes = {{64,64},{166,76},{256,76},{140,150},{170,320},{300,280},{350,400}};
        for (int state = 0; state < 5; state++) {
            android.content.SharedPreferences prefs = context.getSharedPreferences("usage", 0);
            prefs.edit().clear().commit();
            if (state > 0) {
                org.json.JSONObject usage = new org.json.JSONObject("{\"windows\":[{\"used_percent\":0,\"limit_window_seconds\":604800,\"reset_at\":1993294786}],\"reached\":false,\"allowed\":true,\"balance\":\"123456789.01\",\"unlimited\":false}");
                if (state == 2) usage.put("reached", true);
                if (state == 3) usage.put("balance", org.json.JSONObject.NULL);
                prefs.edit().putString("usage", usage.toString()).putLong("usage_time", System.currentTimeMillis())
                        .putString("usage_error", state == 4 ? "Сессия истекла" : "").commit();
            }
            for (int[] size : sizes) {
                FrameLayout host = new FrameLayout(context);
                View view = UsageWidget.render(context, size[0], size[1], false).apply(context, host);
                host.addView(view, new FrameLayout.LayoutParams(-1, -1));
                host.measure(View.MeasureSpec.makeMeasureSpec(size[0], View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(size[1], View.MeasureSpec.EXACTLY));
                host.layout(0, 0, size[0], size[1]);
                check(view, "state=" + state + " " + size[0] + "x" + size[1]);
            }
        }
        context.getSharedPreferences("usage", 0).edit().clear().commit();
    }
    @Test public void renderMatrix() throws Exception {
        int[][] sizes = {{64,64},{76,76},{76,170},{76,260},{76,350},{170,76},{250,100},{350,76},
                {140,150},{170,180},{170,320},{170,400},
                {250,230},{300,280},{350,350},{350,400},{350,650}};
        for (String theme : new String[]{"light", "dark"}) for (float scale : new float[]{1f, 1.3f, 1.6f}) {
            Configuration config = new Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());
            config.fontScale = scale;
            Context context = RuntimeEnvironment.getApplication().createConfigurationContext(config);
            context.getSharedPreferences("appearance", 0).edit().putString("theme", theme).commit();
            for (int[] size : sizes) {
                String name = theme + "-" + scale + "-" + size[0] + "x" + size[1];
                FrameLayout host = new FrameLayout(context);
                View view = UsageWidget.render(context, size[0], size[1], true).apply(context, host);
                host.addView(view, new FrameLayout.LayoutParams(-1, -1));
                host.measure(View.MeasureSpec.makeMeasureSpec(size[0], View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(size[1], View.MeasureSpec.EXACTLY));
                host.layout(0, 0, size[0], size[1]);
                Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
                host.draw(new Canvas(bitmap));
                File folder = new File("build/widget-previews"); folder.mkdirs();
                try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                check(view, name);
                View card = view.findViewById(R.id.reset_card);
                if (card != null && card.isShown()) {
                    assertTrue(name + " collapsed card", card.getHeight() >= 60);
                    assertEquals(name + " unequal cards", card.getHeight(), view.findViewById(R.id.credit_card).getHeight());
                }
            }
        }
        File folder = new File("build/widget-previews");
        StringBuilder gallery = new StringBuilder("<!doctype html><meta charset='utf-8'><title>Usage · Render checks</title><style>body{background:#808080;font:14px system-ui;margin:24px}main{display:flex;flex-wrap:wrap;gap:24px}figure{margin:0}img{display:block;max-width:400px}figcaption{padding:8px 0}</style><h1>Android RemoteViews · темы и размеры</h1><main>");
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".png"));
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File file : files) gallery.append("<figure><img src='").append(file.getName()).append("'><figcaption>").append(file.getName()).append("</figcaption></figure>");
        }
        java.nio.file.Files.write(new File(folder, "index.html").toPath(), gallery.append("</main>").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private void check(View view, String name) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) continue;
                assertTrue(name + " clipped " + child.getId() + " bottom=" + child.getBottom() + " parent=" + group.getHeight(), child.getBottom() <= group.getHeight());
                assertTrue(name + " negative top", child.getTop() >= 0);
                check(child, name);
            }
        } else if (view instanceof TextView text && text.getText().length() > 0) {
            assertNotNull(name, text.getLayout());
            assertTrue(name + " text height clipped: " + text.getText(),
                    text.getLayout().getHeight() <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom());
        }
    }
}
