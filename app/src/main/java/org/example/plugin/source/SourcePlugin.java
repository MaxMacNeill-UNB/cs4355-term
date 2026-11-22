package org.example.plugin.source;

import java.io.IOException;
import java.util.List;

/**
 * Universal contract for resource acquisition plugins (e.g., {@code http}, {@code https}, {@code smb}, {@code file}).
 * Every implementation must expose the same command set and return uniform results to the core monitor.
 */
public interface SourcePlugin {

    /**
     * URI scheme handled by this plugin (lowercase, without {@code ://}, e.g., {@code "https"}).
     */
    String getScheme();

    /**
     * Fetches the raw bytes of a single target resource.
     *
     * @param path full resource URI/path
     * @return raw file or page bytes
     * @throws IOException if the resource cannot be accessed or read
     */
    byte[] fetch(String path) throws IOException;

    /**
     * Discovers matching resource URIs starting from {@code path} (used for {@code page} targets
     * and directory/folder traversal).
     *
     * @param path              starting URI/path
     * @param depth             maximum crawl/traversal depth ({@code 0} = target only)
     * @param allowedDomains    domains permitted during web crawling (empty for non-web sources)
     * @param allowedExtensions file extensions to include (empty string {@code ""} matches extensionless paths)
     * @return list of discovered resource URIs matching the filter criteria
     * @throws IOException if discovery fails
     */
    List<String> discover(
            String path,
            int depth,
            List<String> allowedDomains,
            List<String> allowedExtensions
    ) throws IOException;

    /**
     * Checks whether the resource at {@code path} exists and is reachable.
     *
     * @param path full resource URI/path
     * @return {@code true} if reachable, {@code false} otherwise
     * @throws IOException if the reachability check fails unexpectedly
     */
    boolean isAvailable(String path) throws IOException;
}
