package org.example.plugin.hash;

import java.io.IOException;
import java.io.InputStream;

/**
 * Stub {@link HashPlugin} for {@code BLAKE3}.
 */
public class Blake3HashPlugin implements HashPlugin {

    @Override
    public String getAlgorithmName() {
        return "BLAKE3";
    }

    @Override
    public String hash(byte[] data) {
        throw new UnsupportedOperationException("Blake3HashPlugin.hash(byte[]) is not yet implemented");
    }

    @Override
    public String hash(InputStream input) throws IOException {
        throw new UnsupportedOperationException("Blake3HashPlugin.hash(InputStream) is not yet implemented");
    }
}
