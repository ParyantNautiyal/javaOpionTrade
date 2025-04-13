package com.optiontrading.service.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.optiontrading.events.Event;
import com.optiontrading.events.EventBus;
import com.optiontrading.resources.ResourceManager;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;

/**
 * Service for handling authentication with Kite Connect API
 */
@Singleton
public class KiteAuthService implements AuthService {
    private static final Logger LOGGER = Logger.getLogger(KiteAuthService.class.getName());

    // Property keys
    private static final String API_KEY = "api_key";
    private static final String API_SECRET = "api_secret";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String REQUEST_TOKEN = "request_token";
    private static final String USER_ID = "user_id";
    private static final String TOKEN_TIMESTAMP = "token_timestamp";

    // Token validity duration (in hours)
    private static final int TOKEN_VALIDITY_HOURS = 24;

    // Kite API endpoints
    private static final String KITE_API_BASE = "https://api.kite.trade";
    private static final String KITE_LOGIN_URL = "https://kite.zerodha.com/connect/login";
    private static final String KITE_SESSION_TOKEN_URL = "/session/token";

    // Config paths
    private final File configDir;
    private final File credentialsFile;
    private final File tokensFile;

    // Cache manager
    // private final ResourceManager cacheManager;
    private final EventBus eventBus;

    // Properties
    private Properties credentials;
    private Properties tokens;

    // HTTP client
    private final HttpClient httpClient;

    // Credentials and tokens
    private String apiKey;
    private String apiSecret;
    private String accessToken;
    private String publicToken;
    private String requestToken;
    private String userId;
    private Instant tokenExpiryTime;

    /**
     * Constructor - loads configuration and initializes state
     */
    @Inject
    public KiteAuthService(EventBus eventBus) {
        this.eventBus = eventBus;

        // Initialize config paths
        this.configDir = new File("config");
        this.credentialsFile = new File(configDir, "kite_credentials.properties");
        this.tokensFile = new File(configDir, "kite_tokens.properties");

        // Ensure config directory exists
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        // Initialize HTTP client
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        // Initialize properties
        this.credentials = new Properties();
        this.tokens = new Properties();

        // Load credentials and tokens
        loadCredentials();
        loadTokens();

        // Check if token is valid
        checkAccessTokenValidity();

        LOGGER.info("Initialized KiteAuthService");
    }

    /**
     * Load API credentials from the properties file
     */
    private void loadCredentials() {
        try {
            if (credentialsFile.exists()) {
                try (FileInputStream fis = new FileInputStream(credentialsFile)) {
                    credentials.load(fis);
                    this.apiKey = credentials.getProperty(API_KEY);
                    this.apiSecret = credentials.getProperty(API_SECRET);
                    LOGGER.info("Loaded API credentials from " + credentialsFile.getName());
                    // Log the loaded credentials (mask the secret)
                    LOGGER.info("==== API CREDENTIAL CHECK ====");
                    LOGGER.info("LOADED API KEY: " + this.apiKey);
                    LOGGER.info("LOADED API SECRET: "
                            + (this.apiSecret != null && this.apiSecret.length() > 4
                                    ? this.apiSecret.substring(0, 4) + "***"
                                    : "[NOT SET]"));
                    LOGGER.info("==============================");
                }
            } else {
                LOGGER.info("Credentials file does not exist: " + credentialsFile.getAbsolutePath());
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "ERROR LOADING API CREDENTIALS FROM " + credentialsFile.getAbsolutePath(), e);
            // Ensure apiKey and apiSecret are null if loading fails
            this.apiKey = null;
            this.apiSecret = null;
        }
    }

