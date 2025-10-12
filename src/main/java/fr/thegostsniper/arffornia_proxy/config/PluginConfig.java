package fr.thegostsniper.arffornia_proxy.config;

import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Manages the plugin's configuration, loading credentials and API settings
 * from a properties file.
 */
public class PluginConfig {
    private final Logger logger;
    private final Path dataDirectory;

    private String apiUrl;
    private String apiClientId;
    private String apiClientSecret;

    @Inject
    public PluginConfig(Logger logger, @com.velocitypowered.api.plugin.annotation.DataDirectory Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    /**
     * Loads the configuration from the config.properties file.
     * If the file doesn't exist, it creates a default one.
     *
     * @return true if the configuration was loaded successfully, false otherwise.
     */
    public boolean load() {
        try {
            if (Files.notExists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }

            Path configFile = dataDirectory.resolve("config.properties");
            if (Files.notExists(configFile)) {
                logger.info("Configuration file not found, creating a default one...");
                try (InputStream stream = getClass().getClassLoader().getResourceAsStream("config.properties")) {
                    if (stream == null) {
                        throw new IOException("Default config.properties not found in JAR.");
                    }
                    Files.copy(stream, configFile);
                }
            }

            Properties properties = new Properties();
            try (InputStream stream = Files.newInputStream(configFile)) {
                properties.load(stream);
            }

            this.apiUrl = properties.getProperty("api.base-url", "");
            this.apiClientId = properties.getProperty("api.client-id", "");
            this.apiClientSecret = properties.getProperty("api.client-secret", "");

            if (apiUrl.isEmpty() || apiClientId.isEmpty() || apiClientSecret.isEmpty()) {
                logger.error("Configuration is incomplete. Please fill in api.base-url, api.client-id, and api.client-secret in config.properties.");
                return false;
            }

            logger.info("Configuration loaded successfully.");
            return true;

        } catch (IOException e) {
            logger.error("Failed to load or create configuration file.", e);
            return false;
        }
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getApiClientId() {
        return apiClientId;
    }

    public String getApiClientSecret() {
        return apiClientSecret;
    }
}