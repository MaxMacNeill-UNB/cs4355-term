package org.example.plugin.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileSourcePluginTest {

    private final FileSourcePlugin plugin = new FileSourcePlugin();

    @Test
    void schemeIsFileAndPlatformIsDetected() {
        assertEquals("file", plugin.getScheme());
        assertNotNull(plugin.getPlatform());
        assertEquals(FileSourcePlugin.detectPlatform(), plugin.getPlatform());
    }

    @Test
    void unixPathNormalizationHandlesStandardAndEdgeCases() {
        FileSourcePlugin unixPlugin = new FileSourcePlugin(FileSourcePlugin.Platform.UNIX);

        assertEquals("/etc/hosts", unixPlugin.normalizeFileUriToPlatformPath("file:///etc/hosts"));
        assertEquals("/etc/hosts", unixPlugin.normalizeFileUriToPlatformPath("file://localhost/etc/hosts"));
        assertEquals("var/scripts/deploy.sh", unixPlugin.normalizeFileUriToPlatformPath("file://var/scripts/deploy.sh"));
        assertEquals("/tmp/my file+1.sh", unixPlugin.normalizeFileUriToPlatformPath("file:///tmp/my%20file+1.sh"));
        assertEquals("/var/log/app.log", unixPlugin.normalizeFileUriToPlatformPath("file:////var/log/app.log"));

        assertThrows(IllegalArgumentException.class, () ->
                unixPlugin.normalizeFileUriToPlatformPath("file:///C:/Users/admin/deploy.ps1"));
        assertThrows(IllegalArgumentException.class, () ->
                unixPlugin.normalizeFileUriToPlatformPath("file://C:\\Users\\admin\\deploy.ps1"));
        assertThrows(IllegalArgumentException.class, () ->
                unixPlugin.normalizeFileUriToPlatformPath("file:///tmp/bad\0name"));
    }

    @Test
    void windowsPathNormalizationHandlesDriveLettersUncAndValidation() {
        FileSourcePlugin winPlugin = new FileSourcePlugin(FileSourcePlugin.Platform.WINDOWS);

        assertEquals("C:\\Users\\admin\\deploy.ps1",
                winPlugin.normalizeFileUriToPlatformPath("file:///C:/Users/admin/deploy.ps1"));
        assertEquals("C:\\Users\\admin\\deploy.ps1",
                winPlugin.normalizeFileUriToPlatformPath("file://C:/Users/admin/deploy.ps1"));
        assertEquals("C:\\Users\\admin\\deploy.ps1",
                winPlugin.normalizeFileUriToPlatformPath("file:///C:\\Users\\admin\\deploy.ps1"));
        assertEquals("C:\\Users\\admin\\deploy.ps1",
                winPlugin.normalizeFileUriToPlatformPath("file://localhost/C:/Users/admin/deploy.ps1"));
        assertEquals("\\\\fileserver\\share\\scripts\\run.ps1",
                winPlugin.normalizeFileUriToPlatformPath("file:////fileserver/share/scripts/run.ps1"));
        assertEquals("\\\\fileserver\\share\\scripts\\run.ps1",
                winPlugin.normalizeFileUriToPlatformPath("file://fileserver/share/scripts/run.ps1"));
        assertEquals(".\\scripts\\run.ps1",
                winPlugin.normalizeFileUriToPlatformPath("file://./scripts/run.ps1"));

        // Bare Unix root path rejected on Windows
        assertThrows(IllegalArgumentException.class, () ->
                winPlugin.normalizeFileUriToPlatformPath("file:///etc/hosts"));
        // Invalid Windows characters rejected
        assertThrows(IllegalArgumentException.class, () ->
                winPlugin.normalizeFileUriToPlatformPath("file:///C:/Users/bad*file.txt"));
        assertThrows(IllegalArgumentException.class, () ->
                winPlugin.normalizeFileUriToPlatformPath("file:///C:/Users/bad:name.txt"));
    }

    @Test
    void rejectsInvalidSchemesAndEmptyPaths() {
        assertThrows(IllegalArgumentException.class, () -> plugin.fetch("http://example.com/a.js"));
        assertThrows(IllegalArgumentException.class, () -> plugin.fetch("https://example.com/a.js"));
        assertThrows(IllegalArgumentException.class, () -> plugin.fetch("file://"));
        assertThrows(IllegalArgumentException.class, () -> plugin.fetch(""));
        assertThrows(IllegalArgumentException.class, () -> plugin.fetch(null));
        assertThrows(IllegalArgumentException.class, () -> plugin.isAvailable("http://example.com/a.js"));
        assertThrows(IllegalArgumentException.class, () -> plugin.discover("http://example.com", 1, List.of(), List.of()));
    }

    @Test
    void fetchReadsTextBinaryEmptyAndEncodedSpaceFiles(@TempDir Path tempDir) throws IOException {
        Path textFile = tempDir.resolve("script.sh");
        Files.writeString(textFile, "#!/bin/sh\necho ok\n", StandardCharsets.UTF_8);

        Path binaryFile = tempDir.resolve("data.bin");
        byte[] binaryData = new byte[]{0x00, 0x10, 0x7F, (byte) 0xFF, 0x42};
        Files.write(binaryFile, binaryData);

        Path emptyFile = tempDir.resolve("empty.txt");
        Files.write(emptyFile, new byte[0]);

        Path spacedFile = tempDir.resolve("my script.py");
        Files.writeString(spacedFile, "print('hi')", StandardCharsets.UTF_8);

        assertEquals("#!/bin/sh\necho ok\n",
                new String(plugin.fetch(textFile.toUri().toString()), StandardCharsets.UTF_8));
        assertArrayEquals(binaryData, plugin.fetch(binaryFile.toUri().toString()));
        assertArrayEquals(new byte[0], plugin.fetch(emptyFile.toUri().toString()));
        assertEquals("print('hi')",
                new String(plugin.fetch(spacedFile.toUri().toString()), StandardCharsets.UTF_8));

        // Directory and missing file should fail with IOException
        assertThrows(IOException.class, () -> plugin.fetch(tempDir.toUri().toString()));
        assertThrows(IOException.class, () -> plugin.fetch(tempDir.resolve("missing.txt").toUri().toString()));
    }

    @Test
    void isAvailableChecksExistenceAndReadability(@TempDir Path tempDir) throws IOException {
        Path existing = tempDir.resolve("present.txt");
        Files.writeString(existing, "ok", StandardCharsets.UTF_8);

        assertTrue(plugin.isAvailable(existing.toUri().toString()));
        assertTrue(plugin.isAvailable(tempDir.toUri().toString()));
        assertFalse(plugin.isAvailable(tempDir.resolve("absent.txt").toUri().toString()));
    }

    @Test
    void discoverRespectsDepthExtensionsAndDeterministicOrdering(@TempDir Path tempDir) throws IOException {
        Path rootJs = Files.writeString(tempDir.resolve("app.js"), "console.log(1);");
        Path rootUpperJs = Files.writeString(tempDir.resolve("UPPER.JS"), "console.log(2);");
        Path rootNoExt = Files.writeString(tempDir.resolve("Makefile"), "all:");
        Files.writeString(tempDir.resolve("ignore.txt"), "ignored");

        Path level1Dir = Files.createDirectory(tempDir.resolve("sub"));
        Path level1Js = Files.writeString(level1Dir.resolve("mod.js"), "export {};");

        Path level2Dir = Files.createDirectory(level1Dir.resolve("deep"));
        Path level2Js = Files.writeString(level2Dir.resolve("leaf.js"), "export default 1;");

        String dirUri = tempDir.toUri().toString();

        // depth 0 on a directory returns empty (no children traversed)
        assertTrue(plugin.discover(dirUri, 0, List.of(), List.of(".js")).isEmpty());

        // depth 0 on a single regular file returns that file if extension matches
        assertEquals(List.of(rootJs.toUri().toString()),
                plugin.discover(rootJs.toUri().toString(), 0, List.of(), List.of(".js")));
        assertTrue(plugin.discover(rootJs.toUri().toString(), 0, List.of(), List.of(".py")).isEmpty());

        // depth 1: direct children only (case-insensitive extension + extensionless "")
        List<String> depth1 = plugin.discover(dirUri, 1, List.of("ignored.example.com"), List.of(".js", ""));
        assertEquals(List.of(
                rootNoExt.toUri().toString(),
                rootUpperJs.toUri().toString(),
                rootJs.toUri().toString()
        ), depth1);

        // depth 2: includes sub/mod.js but not sub/deep/leaf.js
        List<String> depth2 = plugin.discover(dirUri, 2, List.of(), List.of(".js"));
        assertEquals(List.of(
                rootUpperJs.toUri().toString(),
                rootJs.toUri().toString(),
                level1Js.toUri().toString()
        ), depth2);

        // depth 3: includes sub/deep/leaf.js (sorted lexicographically)
        List<String> depth3 = plugin.discover(dirUri, 3, List.of(), List.of(".js"));
        assertEquals(List.of(
                rootUpperJs.toUri().toString(),
                rootJs.toUri().toString(),
                level2Js.toUri().toString(),
                level1Js.toUri().toString()
        ), depth3);
    }

    @Test
    void discoverDoesNotFollowSymlinkLoopsAndValidatesArguments(@TempDir Path tempDir) throws IOException {
        Path subDir = Files.createDirectory(tempDir.resolve("sub"));
        Path file = Files.writeString(subDir.resolve("a.js"), "1");
        try {
            Files.createSymbolicLink(subDir.resolve("loop"), tempDir);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Symlinks may require elevated privileges on some Windows setups
        }

        List<String> found = plugin.discover(tempDir.toUri().toString(), 5, List.of(), List.of(".js"));
        assertEquals(List.of(file.toUri().toString()), found);

        assertThrows(IllegalArgumentException.class, () ->
                plugin.discover(tempDir.toUri().toString(), -1, List.of(), List.of(".js")));
        assertThrows(IOException.class, () ->
                plugin.discover(tempDir.resolve("does-not-exist").toUri().toString(), 1, List.of(), List.of()));
    }
}