    /**
     * Load tokens from the tokens file
     */
    private void loadTokens() {
        tokens = new Properties();

        if (tokensFile.exists()) {
            try (FileInputStream fis = new FileInputStream(tokensFile)) {
                tokens.load(fis);
                LOGGER.info("Loaded tokens from " + tokensFile.getAbsolutePath());

                // Set instance variables from properties
                this.accessToken = tokens.getProperty(ACCESS_TOKEN);
                this.requestToken = tokens.getProperty(REQUEST_TOKEN);
                this.userId = tokens.getProperty(USER_ID);

                // Parse token timestamp for expiry time if available
                String timestamp = tokens.getProperty(TOKEN_TIMESTAMP);
                if (timestamp != null && !timestamp.isEmpty()) {
                    try {
                        long tokenTime = Long.parseLong(timestamp);
                        this.tokenExpiryTime = Instant.ofEpochMilli(tokenTime)
                                .plusSeconds(TOKEN_VALIDITY_HOURS * 3600);
                    } catch (NumberFormatException e) {
                        LOGGER.log(Level.WARNING, "Error parsing token timestamp", e);
                    }
                }

                // Check if we have a valid access token
                checkAccessTokenValidity();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error loading tokens", e);
            }
        } else {
            LOGGER.info("Tokens file does not exist, using empty properties");
        }
    }

    /**
     * Check if the current access token is valid
     * 
     * @return true if valid, false otherwise
     */
    public boolean isAccessTokenValid() {
        LOGGER.info("========= TOKEN VALIDATION CHECK =========");
        String accessToken = getAccessToken();
        String timestamp = tokens.getProperty(TOKEN_TIMESTAMP);

        LOGGER.info("ACCESS TOKEN EXISTS: " + (accessToken != null && !accessToken.isEmpty()));
        if (accessToken != null && !accessToken.isEmpty()) {
            LOGGER.info("ACCESS TOKEN (FIRST 5 CHARS): " +
                    (accessToken.length() > 5 ? accessToken.substring(0, 5) + "***" : accessToken));

            // Check timestamp for basic validation
            LOGGER.info("TIMESTAMP EXISTS: " + (timestamp != null && !timestamp.isEmpty()));
            if (timestamp != null && !timestamp.isEmpty()) {
                try {
                    // First do a local timestamp check
                    long tokenTimestamp = Long.parseLong(timestamp);
                    Instant expiryTime = Instant.ofEpochMilli(tokenTimestamp)
                            .plusSeconds(TOKEN_VALIDITY_HOURS * 3600);

                    if (Instant.now().isAfter(expiryTime)) {
                        LOGGER.info("ACCESS TOKEN EXPIRED BY TIMESTAMP");
                        LOGGER.info("=========================================");
                        return false;
                    }

                    // Now do an actual API test call to verify token validity
                    LOGGER.info("PERFORMING API VALIDATION CHECK");
                    try {
                        // Create a temporary KiteConnect instance for checking
                        KiteConnect testClient = new KiteConnect(apiKey);
                        testClient.setAccessToken(accessToken);

                        // Call a lightweight profile endpoint to test authentication
                        testClient.getProfile();

                        // If we get here, the API call succeeded and token is valid
                        long remainingHours = Duration.between(Instant.now(), expiryTime).toHours();
                        long remainingMinutes = Duration.between(Instant.now(), expiryTime).toMinutes() % 60;
                        LOGGER.info("ACCESS TOKEN IS VALID (SERVER VERIFIED)");
                        LOGGER.info("REMAINING TIME: " + remainingHours + " HOURS, " + remainingMinutes + " MINUTES");
                        LOGGER.info("=========================================");
                        return true;
                    } catch (KiteException e) {
                        // API call failed - token is invalid
                        LOGGER.warning("ACCESS TOKEN REJECTED BY SERVER: " + e.message + " (Code: " + e.code + ")");
                        LOGGER.info("=========================================");
                        return false;
                    } catch (IOException e) {
                        // Network error - can't determine validity
                        LOGGER.warning("NETWORK ERROR DURING TOKEN VALIDATION: " + e.getMessage());
                        // Fall back to timestamp-based validation
                        long remainingHours = Duration.between(Instant.now(), expiryTime).toHours();
                        long remainingMinutes = Duration.between(Instant.now(), expiryTime).toMinutes() % 60;
                        LOGGER.info("USING TIMESTAMP VALIDATION DUE TO NETWORK ERROR");
                        LOGGER.info("REMAINING TIME: " + remainingHours + " HOURS, " + remainingMinutes + " MINUTES");
                        LOGGER.info("=========================================");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warning("ERROR PARSING TOKEN TIMESTAMP: " + e.getMessage());
                }
            }
        }

        LOGGER.info("ACCESS TOKEN IS INVALID");
        LOGGER.info("=========================================");
        return false;
    }

    /**
     * Check and refresh access token if needed
     */
    private void checkAccessTokenValidity() {
        if (isAccessTokenValid()) {
            // Token is valid, publish event
            String accessToken = getAccessToken();
            String userId = getUserId();

            if (accessToken != null && userId != null) {
                LOGGER.info("Found valid access token, using it");
                eventBus.publishAsync(new AccessTokenGeneratedEvent(accessToken));
            }
        } else {
            // Token is invalid, clear it
            LOGGER.info("Access token is invalid, it will need to be refreshed");
            tokens.remove(ACCESS_TOKEN);
            saveTokens();
        }
    }

    /**
     * Get the login URL
     * 
     * @return the login URL
     */
    public String getLoginUrl() {
        String apiKey = getApiKey();

        // Check if API key is null or empty
        if (apiKey == null || apiKey.isEmpty()) {
            // Try reloading credentials from file first
            LOGGER.warning("API key is not set in memory, attempting to reload from file");
            loadCredentials();

            // Check again after reload
            apiKey = getApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                LOGGER.severe(
                        "API key is not set after reload attempt. File may not exist or credentials may not be saved yet.");
                throw new RuntimeException(
                        "API key is not set or could not be loaded. Please ensure you've entered valid credentials.");
            }
        }

        LOGGER.info("Generating login URL with API key: " + apiKey);
        return KITE_LOGIN_URL + "?api_key=" + apiKey + "&v=3";
    }

