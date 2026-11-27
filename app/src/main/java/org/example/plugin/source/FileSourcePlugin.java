package org.example.plugin.source;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@link SourcePlugin} implementation for local filesystem ({@code file://}) resources.
 *
 * <p>Detects the runtime platform (Windows vs. Unix-like) to enforce and normalize
 * platform-appropriate path conventions (e.g., drive letters like {@code C:\} and UNC paths
 * on Windows vs. {@code /}-rooted paths on Unix-like systems), performs single-pass
 * {@link Files#walkFileTree} directory traversal using OS-provided {@link BasicFileAttributes}
 * to minimize filesystem metadata syscalls, and uses pre-sized channel reads for regular files
 * with a streaming fallback for zero-reported-size virtual files.
 */
public class FileSourcePlugin implements SourcePlugin {

    private static final String SCHEME = "file";
    private static final String SCHEME_PREFIX = "file://";
    private static final int STREAM_BUFFER_SIZE = 8192;
    private static final long MAX_FETCH_SIZE_BYTES = Integer.MAX_VALUE - 8;
    private static final String WINDOWS_INVALID_CHARS = "<>\"|?*";

    public enum Platform {
        WINDOWS,
        UNIX
    }

    private final Platform platform;

    public FileSourcePlugin() {
        this(detectPlatform());
    }

    protected FileSourcePlugin(Platform platform) {
        if (platform == null) {
            throw new IllegalArgumentException("Platform must not be null");
        }
        this.platform = platform;
    }

    public static Platform detectPlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return osName.contains("win") ? Platform.WINDOWS : Platform.UNIX;
    }

    public Platform getPlatform() {
        return platform;
    }

    @Override
    public String getScheme() {
        return SCHEME;
    }

    @Override
    public byte[] fetch(String path) throws IOException {
        Path resolved = resolvePath(path);
        BasicFileAttributes attrs = Files.readAttributes(resolved, BasicFileAttributes.class);
        if (!attrs.isRegularFile()) {
            throw new IOException("Target path is not a regular file: " + resolved);
        }

        long size = attrs.size();
        if (size > MAX_FETCH_SIZE_BYTES) {
            throw new IOException("File exceeds maximum supported size (" + size + " bytes): " + resolved);
        }

        if (size == 0) {
            return readStreaming(resolved);
        }
        return readPreSized(resolved, (int) size);
    }

    @Override
    public List<String> discover(
            String path,
            int depth,
            List<String> allowedDomains,
            List<String> allowedExtensions
    ) throws IOException {
        if (depth < 0) {
            throw new IllegalArgumentException("Traversal depth must not be negative: " + depth);
        }

        Path root = resolvePath(path);
        BasicFileAttributes rootAttrs = Files.readAttributes(root, BasicFileAttributes.class);
        Set<String> normalizedExtensions = normalizeAllowedExtensions(allowedExtensions);

        if (rootAttrs.isRegularFile()) {
            if (matchesExtension(root, normalizedExtensions)) {
                return List.of(toFileUri(root));
            }
            return Collections.emptyList();
        }

        if (!rootAttrs.isDirectory()) {
            throw new IOException("Target path is neither a regular file nor a directory: " + root);
        }
        if (!Files.isReadable(root)) {
            throw new AccessDeniedException(root.toString(), null, "Root directory is not readable");
        }

        List<String> discovered = new ArrayList<>();
        Files.walkFileTree(
                root,
                EnumSet.noneOf(FileVisitOption.class),
                depth,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isRegularFile() && matchesExtension(file, normalizedExtensions)) {
                            discovered.add(toFileUri(file));
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                        if (file.equals(root)) {
                            throw exc;
                        }
                        if (exc instanceof AccessDeniedException || exc instanceof NoSuchFileException) {
                            return FileVisitResult.CONTINUE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                }
        );

        Collections.sort(discovered);
        return List.copyOf(discovered);
    }

    @Override
    public boolean isAvailable(String path) throws IOException {
        Path resolved = resolvePath(path);
        return Files.exists(resolved) && Files.isReadable(resolved);
    }

    /**
     * Parses a {@code file://} URI string and normalizes it according to the configured {@link Platform}.
     */
    protected Path resolvePath(String uriString) {
        String normalizedPathStr = normalizeFileUriToPlatformPath(uriString);
        return Path.of(normalizedPathStr).normalize();
    }

    /**
     * Converts a {@code file://} URI string into a platform-appropriate filesystem path string
     * without requiring the target OS filesystem to be active (enabling cross-platform unit testing).
     */
    String normalizeFileUriToPlatformPath(String uriString) {
        if (uriString == null || uriString.isBlank()) {
            throw new IllegalArgumentException("Resource path must not be null or blank");
        }
        String trimmed = uriString.trim();
        if (trimmed.length() < SCHEME_PREFIX.length()
                || !trimmed.substring(0, SCHEME_PREFIX.length()).equalsIgnoreCase(SCHEME_PREFIX)) {
            throw new IllegalArgumentException("Expected 'file://' URI scheme, got: " + uriString);
        }

        String remainder = decodePercentEncoding(trimmed.substring(SCHEME_PREFIX.length()));
        if (remainder.isEmpty()) {
            throw new IllegalArgumentException("File URI is missing a filesystem path: " + uriString);
        }
        if (remainder.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("File URI contains an illegal NUL character: " + uriString);
        }

        if (platform == Platform.WINDOWS) {
            return normalizeWindowsPath(remainder, uriString);
        }
        return normalizeUnixPath(remainder, uriString);
    }

    private static String normalizeUnixPath(String remainder, String originalUri) {
        String path = remainder;

        // Strip optional localhost authority: file://localhost/etc/hosts -> /etc/hosts
        if (path.regionMatches(true, 0, "localhost/", 0, "localhost/".length())) {
            path = path.substring("localhost".length());
        } else if (path.equalsIgnoreCase("localhost")) {
            throw new IllegalArgumentException("File URI is missing a path after localhost: " + originalUri);
        }

        // Reject Windows drive-letter paths (e.g., file:///C:/... or file://C:/... or file://C:\...)
        String withoutLeadingSlash = path.startsWith("/") ? path.substring(1) : path;
        if (startsWithWindowsDrive(withoutLeadingSlash)) {
            throw new IllegalArgumentException(
                    "Windows drive path cannot be resolved on a Unix-like platform: " + originalUri
            );
        }

        // Collapse leading multiple slashes from file:////path -> /path
        if (path.startsWith("//")) {
            path = "/" + path.replaceFirst("^/+", "");
        }

        return path;
    }

    private static String normalizeWindowsPath(String remainder, String originalUri) {
        String path = remainder.replace('\\', '/');

        // Strip optional localhost authority: file://localhost/C:/foo -> /C:/foo
        if (path.regionMatches(true, 0, "localhost/", 0, "localhost/".length())) {
            path = path.substring("localhost".length());
        } else if (path.equalsIgnoreCase("localhost")) {
            throw new IllegalArgumentException("File URI is missing a path after localhost: " + originalUri);
        }

        // Case 1: file:///C:/... -> C:\...
        if (path.startsWith("/") && startsWithWindowsDrive(path.substring(1))) {
            String drivePath = path.substring(1);
            validateWindowsPathChars(drivePath, true, originalUri);
            return drivePath.replace('/', '\\');
        }

        // Case 2: file://C:/... -> C:\...
        if (startsWithWindowsDrive(path)) {
            validateWindowsPathChars(path, true, originalUri);
            return path.replace('/', '\\');
        }

        // Case 3: UNC path file:////server/share/... -> \\server\share\...
        if (path.startsWith("//")) {
            String uncBody = path.substring(2);
            if (uncBody.isEmpty() || !uncBody.contains("/")) {
                throw new IllegalArgumentException("Invalid Windows UNC path: " + originalUri);
            }
            validateWindowsPathChars(uncBody, false, originalUri);
            return "\\\\" + uncBody.replace('/', '\\');
        }

        // Case 4: Bare Unix-style root path without drive letter (e.g., file:///etc/hosts)
        if (path.startsWith("/")) {
            throw new IllegalArgumentException(
                    "Unix-style root path without a Windows drive letter cannot be resolved on Windows: " + originalUri
            );
        }

        // Case 5: Relative path (e.g., file://./scripts/deploy.ps1 or file://../foo.txt)
        if (path.startsWith("./") || path.startsWith("../") || path.equals(".") || path.equals("..")) {
            validateWindowsPathChars(path, false, originalUri);
            return path.replace('/', '\\');
        }

        // Case 6: UNC authority form file://server/share/... -> \\server\share\...
        if (path.contains("/")) {
            validateWindowsPathChars(path, false, originalUri);
            return "\\\\" + path.replace('/', '\\');
        }

        // Single relative filename (e.g., file://deploy.ps1)
        validateWindowsPathChars(path, false, originalUri);
        return path;
    }

    private static boolean startsWithWindowsDrive(String value) {
        if (value.length() < 2) {
            return false;
        }
        char drive = value.charAt(0);
        boolean isLetter = (drive >= 'A' && drive <= 'Z') || (drive >= 'a' && drive <= 'z');
        if (!isLetter || value.charAt(1) != ':') {
            return false;
        }
        return value.length() == 2 || value.charAt(2) == '/' || value.charAt(2) == '\\';
    }

    private static void validateWindowsPathChars(String path, boolean hasDrivePrefix, String originalUri) {
        int startIndex = hasDrivePrefix ? 2 : 0;
        for (int i = startIndex; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == ':' || WINDOWS_INVALID_CHARS.indexOf(c) >= 0) {
                throw new IllegalArgumentException(
                        "Invalid Windows path character '" + c + "' in URI: " + originalUri
                );
            }
        }
    }

    private static String decodePercentEncoding(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        // URLDecoder turns '+' into space; preserve literal '+' by encoding it first
        String safe = value.replace("+", "%2B");
        try {
            return URLDecoder.decode(safe, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid percent-encoding in file URI: " + value, e);
        }
    }

    private static byte[] readPreSized(Path path, int expectedSize) throws IOException {
        byte[] buffer = new byte[expectedSize];
        int totalRead = 0;

        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            while (byteBuffer.hasRemaining()) {
                int read = channel.read(byteBuffer);
                if (read < 0) {
                    break;
                }
                totalRead += read;
            }
            // Handle concurrent growth if file grew while reading
            if (totalRead == expectedSize) {
                ByteBuffer probe = ByteBuffer.allocate(STREAM_BUFFER_SIZE);
                int extra = channel.read(probe);
                if (extra > 0) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream(expectedSize + extra);
                    out.write(buffer, 0, totalRead);
                    do {
                        out.write(probe.array(), 0, extra);
                        probe.clear();
                        extra = channel.read(probe);
                    } while (extra > 0);
                    return out.toByteArray();
                }
                return buffer;
            }
        }

        // File shrank concurrently while reading
        byte[] trimmed = new byte[totalRead];
        System.arraycopy(buffer, 0, trimmed, 0, totalRead);
        return trimmed;
    }

    private static byte[] readStreaming(Path path) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path, StandardOpenOption.READ), STREAM_BUFFER_SIZE);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[STREAM_BUFFER_SIZE];
            int read;
            while ((read = in.read(chunk)) != -1) {
                out.write(chunk, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static String toFileUri(Path path) {
        return path.toAbsolutePath().normalize().toUri().toString();
    }

    private static Set<String> normalizeAllowedExtensions(List<String> allowedExtensions) {
        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<>();
        for (String ext : allowedExtensions) {
            if (ext == null) {
                continue;
            }
            String trimmed = ext.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                normalized.add("");
            } else if (trimmed.startsWith(".")) {
                normalized.add(trimmed);
            } else {
                normalized.add("." + trimmed);
            }
        }
        return normalized;
    }

    private static boolean matchesExtension(Path file, Set<String> allowedExtensions) {
        if (allowedExtensions.isEmpty()) {
            return true;
        }
        Path fileName = file.getFileName();
        if (fileName == null) {
            return allowedExtensions.contains("");
        }
        String name = fileName.toString();
        int lastDot = name.lastIndexOf('.');
        String ext = (lastDot <= 0 || lastDot == name.length() - 1)
                ? ""
                : name.substring(lastDot).toLowerCase(Locale.ROOT);
        return allowedExtensions.contains(ext);
    }
}
