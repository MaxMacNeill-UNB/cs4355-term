package org.example.plugin.source;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HttpSourcePluginTest {

    private static HttpServer server;
    private static String baseUrl;
    private final HttpSourcePlugin plugin = new HttpSourcePlugin();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;

        server.createContext("/hello.txt", exchange -> {
            byte[] body = "Hello, FIM!".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
            exchange.close();
        });

        server.createContext("/binary.bin", exchange -> {
            byte[] body = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, 0x7F};
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
            exchange.close();
        });

        server.createContext("/no-head.txt", exchange -> {
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            byte[] body = "GET only".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
            exchange.close();
        });

        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/hello.txt");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        server.createContext("/redirect-to-https", exchange -> {
            exchange.getResponseHeaders().set("Location", "https://127.0.0.1:" + port + "/hello.txt");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });

        server.createContext("/redirect-loop", exchange -> {
            exchange.getResponseHeaders().set("Location", "/redirect-loop");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        server.createContext("/error", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        // Crawl test pages
        server.createContext("/index.html", exchange -> {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                      <link rel="stylesheet" href="/assets/style.css">
                      <script src="/assets/app.js"></script>
                      <!-- <script src="/assets/commented-out.js"></script> -->
                    </head>
                    <body>
                      <a href="/sub/page2.html#section">Page 2</a>
                      <a href="/login">Login</a>
                      <a href="http://external.example.com/evil.js">External</a>
                      <a href="https://127.0.0.1:%d/secure.js">HTTPS Link</a>
                      <a href="javascript:void(0)">JS</a>
                      <a href="mailto:test@example.com">Mail</a>
                    </body>
                    </html>
                    """.formatted(port);
            sendHtml(exchange, html);
        });

        server.createContext("/sub/page2.html", exchange -> {
            String html = """
                    <html>
                    <body>
                      <script src="../assets/deep.js"></script>
                      <a href="/index.html">Back to index (cycle)</a>
                      <a href="/sub/page3.html">Page 3</a>
                    </body>
                    </html>
                    """;
            sendHtml(exchange, html);
        });

        server.createContext("/sub/page3.html", exchange -> {
            String html = """
                    <html>
                    <body>
                      <script src="/assets/too-deep.js"></script>
                    </body>
                    </html>
                    """;
            sendHtml(exchange, html);
        });

        server.createContext("/login", exchange -> sendHtml(exchange, "<html><body>Login</body></html>"));
        server.createContext("/assets/app.js", exchange -> sendText(exchange, "application/javascript", "console.log('app');"));
        server.createContext("/assets/deep.js", exchange -> sendText(exchange, "application/javascript", "console.log('deep');"));
        server.createContext("/assets/too-deep.js", exchange -> sendText(exchange, "application/javascript", "console.log('too-deep');"));
        server.createContext("/assets/style.css", exchange -> sendText(exchange, "text/css", "body {}"));

        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void sendHtml(com.sun.net.httpserver.HttpExchange exchange, String html) throws IOException {
        sendText(exchange, "text/html; charset=utf-8", html);
    }

    private static void sendText(com.sun.net.httpserver.HttpExchange exchange, String contentType, String content) throws IOException {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
        exchange.close();
    }

    @Test
    void schemeIsHttp() {
        assertEquals("http", plugin.getScheme());
    }

    @Test
    void fetchReturnsTextAndBinaryContent() throws IOException {
        byte[] text = plugin.fetch(baseUrl + "/hello.txt");
        assertEquals("Hello, FIM!", new String(text, StandardCharsets.UTF_8));

        byte[] binary = plugin.fetch(baseUrl + "/binary.bin");
        assertArrayEquals(new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, 0x7F}, binary);
    }

    @Test
    void fetchFollowsSameSchemeRedirects() throws IOException {
        byte[] body = plugin.fetch(baseUrl + "/redirect");
        assertEquals("Hello, FIM!", new String(body, StandardCharsets.UTF_8));
    }

    @Test
    void fetchRejectsCrossSchemeRedirectToHttps() {
        IOException ex = assertThrows(IOException.class, () -> plugin.fetch(baseUrl + "/redirect-to-https"));
        assertTrue(ex.getMessage().contains("cross-scheme redirect"));
    }

    @Test
    void fetchFailsOnRedirectLoop() {
        IOException ex = assertThrows(IOException.class, () -> plugin.fetch(baseUrl + "/redirect-loop"));
        assertTrue(ex.getMessage().contains("Exceeded maximum redirects"));
    }

    @Test
    void fetchThrowsIOExceptionOnNon2xxStatus() {
        assertThrows(IOException.class, () -> plugin.fetch(baseUrl + "/missing"));
        assertThrows(IOException.class, () -> plugin.fetch(baseUrl + "/error"));
    }

    @Test
    void rejectsNonHttpSchemesAndInvalidUris() {
        assertThrows(IllegalArgumentException.class, () -> plugin.fetch("https://example.com/file.js"));
        assertThrows(IllegalArgumentException.class, () -> plugin.fetch("file:///etc/hosts"));
        assertThrows(IllegalArgumentException.class, () -> plugin.fetch(""));
        assertThrows(IllegalArgumentException.class, () -> plugin.fetch(null));
        assertThrows(IllegalArgumentException.class, () -> plugin.isAvailable("https://example.com/file.js"));
        assertThrows(IllegalArgumentException.class, () -> plugin.discover("https://example.com/", 1, List.of(), List.of()));
    }

    @Test
    void isAvailableChecksHeadFallbackAndErrors() throws IOException {
        assertTrue(plugin.isAvailable(baseUrl + "/hello.txt"));
        assertTrue(plugin.isAvailable(baseUrl + "/redirect"));
        assertTrue(plugin.isAvailable(baseUrl + "/no-head.txt"));
        assertFalse(plugin.isAvailable(baseUrl + "/does-not-exist"));
        assertFalse(plugin.isAvailable(baseUrl + "/error"));

        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        assertFalse(plugin.isAvailable("http://127.0.0.1:" + unusedPort + "/hello.txt"));
    }

    @Test
    void discoverDepthZeroReturnsOnlyTargetWhenExtensionMatches() throws IOException {
        List<String> matching = plugin.discover(
                baseUrl + "/index.html",
                0,
                List.of("127.0.0.1"),
                List.of(".html")
        );
        assertEquals(List.of(baseUrl + "/index.html"), matching);

        List<String> nonMatching = plugin.discover(
                baseUrl + "/index.html",
                0,
                List.of("127.0.0.1"),
                List.of(".js")
        );
        assertTrue(nonMatching.isEmpty());
    }

    @Test
    void discoverDepthOneFindsDirectLinksRespectingExtensionsAndDomains() throws IOException {
        List<String> discovered = plugin.discover(
                baseUrl + "/index.html",
                1,
                List.of("127.0.0.1"),
                List.of(".js", "")
        );

        assertEquals(List.of(
                baseUrl + "/assets/app.js",
                baseUrl + "/login"
        ), discovered);
    }

    @Test
    void discoverDepthTwoTraversesTwoHopsAndAvoidsCyclesAndComments() throws IOException {
        List<String> discovered = plugin.discover(
                baseUrl + "/index.html",
                2,
                List.of("127.0.0.1"),
                List.of(".js")
        );

        assertEquals(List.of(
                baseUrl + "/assets/app.js",
                baseUrl + "/assets/deep.js"
        ), discovered);
    }

    @Test
    void discoverReturnsEmptyWhenStartHostNotInAllowedDomains() throws IOException {
        List<String> discovered = plugin.discover(
                baseUrl + "/index.html",
                1,
                List.of("other.example.com"),
                List.of(".html", ".js")
        );
        assertTrue(discovered.isEmpty());
    }

    @Test
    void discoverRejectsNegativeDepth() {
        assertThrows(IllegalArgumentException.class, () ->
                plugin.discover(baseUrl + "/index.html", -1, List.of("127.0.0.1"), List.of(".html"))
        );
    }
}
