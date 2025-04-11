package com.optiontrading.service.api;

import com.optiontrading.events.EventBus;
import com.optiontrading.service.auth.AuthService;
import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.InstrumentType;
import com.optiontrading.service.model.OptionType;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Quote;
import com.zerodhatech.models.Margin;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;

/**
 * Client for interacting with Kite Connect API
 */
public class KiteConnectClient implements TradingApiClient {
    private static final Logger LOGGER = Logger.getLogger(KiteConnectClient.class.getName());
    private static KiteConnectClient INSTANCE;

    // Kite Connect API endpoints
    private static final String KITE_API_BASE = "https://api.kite.trade";
    private static final String ENDPOINT_ORDERS = "/orders";

    // HTTP client for API calls
    private final HttpClient httpClient;

    // KiteConnect client from the library
    private KiteConnect kiteClient;

    // Authentication service
    private final AuthService authService;
    private final EventBus eventBus;

    // Whether the client is authenticated
    private boolean isAuthenticated = false;

    /**
     * Constructor with dependency injection
     */
    public KiteConnectClient(AuthService authService, EventBus eventBus) {
        this.authService = authService;
        this.eventBus = eventBus;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Initialize Kite client
        initializeKiteClient();

        // Subscribe to authentication events
        subscribeToEvents();

        // Check if we already have valid credentials
        checkAuthentication();

        LOGGER.info("Initialized KiteConnectClient, authenticated: " + isAuthenticated);
    }

    /**
     * Initialize the Kite client with API key
     */
    private void initializeKiteClient() {
        LOGGER.info("============ KITE CLIENT INITIALIZATION ============");
        String apiKey = authService.getApiKey();
        LOGGER.info("API KEY FROM AUTH SERVICE: " + apiKey);

        if (apiKey != null && !apiKey.isEmpty()) {
            LOGGER.info("CREATING KITECONNECT CLIENT WITH API KEY: " + apiKey);
            this.kiteClient = new KiteConnect(apiKey);

            // Set the access token if valid
            if (authService.isAccessTokenValid()) {
                String accessToken = authService.getAccessToken();
                LOGGER.info("SETTING ACCESS TOKEN ON KITECONNECT CLIENT: " +
                        (accessToken != null ? accessToken.substring(0, Math.min(5, accessToken.length())) + "***"
                                : "null"));
                kiteClient.setAccessToken(accessToken);
            } else {
                LOGGER.warning("NO VALID ACCESS TOKEN AVAILABLE - CLIENT INITIALIZED WITHOUT TOKEN");
            }
        } else {
            LOGGER.severe("API KEY NOT FOUND - KITECONNECT CLIENT INITIALIZATION FAILED");
        }
        LOGGER.info("==================================================");
    }

    /**
     * Subscribe to authentication events
     */
    private void subscribeToEvents() {
        // Subscribe to access token generated event
        eventBus.subscribe(com.optiontrading.service.auth.KiteAuthService.AccessTokenGeneratedEvent.class, event -> {
            // Update the kite client with the new token
            if (kiteClient != null) {
                kiteClient.setAccessToken(event.getAccessToken());
            }
            handleAuthenticated();
        });

        // Subscribe to tokens invalidated event
        eventBus.subscribe(com.optiontrading.service.auth.KiteAuthService.TokensInvalidatedEvent.class, event -> {
            isAuthenticated = false;
            // Reset the access token in the kite client
            if (kiteClient != null) {
                kiteClient.setAccessToken(null);
            }
            LOGGER.info("Authentication with Kite API invalidated");
        });
    }

    /**
     * Handle authentication success
     */
    private void handleAuthenticated() {
        isAuthenticated = true;
        LOGGER.info("Successfully authenticated with Kite API");
    }

    /**
     * Check if the client is authenticated
     * 
     * @return true if authenticated, false otherwise
     */
    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    /**
     * Initialize with authentication
     */
    private void checkAuthentication() {
        // Check if we already have a valid access token
        if (authService.isAccessTokenValid()) {
            // Update the kite client with the token
            if (kiteClient != null) {
                kiteClient.setAccessToken(authService.getAccessToken());
            }
            handleAuthenticated();
        }
    }

