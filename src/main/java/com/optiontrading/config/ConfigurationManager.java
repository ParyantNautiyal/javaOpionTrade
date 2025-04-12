package com.optiontrading.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central configuration manager for the application.
 * Loads settings from application.properties and provides them to other
 * components.
 */
@Singleton
public class ConfigurationManager {
    private static final Logger LOGGER = Logger.getLogger(ConfigurationManager.class.getName());

    private final Properties properties = new Properties();
    private final File configFile;
    private boolean initialized = false;

    /**
     * Constructor with dependency injection
     */
    @Inject
    public ConfigurationManager() {
        configFile = new File("config/application.properties");
        loadConfiguration();
    }

    /**
     * Load the configuration from the properties file
     */
    private void loadConfiguration() {
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
                initialized = true;
                LOGGER.info("Loaded configuration from " + configFile.getAbsolutePath());
                LOGGER.info("Loaded " + properties.size() + " configuration properties");
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error loading configuration from " + configFile.getAbsolutePath(), e);
            }
        } else {
            LOGGER.warning("Configuration file does not exist: " + configFile.getAbsolutePath());
            LOGGER.warning("Using default values for all settings");
        }
    }

    /**
     * Reload the configuration from the properties file
     */
    public void reloadConfiguration() {
        properties.clear();
        loadConfiguration();
    }

    /**
     * Get a string property value
     * 
     * @param key the property key
     * @return the property value or null if not found
     */
    public String getString(String key) {
        return properties.getProperty(key);
    }

    /**
     * Get a string property value with a default
     * 
     * @param key          the property key
     * @param defaultValue the default value if not found
     * @return the property value or default if not found
     */
    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Get an integer property value
     * 
     * @param key the property key
     * @return the property value or 0 if not found or invalid
     */
    public int getInt(String key) {
        return getInt(key, 0);
    }

    /**
     * Get an integer property value with a default
     * 
     * @param key          the property key
     * @param defaultValue the default value if not found or invalid
     * @return the property value or default if not found or invalid
     */
    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid integer value for " + key + ": " + value);
            return defaultValue;
        }
    }

    /**
     * Get a long property value
     * 
     * @param key the property key
     * @return the property value or 0 if not found or invalid
     */
    public long getLong(String key) {
        return getLong(key, 0L);
    }

    /**
     * Get a long property value with a default
     * 
     * @param key          the property key
     * @param defaultValue the default value if not found or invalid
     * @return the property value or default if not found or invalid
     */
    public long getLong(String key, long defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid long value for " + key + ": " + value);
            return defaultValue;
        }
    }

    /**
     * Get a double property value
     * 
     * @param key the property key
     * @return the property value or 0.0 if not found or invalid
     */
    public double getDouble(String key) {
        return getDouble(key, 0.0);
    }

    /**
     * Get a double property value with a default
     * 
     * @param key          the property key
     * @param defaultValue the default value if not found or invalid
     * @return the property value or default if not found or invalid
     */
    public double getDouble(String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid double value for " + key + ": " + value);
            return defaultValue;
        }
    }

    /**
     * Get a boolean property value
     * 
     * @param key the property key
     * @return the property value or false if not found or invalid
     */
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    /**
     * Get a boolean property value with a default
     * 
     * @param key          the property key
     * @param defaultValue the default value if not found or invalid
     * @return the property value or default if not found or invalid
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        return Boolean.parseBoolean(value);
    }

    /**
     * Check if the configuration is initialized
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get all properties
     * 
     * @return the properties object
     */
    public Properties getProperties() {
        return properties;
    }
}