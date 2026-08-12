package com.cybertron.cyberaudio.resolver;

import java.net.URI;

public record ResolvedMedia(URI sourceUri, String title, String contentType, long contentLength) {
}
