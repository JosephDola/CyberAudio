package com.cybertron.cyberaudio.resolver;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class DirectAudioResolver {
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public CompletableFuture<ResolvedMedia> resolve(String input) {
        final URI uri;
        try {
            uri = URI.create(input.trim());
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(new IOException("Invalid URL", e));
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            return CompletableFuture.failedFuture(new IOException("Only HTTP and HTTPS URLs are allowed"));
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return CompletableFuture.failedFuture(new IOException("URL must contain a host"));
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "CyberAudio/0.2.1")
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).handle((response, error) -> {
            if (error != null || response == null || response.statusCode() >= 400) {
                return new ResolvedMedia(uri, deriveTitle(uri), "application/octet-stream", -1);
            }
            String contentType = response.headers().firstValue("content-type").orElse("application/octet-stream");
            long length = response.headers().firstValueAsLong("content-length").orElse(-1L);
            return new ResolvedMedia(response.uri(), deriveTitle(response.uri()), contentType, length);
        });
    }

    private static String deriveTitle(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || path.endsWith("/")) return uri.getHost();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.isBlank() ? uri.getHost() : name;
    }
}
