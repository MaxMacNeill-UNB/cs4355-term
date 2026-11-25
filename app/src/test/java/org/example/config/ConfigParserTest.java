package org.example.config;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConfigParserTest {

    @Test
    void parsesExampleTargetConfig() throws Exception {
        ConfigParser parser = new ConfigParser();
        Path examplePath = Path.of("..", "DOCS", "target.yml.example");

        MonitorTarget[] targets = parser.parse(examplePath);
        assertEquals(4, targets.length);

        // 1. prod-deploy-script (file, file://, cron period, signature_validation disabled)
        FileTarget deployScript = assertInstanceOf(FileTarget.class, targets[0]);
        assertEquals("prod-deploy-script", deployScript.getId());
        assertEquals("file", deployScript.getType());
        assertEquals("file://var/scripts/deploy.sh", deployScript.getPath());
        assertEquals("file", deployScript.resolveTransportPlugin());
        assertNotNull(deployScript.getPeriod());
        assertTrue(deployScript.getPeriod().isCron());
        assertFalse(deployScript.getPeriod().isDuration());
        assertEquals(Optional.of("0 0 * * *"), deployScript.getPeriod().getCronExpression());
        assertEquals(Optional.empty(), deployScript.getPeriod().getDuration());
        assertEquals("SHA-256", deployScript.getHashAlgorithm());
        assertEquals("full_versions", deployScript.getLogging().getMode());
        assertEquals(5, deployScript.getLogging().getRetentionCount());
        assertFalse(deployScript.getSignatureValidation().isEnabled());
        assertEquals("", deployScript.resolveSignatureValidatorPlugin());

        // 2. signed-compliance-doc (file, https, 1d duration period, pdf signature validation enabled)
        FileTarget complianceDoc = assertInstanceOf(FileTarget.class, targets[1]);
        assertEquals("signed-compliance-doc", complianceDoc.getId());
        assertEquals("https", complianceDoc.resolveTransportPlugin());
        assertNotNull(complianceDoc.getPeriod());
        assertTrue(complianceDoc.getPeriod().isDuration());
        assertEquals(Optional.of(Duration.ofDays(1)), complianceDoc.getPeriod().getDuration());
        assertEquals(Optional.empty(), complianceDoc.getPeriod().getCronExpression());
        assertTrue(complianceDoc.getSignatureValidation().isEnabled());
        assertEquals("pdf", complianceDoc.resolveSignatureValidatorPlugin());
        assertEquals("file:///var/keys/compliance.pem", complianceDoc.getSignatureValidation().getPublicKeyPath());

        // 3. remote-config-bootstrap (file, https, period omitted, signature_validation omitted)
        FileTarget bootstrap = assertInstanceOf(FileTarget.class, targets[2]);
        assertEquals("remote-config-bootstrap", bootstrap.getId());
        assertNull(bootstrap.getPeriod());
        assertEquals("last_only", bootstrap.getLogging().getMode());
        assertNull(bootstrap.getLogging().getRetentionCount());
        assertFalse(bootstrap.getSignatureValidation().isEnabled());

        // 4. customer-portal-assets (page, minute-only period, crawl_settings with extensions_override)
        PageTarget portalAssets = assertInstanceOf(PageTarget.class, targets[3]);
        assertEquals("customer-portal-assets", portalAssets.getId());
        assertEquals("page", portalAssets.getType());
        assertEquals("https", portalAssets.resolveTransportPlugin());
        assertNotNull(portalAssets.getPeriod());
        assertTrue(portalAssets.getPeriod().isDuration());
        assertEquals(Optional.of(Duration.ofMinutes(60)), portalAssets.getPeriod().getDuration());
        assertEquals(2, portalAssets.getCrawlSettings().getDepth());
        assertEquals(
                List.of("portal.example.com", "static.example.com", "scripts.cdn.example.com"),
                portalAssets.getCrawlSettings().getAllowedDomains()
        );
        assertEquals(List.of(".js", ".mjs", ".html"), portalAssets.getMonitoredExtensions());
    }

    @Test
    void parsesUnquotedMinutesCompoundDurationsAndCronVariants() throws Exception {
        String yaml = """
                targets:
                  - id: "unquoted-minutes"
                    type: "file"
                    path: "file:///tmp/a.txt"
                    period: 45
                  - id: "compound-duration"
                    type: "file"
                    path: "file:///tmp/b.txt"
                    period: "1h30m15s"
                  - id: "complex-cron"
                    type: "page"
                    path: "https://example.com"
                    period: "*/5 9-17 1,15 * 1-5"
                """;

        MonitorTarget[] targets = new ConfigParser().parse(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertEquals(3, targets.length);
        assertEquals(Optional.of(Duration.ofMinutes(45)), targets[0].getPeriod().getDuration());
        assertEquals(
                Optional.of(Duration.ofHours(1).plusMinutes(30).plusSeconds(15)),
                targets[1].getPeriod().getDuration()
        );
        assertTrue(targets[2].getPeriod().isCron());
        assertEquals(Optional.of("*/5 9-17 1,15 * 1-5"), targets[2].getPeriod().getCronExpression());
    }

    @Test
    void rejectsInvalidTargetPeriods() {
        List<String> invalidPeriods = List.of(
                "",
                "   ",
                "0",
                "0m",
                "-5m",
                "10x",
                "* * *",
                "60 * * * *",
                "0 24 * * *",
                "0 0 0 * *",
                "0 0 * 13 *",
                "0 0 * * 8"
        );

        for (String invalid : invalidPeriods) {
            assertThrows(IllegalArgumentException.class, () -> TargetPeriod.parse(invalid),
                    "Expected rejection for period: '" + invalid + "'");
        }

        String badYaml = """
                targets:
                  - id: "bad-period"
                    type: "file"
                    path: "file:///tmp/a.txt"
                    period: "not-a-period"
                """;
        assertThrows(IOException.class, () -> new ConfigParser().parse(
                new ByteArrayInputStream(badYaml.getBytes(StandardCharsets.UTF_8))));
    }
}
