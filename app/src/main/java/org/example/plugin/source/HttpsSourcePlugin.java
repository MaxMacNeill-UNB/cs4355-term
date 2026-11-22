package org.example.plugin.source;

import java.io.IOException;
import java.util.List;

/**
 * Stub {@link SourcePlugin} for {@code https://} web resources.
 */
public class HttpsSourcePlugin implements SourcePlugin {

    @Override
    public String getScheme() {
        return "https";
    }

    @Override
    public byte[] fetch(String path) throws IOException {
        throw new UnsupportedOperationException("HttpsSourcePlugin.fetch is not yet implemented");
    }

    @Override
    public List<String> discover(
            String path,
            int depth,
            List<String> allowedDomains,
            List<String> allowedExtensions
    ) throws IOException {
        throw new UnsupportedOperationException("HttpsSourcePlugin.discover is not yet implemented");
    }

    @Override
    public boolean isAvailable(String path) throws IOException {
        throw new UnsupportedOperationException("HttpsSourcePlugin.isAvailable is not yet implemented");
    }
}
