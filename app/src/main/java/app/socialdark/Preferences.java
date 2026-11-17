package app.socialdark;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.LocalDate;

public final class Preferences {
    private final SharedPreferences values;
    public Preferences(Context context) { values = context.getSharedPreferences("socialdark", Context.MODE_PRIVATE); }

    public boolean enabled(Site site) { return values.getBoolean(site.key + ".enabled", true); }
    public void setEnabled(Site site, boolean enabled) { values.edit().putBoolean(site.key + ".enabled", enabled).apply(); }
    public boolean option(Site site, String key) {
        return values.getBoolean(site.key + "." + key, key.equals("shortVideo") || key.equals("explore") || key.equals("autoplay"));
    }
    public void setOption(Site site, String key, boolean value) { values.edit().putBoolean(site.key + "." + key, value).apply(); }
    public int dailyLimit(Site site) { return values.getInt(site.key + ".limit", 0); }
    public void setDailyLimit(Site site, int minutes) { values.edit().putInt(site.key + ".limit", minutes).apply(); }
    public long focusEnd() { return values.getLong("focus.end", 0); }
    public boolean focusing() { return focusEnd() > System.currentTimeMillis(); }
    public void startFocus(int minutes) { values.edit().putLong("focus.end", System.currentTimeMillis() + minutes * 60_000L).apply(); }
    public void stopFocus() { values.edit().remove("focus.end").apply(); }
    private String usageKey(Site site, LocalDate date) { return "usage." + date + "." + site.key; }
    public long usageSeconds(Site site, LocalDate date) { return values.getLong(usageKey(site, date), 0); }
    public void addUsage(Site site, long seconds) {
        if (seconds <= 0) return;
        String key = usageKey(site, LocalDate.now());
        values.edit().putLong(key, values.getLong(key, 0) + seconds).apply();
    }
    public boolean limitReached(Site site) {
        return dailyLimit(site) > 0 && usageSeconds(site, LocalDate.now()) >= dailyLimit(site) * 60L;
    }
    public void clearUsage() {
        SharedPreferences.Editor editor = values.edit();
        for (String key : values.getAll().keySet()) if (key.startsWith("usage.")) editor.remove(key);
        editor.apply();
    }
    public JSONObject filterConfig(Site site) {
        JSONObject result = new JSONObject();
        boolean focus = focusing();
        try {
            result.put("site", site.key);
            result.put("enabled", enabled(site) || focus);
            for (String key : new String[]{"shortVideo", "explore", "feed", "stories", "recommendations", "comments", "autoplay"}) {
                result.put(key, option(site, key) || focus);
            }
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        return result;
    }
}