    /**
     * Get LTP (Last Traded Price) for multiple instruments
     * 
     * @param instrumentIds list of instrument IDs
     * @return map of instrument ID to price
     */
    public Map<String, BigDecimal> getLTP(List<String> instrumentIds) {
        if (instrumentIds == null || instrumentIds.isEmpty()) {
            return new HashMap<>();
        }

        LOGGER.info("Fetching LTP for " + instrumentIds.size() + " instruments");

        try {
            // Convert to array for Kite Connect API
            String[] instruments = instrumentIds.toArray(new String[0]);

            // Use Kite Connect library's LTP method directly
            Map<String, com.zerodhatech.models.LTPQuote> ltpQuotes = kiteClient.getLTP(instruments);

            // Convert to our simplified map format
            Map<String, BigDecimal> prices = new HashMap<>();

            // Extract the last price from each LTP quote
            for (Map.Entry<String, com.zerodhatech.models.LTPQuote> entry : ltpQuotes.entrySet()) {
                String instrumentId = entry.getKey();
                com.zerodhatech.models.LTPQuote ltpQuote = entry.getValue();

                if (ltpQuote != null) {
                    prices.put(instrumentId, BigDecimal.valueOf(ltpQuote.lastPrice));
                }
            }

            return prices;
        } catch (KiteException e) {
            LOGGER.log(Level.SEVERE, "Kite API error fetching LTP: " + e.message + " (code: " + e.code + ")", e);

            // Handle authentication error
            if (isAuthenticationError(e)) {
                LOGGER.severe("Authentication issue detected during API call");
                handleAuthenticationError();
            }

            return new HashMap<>();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Network error fetching LTP: " + e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * Get instruments from the Kite API
     * 
     * @param exchange the exchange to get instruments for (NSE, BSE, NFO, BFO)
     * @return list of instruments
     */
    public List<Instrument> getInstruments(String exchange) {
        LOGGER.info("Fetching instruments for exchange: " + exchange);

        try {
            // Use Kite Connect library to get instruments
            List<com.zerodhatech.models.Instrument> kiteInstruments = kiteClient.getInstruments(exchange);

            // Convert to our Instrument model
            List<Instrument> instruments = new ArrayList<>();

            for (com.zerodhatech.models.Instrument kiteInstrument : kiteInstruments) {
                // Extract information from Kite's instrument model
                String tradingSymbol = kiteInstrument.tradingsymbol;
                String name = kiteInstrument.name;
                String exch = kiteInstrument.exchange;
                String token = String.valueOf(kiteInstrument.instrument_token);
                InstrumentType type = mapInstrumentType(kiteInstrument.instrument_type);
                OptionType optionType = mapOptionType(kiteInstrument.instrument_type);

                // Create our Instrument using builder pattern
                Instrument.Builder builder = Instrument.builder()
                        .instrumentId(exch + ":" + tradingSymbol)
                        .tradingSymbol(tradingSymbol)
                        .exchange(exch)
                        .type(type)
                        .lotSize((int) kiteInstrument.lot_size);

                // Add option-specific data if available
                if (kiteInstrument.expiry != null) {
                    try {
                        // Parse the date string - format might vary depending on API version
                        String expiryStr = kiteInstrument.expiry.toString();
                        if (expiryStr.length() >= 10) {
                            LocalDate expiryDate = LocalDate.parse(expiryStr.substring(0, 10));
                            builder.expiryDate(expiryDate);
                        }
                    } catch (Exception e) {
                        LOGGER.fine("Could not parse expiry date: " + kiteInstrument.expiry);
                    }
                }

                // Handle strike price - might be a string or a number depending on API version
                try {
                    double strike = Double.parseDouble(String.valueOf(kiteInstrument.strike));
                    if (strike > 0) {
                        builder.strikePrice(BigDecimal.valueOf(strike));
                    }
                } catch (Exception e) {
                    LOGGER.fine("Could not parse strike price: " + kiteInstrument.strike);
                }

                if (optionType != null) {
                    builder.optionType(optionType);
                }

                if (kiteInstrument.name != null) {
                    builder.underlyingSymbol(kiteInstrument.name);
                }

                instruments.add(builder.build());
            }

            LOGGER.info("Fetched " + instruments.size() + " instruments from exchange: " + exchange);
            return instruments;
        } catch (KiteException e) {
            LOGGER.log(Level.SEVERE, "Kite API error fetching instruments: " + e.message, e);

            // Handle authentication error
            if (isAuthenticationError(e)) {
                LOGGER.severe("Authentication issue detected during instrument fetch");
                handleAuthenticationError();
            }

            throw new RuntimeException("Failed to fetch instruments from Kite API: " + e.message, e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Network error fetching instruments", e);
            throw new RuntimeException("Network error fetching instruments from Kite API: " + e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching instruments from Kite API", e);
            throw new RuntimeException("Failed to fetch instruments from Kite API: " + e.getMessage(), e);
        }
    }

    /**
     * Map Kite instrument type to our instrument type
     */
    private InstrumentType mapInstrumentType(String kiteType) {
        if (kiteType == null)
            return InstrumentType.INDEX; // Default to INDEX if unknown

        switch (kiteType.toUpperCase()) {
            case "EQ":
                return InstrumentType.EQUITY;
            case "CE":
            case "PE":
                return InstrumentType.OPTION;
            case "FUT":
                return InstrumentType.FUTURE;
            default:
                return InstrumentType.INDEX;
        }
    }

    /**
     * Map Kite instrument type to our option type
     */
    private OptionType mapOptionType(String kiteType) {
        if (kiteType == null)
            return null;

        switch (kiteType.toUpperCase()) {
            case "CE":
                return OptionType.CALL;
            case "PE":
                return OptionType.PUT;
            default:
                return null;
        }
    }

    /**
     * Place an order using direct HTTP calls for detailed error messages
     * 
     * @param instrumentId the instrument ID
     * @param quantity     the quantity
     * @param price        the price (null for market orders)
     * @param isBuy        true to buy, false to sell
     * @return the order ID
     */
    public String placeOrder(String instrumentId, int quantity, BigDecimal price, boolean isBuy) {
        LOGGER.info("Placing order: " + instrumentId + ", quantity: " + quantity +
                ", price: " + price + ", isBuy: " + isBuy);

        try {
            // Prepare order parameters
            Map<String, String> params = new HashMap<>();
            params.put("tradingsymbol", instrumentId.split(":")[1]); // Remove exchange prefix
            params.put("exchange", instrumentId.split(":")[0]);
            params.put("transaction_type", isBuy ? "BUY" : "SELL");
            params.put("quantity", String.valueOf(quantity));
            params.put("order_type", price != null ? "LIMIT" : "MARKET");

            if (price != null) {
                params.put("price", price.toString());
            }

            params.put("product", "CNC"); // Cash and carry
            params.put("validity", "DAY");

            // Form the request body
            String requestBody = formDataToString(params);

            // Create the request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(KITE_API_BASE + ENDPOINT_ORDERS + "/regular"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Kite-Version", "3")
                    .header("Authorization", "token " + authService.getApiKey() + ":" + authService.getAccessToken())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // Send the request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Process the response
            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());

                if ("success".equals(json.getString("status"))) {
                    JSONObject data = json.getJSONObject("data");
                    String orderId = data.getString("order_id");
                    LOGGER.info("Order placed successfully. Order ID: " + orderId);
                    return orderId;
                } else {
                    String errorMessage = json.optString("message", "Unknown error");
                    LOGGER.severe("Error placing order: " + errorMessage);
                    throw new RuntimeException("Failed to place order: " + errorMessage);
                }
            } else {
                String errorBody = response.body();
                LOGGER.severe("Error placing order. Status: " + response.statusCode() + ", Body: " + errorBody);

                // Check for authentication errors in response
                if (response.statusCode() == 403 || response.statusCode() == 401) {
                    LOGGER.severe("Authentication issue detected during order placement");
                    handleAuthenticationError();
                }

                // Try to extract detailed error message
                try {
                    JSONObject error = new JSONObject(errorBody);
                    String errorMessage = error.optString("message", "Unknown error");
                    throw new RuntimeException(
                            "Failed to place order: " + errorMessage + " (Status: " + response.statusCode() + ")");
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Failed to place order. Status: " + response.statusCode() + ", Body: " + errorBody);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "Interrupted while placing order", e);
            throw new RuntimeException("Interrupted while placing order: " + e.getMessage(), e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Network error placing order: " + e.getMessage(), e);

            if (e.getMessage() != null && e.getMessage().contains("authentication")) {
                LOGGER.severe("Authentication issue detected during order placement");
                handleAuthenticationError();
            }

            throw new RuntimeException("Network error placing order: " + e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error placing order: " + e.getMessage(), e);
            throw new RuntimeException("Failed to place order: " + e.getMessage(), e);
        }
    }

    /**
     * Get margins data
     * 
     * @return the margins map
     */
    public Map<String, Object> getMargins() {
        LOGGER.info("Fetching margins");

        try {
            // Fetch the margin data from Kite Connect API
            Map<String, Margin> margins = kiteClient.getMargins();

            // Convert result to a simple map
            Map<String, Object> result = new HashMap<>();

            // Process each segment
            for (Map.Entry<String, Margin> entry : margins.entrySet()) {
                result.put(entry.getKey(), convertToMap(entry.getValue()));
            }

            return result;
        } catch (KiteException e) {
            LOGGER.log(Level.SEVERE, "Kite API error fetching margins: " + e.message, e);

            // Handle authentication error
            if (isAuthenticationError(e)) {
                LOGGER.severe("Authentication issue detected during margins fetch");
                handleAuthenticationError();
            }

            throw new RuntimeException("Failed to fetch margins from Kite API: " + e.message, e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Network error fetching margins", e);
            throw new RuntimeException("Network error fetching margins from Kite API: " + e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching margins from Kite API", e);
            throw new RuntimeException("Failed to fetch margins from Kite API: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to convert a margin object to a Map
     */
    private Map<String, Object> convertToMap(Object object) {
        if (object == null)
            return new HashMap<>();

        Map<String, Object> result = new HashMap<>();

        try {
            // Use reflection to get all fields
            java.lang.reflect.Field[] fields = object.getClass().getDeclaredFields();

            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(object);

                // Recursively convert nested objects
                if (value != null && !isPrimitive(value)) {
                    value = convertToMap(value);
                }

                result.put(field.getName(), value);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error converting object to map", e);
        }

        return result;
    }

    /**
     * Check if an object is a primitive value
     */
    private boolean isPrimitive(Object value) {
        return value instanceof String || value instanceof Number ||
                value instanceof Boolean || value instanceof Character ||
                value.getClass().isPrimitive();
    }

    /**
     * Convert a JSONObject to a Map
     */
    private Map<String, Object> jsonToMap(JSONObject json) {
        Map<String, Object> map = new HashMap<>();

        for (String key : json.keySet()) {
            Object value = json.get(key);

            if (value instanceof JSONObject) {
                map.put(key, jsonToMap((JSONObject) value));
            } else {
                map.put(key, value);
            }
        }

        return map;
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
                result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.toString()));
                result.append("=");
                result.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.toString()));
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error encoding form data", e);
            }
        }
        return result.toString();
    }

    /**
     * Test the API connection
     * This is useful for verifying authentication
     * 
     * @return true if connection test is successful
     */
    public boolean testConnection() {
        try {
            LOGGER.info("=============== TESTING KITE API CONNECTION ===============");
            if (kiteClient == null) {
                LOGGER.severe("KITECONNECT CLIENT IS NULL - TEST FAILED");
                LOGGER.info("=======================================================");
                return false;
            }

            // Log the current settings
            LOGGER.info("KITE CLIENT API KEY: " + kiteClient.getApiKey());
            LOGGER.info("KITE CLIENT ACCESS TOKEN EXISTS: " + (kiteClient.getAccessToken() != null));
            if (kiteClient.getAccessToken() != null) {
                LOGGER.info("ACCESS TOKEN (FIRST 5 CHARS): " +
                        kiteClient.getAccessToken().substring(0, Math.min(5, kiteClient.getAccessToken().length()))
                        + "***");
            }

            try {
                // Try a simple API call - getLTP for a major index
                LOGGER.info("ATTEMPTING TEST API CALL: getLTP for NSE:NIFTY50");
                String[] testInstruments = { "NSE:NIFTY50" };
                Map<String, com.zerodhatech.models.LTPQuote> ltpQuotes = kiteClient.getLTP(testInstruments);

                if (ltpQuotes != null && !ltpQuotes.isEmpty()) {
                    com.zerodhatech.models.LTPQuote quote = ltpQuotes.get("NSE:NIFTY50");
                    if (quote != null) {
                        LOGGER.info("API TEST SUCCESSFUL - RECEIVED LTP FOR NIFTY50: " + quote.lastPrice);
                        LOGGER.info("=======================================================");
                        return true;
                    }
                }
            } catch (KiteException e) {
                LOGGER.severe("KITE API ERROR DURING TEST: " + e.message);
                LOGGER.severe("ERROR CODE: " + e.code + ", ERROR TYPE: " + e.getClass().getName());

                // Handle token expiry error specially
                if (e.message != null && (e.message.contains("token") || e.message.contains("authorization") ||
                        e.message.contains("login") || e.message.contains("authenticate") || e.code == 403)) {
                    LOGGER.severe(
                            "TOKEN APPEARS TO BE EXPIRED OR INVALID ON KITE'S SERVERS - FORCING RE-AUTHENTICATION");
                    authService.invalidateTokens();
                }
                LOGGER.info("=======================================================");
                return false;
            } catch (IOException e) {
                LOGGER.severe("NETWORK ERROR DURING TEST: " + e.getMessage());
                LOGGER.info("=======================================================");
                return false;
            }

            LOGGER.warning("API TEST FAILED - NO VALID LTP DATA RECEIVED");
            LOGGER.info("=======================================================");
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "API TEST FAILED WITH EXCEPTION: " + e.getMessage(), e);
            LOGGER.info("=======================================================");
            return false;
        }
    }

    /**
     * Get the singleton instance (for backward compatibility)
     */
    public static synchronized KiteConnectClient getInstance() {
        if (INSTANCE == null) {
            try {
                // For backward compatibility, try to access the necessary dependencies
                com.optiontrading.events.EventBus eventBus = com.optiontrading.events.EventBus.getInstance();
                com.optiontrading.service.auth.KiteAuthService authService = com.optiontrading.service.auth.KiteAuthService
                        .getInstance();

                INSTANCE = new KiteConnectClient(authService, eventBus);
                LOGGER.info("Created KiteConnectClient singleton instance");
            } catch (Exception e) {
                LOGGER.severe("Error creating KiteConnectClient singleton: " + e.getMessage());
                throw new RuntimeException("Failed to create KiteConnectClient singleton", e);
            }
        }
        return INSTANCE;
    }

    /**
     * Check if the KiteException is related to authentication issues
     * 
     * @param e The KiteException to check
     * @return true if it's an authentication error
     */
    private boolean isAuthenticationError(KiteException e) {
        return e.message != null && (e.message.contains("token") ||
                e.message.contains("authorization") ||
                e.message.contains("login") ||
                e.message.contains("authenticate") ||
                e.code == 403);
    }

    /**
     * Handle authentication errors by firing the appropriate event
     */
    private void handleAuthenticationError() {
        // Invalidate tokens and notify the application
        authService.invalidateTokens();

        // Fire authentication required event
        AuthenticationRequiredEvent event = new AuthenticationRequiredEvent();
        eventBus.publishSync(event);
    }

    /**
     * Event class for notifying that authentication is required
     */
    public static class AuthenticationRequiredEvent extends com.optiontrading.events.Event {
        // Event with no additional data
    }
}