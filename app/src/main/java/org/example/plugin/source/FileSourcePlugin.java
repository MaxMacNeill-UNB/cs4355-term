package org.example.plugin.source;

import java.io.IOException;
import java.util.List;

/**
 * Stub {@link SourcePlugin} for {@code file://} local filesystem resources.
 */
public class FileSourcePlugin implements SourcePlugin {

    @Override
    public String getScheme() {
        return "file";
    }

    @Override
    public byte[] fetch(String path) throws IOException {
        throw new UnsupportedOperationException("FileSourcePlugin.fetch is not yet implemented");
    }

    @Override
    public List<String> discover(
            String path,
            int depth,
            List<String> allowedDomains,
            List<String> allowedExtensions
    ) throws IOException {
        throw new UnsupportedOperationException("FileSourcePlugin.discover is not yet implemented");
    }

    @Override
    public boolean isAvailable(String path) throws IOException {
        throw new UnsupportedOperationException("FileSourcePlugin.isAvailable is not yet implemented");
    }
}
