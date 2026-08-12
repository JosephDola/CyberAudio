package com.cybertron.cyberaudio.playlist;

import java.util.ArrayList;
import java.util.List;

public final class Playlist {
    public String name = "My Playlist";
    public List<PlaylistTrack> tracks = new ArrayList<>();

    public Playlist() {}

    public Playlist(String name) {
        this.name = name == null || name.isBlank() ? "My Playlist" : name.trim();
    }
}
