package com.cybertron.cyberaudio.resolver;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Classifies user-entered HTTP(S) URLs and converts supported web-media links
 * to official embed-player URLs. It does not extract, download, decrypt or
 * otherwise bypass protected media streams.
 */
public final class MediaUrlRouter {
    private static final Pattern YOUTUBE_ID = Pattern.compile("[A-Za-z0-9_-]{6,64}");
    private static final Pattern SPOTIFY_ID = Pattern.compile("[A-Za-z0-9]+", Pattern.CASE_INSENSITIVE);

    private MediaUrlRouter() {}

    public enum Kind {
        DIRECT_AUDIO,
        YOUTUBE,
        SPOTIFY,
        INVALID
    }

    public record Route(Kind kind, String originalUrl, String playbackUrl, String label, String error) {
        public boolean valid() {
            return kind != Kind.INVALID && error.isBlank();
        }

        public boolean webMedia() {
            return kind == Kind.YOUTUBE || kind == Kind.SPOTIFY;
        }
    }

    public static Route route(String input) {
        String raw = input == null ? "" : input.trim();
        if (raw.isBlank()) return invalid(raw, "Enter an audio, YouTube, or Spotify URL.");

        final URI uri;
        try {
            uri = URI.create(raw);
        } catch (IllegalArgumentException e) {
            return invalid(raw, "Invalid URL.");
        }

        String scheme = lower(uri.getScheme());
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return invalid(raw, "Only HTTP and HTTPS URLs are supported.");
        }

        String host = normalizeHost(uri.getHost());
        if (host.isBlank()) return invalid(raw, "URL must contain a host.");

        if (isYouTubeHost(host)) return routeYouTube(uri, raw, host);
        if (isSpotifyHost(host)) return routeSpotify(uri, raw, host);

        return new Route(Kind.DIRECT_AUDIO, raw, raw, "Direct audio", "");
    }

    private static Route routeYouTube(URI uri, String raw, String host) {
        String videoId = "";
        String playlistId = queryValue(uri, "list").orElse("");
        List<String> segments = pathSegments(uri);

        if (host.equals("youtu.be")) {
            if (!segments.isEmpty()) videoId = segments.getFirst();
        } else {
            videoId = queryValue(uri, "v").orElse("");
            if (videoId.isBlank() && segments.size() >= 2) {
                String first = segments.getFirst().toLowerCase(Locale.ROOT);
                if (first.equals("shorts") || first.equals("embed") || first.equals("live")) {
                    videoId = segments.get(1);
                }
            }
        }

        if (!videoId.isBlank() && !YOUTUBE_ID.matcher(videoId).matches()) {
            return invalid(raw, "Unsupported YouTube video ID.");
        }
        if (!playlistId.isBlank() && !YOUTUBE_ID.matcher(playlistId).matches()) {
            return invalid(raw, "Unsupported YouTube playlist ID.");
        }

        String playback;
        if (!videoId.isBlank()) {
            playback = "https://www.youtube.com/embed/" + videoId
                    + "?autoplay=1&controls=1&playsinline=1&rel=0";
            if (!playlistId.isBlank()) playback += "&list=" + playlistId;
        } else if (!playlistId.isBlank()) {
            playback = "https://www.youtube.com/embed/videoseries?list=" + playlistId
                    + "&autoplay=1&controls=1&playsinline=1";
        } else {
            return invalid(raw, "This YouTube URL does not contain a playable video or playlist.");
        }

        return new Route(Kind.YOUTUBE, raw, playback, "YouTube", "");
    }

    private static Route routeSpotify(URI uri, String raw, String host) {
        if (host.equals("spotify.link")) {
            return new Route(Kind.SPOTIFY, raw, raw, "Spotify", "");
        }

        List<String> segments = new ArrayList<>(pathSegments(uri));
        if (!segments.isEmpty() && segments.getFirst().toLowerCase(Locale.ROOT).startsWith("intl-")) {
            segments.removeFirst();
        }
        if (segments.isEmpty()) return invalid(raw, "Unsupported Spotify URL.");

        if (segments.getFirst().equalsIgnoreCase("embed")) {
            return new Route(Kind.SPOTIFY, raw, raw, "Spotify", "");
        }
        if (segments.size() < 2) return invalid(raw, "Spotify link must point to a track, album, playlist, artist, show, or episode.");

        String type = lower(segments.get(0));
        String id = segments.get(1);
        if (!List.of("track", "album", "playlist", "artist", "show", "episode").contains(type)) {
            return invalid(raw, "Unsupported Spotify content type: " + type);
        }
        if (!SPOTIFY_ID.matcher(id).matches()) return invalid(raw, "Unsupported Spotify content ID.");

        String playback = "https://open.spotify.com/embed/" + type + "/" + id + "?utm_source=cyberaudio&theme=0";
        return new Route(Kind.SPOTIFY, raw, playback, "Spotify", "");
    }

    private static boolean isYouTubeHost(String host) {
        return host.equals("youtube.com") || host.equals("youtu.be") || host.endsWith(".youtube.com");
    }

    private static boolean isSpotifyHost(String host) {
        return host.equals("open.spotify.com") || host.equals("spotify.link");
    }

    private static String normalizeHost(String host) {
        String result = lower(host);
        return result.startsWith("www.") ? result.substring(4) : result;
    }

    private static List<String> pathSegments(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) return new ArrayList<>();
        ArrayList<String> out = new ArrayList<>();
        for (String piece : path.split("/")) {
            if (!piece.isBlank()) out.add(piece);
        }
        return out;
    }

    private static Optional<String> queryValue(URI uri, String key) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) return Optional.empty();
        for (String part : query.split("&")) {
            int equals = part.indexOf('=');
            String name = equals < 0 ? part : part.substring(0, equals);
            if (name.equalsIgnoreCase(key)) {
                String value = equals < 0 ? "" : part.substring(equals + 1);
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static Route invalid(String original, String error) {
        return new Route(Kind.INVALID, original, "", "Invalid", error);
    }
}
