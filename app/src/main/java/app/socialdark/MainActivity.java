package app.socialdark;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Native Android shell. Remote pages never receive a JavaScript-to-native bridge. */
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 18, 17), SURFACE = Color.rgb(28, 32, 29);
    private static final int TEXT = Color.rgb(240, 244, 239), MUTED = Color.rgb(161, 174, 163);
    private static final int MINT = Color.rgb(184, 243, 206), LINE = Color.rgb(48, 57, 50);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Preferences prefs;
    private LinearLayout root, body, bottom;
    private FrameLayout stage, browserFrame;
    private WebView browser;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private Site currentSite;
    private String screen = "home", filterScript;
    private TextView focusClock, totalUsage;
    private boolean resumed, wasFocusing;
    private long lastUsageTick;
    private ProgressBar progress;
    private View errorView;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Preferences(this);
        try (var input = getAssets().open("filters.js")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            filterScript = output.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) { throw new IllegalStateException("Missing bundled filters", e); }
        root = column(); root.setBackgroundColor(BG);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        } else {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets;
            });
        }
        setContentView(root);
        stage = new FrameLayout(this);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        bottom = row(); bottom.setPadding(dp(12), dp(6), dp(12), dp(6));
        root.addView(bottom);
        showHome();
        wasFocusing = prefs.focusing();
        if (savedInstanceState != null && savedInstanceState.containsKey("site")) {
            try { openSite(Site.valueOf(savedInstanceState.getString("site"))); }
            catch (IllegalArgumentException ignored) { showHome(); }
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke); return d;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setFontFeatureSettings("kern"); v.setLineSpacing(dp(3), 1);
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }
    private void gap(LinearLayout parent, int height) { View space = new View(this); parent.addView(space, new LinearLayout.LayoutParams(1, dp(height))); }
    private Button button(String label, boolean primary, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(primary ? BG : TEXT); b.setBackground(shape(primary ? MINT : SURFACE, 18, primary ? 0 : LINE));
        b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setPadding(dp(16), dp(8), dp(16), dp(8));
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private LinearLayout card() {
        LinearLayout c = column(); c.setPadding(dp(20), dp(20), dp(20), dp(20)); c.setBackground(shape(SURFACE, 24, LINE));
        return c;
    }
    private void addCard(LinearLayout card) { body.addView(card, new LinearLayout.LayoutParams(-1, -2)); gap(body, 14); }
    private void resetScreen(String name) {
        closeBrowser(); screen = name; focusClock = null; totalUsage = null;
        stage.removeAllViews();
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        body = column(); body.setPadding(dp(24), dp(24), dp(24), dp(24));
        scroll.addView(body); stage.addView(scroll);
        bottom.setVisibility(View.VISIBLE); renderBottom();
    }
    private void renderBottom() {
        bottom.removeAllViews();
        String[] names = {"home", "activity", "settings"};
        String[] labels = {"홈", "사용 기록", "필터 설정"};
        for (int i = 0; i < names.length; i++) {
            final String target = names[i];
            Button b = button(labels[i], screen.equals(target), () -> {
                if (target.equals("home")) showHome();
                else if (target.equals("activity")) showActivity(); else showSettings();
            });
            b.setTag("nav-" + target);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1); lp.setMargins(dp(3), 0, dp(3), 0);
            bottom.addView(b, lp);
        }
    }
    private void heading(String eyebrow, String title, String subtitle) {
        body.addView(text(eyebrow, 12, MINT, true)); gap(body, 10);
        body.addView(text(title, 30, TEXT, true)); gap(body, 10);
        body.addView(text(subtitle, 14, MUTED, false)); gap(body, 26);
    }

    private void showHome() {
        resetScreen("home");
        LinearLayout brand = row();
        TextView logo = text("S", 24, BG, true); logo.setGravity(Gravity.CENTER); logo.setBackground(shape(MINT, 14, 0));
        brand.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView name = text("  SocialDark", 19, TEXT, true); brand.addView(name);
        body.addView(brand); gap(body, 30);
        heading("LESS SCROLL. MORE LIFE.", "연결은 가깝게,\n소음은 멀리.", "보고 싶은 것만 남기는 나만의 소셜 공간");
        LinearLayout focus = card(); focus.setBackground(shape(Color.rgb(37, 53, 43), 24, Color.rgb(58, 83, 66)));
        focus.addView(text("✳  나에게 집중하는 시간", 13, MINT, true)); gap(focus, 14);
        focusClock = text(prefs.focusing() ? remainingFocus() : "잠깐, 스크롤을 내려놓아요", 23, TEXT, true);
        focusClock.setTag("focus-clock"); focus.addView(focusClock); gap(focus, 8);
        focus.addView(text("집중 중에는 피드·추천·숏폼을 숨겨요.\n검색과 메시지는 계속 사용할 수 있어요.", 13, MUTED, false)); gap(focus, 18);
        focus.addView(button(prefs.focusing() ? "집중 세션 종료" : "집중 시작하기  ↗", true, this::focusAction));
        addCard(focus);
        gap(body, 12); body.addView(text("내 소셜 앱", 19, TEXT, true)); gap(body, 6);
        body.addView(text("필요한 순간에, 필요한 만큼만", 13, MUTED, false)); gap(body, 16);
        for (Site site : Site.values()) {
            LinearLayout tile = card(); LinearLayout titleRow = row();
            TextView icon = text(site == Site.INSTAGRAM ? "◎" : "▶", 25, site == Site.INSTAGRAM ? Color.rgb(246, 179, 209) : Color.rgb(255, 158, 148), true);
            icon.setGravity(Gravity.CENTER); icon.setBackground(shape(site == Site.INSTAGRAM ? Color.rgb(60, 40, 52) : Color.rgb(62, 40, 37), 15, 0));
            titleRow.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));
            LinearLayout copy = column(); copy.setPadding(dp(14), 0, 0, 0);
            copy.addView(text(site.title, 19, TEXT, true));
            copy.addView(text((prefs.enabled(site) || prefs.focusing() ? "필터 사용 중" : "필터 꺼짐") + "  ·  오늘 " + duration(prefs.usageSeconds(site, LocalDate.now())), 12, MUTED, false));
            titleRow.addView(copy, new LinearLayout.LayoutParams(0, -2, 1)); tile.addView(titleRow); gap(tile, 18);
            tile.addView(button(site.title + " 열기  ↗", false, () -> openSite(site))); addCard(tile);
        }
        LinearLayout usage = row(); totalUsage = text("오늘 사용  " + duration(todayTotal()), 13, MUTED, false);
        usage.addView(totalUsage, new LinearLayout.LayoutParams(0, -2, 1)); usage.addView(text("기록은 이 기기에만", 11, MUTED, false));
        body.addView(usage); gap(body, 20);
        body.addView(text("앱 안에서 연 Instagram·YouTube에 필터가 적용돼요.", 12, MUTED, false));
    }

    private void showSettings() {
        resetScreen("settings");
        heading("MAKE ROOM FOR WHAT MATTERS", "내 방식대로\n가볍게.", "숨길 항목을 선택하세요. 변경 사항은 바로 저장돼요.");
        if (prefs.focusing()) { body.addView(text("✳ 집중 세션 중에는 모든 집중 필터가 켜져요.\n세션이 끝나면 원래 설정으로 돌아와요.", 14, MINT, false)); gap(body, 20); }
        for (Site site : Site.values()) {
            LinearLayout section = card(); section.addView(text(site.title, 22, TEXT, true)); gap(section, 12);
            addToggle(section, site, "enabled", "집중 필터 사용", "이 앱의 화면 숨기기 켜기", true);
            addToggle(section, site, "shortVideo", site == Site.INSTAGRAM ? "릴스 숨기기" : "Shorts 숨기기", "숏폼 탭·추천·직접 링크 차단", false);
            addToggle(section, site, "feed", "홈 피드 숨기기", "검색해서 원하는 콘텐츠만 보기", false);
            if (site == Site.INSTAGRAM) {
                addToggle(section, site, "explore", "탐색 탭 숨기기", "새로운 추천에 빠지지 않도록", false);
                addToggle(section, site, "stories", "스토리 숨기기", "스토리 목록과 링크 숨기기", false);
            } else {
                addToggle(section, site, "recommendations", "추천 동영상 숨기기", "시청 화면의 다음 동영상 목록", false);
                addToggle(section, site, "autoplay", "연속 자동재생 끄기", "다음 동영상으로 넘어가지 않도록", false);
            }
            addToggle(section, site, "comments", "댓글 숨기기", "콘텐츠 자체에 집중하기", false);
            gap(section, 14);
            int limit = prefs.dailyLimit(site);
            Button limitButton = button("하루 사용 한도  ·  " + (limit == 0 ? "제한 없음" : limit + "분"), false, () -> chooseLimit(site));
            limitButton.setEnabled(!prefs.focusing()); section.addView(limitButton); addCard(section);
        }
        LinearLayout privacy = card(); privacy.addView(text("내 정보는, 내 기기에", 18, TEXT, true)); gap(privacy, 10);
        privacy.addView(text("사용 기록과 필터 설정은 기기에만 저장됩니다. 로그인은 각 서비스의 웹 화면에서 직접 진행하며, 앱은 비밀번호를 수집하지 않습니다. 웹 서비스 자체의 개인정보 처리방침은 별도로 적용됩니다.", 13, MUTED, false)); gap(privacy, 12);
        privacy.addView(text("일부 로그인 방식은 내장 브라우저에서 제한될 수 있어요. 사이트 화면이 바뀌면 일부 필터도 조정이 필요할 수 있습니다.", 12, MUTED, false)); addCard(privacy);
        body.addView(text("SocialDark 0.1.0 · Android", 12, MUTED, false));
    }
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private void addToggle(LinearLayout section, Site site, String key, String title, String subtitle, boolean master) {
        LinearLayout line = row(); line.setPadding(0, dp(12), 0, dp(12));
        LinearLayout copy = column(); copy.addView(text(title, 15, TEXT, true)); copy.addView(text(subtitle, 12, MUTED, false));
        line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this); toggle.setTag(site.key + "." + key); toggle.setContentDescription(site.title + " " + title);
        toggle.setChecked(prefs.focusing() || (master ? prefs.enabled(site) : prefs.option(site, key)));
        toggle.setMinWidth(dp(52)); toggle.setMinHeight(dp(48));
        toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{MINT, MUTED}));
        toggle.setEnabled(!prefs.focusing() && (master || prefs.enabled(site)));
        toggle.setOnCheckedChangeListener((v, checked) -> {
            if (master) { prefs.setEnabled(site, checked); showSettings(); }
            else prefs.setOption(site, key, checked);
        });
        line.addView(toggle); section.addView(line);
        View divider = new View(this); divider.setBackgroundColor(LINE); section.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }
    private void chooseLimit(Site site) {
        int[] options = {0, 15, 30, 60}; String[] labels = {"제한 없음", "15분", "30분", "1시간"};
        int selected = 0; for (int i = 0; i < options.length; i++) if (prefs.dailyLimit(site) == options[i]) selected = i;
        new AlertDialog.Builder(this).setTitle(site.title + " 하루 사용 한도")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    prefs.setDailyLimit(site, options[which]); dialog.dismiss(); showSettings();
                }).setNegativeButton("취소", null).show();
    }

    private void showActivity() {
        resetScreen("activity");
        heading("YOUR TIME, BACK IN YOUR HANDS", "시간을\n돌아보는 순간.", "이 앱에서 화면을 보고 있는 시간만 기록해요.");
        LinearLayout summary = card(); summary.addView(text("오늘의 소셜 사용", 14, MUTED, false)); gap(summary, 8);
        summary.addView(text(duration(todayTotal()), 40, MINT, true)); gap(summary, 12);
        for (Site site : Site.values()) summary.addView(text(site.title + "   " + duration(prefs.usageSeconds(site, LocalDate.now())), 14, TEXT, false));
        addCard(summary);
        LinearLayout history = card(); history.addView(text("최근 7일", 19, TEXT, true)); gap(history, 16);
        long max = 60;
        for (int i = 0; i < 7; i++) {
            LocalDate date = LocalDate.now().minusDays(i); long sum = 0;
            for (Site site : Site.values()) sum += prefs.usageSeconds(site, date);
            max = Math.max(max, sum);
        }
        for (int i = 0; i < 7; i++) {
            LocalDate date = LocalDate.now().minusDays(i); long sum = 0;
            for (Site site : Site.values()) sum += prefs.usageSeconds(site, date);
            LinearLayout line = row();
            line.addView(text(i == 0 ? "오늘" : date.format(DateTimeFormatter.ofPattern("M/d")), 13, MUTED, false), new LinearLayout.LayoutParams(dp(44), -2));
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setProgressTintList(ColorStateList.valueOf(MINT)); bar.setMax(1000); bar.setProgress((int) (sum * 1000 / max));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(12), 1); bp.setMargins(0, 0, dp(12), 0); line.addView(bar, bp);
            TextView duration = text(duration(sum), 12, TEXT, false); duration.setGravity(Gravity.END);
            line.addView(duration, new LinearLayout.LayoutParams(dp(62), -2)); history.addView(line); gap(history, 18);
        }
        addCard(history);
        body.addView(button("사용 기록 지우기", false, () -> new AlertDialog.Builder(this)
                .setTitle("사용 기록을 지울까요?").setMessage("이 기기의 사용 기록이 모두 삭제됩니다. 필터 설정은 유지돼요.")
                .setNegativeButton("취소", null).setPositiveButton("지우기", (d, w) -> { prefs.clearUsage(); showActivity(); }).show()));
    }

    private void focusAction() {
        if (prefs.focusing()) {
            new AlertDialog.Builder(this).setTitle("집중 세션을 종료할까요?")
                    .setMessage("기존 필터 설정으로 돌아갑니다.").setNegativeButton("계속 집중", null)
                    .setPositiveButton("종료", (d, w) -> { prefs.stopFocus(); wasFocusing = false; showHome(); }).show();
        } else {
            String[] choices = {"5분 · 가볍게 시작", "15분 · 잠깐의 여유", "25분 · 깊이 집중", "45분 · 나만의 시간"};
            int[] minutes = {5, 15, 25, 45};
            new AlertDialog.Builder(this).setTitle("얼마나 집중할까요?")
                    .setItems(choices, (d, which) -> { prefs.startFocus(minutes[which]); wasFocusing = true; showHome(); })
                    .setNegativeButton("취소", null).show();
        }
    }
    private String remainingFocus() {
        long seconds = Math.max(0, (prefs.focusEnd() - System.currentTimeMillis() + 999) / 1000);
        return String.format(Locale.KOREA, "%02d:%02d  집중 중", seconds / 60, seconds % 60);
    }
    private long todayTotal() { long sum = 0; for (Site s : Site.values()) sum += prefs.usageSeconds(s, LocalDate.now()); return sum; }
    static String duration(long seconds) { return seconds < 60 ? seconds + "초" : seconds / 60 + "분"; }

    @SuppressLint("SetJavaScriptEnabled")
    private void openSite(Site site) {
        if (prefs.limitReached(site)) { showLimit(site); return; }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        closeBrowser(); screen = "browser"; currentSite = site; focusClock = null;
        stage.removeAllViews(); bottom.setVisibility(View.GONE);
        LinearLayout shell = column(); stage.addView(shell);
        LinearLayout toolbar = row(); toolbar.setPadding(dp(8), dp(6), dp(8), dp(6));
        Button back = button("‹", false, this::browserBack); back.setContentDescription("뒤로 가기"); toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView label = text("  " + site.title, 17, TEXT, true); toolbar.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        toolbar.addView(button("필터", false, this::showSettings));
        Button home = button("홈", false, this::showHome); home.setContentDescription("SocialDark 홈으로"); toolbar.addView(home);
        shell.addView(toolbar);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(ColorStateList.valueOf(MINT)); shell.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        browserFrame = new FrameLayout(this); shell.addView(browserFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        browser = new WebView(this); browser.setBackgroundColor(BG); browser.setTag("service-webview");
        browserFrame.addView(browser, new FrameLayout.LayoutParams(-1, -1));
        WebSettings settings = browser.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true); settings.setSafeBrowsingEnabled(true);
        settings.setSupportMultipleWindows(false); settings.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false);
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                String url = request.getUrl().toString();
                if (NavigationPolicy.belongsTo(url, site) || isGoogleLogin(url)) {
                    if (blockedRoute(url, site)) { toast("집중 필터로 숨긴 화면이에요"); return true; }
                    return false;
                }
                if (request.hasGesture() && NavigationPolicy.isWebLink(url)) confirmExternal(url);
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE); removeError();
                if (!(NavigationPolicy.belongsTo(url, site) || isGoogleLogin(url))) {
                    view.stopLoading(); showWebError("이 주소는 앱 안에서 열 수 없어요.");
                } else if (blockedRoute(url, site)) {
                    view.stopLoading(); view.loadUrl(site.home); toast("집중 필터로 숨긴 화면이에요");
                }
            }
            @Override public void onPageCommitVisible(WebView view, String url) { injectFilters(); }
            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE); injectFilters(); CookieManager.getInstance().flush();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showWebError("연결을 확인해 주세요.\n네트워크가 연결되면 다시 시도할 수 있어요.");
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) { progress.setProgress(value); }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreen != null) { callback.onCustomViewHidden(); return; }
                fullscreen = view; fullscreenCallback = callback; stage.addView(view, new FrameLayout.LayoutParams(-1, -1));
            }
            @Override public void onHideCustomView() { hideFullscreen(); }
        });
        lastUsageTick = resumed ? SystemClock.elapsedRealtime() : 0;
        browser.loadUrl(site.home);
    }
    private boolean isGoogleLogin(String url) {
        try { Uri uri = Uri.parse(url); return "https".equals(uri.getScheme()) && "accounts.google.com".equals(uri.getHost()) && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443); }
        catch (Exception e) { return false; }
    }
    private boolean blockedRoute(String url, Site site) {
        var config = prefs.filterConfig(site);
        return config.optBoolean("enabled") && NavigationPolicy.isBlockedRoute(url, site, config.optBoolean("shortVideo"), config.optBoolean("explore"));
    }
    private void injectFilters() {
        if (browser == null || currentSite == null || !NavigationPolicy.belongsTo(browser.getUrl(), currentSite)) return;
        browser.evaluateJavascript(filterScript + "\nwindow.SocialDarkFilters.apply(" + prefs.filterConfig(currentSite) + ");", null);
    }
    private void confirmExternal(String url) {
        new AlertDialog.Builder(this).setTitle("외부 브라우저에서 열까요?")
                .setMessage(Uri.parse(url).getHost() + "에서는 SocialDark 필터가 적용되지 않아요.")
                .setNegativeButton("취소", null).setPositiveButton("열기", (d, w) -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)); }
                    catch (ActivityNotFoundException e) { toast("링크를 열 브라우저가 없어요"); }
                }).show();
    }
    private void showWebError(String message) {
        if (browserFrame == null || errorView != null) return;
        LinearLayout error = column(); error.setGravity(Gravity.CENTER); error.setPadding(dp(28), dp(28), dp(28), dp(28)); error.setBackgroundColor(BG);
        TextView title = text("잠시 연결이 멈췄어요", 23, TEXT, true); title.setGravity(Gravity.CENTER); error.addView(title); gap(error, 12);
        TextView note = text(message, 14, MUTED, false); note.setGravity(Gravity.CENTER); error.addView(note); gap(error, 24);
        error.addView(button("다시 시도", true, () -> { removeError(); if (browser != null && currentSite != null) browser.loadUrl(currentSite.home); }));
        errorView = error; browserFrame.addView(error, new FrameLayout.LayoutParams(-1, -1)); progress.setVisibility(View.GONE);
    }
    private void removeError() { if (errorView != null && browserFrame != null) browserFrame.removeView(errorView); errorView = null; }
    private void hideFullscreen() {
        if (fullscreen == null) return;
        stage.removeView(fullscreen); fullscreen = null;
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden(); fullscreenCallback = null;
    }
    private void browserBack() { if (fullscreen != null) hideFullscreen(); else if (browser != null && browser.canGoBack()) browser.goBack(); else showHome(); }
    @Override public void onBackPressed() { if (browser != null) browserBack(); else if (!screen.equals("home")) showHome(); else super.onBackPressed(); }
    private void closeBrowser() {
        recordUsage(); hideFullscreen();
        if (browser != null) {
            browser.stopLoading(); browser.onPause();
            CookieManager.getInstance().flush();
            if (browser.getParent() instanceof ViewGroup parent) parent.removeView(browser);
            browser.setWebChromeClient(null); browser.setWebViewClient(new WebViewClient()); browser.destroy(); browser = null;
        }
        currentSite = null; browserFrame = null; errorView = null; lastUsageTick = 0;
    }
    private void recordUsage() {
        if (currentSite == null || lastUsageTick == 0) return;
        long now = SystemClock.elapsedRealtime(), seconds = (now - lastUsageTick) / 1000;
        if (seconds > 0) { prefs.addUsage(currentSite, seconds); lastUsageTick += seconds * 1000; }
    }
    private void showLimit(Site site) {
        showHome();
        new AlertDialog.Builder(this).setTitle("오늘은 충분히 둘러봤어요")
                .setMessage(site.title + "의 하루 사용 한도에 도달했어요. 내일 다시 이용하거나 필터 설정에서 한도를 변경할 수 있습니다.")
                .setPositiveButton("알겠어요", null).setNegativeButton("한도 설정", (d, w) -> showSettings()).show();
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            recordUsage();
            if (currentSite != null && prefs.limitReached(currentSite)) { Site site = currentSite; showLimit(site); }
            boolean focusing = prefs.focusing();
            if (wasFocusing && !focusing) {
                wasFocusing = false; injectFilters();
                if (screen.equals("home")) showHome(); else if (screen.equals("settings")) showSettings();
                toast("집중 세션을 마쳤어요. 수고했어요!");
            }
            if (focusClock != null && focusing) focusClock.setText(remainingFocus());
            if (totalUsage != null) totalUsage.setText(getString(R.string.today_usage, duration(todayTotal())));
            handler.postDelayed(this, 1000);
        }
    };
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if (browser != null) { browser.onResume(); lastUsageTick = SystemClock.elapsedRealtime(); injectFilters(); }
        handler.removeCallbacks(tick); handler.post(tick);
    }
    @Override protected void onPause() {
        recordUsage(); resumed = false; lastUsageTick = 0;
        handler.removeCallbacks(tick);
        if (browser != null) {
            browser.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(m){m.pause()})", null);
            browser.onPause();
            CookieManager.getInstance().flush();
        }
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (currentSite != null) state.putString("site", currentSite.name()); super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { handler.removeCallbacks(tick); closeBrowser(); super.onDestroy(); }
}
