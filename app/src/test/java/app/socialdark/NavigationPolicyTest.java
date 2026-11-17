package app.socialdark;

import org.junit.Test;
import static org.junit.Assert.*;

public class NavigationPolicyTest {
    @Test public void recognizesServiceAndMobileDomains() {
        assertTrue(NavigationPolicy.belongsTo("https://m.youtube.com/watch?v=abc", Site.YOUTUBE));
        assertTrue(NavigationPolicy.belongsTo("https://www.instagram.com/direct/inbox/", Site.INSTAGRAM));
        assertTrue(NavigationPolicy.belongsTo("https://consent.youtube.com/", Site.YOUTUBE));
    }
    @Test public void rejectsLookalikeDomainsAndCredentials() {
        for (String url : new String[]{"https://youtube.com.evil.test/", "https://notyoutube.com/", "https://youtube.com@evil.test/", "https://evil@youtube.com/", "https://youtube.com:8443/"}) {
            assertFalse(url, NavigationPolicy.belongsTo(url, Site.YOUTUBE));
        }
    }
    @Test public void rejectsInsecureAndNonWebSchemes() {
        for (String url : new String[]{"http://youtube.com/", "javascript:alert(1)", "intent://youtube.com/", "file:///youtube.com", "not a url", "", null}) {
            assertFalse(NavigationPolicy.belongsTo(url, Site.YOUTUBE));
        }
    }
    @Test public void blocksOnlySelectedRoutes() {
        assertTrue(NavigationPolicy.isBlockedRoute("https://m.youtube.com/shorts/123", Site.YOUTUBE, true, false));
        assertTrue(NavigationPolicy.isBlockedRoute("https://www.instagram.com/reel/123/", Site.INSTAGRAM, true, false));
        assertTrue(NavigationPolicy.isBlockedRoute("https://www.instagram.com/explore/", Site.INSTAGRAM, false, true));
        assertFalse(NavigationPolicy.isBlockedRoute("https://m.youtube.com/shorts/123", Site.YOUTUBE, false, false));
        assertFalse(NavigationPolicy.isBlockedRoute("https://m.youtube.com/watch?v=123", Site.YOUTUBE, true, true));
        assertFalse(NavigationPolicy.isBlockedRoute("https://www.instagram.com/direct/inbox/", Site.INSTAGRAM, true, true));
        assertFalse(NavigationPolicy.isBlockedRoute("https://evil.test/shorts/123", Site.YOUTUBE, true, true));
    }
    @Test public void decodesEncodedRoutes() {
        assertTrue(NavigationPolicy.isBlockedRoute("https://m.youtube.com/%73horts/123", Site.YOUTUBE, true, false));
    }
    @Test public void externalLinksRequireWebUrls() {
        assertTrue(NavigationPolicy.isWebLink("https://example.com/path"));
        assertFalse(NavigationPolicy.isWebLink("intent://example.com"));
        assertFalse(NavigationPolicy.isWebLink("https://user:pass@example.com/"));
        assertFalse(NavigationPolicy.isWebLink(null));
    }
}
