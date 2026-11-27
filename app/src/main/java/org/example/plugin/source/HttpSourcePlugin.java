package org.example.plugin.source;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link SourcePlugin} implementation for unencrypted {@code http://} web resources.
 *
 * <p>Handles single-resource fetching, availability checks, and bounded breadth-first
 * web discovery over HTTP. Scheme enforcement ensures this plugin only operates on
 * {@code http://} URIs and does not silently follow cross-scheme redirects.
 */
public class HttpSourcePlugin implements SourcePlugin {

    protected static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    protected static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    protected static final int DEFAULT_MAX_REDIRECTS = 5;
    protected static final String USER_AGENT = "FIM-Monitor/1.0";

    private static final Pattern HTML_COMMENT_PATTERN = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern BASE_HREF_PATTERN = Pattern.compile(
            "<base\\b[^>]*?\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'<>`]+))",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LINK_ATTRIBUTE_PATTERN = Pattern.compile(
            "<(?:a|link|script|img|iframe|source|embed|object)\\b[^>]*?\\b(?:href|src|data)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'<>`]+))",
            Pattern.CASE_INSENSITIVE
    );

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final int maxRedirects;

    public HttpSourcePlugin() {
        this(createDefaultHttpClient(), DEFAULT_REQUEST_TIMEOUT, DEFAULT_MAX_REDIRECTS);
    }

    protected HttpSourcePlugin(HttpClient httpClient) {
        this(httpClient, DEFAULT_REQUEST_TIMEOUT, DEFAULT_MAX_REDIRECTS);
    }

    protected HttpSourcePlugin(HttpClient httpClient, Duration requestTimeout, int maxRedirects) {
        if (httpClient == null) {
            throw new IllegalArgumentException("HttpClient must not be null");
        }
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("Request timeout must be positive");
        }
        if (maxRedirects < 0) {
            throw new IllegalArgumentException("Max redirects must not be negative");
        }
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.maxRedirects = maxRedirects;
    }

    private static HttpClient createDefaultHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String getScheme() {
        return "http";
    }

    @Override
    public byte[] fetch(String path) throws IOException {
        URI uri = parseAndValidateUri(path);
        HttpResult<byte[]> result = sendFollowingRedirects(uri, "GET", HttpResponse.BodyHandlers.ofByteArray());
        int status = result.response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP GET failed with status " + status + " for " + result.finalUri);
        }
        return result.response.body();
    }

    @Override
    public List<String> discover(
            String path,
            int depth,
            List<String> allowedDomains,
            List<String> allowedExtensions
    ) throws IOException {
        URI startUri = normalizeUri(parseAndValidateUri(path));
        if (depth < 0) {
            throw new IllegalArgumentException("Crawl depth must not be negative: " + depth);
        }

        Set<String> normalizedDomains = normalizeAllowedDomains(allowedDomains, startUri);
        Set<String> normalizedExtensions = normalizeAllowedExtensions(allowedExtensions);

        if (!isDomainAllowed(startUri, normalizedDomains)) {
            return Collections.emptyList();
        }

        Set<String> visited = new HashSet<>();
        Set<String> discovered = new LinkedHashSet<>();
        Queue<CrawlNode> queue = new ArrayDeque<>();

        visited.add(startUri.toString());
        queue.add(new CrawlNode(startUri, 0));

        while (!queue.isEmpty()) {
            CrawlNode current = queue.poll();

            if (matchesExtension(current.uri, normalizedExtensions)) {
                discovered.add(current.uri.toString());
            }

            if (current.depth >= depth) {
                continue;
            }

            HttpResult<byte[]> result;
            try {
                result = sendFollowingRedirects(current.uri, "GET", HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException e) {
                if (current.depth == 0 || shouldAbortDiscovery(e)) {
                    throw e;
                }
                continue;
            }

            int status = result.response.statusCode();
            if (status < 200 || status >= 300) {
                if (current.depth == 0) {
                    throw new IOException("HTTP GET failed with status " + status + " for " + result.finalUri);
                }
                continue;
            }

            if (!isHtmlResponse(result.response)) {
                continue;
            }

            String html = new String(result.response.body(), StandardCharsets.UTF_8);
            URI baseUri = resolveBaseUri(result.finalUri, html);
            for (String rawLink : extractLinks(html)) {
                URI resolved = resolveCandidateLink(baseUri, rawLink);
                if (resolved == null) {
                    continue;
                }
                if (!getScheme().equalsIgnoreCase(resolved.getScheme())) {
                    continue;
                }
                if (!isDomainAllowed(resolved, normalizedDomains)) {
                    continue;
                }
                String canonical = resolved.toString();
                if (visited.add(canonical)) {
                    queue.add(new CrawlNode(resolved, current.depth + 1));
                }
            }
        }

        return List.copyOf(discovered);
    }

    @Override
    public boolean isAvailable(String path) throws IOException {
        URI uri = parseAndValidateUri(path);
        try {
            HttpResult<Void> headResult = sendFollowingRedirects(uri, "HEAD", HttpResponse.BodyHandlers.discarding());
            int status = headResult.response.statusCode();
            if (status >= 200 && status < 300) {
                return true;
            }
            if (status == 405 || status == 501) {
                HttpResult<Void> getResult = sendFollowingRedirects(uri, "GET", HttpResponse.BodyHandlers.discarding());
                int getStatus = getResult.response.statusCode();
                return getStatus >= 200 && getStatus < 300;
            }
            return false;
        } catch (ConnectException | UnknownHostException | HttpTimeoutException e) {
            return false;
        }
    }

    protected URI parseAndValidateUri(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Resource path must not be null or blank");
        }
        URI uri;
        try {
            uri = new URI(path.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + path, e);
        }
        String expectedScheme = getScheme();
        if (uri.getScheme() == null || !expectedScheme.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(
                    "Expected '" + expectedScheme + "' URI scheme, got: " + path
            );
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("URI is missing a valid host: " + path);
        }
        return uri;
    }

    protected <T> HttpResult<T> sendFollowingRedirects(
            URI initialUri,
            String method,
            HttpResponse.BodyHandler<T> bodyHandler
    ) throws IOException {
        URI currentUri = initialUri;
        String currentMethod = method;

        for (int redirectCount = 0; redirectCount <= maxRedirects; redirectCount++) {
            HttpRequest request = HttpRequest.newBuilder(currentUri)
                    .timeout(requestTimeout)
                    .header("User-Agent", USER_AGENT)
                    .method(currentMethod, HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<T> response = sendRequest(request, bodyHandler);
            int status = response.statusCode();

            if (isRedirectStatus(status)) {
                Optional<String> location = response.headers().firstValue("Location");
                if (location.isEmpty() || location.get().isBlank()) {
                    return new HttpResult<>(currentUri, response);
                }
                if (redirectCount == maxRedirects) {
                    throw new IOException("Exceeded maximum redirects (" + maxRedirects + ") for " + initialUri);
                }
                URI targetUri;
                try {
                    targetUri = currentUri.resolve(location.get().trim());
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid redirect Location header '" + location.get() + "' from " + currentUri, e);
                }
                validateRedirectTarget(currentUri, targetUri);
                currentUri = targetUri;
                if (status == 303 && !"HEAD".equalsIgnoreCase(currentMethod)) {
                    currentMethod = "GET";
                }
                continue;
            }

            return new HttpResult<>(currentUri, response);
        }

        throw new IOException("Exceeded maximum redirects (" + maxRedirects + ") for " + initialUri);
    }

    protected void validateRedirectTarget(URI fromUri, URI toUri) throws IOException {
        String targetScheme = toUri.getScheme();
        if (targetScheme == null || !getScheme().equalsIgnoreCase(targetScheme)) {
            throw new IOException(
                    "Refusing cross-scheme redirect from " + fromUri.getScheme() + " to " + targetScheme
                            + " (" + toUri + ")"
            );
        }
        if (toUri.getHost() == null || toUri.getHost().isBlank()) {
            throw new IOException("Redirect target is missing a valid host: " + toUri);
        }
    }

    protected boolean shouldAbortDiscovery(IOException e) {
        return false;
    }

    private <T> HttpResponse<T> sendRequest(
            HttpRequest request,
            HttpResponse.BodyHandler<T> bodyHandler
    ) throws IOException {
        try {
            return httpClient.send(request, bodyHandler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted for " + request.uri(), e);
        }
    }

    private static boolean isRedirectStatus(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static boolean isHtmlResponse(HttpResponse<byte[]> response) {
        Optional<String> contentType = response.headers().firstValue("Content-Type");
        if (contentType.isEmpty()) {
            return false;
        }
        String normalized = contentType.get().toLowerCase(Locale.ROOT);
        return normalized.contains("text/html") || normalized.contains("application/xhtml+xml");
    }

    private static URI resolveBaseUri(URI pageUri, String html) {
        String withoutComments = HTML_COMMENT_PATTERN.matcher(html).replaceAll("");
        Matcher baseMatcher = BASE_HREF_PATTERN.matcher(withoutComments);
        if (baseMatcher.find()) {
            String baseHref = firstNonNullGroup(baseMatcher);
            if (baseHref != null && !baseHref.isBlank()) {
                try {
                    return pageUri.resolve(baseHref.trim());
                } catch (IllegalArgumentException ignored) {
                    return pageUri;
                }
            }
        }
        return pageUri;
    }

    private static Set<String> extractLinks(String html) {
        String withoutComments = HTML_COMMENT_PATTERN.matcher(html).replaceAll("");
        Set<String> links = new LinkedHashSet<>();
        Matcher matcher = LINK_ATTRIBUTE_PATTERN.matcher(withoutComments);
        while (matcher.find()) {
            String value = firstNonNullGroup(matcher);
            if (value != null && !value.isBlank()) {
                links.add(value.trim());
            }
        }
        return links;
    }

    private static String firstNonNullGroup(Matcher matcher) {
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String value = matcher.group(i);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static URI resolveCandidateLink(URI baseUri, String rawLink) {
        if (rawLink == null || rawLink.isBlank()) {
            return null;
        }
        String trimmed = rawLink.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("#")
                || lower.startsWith("javascript:")
                || lower.startsWith("mailto:")
                || lower.startsWith("tel:")
                || lower.startsWith("data:")) {
            return null;
        }
        try {
            URI resolved = baseUri.resolve(trimmed);
            return normalizeUri(resolved);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static URI normalizeUri(URI uri) {
        URI normalized = uri.normalize();
        String scheme = normalized.getScheme() != null ? normalized.getScheme().toLowerCase(Locale.ROOT) : null;
        String host = normalized.getHost() != null ? normalized.getHost().toLowerCase(Locale.ROOT) : null;
        String path = normalized.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        try {
            return new URI(
                    scheme,
                    normalized.getRawUserInfo(),
                    host,
                    normalized.getPort(),
                    path,
                    normalized.getRawQuery(),
                    null
            );
        } catch (URISyntaxException e) {
            return normalized;
        }
    }

    private static Set<String> normalizeAllowedDomains(List<String> allowedDomains, URI startUri) {
        Set<String> domains = new HashSet<>();
        if (allowedDomains == null || allowedDomains.isEmpty()) {
            domains.add(startUri.getHost().toLowerCase(Locale.ROOT));
            return domains;
        }
        for (String domain : allowedDomains) {
            if (domain != null && !domain.isBlank()) {
                domains.add(domain.trim().toLowerCase(Locale.ROOT));
            }
        }
        return domains;
    }

    private static boolean isDomainAllowed(URI uri, Set<String> allowedDomains) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        for (String domain : allowedDomains) {
            if (normalizedHost.equals(domain) || normalizedHost.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> normalizeAllowedExtensions(List<String> allowedExtensions) {
        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<>();
        for (String ext : allowedExtensions) {
            if (ext == null) {
                continue;
            }
            String trimmed = ext.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                normalized.add("");
            } else if (trimmed.startsWith(".")) {
                normalized.add(trimmed);
            } else {
                normalized.add("." + trimmed);
            }
        }
        return normalized;
    }

    private static boolean matchesExtension(URI uri, Set<String> allowedExtensions) {
        if (allowedExtensions.isEmpty()) {
            return true;
        }
        String extension = extractExtension(uri.getPath());
        return allowedExtensions.contains(extension);
    }

    private static String extractExtension(String path) {
        if (path == null || path.isEmpty() || path.endsWith("/")) {
            return "";
        }
        int lastSlash = path.lastIndexOf('/');
        String segment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        int lastDot = segment.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == segment.length() - 1) {
            return "";
        }
        return segment.substring(lastDot).toLowerCase(Locale.ROOT);
    }

    protected static final class HttpResult<T> {
        final URI finalUri;
        final HttpResponse<T> response;

        HttpResult(URI finalUri, HttpResponse<T> response) {
            this.finalUri = finalUri;
            this.response = response;
        }
    }

    private record CrawlNode(URI uri, int depth) {
    }
}
