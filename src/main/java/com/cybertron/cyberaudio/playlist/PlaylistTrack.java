package com.cybertron.cyberaudio.playlist;

public final class PlaylistTrack {
    public String name = "";
    public String url = "";

    public PlaylistTrack() {}

    public PlaylistTrack(String name, String url) {
        this.name = name == null ? "" : name.trim();
        this.url = url == null ? "" : url.trim();
    }

    public String displayName() {
        if (name != null && !name.isBlank()) return name;
        if (url == null || url.isBlank()) return "Untitled track";
        String compact = url.replaceFirst("^https?://", "");
        return compact.length() > 56 ? compact.substring(0, 53) + "..." : compact;
    }
}
