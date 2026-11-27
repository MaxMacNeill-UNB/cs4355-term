package org.example.plugin.source;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HttpsSourcePluginTest {

    private static HttpsServer server;
    private static HttpsServer untrustedServer;
    private static String baseUrl;
    private static String untrustedBaseUrl;
    private static HttpsSourcePlugin trustedPlugin;

    @BeforeAll
    static void startHttpsServer(@TempDir Path tempDir) throws Exception {
        KeyStore keyStore = createSelfSignedKeyStore(tempDir.resolve("test-keystore.p12"));
        KeyStore untrustedKeyStore = createSelfSignedKeyStore(tempDir.resolve("untrusted-keystore.p12"));
        char[] password = "changeit".toCharArray();

        SSLContext serverContext = createServerSslContext(keyStore, password);
        SSLContext untrustedServerContext = createServerSslContext(untrustedKeyStore, password);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext clientContext = SSLContext.getInstance("TLS");
        clientContext.init(null, tmf.getTrustManagers(), null);

        trustedPlugin = new HttpsSourcePlugin(clientContext);

        untrustedServer = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        untrustedServer.setHttpsConfigurator(new HttpsConfigurator(untrustedServerContext));
        int untrustedPort = untrustedServer.getAddress().getPort();
        untrustedBaseUrl = "https://127.0.0.1:" + untrustedPort;
        untrustedServer.createContext("/bad-cert.html", exchange -> sendText(exchange, "text/html; charset=utf-8", "<html></html>"));
        untrustedServer.start();

        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        int port = server.getAddress().getPort();
        baseUrl = "https://127.0.0.1:" + port;

        server.createContext("/secret.txt", exchange -> {
            byte[] body = "Encrypted FIM payload".getBytes(StandardCharsets.UTF_8);
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

        server.createContext("/redirect-https", exchange -> {
            exchange.getResponseHeaders().set("Location", "/secret.txt");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        server.createContext("/downgrade-to-http", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + port + "/secret.txt");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        server.createContext("/portal/index.html", exchange -> {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                      <script src="/portal/app.js"></script>
                    </head>
                    <body>
                      <a href="/portal/sub.html">Subpage</a>
                      <a href="http://127.0.0.1:%d/insecure.js">Insecure Link</a>
                    </body>
                    </html>
                    """.formatted(port);
            sendText(exchange, "text/html; charset=utf-8", html);
        });

        server.createContext("/portal/sub.html", exchange -> {
            String html = """
                    <html>
                    <body>
                      <script src="/portal/nested.js"></script>
                    </body>
                    </html>
                    """;
            sendText(exchange, "text/html; charset=utf-8", html);
        });

        server.createContext("/portal/links-to-untrusted.html", exchange -> {
            String html = """
                    <html>
                    <body>
                      <a href="%s/bad-cert.html">Untrusted Child</a>
                    </body>
                    </html>
                    """.formatted(untrustedBaseUrl);
            sendText(exchange, "text/html; charset=utf-8", html);
        });

        server.createContext("/portal/app.js", exchange -> sendText(exchange, "application/javascript", "console.log('https app');"));
        server.createContext("/portal/nested.js", exchange -> sendText(exchange, "application/javascript", "console.log('https nested');"));

        server.start();
    }

    private static KeyStore createSelfSignedKeyStore(Path keystorePath) throws Exception {
        char[] password = "changeit".toCharArray();
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(
                keytool,
                "-genkeypair",
                "-alias", "test",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-storetype", "PKCS12",
                "-keystore", keystorePath.toString(),
                "-storepass", new String(password),
                "-keypass", new String(password),
                "-dname", "CN=localhost",
                "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-validity", "1"
        ).redirectErrorStream(true).start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException("keytool failed with exit code " + exitCode + ": " + output);
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = Files.newInputStream(keystorePath)) {
            keyStore.load(is, password);
        }
        return keyStore;
    }

    private static SSLContext createServerSslContext(KeyStore keyStore, char[] password) throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    @AfterAll
    static void stopHttpsServer() {
        if (server != null) {
            server.stop(0);
        }
        if (untrustedServer != null) {
            untrustedServer.stop(0);
        }
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
    void schemeIsHttps() {
        assertEquals("https", trustedPlugin.getScheme());
    }

    @Test
    void fetchRetrievesEncryptedContentAndFollowsHttpsRedirects() throws IOException {
        byte[] content = trustedPlugin.fetch(baseUrl + "/secret.txt");
        assertEquals("Encrypted FIM payload", new String(content, StandardCharsets.UTF_8));

        byte[] redirected = trustedPlugin.fetch(baseUrl + "/redirect-https");
        assertEquals("Encrypted FIM payload", new String(redirected, StandardCharsets.UTF_8));
    }

    @Test
    void defaultPluginRejectsUntrustedSelfSignedCertificateWithClearMessage() {
        HttpsSourcePlugin defaultPlugin = new HttpsSourcePlugin();

        SSLHandshakeException fetchEx = assertThrows(
                SSLHandshakeException.class,
                () -> defaultPlugin.fetch(baseUrl + "/secret.txt")
        );
        assertTrue(fetchEx.getMessage().contains("TLS certificate validation failed"));
        assertTrue(fetchEx.getMessage().contains(baseUrl + "/secret.txt"));
        assertNotNull(fetchEx.getCause());

        SSLHandshakeException availEx = assertThrows(
                SSLHandshakeException.class,
                () -> defaultPlugin.isAvailable(baseUrl + "/secret.txt")
        );
        assertTrue(availEx.getMessage().contains("TLS certificate validation failed"));

        SSLHandshakeException discoverEx = assertThrows(
                SSLHandshakeException.class,
                () -> defaultPlugin.discover(baseUrl + "/portal/index.html", 1, List.of("127.0.0.1"), List.of(".js"))
        );
        assertTrue(discoverEx.getMessage().contains("TLS certificate validation failed"));
    }

    @Test
    void discoverAbortsOnTlsCertificateFailureAtChildDepth() {
        SSLHandshakeException ex = assertThrows(
                SSLHandshakeException.class,
                () -> trustedPlugin.discover(
                        baseUrl + "/portal/links-to-untrusted.html",
                        2,
                        List.of("127.0.0.1"),
                        List.of(".html")
                )
        );
        assertTrue(ex.getMessage().contains("TLS certificate validation failed"));
    }

    @Test
    void fetchRefusesHttpsToHttpDowngradeRedirect() {
        IOException ex = assertThrows(IOException.class, () -> trustedPlugin.fetch(baseUrl + "/downgrade-to-http"));
        assertTrue(ex.getMessage().contains("downgrade"));
    }

    @Test
    void rejectsNonHttpsSchemes() {
        assertThrows(IllegalArgumentException.class, () -> trustedPlugin.fetch("http://example.com/file.js"));
        assertThrows(IllegalArgumentException.class, () -> trustedPlugin.isAvailable("http://example.com/file.js"));
        assertThrows(IllegalArgumentException.class, () -> trustedPlugin.discover("http://example.com/", 1, List.of(), List.of()));
    }

    @Test
    void isAvailableChecksHttpsResources() throws IOException {
        assertTrue(trustedPlugin.isAvailable(baseUrl + "/secret.txt"));
        assertTrue(trustedPlugin.isAvailable(baseUrl + "/redirect-https"));
        assertFalse(trustedPlugin.isAvailable(baseUrl + "/missing"));
    }

    @Test
    void discoverCrawlsHttpsAndIgnoresHttpLinks() throws IOException {
        List<String> discovered = trustedPlugin.discover(
                baseUrl + "/portal/index.html",
                2,
                List.of("127.0.0.1"),
                List.of(".js")
        );

        assertEquals(List.of(
                baseUrl + "/portal/app.js",
                baseUrl + "/portal/nested.js"
        ), discovered);
    }
}
