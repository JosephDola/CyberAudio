package com.cybertron.cyberaudio.playlist;

import com.cybertron.cyberaudio.CyberAudio;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PlaylistManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Playlist>>() {}.getType();

    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("cyberaudio-playlists.json");
    private final List<Playlist> playlists = new ArrayList<>();
    private int selectedPlaylist;

    public void load() {
        playlists.clear();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                List<Playlist> loaded = GSON.fromJson(reader, LIST_TYPE);
                if (loaded != null) playlists.addAll(loaded);
            } catch (Exception e) {
                CyberAudio.LOGGER.warn("Unable to read CyberAudio playlists; using a fresh library", e);
            }
        }
        sanitize();
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(playlists, LIST_TYPE, writer);
            }
        } catch (Exception e) {
            CyberAudio.LOGGER.warn("Unable to save CyberAudio playlists", e);
        }
    }

    private void sanitize() {
        playlists.removeIf(p -> p == null);
        for (Playlist playlist : playlists) {
            if (playlist.name == null || playlist.name.isBlank()) playlist.name = "My Playlist";
            if (playlist.tracks == null) playlist.tracks = new ArrayList<>();
            playlist.tracks.removeIf(track -> track == null || track.url == null || track.url.isBlank());
        }
        if (playlists.isEmpty()) playlists.add(new Playlist("My Playlist"));
        selectedPlaylist = Math.clamp(selectedPlaylist, 0, playlists.size() - 1);
    }

    public Playlist current() {
        sanitize();
        return playlists.get(selectedPlaylist);
    }

    public int playlistCount() {
        return playlists.size();
    }

    public int selectedPlaylistIndex() {
        return selectedPlaylist;
    }

    public void previousPlaylist() {
        if (playlists.isEmpty()) return;
        selectedPlaylist = (selectedPlaylist - 1 + playlists.size()) % playlists.size();
    }

    public void nextPlaylist() {
        if (playlists.isEmpty()) return;
        selectedPlaylist = (selectedPlaylist + 1) % playlists.size();
    }

    public boolean createPlaylist(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isBlank()) return false;
        playlists.add(new Playlist(clean));
        selectedPlaylist = playlists.size() - 1;
        save();
        return true;
    }

    public boolean deleteCurrentPlaylist() {
        if (playlists.size() <= 1) return false;
        playlists.remove(selectedPlaylist);
        selectedPlaylist = Math.min(selectedPlaylist, playlists.size() - 1);
        save();
        return true;
    }

    public int addTrack(String name, String url) {
        PlaylistTrack track = new PlaylistTrack(name, url);
        if (track.url.isBlank()) return -1;
        current().tracks.add(track);
        save();
        return current().tracks.size() - 1;
    }

    public boolean removeTrack(int index) {
        if (index < 0 || index >= current().tracks.size()) return false;
        current().tracks.remove(index);
        save();
        return true;
    }

    public int moveTrack(int index, int delta) {
        int target = index + delta;
        if (index < 0 || index >= current().tracks.size() || target < 0 || target >= current().tracks.size()) return index;
        PlaylistTrack track = current().tracks.remove(index);
        current().tracks.add(target, track);
        save();
        return target;
    }

    public PlaylistTrack track(int index) {
        if (index < 0 || index >= current().tracks.size()) return null;
        return current().tracks.get(index);
    }

    public int trackCount() {
        return current().tracks.size();
    }
}
