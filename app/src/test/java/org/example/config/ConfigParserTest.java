package org.example.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigParserTest {

    @Test
    void parsesExampleTargetConfig() throws Exception {
        ConfigParser parser = new ConfigParser();
        Path examplePath = Path.of("..", "DOCS", "target.yml.example");

        MonitorTarget[] targets = parser.parse(examplePath);
        assertEquals(4, targets.length);

        // 1. prod-deploy-script (file, smb, signature_validation disabled)
        FileTarget deployScript = assertInstanceOf(FileTarget.class, targets[0]);
        assertEquals("prod-deploy-script", deployScript.getId());
        assertEquals("file", deployScript.getType());
        assertEquals("smb://fileserver.example.com/scripts/deploy.sh", deployScript.getPath());
        assertEquals("smb", deployScript.resolveTransportPlugin());
        assertEquals("SHA-256", deployScript.getHashAlgorithm());
        assertEquals("full_versions", deployScript.getLogging().getMode());
        assertEquals(5, deployScript.getLogging().getRetentionCount());
        assertFalse(deployScript.getSignatureValidation().isEnabled());
        assertEquals("", deployScript.resolveSignatureValidatorPlugin());

        // 2. signed-compliance-doc (file, https, pdf signature validation enabled)
        FileTarget complianceDoc = assertInstanceOf(FileTarget.class, targets[1]);
        assertEquals("signed-compliance-doc", complianceDoc.getId());
        assertEquals("https", complianceDoc.resolveTransportPlugin());
        assertTrue(complianceDoc.getSignatureValidation().isEnabled());
        assertEquals("pdf", complianceDoc.resolveSignatureValidatorPlugin());
        assertEquals("file:///var/keys/compliance.pem", complianceDoc.getSignatureValidation().getPublicKeyPath());

        // 3. remote-config-bootstrap (file, https, signature_validation omitted)
        FileTarget bootstrap = assertInstanceOf(FileTarget.class, targets[2]);
        assertEquals("remote-config-bootstrap", bootstrap.getId());
        assertEquals("last_only", bootstrap.getLogging().getMode());
        assertNull(bootstrap.getLogging().getRetentionCount());
        assertFalse(bootstrap.getSignatureValidation().isEnabled());

        // 4. customer-portal-assets (page, crawl_settings with extensions_override)
        PageTarget portalAssets = assertInstanceOf(PageTarget.class, targets[3]);
        assertEquals("customer-portal-assets", portalAssets.getId());
        assertEquals("page", portalAssets.getType());
        assertEquals("https", portalAssets.resolveTransportPlugin());
        assertEquals(2, portalAssets.getCrawlSettings().getDepth());
        assertEquals(
                List.of("portal.example.com", "static.example.com", "scripts.cdn.example.com"),
                portalAssets.getCrawlSettings().getAllowedDomains()
        );
        assertEquals(List.of(".js", ".mjs", ".html"), portalAssets.getMonitoredExtensions());
    }
}