    /**
     * Check if we need to login
     * 
     * @return true if login is needed, false otherwise
     */
    public boolean needsLogin() {
        return !isAccessTokenValid();
    }

    /**
     * Save API credentials to file
     */
    private void saveCredentials() {
        try (FileOutputStream fos = new FileOutputStream(credentialsFile)) {
            credentials.store(fos, "Kite API Credentials");
            LOGGER.info("Saved API credentials to " + credentialsFile);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error saving API credentials", e);
        }
    }

    /**
     * Save tokens to file
     */
    private void saveTokens() {
        // Ensure all current token values are in the properties object
        if (this.accessToken != null) {
            tokens.setProperty(ACCESS_TOKEN, this.accessToken);
        }
        if (this.requestToken != null) {
            tokens.setProperty(REQUEST_TOKEN, this.requestToken);
        }
        if (this.userId != null) {
            tokens.setProperty(USER_ID, this.userId);
        }

        // Set current timestamp when saving tokens
        tokens.setProperty(TOKEN_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

        try (FileOutputStream fos = new FileOutputStream(tokensFile)) {
            tokens.store(fos, "Kite API Tokens");
            LOGGER.info("Saved API tokens to " + tokensFile);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error saving API tokens", e);
        }
    }

    /**
     * Set the API credentials
     * 
     * @param apiKey    the API key
     * @param apiSecret the API secret
     */
    public void setApiCredentials(String apiKey, String apiSecret) {
        // Update properties
        credentials.setProperty(API_KEY, apiKey);
        credentials.setProperty(API_SECRET, apiSecret);

        // Save to file
        saveCredentials();

        // Update in-memory variables
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;

        LOGGER.info("API credentials updated in memory - API Key: " + apiKey + ", Secret: " +
                (apiSecret != null && apiSecret.length() > 4 ? apiSecret.substring(0, 4) + "***" : "[NOT SET]"));

        // Publish event
        eventBus.publishAsync(new ApiCredentialsUpdatedEvent(apiKey));
    }

    /**
     * Check if API credentials are set
     * 
     * @return true if API credentials are set
     */
    public boolean hasApiCredentials() {
        String apiKey = credentials.getProperty(API_KEY);
        String apiSecret = credentials.getProperty(API_SECRET);
        return apiKey != null && !apiKey.isEmpty() && apiSecret != null && !apiSecret.isEmpty();
    }

    /**
     * Set the request token received after login
     * 
     * @param requestToken the request token
     */
    public void setRequestToken(String requestToken) {
        tokens.setProperty(REQUEST_TOKEN, requestToken);
        saveTokens();
    }

    /**
     * Get the access token
     * 
     * @return the access token or null if not set
     */
    public String getAccessToken() {
        return this.accessToken;
    }

    /**
     * Get the API key
     * 
     * @return the API key or null if not set
     */
    public String getApiKey() {
        return this.apiKey;
    }

    /**
     * Get the API secret
     * 
     * @return the API secret or null if not set
     */
    public String getApiSecret() {
        return this.apiSecret;
    }

    /**
     * Get the user ID
     * 
     * @return the user ID or null if not set
     */
    public String getUserId() {
        return this.userId;
    }

    /**
     * Generate a new access token using the request token
     *
     * @param requestToken the request token received from Kite
     * @param userId       the user ID (optional, not used)
     * @return the new access token
     * @throws RuntimeException if generation fails
     */
    public String generateAccessToken(String requestToken, String userId) {
        LOGGER.info("=============== GENERATING ACCESS TOKEN ===============");
        ensureApiCredentialsSet();

        try {
            // Initialize KiteConnect instance
            KiteConnect kiteConnect = new KiteConnect(this.apiKey);

            LOGGER.info("USING KITECONNECT LIBRARY FOR TOKEN GENERATION");
            LOGGER.info("API KEY: " + this.apiKey);
            LOGGER.info("REQUEST TOKEN: " + requestToken);

            // Use the library's method to generate the session
            com.zerodhatech.models.User user = kiteConnect.generateSession(requestToken, this.apiSecret);

            // Extract data from the user object
            String newAccessToken = user.accessToken;
            String newPublicToken = user.publicToken;
            String newUserId = user.userId;

            LOGGER.info("RECEIVED NEW ACCESS TOKEN: " +
                    newAccessToken.substring(0, Math.min(5, newAccessToken.length())) + "***");
            LOGGER.info("RECEIVED USER ID: " + newUserId);

            // Update local state
            this.accessToken = newAccessToken;
            this.publicToken = newPublicToken;
            this.userId = newUserId; // We store userId but don't compare it
            this.tokenExpiryTime = Instant.now().plusSeconds(TOKEN_VALIDITY_HOURS * 3600);

            // Update properties object with new tokens
            tokens.setProperty(ACCESS_TOKEN, newAccessToken);
            tokens.setProperty(USER_ID, newUserId);
            tokens.setProperty(TOKEN_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

            LOGGER.info("UPDATED TOKENS IN MEMORY - SAVING TO FILE");

            // Save tokens to file
            saveTokens();

            LOGGER.info("ACCESS TOKEN GENERATED SUCCESSFULLY");
            LOGGER.info("ACCESS TOKEN EXPIRES AT: " + LocalDateTime.ofInstant(tokenExpiryTime, ZoneId.systemDefault()));
            LOGGER.info("=================================================");

            // Publish event
            eventBus.publish(new AccessTokenGeneratedEvent(newAccessToken));

            return newAccessToken;
        } catch (KiteException e) {
            LOGGER.log(Level.SEVERE, "KITE API ERROR GENERATING ACCESS TOKEN: " + e.message + " (code: " + e.code + ")",
                    e);
            LOGGER.info("=================================================");
            throw new RuntimeException("Failed to generate access token: " + e.message, e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IO ERROR GENERATING ACCESS TOKEN", e);
            LOGGER.info("=================================================");
            throw new RuntimeException("Failed to generate access token: " + e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "ERROR GENERATING ACCESS TOKEN", e);
            LOGGER.info("=================================================");
            throw new RuntimeException("Failed to generate access token: " + e.getMessage(), e);
        }
    }

    /**
     * Generate checksum for API request
     * 
     * @param apiKey       the API key
     * @param requestToken the request token
     * @param apiSecret    the API secret
     * @return the checksum
     */
    private String calculateChecksum(String apiKey, String requestToken, String apiSecret) {
        try {
            String input = apiKey + requestToken + apiSecret;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating checksum", e);
            throw new RuntimeException("Failed to generate checksum: " + e.getMessage(), e);
        }
    }

    /**
     * Convert form data to string
     * 
     * @param formData the form data
     * @return the string representation
     */
    private String formDataToString(Map<String, String> formData) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            if (result.length() > 0) {
                result.append("&");
            }
            try {
                result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                result.append("=");
                result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                LOGGER.log(Level.SEVERE, "Error encoding form data", e);
            }
        }
        return result.toString();
    }

    /**
     * Invalidate tokens
     */
    public void invalidateTokens() {
        tokens.remove(ACCESS_TOKEN);
        tokens.remove(REQUEST_TOKEN);
        tokens.remove(TOKEN_TIMESTAMP);
        saveTokens();

        // Publish event
        eventBus.publishAsync(new TokensInvalidatedEvent());

        LOGGER.info("Tokens invalidated");
    }

    /**
     * Event for API credentials being updated
     */
    public static class ApiCredentialsUpdatedEvent extends Event {
        private final String apiKey;

        public ApiCredentialsUpdatedEvent(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiKey() {
            return apiKey;
        }
    }

    /**
     * Event for access token being generated
     */
    public static class AccessTokenGeneratedEvent extends Event {
        private final String accessToken;

        public AccessTokenGeneratedEvent(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getAccessToken() {
            return accessToken;
        }

        @Override
        public String toString() {
            return "AccessTokenGeneratedEvent [accessToken=***]"; // Mask token in logs
        }
    }

    /**
     * Event for tokens being invalidated
     */
    public static class TokensInvalidatedEvent extends Event {
    }

    /**
     * Get the expiry date/time of the current access token
     * 
     * @return string representation of the token expiry date/time or "Unknown" if
     *         no valid token
     */
    public String getTokenExpiry() {
        String timestamp = tokens.getProperty(TOKEN_TIMESTAMP);
        if (timestamp == null || timestamp.isEmpty()) {
            return "Unknown";
        }

        try {
            long tokenTime = Long.parseLong(timestamp);
            long validity = TOKEN_VALIDITY_HOURS * 60 * 60 * 1000; // Convert hours to milliseconds
            long expiryTime = tokenTime + validity;

            // Convert to readable date/time
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(expiryTime));
        } catch (NumberFormatException e) {
            LOGGER.log(Level.SEVERE, "Error parsing token timestamp", e);
            return "Unknown";
        }
    }

    private void ensureApiCredentialsSet() {
        if (!hasApiCredentials()) {
            throw new RuntimeException("API credentials are not set");
        }
    }

    /**
     * Update API credentials and save them
     * 
     * @param apiKey    the API key
     * @param apiSecret the API secret
     */
    public void updateCredentials(String apiKey, String apiSecret) {
        if (apiKey == null || apiKey.isEmpty() || apiSecret == null || apiSecret.isEmpty()) {
            throw new IllegalArgumentException("API key and secret cannot be null or empty");
        }

        LOGGER.info("Updating API credentials");
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;

        // Update the properties
        credentials.setProperty(API_KEY, apiKey);
        credentials.setProperty(API_SECRET, apiSecret);

        // Save to file
        saveCredentials();

        // Invalidate existing tokens since we changed credentials
        invalidateTokens();

        // Publish event
        eventBus.publishAsync(new ApiCredentialsUpdatedEvent(apiKey));

        LOGGER.info("API credentials updated successfully");
    }

    /**
     * Reload credentials from file
     * This is useful when credentials may have been updated externally
     */
    public void reloadCredentials() {
        LOGGER.info("Manually reloading credentials from file");
        loadCredentials();
    }
}