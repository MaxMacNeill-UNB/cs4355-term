package org.example.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * Common contract for all File Integrity Monitor (FIM) targets parsed from configuration.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = FileTarget.class, name = "file"),
        @JsonSubTypes.Type(value = PageTarget.class, name = "page")
})
public interface MonitorTarget {

    /**
     * Unique identifier for this monitoring target.
     */
    String getId();

    /**
     * Target type discriminator (e.g., "file" or "page").
     */
    String getType();

    /**
     * Target resource URI/path (e.g., file://, smb://, http://, https://).
     */
    String getPath();

    /**
     * Human-readable description of the target.
     */
    String getDescription();

    /**
     * Logging and retention configuration for this target.
     */
    LoggingConfig getLogging();

    /**
     * Hashing algorithm to use for integrity checks (inherited from global_defaults).
     */
    String getHashAlgorithm();

    /**
     * Effective list of monitored file extensions for this target.
     */
    List<String> getMonitoredExtensions();

    /**
     * Applies global default settings from {@code global_defaults} when not overridden by the target.
     *
     * @param defaultHashAlgorithm       default hash algorithm from global_defaults
     * @param defaultMonitoredExtensions default monitored extensions from global_defaults
     */
    void applyGlobalDefaults(String defaultHashAlgorithm, List<String> defaultMonitoredExtensions);

    /**
     * Placeholder for selecting the transport/protocol plugin (e.g., file, smb, http, https)
     * based on {@link #getPath()}.
     *
     * <p>TODO: Replace with plugin registry lookup and invalid-scheme rejection once the plugin system is implemented.
     *
     * @return placeholder identifier for the transport plugin (currently the URI scheme, or empty string if absent)
     */
    default String resolveTransportPlugin() {
        String path = getPath();
        if (path == null) {
            return "";
        }
        int schemeEnd = path.indexOf("://");
        return schemeEnd > 0 ? path.substring(0, schemeEnd).toLowerCase() : "";
    }

    /**
     * Logging and retention configuration shared by all target types.
     */
    class LoggingConfig {
        private String mode;

        @JsonProperty("retention_count")
        private Integer retentionCount;

        public LoggingConfig() {
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public Integer getRetentionCount() {
            return retentionCount;
        }

        public void setRetentionCount(Integer retentionCount) {
            this.retentionCount = retentionCount;
        }
    }
}
