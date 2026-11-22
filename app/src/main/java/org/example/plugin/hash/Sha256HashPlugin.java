package org.example.plugin.hash;

import java.io.IOException;
import java.io.InputStream;

/**
 * Stub {@link HashPlugin} for {@code SHA-256}.
 */
public class Sha256HashPlugin implements HashPlugin {

    @Override
    public String getAlgorithmName() {
        return "SHA-256";
    }

    @Override
    public String hash(byte[] data) {
        throw new UnsupportedOperationException("Sha256HashPlugin.hash(byte[]) is not yet implemented");
    }

    @Override
    public String hash(InputStream input) throws IOException {
        throw new UnsupportedOperationException("Sha256HashPlugin.hash(InputStream) is not yet implemented");
    }
}
