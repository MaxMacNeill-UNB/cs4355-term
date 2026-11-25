package org.example.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Configuration model for a single-file monitoring target ({@code type: "file"}).
 */
public class FileTarget implements MonitorTarget {

    private String id;
    private String type = "file";
    private String path;
    private String description;
    private TargetPeriod period;
    private LoggingConfig logging;

    @JsonProperty("signature_validation")
    private SignatureValidation signatureValidation = new SignatureValidation();

    private String hashAlgorithm;
    private List<String> monitoredExtensions = Collections.emptyList();

    public FileTarget() {
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
    public TargetPeriod getPeriod() {
        return period;
    }

    public void setPeriod(TargetPeriod period) {
        this.period = period;
    }

    @Override
    public LoggingConfig getLogging() {
        return logging;
    }

    public void setLogging(LoggingConfig logging) {
        this.logging = logging;
    }

    public SignatureValidation getSignatureValidation() {
        return signatureValidation;
    }

    public void setSignatureValidation(SignatureValidation signatureValidation) {
        this.signatureValidation = signatureValidation != null ? signatureValidation : new SignatureValidation();
    }

    @Override
    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    @Override
    public List<String> getMonitoredExtensions() {
        return monitoredExtensions;
    }

    @Override
    public void applyGlobalDefaults(String defaultHashAlgorithm, List<String> defaultMonitoredExtensions) {
        this.hashAlgorithm = defaultHashAlgorithm;
        this.monitoredExtensions = defaultMonitoredExtensions != null
                ? List.copyOf(defaultMonitoredExtensions)
                : Collections.emptyList();
    }

    /**
     * Placeholder for selecting the cryptographic signature validator plugin (e.g., "pdf")
     * when {@code signature_validation.enabled} is {@code true}.
     *
     * <p>TODO: Replace with plugin registry lookup and invalid-plugin rejection once the plugin system is implemented.
     *
     * @return placeholder plugin name if enabled, or empty string if signature validation is disabled
     */
    public String resolveSignatureValidatorPlugin() {
        if (signatureValidation == null || !signatureValidation.isEnabled()) {
            return "";
        }
        return signatureValidation.getPlugin() != null ? signatureValidation.getPlugin() : "";
    }

    /**
     * Optional cryptographic signature verification settings for {@code file} targets.
     */
    public static class SignatureValidation {
        private boolean enabled = false;
        private String plugin;

        @JsonProperty("public_key_path")
        private String publicKeyPath;

        public SignatureValidation() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPlugin() {
            return plugin;
        }

        public void setPlugin(String plugin) {
            this.plugin = plugin;
        }

        public String getPublicKeyPath() {
            return publicKeyPath;
        }

        public void setPublicKeyPath(String publicKeyPath) {
            this.publicKeyPath = publicKeyPath;
        }
    }
}
