package org.example.plugin.hash;

import java.io.IOException;
import java.io.InputStream;

/**
 * Universal contract for cryptographic hashing plugins (e.g., {@code SHA-256}, {@code BLAKE3}).
 * Every implementation must expose the same command set and return lowercase hex-encoded digests.
 */
public interface HashPlugin {

    /**
     * Canonical algorithm identifier (e.g., {@code "SHA-256"}, {@code "BLAKE3"}).
     */
    String getAlgorithmName();

    /**
     * Computes the digest of an in-memory byte array.
     *
     * @param data input bytes to hash
     * @return lowercase hexadecimal digest string
     */
    String hash(byte[] data);

    /**
     * Computes the digest of a byte stream.
     *
     * @param input stream to read and hash
     * @return lowercase hexadecimal digest string
     * @throws IOException if reading from the stream fails
     */
    String hash(InputStream input) throws IOException;

    /**
     * Verifies that {@code data} matches {@code expectedHexDigest}.
     *
     * @param data              input bytes to hash
     * @param expectedHexDigest expected hex-encoded digest (case-insensitive)
     * @return {@code true} if the computed digest matches {@code expectedHexDigest}
     */
    default boolean verify(byte[] data, String expectedHexDigest) {
        if (expectedHexDigest == null) {
            return false;
        }
        return hash(data).equalsIgnoreCase(expectedHexDigest);
    }
}
