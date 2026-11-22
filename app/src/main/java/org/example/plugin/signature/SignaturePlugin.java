package org.example.plugin.signature;

import java.io.IOException;

/**
 * Universal contract for cryptographic signature verification plugins (e.g., {@code pdf}).
 * Every implementation must expose the same command set and return a uniform {@link VerificationResult}.
 */
public interface SignaturePlugin {

    /**
     * Plugin identifier matching {@code signature_validation.plugin} in configuration (e.g., {@code "pdf"}).
     */
    String getPluginName();

    /**
     * Verifies the cryptographic signature embedded in or associated with {@code content}
     * against the public key at {@code publicKeyPath}.
     *
     * @param content       raw target document bytes
     * @param publicKeyPath path/URI to the trusted public key or certificate
     * @return structured verification result
     * @throws IOException if the content or public key cannot be read/parsed
     */
    VerificationResult verify(byte[] content, String publicKeyPath) throws IOException;

    /**
     * Uniform result returned by all {@link SignaturePlugin} implementations.
     *
     * @param valid      {@code true} if the signature is valid and trusted
     * @param signerInfo human-readable signer identity or key fingerprint (may be empty)
     * @param message    diagnostic or status detail
     */
    record VerificationResult(boolean valid, String signerInfo, String message) {
    }
}
