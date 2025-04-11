package com.optiontrading.service.auth;

import java.time.Instant;

/**
 * Interface for authentication services to connect to trading platforms
 */
public interface AuthService {

    /**
     * Check if API credentials are set
     * 
     * @return true if credentials are set, false otherwise
     */
    boolean hasApiCredentials();

    /**
     * Set API credentials
     * 
     * @param apiKey    the API key
     * @param apiSecret the API secret
     */
    void setApiCredentials(String apiKey, String apiSecret);

    /**
     * Get the API key
     * 
     * @return the API key or null if not set
     */
    String getApiKey();

    /**
     * Get the API secret
     * 
     * @return the API secret or null if not set
     */
    String getApiSecret();

    /**
     * Get the login URL for user authentication
     * 
     * @return the login URL
     */
    String getLoginUrl();

    /**
     * Set the request token
     * 
     * @param requestToken the request token
     */
    void setRequestToken(String requestToken);

    /**
     * Generate a new access token
     * 
     * @param requestToken the request token
     * @param userId       the user ID
     * @return the new access token
     * @throws RuntimeException if token generation fails
     */
    String generateAccessToken(String requestToken, String userId);

    /**
     * Check if the access token is valid
     * 
     * @return true if valid, false otherwise
     */
    boolean isAccessTokenValid();

    /**
     * Get the access token
     * 
     * @return the access token or null if not set
     */
    String getAccessToken();

    /**
     * Get the user ID
     * 
     * @return the user ID or null if not set
     */
    String getUserId();

    /**
     * Invalidate the current tokens
     */
    void invalidateTokens();

    /**
     * Check if login is needed
     * 
     * @return true if login is needed, false otherwise
     */
    boolean needsLogin();

    /**
     * Get the expiry date/time of the access token
     * 
     * @return string representation of the token expiry time
     */
    String getTokenExpiry();
}