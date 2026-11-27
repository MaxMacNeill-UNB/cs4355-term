package org.example.plugin;

import org.example.config.ConfigParser;
import org.example.config.FileTarget;
import org.example.config.MonitorTarget;
import org.example.plugin.hash.Blake3HashPlugin;
import org.example.plugin.hash.HashPlugin;
import org.example.plugin.hash.Sha256HashPlugin;
import org.example.plugin.signature.PdfSignaturePlugin;
import org.example.plugin.signature.SignaturePlugin;
import org.example.plugin.source.FileSourcePlugin;
import org.example.plugin.source.HttpSourcePlugin;
import org.example.plugin.source.HttpsSourcePlugin;
import org.example.plugin.source.SourcePlugin;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PluginRegistryTest {

    private final PluginRegistry registry = PluginRegistry.withDefaults();

    @Test
    void selectsDefaultSourcePluginsAndRejectsInvalidSchemes() {
        assertInstanceOf(HttpSourcePlugin.class, registry.selectSourcePlugin("http://example.com/a.js"));
        assertInstanceOf(HttpsSourcePlugin.class, registry.selectSourcePlugin("https://example.com/a.js"));
        assertInstanceOf(FileSourcePlugin.class, registry.selectSourcePlugin("file:///etc/hosts"));

        assertThrows(IllegalArgumentException.class, () -> registry.selectSourcePlugin("smb://server/share/file.sh"));
        assertThrows(IllegalArgumentException.class, () -> registry.selectSourcePlugin("ftp://example.com/file"));
        assertThrows(IllegalArgumentException.class, () -> registry.selectSourcePlugin("/var/log/syslog"));
        assertThrows(IllegalArgumentException.class, () -> registry.selectSourcePlugin((String) null));
    }

    @Test
    void selectsDefaultHashPluginsAndRejectsInvalidAlgorithms() {
        assertInstanceOf(Sha256HashPlugin.class, registry.selectHashPlugin("SHA-256"));
        assertInstanceOf(Sha256HashPlugin.class, registry.selectHashPlugin("sha256"));
        assertInstanceOf(Blake3HashPlugin.class, registry.selectHashPlugin("BLAKE3"));

        assertThrows(IllegalArgumentException.class, () -> registry.selectHashPlugin("MD5"));
        assertThrows(IllegalArgumentException.class, () -> registry.selectHashPlugin(""));
    }

    @Test
    void selectsDefaultSignaturePluginsAndRejectsInvalidPlugins() {
        assertInstanceOf(PdfSignaturePlugin.class, registry.selectSignaturePlugin("pdf"));
        assertThrows(IllegalArgumentException.class, () -> registry.selectSignaturePlugin("pgp"));
        assertThrows(IllegalArgumentException.class, () -> registry.selectSignaturePlugin((String) null));
    }

    @Test
    void resolvesPluginsForParsedTargets() throws Exception {
        MonitorTarget[] targets = new ConfigParser().parse(Path.of("..", "DOCS", "target.yml.example"));

        FileTarget deployScript = assertInstanceOf(FileTarget.class, targets[0]);
        assertInstanceOf(FileSourcePlugin.class, registry.selectSourcePlugin(deployScript));
        assertInstanceOf(Sha256HashPlugin.class, registry.selectHashPlugin(deployScript));
        assertEquals(Optional.empty(), registry.selectSignaturePlugin(deployScript));

        FileTarget complianceDoc = assertInstanceOf(FileTarget.class, targets[1]);
        assertInstanceOf(HttpsSourcePlugin.class, registry.selectSourcePlugin(complianceDoc));
        Optional<SignaturePlugin> sigPlugin = registry.selectSignaturePlugin(complianceDoc);
        assertTrue(sigPlugin.isPresent());
        assertInstanceOf(PdfSignaturePlugin.class, sigPlugin.get());
    }

    @Test
    void stubPluginsThrowUnsupportedOperationException() {
        SourcePlugin source = registry.selectSourcePlugin("file:///tmp/example.txt");
        assertThrows(UnsupportedOperationException.class, () -> source.fetch("file:///tmp/example.txt"));
        assertThrows(UnsupportedOperationException.class, () -> source.discover("file:///tmp/example.txt", 1, List.of(), List.of()));
        assertThrows(UnsupportedOperationException.class, () -> source.isAvailable("file:///tmp/example.txt"));

        HashPlugin hash = registry.selectHashPlugin("BLAKE3");
        assertThrows(UnsupportedOperationException.class, () -> hash.hash(new byte[0]));

        SignaturePlugin sig = registry.selectSignaturePlugin("pdf");
        assertThrows(UnsupportedOperationException.class, () -> sig.verify(new byte[0], "file:///key.pem"));
    }
}
