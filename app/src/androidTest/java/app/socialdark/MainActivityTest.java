package app.socialdark;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {
    @Before public void clear() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("socialdark", Context.MODE_PRIVATE).edit().clear().commit();
    }
    @Test public void nativeScreensAndFilterPersistenceWork() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.getWindow().getDecorView().findViewWithTag("nav-settings").performClick();
                Switch shorts = activity.getWindow().getDecorView().findViewWithTag("youtube.shortVideo");
                assertTrue(shorts.isChecked()); shorts.setChecked(false);
                assertFalse(new Preferences(activity).option(Site.YOUTUBE, "shortVideo"));
                activity.getWindow().getDecorView().findViewWithTag("nav-activity").performClick();
                assertNotNull(findButton(activity.getWindow().getDecorView(), "사용 기록 지우기"));
                activity.getWindow().getDecorView().findViewWithTag("nav-home").performClick();
                assertNotNull(findButton(activity.getWindow().getDecorView(), "Instagram 열기  ↗"));
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertFalse(new Preferences(activity).option(Site.YOUTUBE, "shortVideo")));
        }
    }
    @Test public void focusDisablesSettingsAndPreservesOriginals() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                new Preferences(activity).startFocus(5);
                activity.getWindow().getDecorView().findViewWithTag("nav-settings").performClick();
                Switch feed = activity.getWindow().getDecorView().findViewWithTag("instagram.feed");
                assertTrue(feed.isChecked()); assertFalse(feed.isEnabled());
                assertFalse(new Preferences(activity).option(Site.INSTAGRAM, "feed"));
            });
        }
    }
    @Test public void openingServiceUsesSafeWebView() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                findButton(activity.getWindow().getDecorView(), "YouTube 열기  ↗").performClick();
                android.webkit.WebView web = activity.getWindow().getDecorView().findViewWithTag("service-webview");
                assertNotNull(web);
                assertFalse(web.getSettings().getAllowFileAccess());
                assertTrue(web.getSettings().getJavaScriptEnabled());
                assertTrue(web.getSettings().getMediaPlaybackRequiresUserGesture());
            });
        }
    }
    @Test public void backgroundTimeIsNotCountedAsUsage() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> findButton(activity.getWindow().getDecorView(), "YouTube 열기  ↗").performClick());
            android.os.SystemClock.sleep(1500);
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            Context context = ApplicationProvider.getApplicationContext();
            long before = new Preferences(context).usageSeconds(Site.YOUTUBE, java.time.LocalDate.now());
            assertTrue(before >= 1);
            android.os.SystemClock.sleep(1500);
            assertEquals(before, new Preferences(context).usageSeconds(Site.YOUTUBE, java.time.LocalDate.now()));
        }
    }
    private Button findButton(View v, String text) {
        if (v instanceof Button b && b.getText().toString().equals(text)) return b;
        if (v instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            Button found = findButton(group.getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
}
