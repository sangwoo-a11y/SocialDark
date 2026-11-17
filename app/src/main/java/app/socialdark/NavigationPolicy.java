package app.socialdark;

import java.net.URI;
import java.util.Locale;

/** Only HTTPS service pages stay inside the credential-bearing WebView. */
public final class NavigationPolicy {
    private NavigationPolicy() {}

    public static boolean belongsTo(String url, Site site) {
        if (url == null || site == null) return false;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443) && host != null
                    && (host.equalsIgnoreCase(site.domain)
                    || host.toLowerCase(Locale.ROOT).endsWith("." + site.domain));
        } catch (IllegalArgumentException e) { return false; }
    }

    public static boolean isWebLink(String url) {
        if (url == null) return false;
        try {
            URI uri = URI.create(url);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException e) { return false; }
    }

    public static boolean isBlockedRoute(String url, Site site, boolean shortVideo, boolean explore) {
        if (!belongsTo(url, site)) return false;
        String path = URI.create(url).getPath();
        if (path == null) return false;
        if (site == Site.YOUTUBE) return shortVideo && (path.equals("/shorts") || path.startsWith("/shorts/"));
        return (shortVideo && (path.equals("/reels") || path.startsWith("/reels/")
                || path.equals("/reel") || path.startsWith("/reel/")))
                || (explore && (path.equals("/explore") || path.startsWith("/explore/")));
    }
}
