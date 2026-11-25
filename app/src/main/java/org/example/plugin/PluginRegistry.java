package org.example.plugin;

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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry for selecting {@link SourcePlugin}, {@link HashPlugin}, and {@link SignaturePlugin}
 * implementations and rejecting unsupported or invalid configuration values.
 */
public class PluginRegistry {

    private final Map<String, SourcePlugin> sourcePlugins = new HashMap<>();
    private final Map<String, HashPlugin> hashPlugins = new HashMap<>();
    private final Map<String, SignaturePlugin> signaturePlugins = new HashMap<>();

    /**
     * Creates a registry pre-populated with the built-in stub plugins.
     */
    public static PluginRegistry withDefaults() {
        PluginRegistry registry = new PluginRegistry();
        registry.registerSourcePlugin(new HttpSourcePlugin());
        registry.registerSourcePlugin(new HttpsSourcePlugin());
        registry.registerSourcePlugin(new FileSourcePlugin());

        registry.registerHashPlugin(new Sha256HashPlugin());
        registry.registerHashPlugin(new Blake3HashPlugin());

        registry.registerSignaturePlugin(new PdfSignaturePlugin());
        return registry;
    }

    public void registerSourcePlugin(SourcePlugin plugin) {
        if (plugin == null || plugin.getScheme() == null || plugin.getScheme().isBlank()) {
            throw new IllegalArgumentException("SourcePlugin and its scheme must not be null or blank");
        }
        sourcePlugins.put(normalize(plugin.getScheme()), plugin);
    }

    public void registerHashPlugin(HashPlugin plugin) {
        if (plugin == null || plugin.getAlgorithmName() == null || plugin.getAlgorithmName().isBlank()) {
            throw new IllegalArgumentException("HashPlugin and its algorithm name must not be null or blank");
        }
        hashPlugins.put(normalizeHashName(plugin.getAlgorithmName()), plugin);
    }

    public void registerSignaturePlugin(SignaturePlugin plugin) {
        if (plugin == null || plugin.getPluginName() == null || plugin.getPluginName().isBlank()) {
            throw new IllegalArgumentException("SignaturePlugin and its plugin name must not be null or blank");
        }
        signaturePlugins.put(normalize(plugin.getPluginName()), plugin);
    }

    /**
     * Selects the {@link SourcePlugin} matching the URI scheme of {@code path}.
     *
     * @param path resource URI/path (e.g., {@code https://...}, {@code file://...})
     * @return matching {@link SourcePlugin}
     * @throws IllegalArgumentException if {@code path} has no valid scheme or no plugin supports it
     */
    public SourcePlugin selectSourcePlugin(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Target path must not be null or blank");
        }
        int schemeEnd = path.indexOf("://");
        if (schemeEnd <= 0) {
            throw new IllegalArgumentException("Target path is missing a valid URI scheme: " + path);
        }
        String scheme = normalize(path.substring(0, schemeEnd));
        SourcePlugin plugin = sourcePlugins.get(scheme);
        if (plugin == null) {
            throw new IllegalArgumentException("Unsupported source scheme '" + scheme + "' in path: " + path);
        }
        return plugin;
    }

    /**
     * Selects the {@link SourcePlugin} for a {@link MonitorTarget}.
     */
    public SourcePlugin selectSourcePlugin(MonitorTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("MonitorTarget must not be null");
        }
        return selectSourcePlugin(target.getPath());
    }

    /**
     * Selects the {@link HashPlugin} matching {@code algorithmName}.
     *
     * @param algorithmName configured hash algorithm (e.g., {@code "SHA-256"}, {@code "BLAKE3"})
     * @return matching {@link HashPlugin}
     * @throws IllegalArgumentException if {@code algorithmName} is null, blank, or unsupported
     */
    public HashPlugin selectHashPlugin(String algorithmName) {
        if (algorithmName == null || algorithmName.isBlank()) {
            throw new IllegalArgumentException("Hash algorithm must not be null or blank");
        }
        HashPlugin plugin = hashPlugins.get(normalizeHashName(algorithmName));
        if (plugin == null) {
            throw new IllegalArgumentException("Unsupported hash algorithm: " + algorithmName);
        }
        return plugin;
    }

    /**
     * Selects the {@link HashPlugin} for a {@link MonitorTarget}.
     */
    public HashPlugin selectHashPlugin(MonitorTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("MonitorTarget must not be null");
        }
        return selectHashPlugin(target.getHashAlgorithm());
    }

    /**
     * Selects the {@link SignaturePlugin} matching {@code pluginName}.
     *
     * @param pluginName configured signature plugin name (e.g., {@code "pdf"})
     * @return matching {@link SignaturePlugin}
     * @throws IllegalArgumentException if {@code pluginName} is null, blank, or unsupported
     */
    public SignaturePlugin selectSignaturePlugin(String pluginName) {
        if (pluginName == null || pluginName.isBlank()) {
            throw new IllegalArgumentException("Signature plugin name must not be null or blank");
        }
        SignaturePlugin plugin = signaturePlugins.get(normalize(pluginName));
        if (plugin == null) {
            throw new IllegalArgumentException("Unsupported signature plugin: " + pluginName);
        }
        return plugin;
    }

    /**
     * Selects the {@link SignaturePlugin} for a {@link FileTarget} if signature validation is enabled.
     *
     * @return {@link Optional#empty()} when signature validation is disabled, or the selected plugin
     * @throws IllegalArgumentException if enabled but the configured plugin name is missing or unsupported
     */
    public Optional<SignaturePlugin> selectSignaturePlugin(FileTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("FileTarget must not be null");
        }
        FileTarget.SignatureValidation sig = target.getSignatureValidation();
        if (sig == null || !sig.isEnabled()) {
            return Optional.empty();
        }
        return Optional.of(selectSignaturePlugin(sig.getPlugin()));
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeHashName(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace("-", "");
    }
}
