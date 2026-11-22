package org.example.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Configuration model for a web-crawl or directory-traversal monitoring target ({@code type: "page"}).
 * Unsupported fields such as {@code signature_validation} are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageTarget implements MonitorTarget {

    private String id;
    private String type = "page";
    private String path;
    private String description;
    private LoggingConfig logging;

    @JsonProperty("crawl_settings")
    private CrawlSettings crawlSettings = new CrawlSettings();

    private String hashAlgorithm;
    private List<String> globalMonitoredExtensions = Collections.emptyList();

    public PageTarget() {
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public LoggingConfig getLogging() {
        return logging;
    }

    public void setLogging(LoggingConfig logging) {
        this.logging = logging;
    }

    public CrawlSettings getCrawlSettings() {
        return crawlSettings;
    }

    public void setCrawlSettings(CrawlSettings crawlSettings) {
        this.crawlSettings = crawlSettings != null ? crawlSettings : new CrawlSettings();
    }

    @Override
    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    @Override
    public List<String> getMonitoredExtensions() {
        if (crawlSettings != null
                && crawlSettings.getExtensionsOverride() != null
                && !crawlSettings.getExtensionsOverride().isEmpty()) {
            return List.copyOf(crawlSettings.getExtensionsOverride());
        }
        return globalMonitoredExtensions;
    }

    @Override
    public void applyGlobalDefaults(String defaultHashAlgorithm, List<String> defaultMonitoredExtensions) {
        this.hashAlgorithm = defaultHashAlgorithm;
        this.globalMonitoredExtensions = defaultMonitoredExtensions != null
                ? List.copyOf(defaultMonitoredExtensions)
                : Collections.emptyList();
    }

    /**
     * Crawl and traversal settings for {@code page} targets.
     */
    public static class CrawlSettings {
        private int depth = 0;

        @JsonProperty("allowed_domains")
        private List<String> allowedDomains = Collections.emptyList();

        @JsonProperty("extensions_override")
        private List<String> extensionsOverride;

        public CrawlSettings() {
        }

        public int getDepth() {
            return depth;
        }

        public void setDepth(int depth) {
            this.depth = depth;
        }

        public List<String> getAllowedDomains() {
            return allowedDomains;
        }

        public void setAllowedDomains(List<String> allowedDomains) {
            this.allowedDomains = allowedDomains != null ? allowedDomains : Collections.emptyList();
        }

        public List<String> getExtensionsOverride() {
            return extensionsOverride;
        }

        public void setExtensionsOverride(List<String> extensionsOverride) {
            this.extensionsOverride = extensionsOverride;
        }
    }
}
