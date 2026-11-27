package org.example.plugin.source;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.time.Duration;
import java.util.List;

/**
 * {@link SourcePlugin} implementation for TLS-encrypted {@code https://} web resources.
 *
 * <p>Reuses all HTTP transport, redirect handling, availability checking, and web crawling logic
 * from {@link HttpSourcePlugin}, adding TLS configuration (modern protocols and HTTPS hostname
 * verification), strict HTTPS-to-HTTP downgrade prevention, and explicit TLS/certificate error
 * handling.
 */
public class HttpsSourcePlugin extends HttpSourcePlugin {

    private static final String[] DEFAULT_TLS_PROTOCOLS = {"TLSv1.3", "TLSv1.2"};

    public HttpsSourcePlugin() {
        this(defaultSslContext(), createDefaultSslParameters());
    }

    public HttpsSourcePlugin(SSLContext sslContext) {
        this(sslContext, createDefaultSslParameters());
    }

    public HttpsSourcePlugin(SSLContext sslContext, SSLParameters sslParameters) {
        this(createHttpsClient(sslContext, sslParameters), DEFAULT_REQUEST_TIMEOUT, DEFAULT_MAX_REDIRECTS);
    }

    protected HttpsSourcePlugin(HttpClient httpClient, Duration requestTimeout, int maxRedirects) {
        super(httpClient, requestTimeout, maxRedirects);
    }

    private static SSLContext defaultSslContext() {
        try {
            return SSLContext.getDefault();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Default SSLContext is unavailable", e);
        }
    }

    protected static SSLParameters createDefaultSslParameters() {
        SSLParameters params = new SSLParameters();
        params.setProtocols(DEFAULT_TLS_PROTOCOLS);
        params.setEndpointIdentificationAlgorithm("HTTPS");
        return params;
    }

    private static HttpClient createHttpsClient(SSLContext sslContext, SSLParameters sslParameters) {
        if (sslContext == null) {
            throw new IllegalArgumentException("SSLContext must not be null");
        }
        if (sslParameters == null) {
            throw new IllegalArgumentException("SSLParameters must not be null");
        }
        return HttpClient.newBuilder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .sslContext(sslContext)
                .sslParameters(sslParameters)
                .build();
    }

    @Override
    public String getScheme() {
        return "https";
    }

    @Override
    public byte[] fetch(String path) throws IOException {
        try {
            return super.fetch(path);
        } catch (SSLException e) {
            throw handleTlsException(path, e);
        }
    }

    @Override
    public List<String> discover(
            String path,
            int depth,
            List<String> allowedDomains,
            List<String> allowedExtensions
    ) throws IOException {
        try {
            return super.discover(path, depth, allowedDomains, allowedExtensions);
        } catch (SSLException e) {
            throw handleTlsException(path, e);
        }
    }

    @Override
    public boolean isAvailable(String path) throws IOException {
        try {
            return super.isAvailable(path);
        } catch (SSLException e) {
            throw handleTlsException(path, e);
        }
    }

    @Override
    protected void validateRedirectTarget(URI fromUri, URI toUri) throws IOException {
        if ("http".equalsIgnoreCase(toUri.getScheme())) {
            throw new IOException("Refusing HTTPS downgrade redirect to unencrypted HTTP: " + toUri);
        }
        super.validateRedirectTarget(fromUri, toUri);
    }

    @Override
    protected boolean shouldAbortDiscovery(IOException e) {
        return e instanceof SSLException;
    }

    protected SSLException handleTlsException(String path, SSLException cause) {
        String reason = describeTlsFailure(cause);
        String message = reason + " for " + path;

        if (cause instanceof SSLHandshakeException) {
            SSLHandshakeException wrapped = new SSLHandshakeException(message);
            wrapped.initCause(cause);
            return wrapped;
        }
        if (cause instanceof SSLPeerUnverifiedException) {
            SSLPeerUnverifiedException wrapped = new SSLPeerUnverifiedException(message);
            wrapped.initCause(cause);
            return wrapped;
        }
        return new SSLException(message, cause);
    }

    private static String describeTlsFailure(SSLException cause) {
        if (hasCause(cause, CertificateExpiredException.class)) {
            return "TLS certificate has expired";
        }
        if (hasCause(cause, CertificateNotYetValidException.class)) {
            return "TLS certificate is not yet valid";
        }
        if (isHostnameMismatch(cause)) {
            return "TLS hostname verification failed";
        }
        if (hasCause(cause, CertPathValidatorException.class) || hasCause(cause, CertificateException.class)) {
            return "TLS certificate validation failed";
        }
        if (cause instanceof SSLPeerUnverifiedException) {
            return "TLS peer identity could not be verified";
        }
        if (cause instanceof SSLHandshakeException) {
            return "TLS handshake failed";
        }
        return "TLS error";
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isHostnameMismatch(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && (msg.contains("No subject alternative")
                    || msg.contains("No name matching")
                    || msg.contains("HTTPS hostname wrong"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
