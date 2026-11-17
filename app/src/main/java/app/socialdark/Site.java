package app.socialdark;

public enum Site {
    INSTAGRAM("Instagram", "instagram", "https://www.instagram.com/", "instagram.com"),
    YOUTUBE("YouTube", "youtube", "https://m.youtube.com/", "youtube.com");

    public final String title, key, home, domain;
    Site(String title, String key, String home, String domain) {
        this.title = title; this.key = key; this.home = home; this.domain = domain;
    }
}
