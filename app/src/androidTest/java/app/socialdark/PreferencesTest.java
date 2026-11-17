package app.socialdark;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.LocalDate;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PreferencesTest {
    private Context context;
    private Preferences prefs;
    @Before public void setup() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("socialdark", Context.MODE_PRIVATE).edit().clear().commit();
        prefs = new Preferences(context);
    }
    @Test public void choicesSurviveNewInstancesAndStayIndependent() {
        prefs.setOption(Site.YOUTUBE, "shortVideo", false);
        Preferences restored = new Preferences(context);
        assertFalse(restored.option(Site.YOUTUBE, "shortVideo"));
        assertTrue(restored.option(Site.INSTAGRAM, "shortVideo"));
    }
    @Test public void focusOverridesWithoutOverwritingChoices() {
        prefs.setEnabled(Site.YOUTUBE, false); prefs.setOption(Site.YOUTUBE, "comments", false);
        prefs.startFocus(5);
        assertTrue(new Preferences(context).focusing());
        assertTrue(prefs.filterConfig(Site.YOUTUBE).optBoolean("enabled"));
        assertTrue(prefs.filterConfig(Site.YOUTUBE).optBoolean("comments"));
        prefs.stopFocus();
        assertFalse(prefs.filterConfig(Site.YOUTUBE).optBoolean("enabled"));
        assertFalse(prefs.option(Site.YOUTUBE, "comments"));
    }
    @Test public void expiredFocusDoesNotEnforceFilters() {
        context.getSharedPreferences("socialdark", Context.MODE_PRIVATE).edit().putLong("focus.end", System.currentTimeMillis()-1).commit();
        assertFalse(prefs.focusing());
        assertFalse(prefs.filterConfig(Site.INSTAGRAM).optBoolean("feed"));
    }
    @Test public void usageLimitsCountOnlyRecordedSecondsAndResetByDate() {
        prefs.setDailyLimit(Site.YOUTUBE, 15); prefs.addUsage(Site.YOUTUBE, 899);
        assertFalse(prefs.limitReached(Site.YOUTUBE));
        prefs.addUsage(Site.YOUTUBE, 1);
        assertTrue(prefs.limitReached(Site.YOUTUBE));
        assertFalse(prefs.limitReached(Site.INSTAGRAM));
        assertEquals(0, prefs.usageSeconds(Site.YOUTUBE, LocalDate.now().minusDays(1)));
        prefs.clearUsage();
        assertEquals(0, prefs.usageSeconds(Site.YOUTUBE, LocalDate.now()));
        assertEquals(15, prefs.dailyLimit(Site.YOUTUBE));
    }
}
