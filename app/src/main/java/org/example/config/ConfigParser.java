package org.example.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Parses YAML target configuration files (such as {@code targets.yml}) into {@link MonitorTarget} instances.
 */
public class ConfigParser {

    private static final String DEFAULT_HASH_ALGORITHM = "SHA-256";

    private final ObjectMapper yamlMapper;

    public ConfigParser() {
        this.yamlMapper = new YAMLMapper();
    }

    /**
     * Parses a YAML configuration file at the given path.
     *
     * @param configPath path to the YAML configuration file
     * @return array of configured {@link MonitorTarget} instances
     * @throws IOException if the file cannot be read or contains invalid YAML/target definitions
     */
    public MonitorTarget[] parse(Path configPath) throws IOException {
        return parse(configPath.toFile());
    }

    /**
     * Parses a YAML configuration file.
     *
     * @param configFile the YAML configuration file
     * @return array of configured {@link MonitorTarget} instances
     * @throws IOException if the file cannot be read or contains invalid YAML/target definitions
     */
    public MonitorTarget[] parse(File configFile) throws IOException {
        TargetConfig config = yamlMapper.readValue(configFile, TargetConfig.class);
        return toTargetArray(config);
    }

    /**
     * Parses a YAML configuration from an {@link InputStream}.
     *
     * @param inputStream stream containing YAML configuration
     * @return array of configured {@link MonitorTarget} instances
     * @throws IOException if the stream cannot be read or contains invalid YAML/target definitions
     */
    public MonitorTarget[] parse(InputStream inputStream) throws IOException {
        TargetConfig config = yamlMapper.readValue(inputStream, TargetConfig.class);
        return toTargetArray(config);
    }

    private MonitorTarget[] toTargetArray(TargetConfig config) {
        if (config == null || config.getTargets() == null || config.getTargets().isEmpty()) {
            return new MonitorTarget[0];
        }

        GlobalDefaults defaults = config.getGlobalDefaults();
        String hashAlgorithm = (defaults != null && defaults.getHashAlgorithm() != null)
                ? defaults.getHashAlgorithm()
                : DEFAULT_HASH_ALGORITHM;
        List<String> monitoredExtensions = (defaults != null && defaults.getMonitoredExtensions() != null)
                ? defaults.getMonitoredExtensions()
                : Collections.emptyList();

        List<MonitorTarget> targets = config.getTargets();
        for (MonitorTarget target : targets) {
            target.applyGlobalDefaults(hashAlgorithm, monitoredExtensions);
        }

        return targets.toArray(new MonitorTarget[0]);
    }

    /**
     * Top-level YAML document structure.
     */
    public static class TargetConfig {
        @JsonProperty("global_defaults")
        private GlobalDefaults globalDefaults;

        private List<MonitorTarget> targets;

        public TargetConfig() {
        }

        public GlobalDefaults getGlobalDefaults() {
            return globalDefaults;
        }

        public void setGlobalDefaults(GlobalDefaults globalDefaults) {
            this.globalDefaults = globalDefaults;
        }

        public List<MonitorTarget> getTargets() {
            return targets;
        }

        public void setTargets(List<MonitorTarget> targets) {
            this.targets = targets;
        }
    }

    /**
     * Global default settings applied to all targets unless overridden.
     */
    public static class GlobalDefaults {
        @JsonProperty("monitored_extensions")
        private List<String> monitoredExtensions = Collections.emptyList();

        @JsonProperty("hash_algorithm")
        private String hashAlgorithm = DEFAULT_HASH_ALGORITHM;

        public GlobalDefaults() {
        }

        public List<String> getMonitoredExtensions() {
            return monitoredExtensions;
        }

        public void setMonitoredExtensions(List<String> monitoredExtensions) {
            this.monitoredExtensions = monitoredExtensions != null
                    ? monitoredExtensions
                    : Collections.emptyList();
        }

        public String getHashAlgorithm() {
            return hashAlgorithm;
        }

        public void setHashAlgorithm(String hashAlgorithm) {
            this.hashAlgorithm = hashAlgorithm;
        }
    }
}
