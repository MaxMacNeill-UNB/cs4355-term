package org.example.plugin.source;

import java.io.IOException;
import java.util.List;

/**
 * Stub {@link SourcePlugin} for unencrypted {@code http://} web resources.
 */
public class HttpSourcePlugin implements SourcePlugin {

    @Override
    public String getScheme() {
        return "http";
    }

    @Override
    public byte[] fetch(String path) throws IOException {
        throw new UnsupportedOperationException("HttpSourcePlugin.fetch is not yet implemented");
    }

    @Override
    public List<String> discover(
            String path,
            int depth,
            List<String> allowedDomains,
            List<String> allowedExtensions
    ) throws IOException {
        throw new UnsupportedOperationException("HttpSourcePlugin.discover is not yet implemented");
    }

    @Override
    public boolean isAvailable(String path) throws IOException {
        throw new UnsupportedOperationException("HttpSourcePlugin.isAvailable is not yet implemented");
    }
}
