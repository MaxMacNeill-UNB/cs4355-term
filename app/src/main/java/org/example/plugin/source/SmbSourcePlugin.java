package org.example.plugin.source;

import java.io.IOException;
import java.util.List;

/**
 * Stub {@link SourcePlugin} for {@code smb://} network file shares.
 */
public class SmbSourcePlugin implements SourcePlugin {

    @Override
    public String getScheme() {
        return "smb";
    }

    @Override
    public byte[] fetch(String path) throws IOException {
        throw new UnsupportedOperationException("SmbSourcePlugin.fetch is not yet implemented");
    }

    @Override
    public List<String> discover(
            String path,
            int depth,
            List<String> allowedDomains,
            List<String> allowedExtensions
    ) throws IOException {
        throw new UnsupportedOperationException("SmbSourcePlugin.discover is not yet implemented");
    }

    @Override
    public boolean isAvailable(String path) throws IOException {
        throw new UnsupportedOperationException("SmbSourcePlugin.isAvailable is not yet implemented");
    }
}
