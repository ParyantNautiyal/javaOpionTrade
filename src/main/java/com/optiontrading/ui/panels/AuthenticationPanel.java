package com.optiontrading.ui.panels;

import com.optiontrading.events.EventBus;
import com.optiontrading.service.auth.AuthService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.web.WebView;

/**
 * Panel for managing authentication with Kite API
 */
public class AuthenticationPanel extends BorderPane {

    private final AuthService authService;
    private final EventBus eventBus;

    private Label statusValue;
    private Label apiKeyValue;
    private Label tokenExpiryValue;
    private TextField apiKeyField;
    private PasswordField apiSecretField;
    private TextField requestTokenField;
    private WebView webView;
    private Button loginButton;

    public AuthenticationPanel(AuthService authService, EventBus eventBus) {
        this.authService = authService;
        this.eventBus = eventBus;

        setPadding(new Insets(20));

        // Title
        Text title = new Text("Kite Authentication");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        BorderPane.setAlignment(title, Pos.CENTER);
        setTop(title);

        // Main content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20, 0, 0, 0));
        setCenter(content);

        // Authentication Status Section
        content.getChildren().add(createStatusSection());

        // Credentials Section
        content.getChildren().add(createCredentialsSection());

        // Login Section
        content.getChildren().add(createLoginSection());

        // Action buttons
        HBox actionBox = new HBox(10);
        actionBox.setPadding(new Insets(20, 0, 0, 0));
        actionBox.setAlignment(Pos.CENTER);

        Button refreshButton = new Button("Refresh Status");
        refreshButton.setOnAction(e -> refreshStatus());

        Button logoutButton = new Button("Invalidate Tokens");
        logoutButton.setOnAction(e -> invalidateTokens());

        loginButton = new Button("Authenticate");
        loginButton.setOnAction(e -> authenticate());
        loginButton.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white;");

        actionBox.getChildren().addAll(refreshButton, logoutButton, loginButton);

        setBottom(actionBox);

        // Initial status refresh
        refreshStatus();
    }

    private TitledPane createStatusSection() {
        GridPane grid = new GridPane();
        grid.setVgap(8);
        grid.setHgap(15);
        grid.setPadding(new Insets(10));

        // Status row
        grid.add(new Label("Authentication Status:"), 0, 0);
        statusValue = new Label();
        grid.add(statusValue, 1, 0);

        // API Key row
        grid.add(new Label("API Key:"), 0, 1);
        apiKeyValue = new Label();
        grid.add(apiKeyValue, 1, 1);

        // Token Expiry row
        grid.add(new Label("Token Expiry:"), 0, 2);
        tokenExpiryValue = new Label();
        grid.add(tokenExpiryValue, 1, 2);

        TitledPane pane = new TitledPane("Current Status", grid);
        pane.setCollapsible(false);
        return pane;
    }

    private TitledPane createCredentialsSection() {
        GridPane grid = new GridPane();
        grid.setVgap(8);
        grid.setHgap(15);
        grid.setPadding(new Insets(10));

        // API Key input
        grid.add(new Label("API Key:"), 0, 0);
        apiKeyField = new TextField();
        apiKeyField.setPromptText("Enter your API Key");
        grid.add(apiKeyField, 1, 0);

        // API Secret input
        grid.add(new Label("API Secret:"), 0, 1);
        apiSecretField = new PasswordField();
        apiSecretField.setPromptText("Enter your API Secret");
        grid.add(apiSecretField, 1, 1);

        // Save button
        Button saveButton = new Button("Save Credentials");
        saveButton.setOnAction(e -> saveCredentials());
        grid.add(saveButton, 1, 2);

        TitledPane pane = new TitledPane("API Credentials", grid);
        pane.setCollapsible(false);
        return pane;
    }

    private TitledPane createLoginSection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        // Request token section
        GridPane tokenGrid = new GridPane();
        tokenGrid.setVgap(8);
        tokenGrid.setHgap(15);

        tokenGrid.add(new Label("Request Token:"), 0, 0);
        requestTokenField = new TextField();
        requestTokenField.setPromptText("Paste request token from login page");
        tokenGrid.add(requestTokenField, 1, 0);

        // Generate token button
        Button generateButton = new Button("Generate Access Token");
        generateButton.setOnAction(e -> generateAccessToken());
        tokenGrid.add(generateButton, 1, 1);

        // Login URL and browser
        Button openLoginButton = new Button("Open Login Page");
        openLoginButton.setOnAction(e -> openLoginPage());

        // Add components to content
        content.getChildren().addAll(
                tokenGrid,
                new Label("After saving credentials, click below to open the login page:"),
                openLoginButton);

        TitledPane pane = new TitledPane("Login Process", content);
        pane.setCollapsible(false);
        return pane;
    }

    private void saveCredentials() {
        String apiKey = apiKeyField.getText();
        String apiSecret = apiSecretField.getText();

        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            showError("Please enter both API Key and API Secret");
            return;
        }

        authService.setApiCredentials(apiKey, apiSecret);
        refreshStatus();
        showSuccess("Credentials saved successfully");
    }

    private void openLoginPage() {
        if (!authService.hasApiCredentials()) {
            showError("Please save API credentials first");
            return;
        }

        // Get the login URL
        String loginUrl = authService.getLoginUrl();

        try {
            // Open URL in the system's default browser
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(loginUrl));
            showInfo("Opening login page in browser: " + loginUrl);
        } catch (Exception e) {
            // In case the system browser can't be launched, show the URL
            showError("Could not open browser automatically. Please copy and paste this URL: " + loginUrl);
        }
    }

    private void generateAccessToken() {
        String requestToken = requestTokenField.getText();

        if (requestToken.isEmpty()) {
            showError("Please enter a request token");
            return;
        }

        try {
            // Save the request token first
            authService.setRequestToken(requestToken);

            // Generate the access token (no user ID needed anymore)
            authService.generateAccessToken(requestToken);

            // Refresh status to show the new token state
            refreshStatus();

            showSuccess("Access token generated successfully");
        } catch (Exception e) {
            showError("Failed to generate access token: " + e.getMessage());
        }
    }

    private void authenticate() {
        if (!authService.hasApiCredentials()) {
            showError("Please save API credentials first");
            return;
        }

        if (authService.isAccessTokenValid()) {
            showSuccess("Already authenticated");
            return;
        }

        // Display dialog to guide through authentication steps
        openLoginPage();
    }

    private void invalidateTokens() {
        authService.invalidateTokens();
        refreshStatus();
        showInfo("Tokens invalidated");
    }

    private void refreshStatus() {
        boolean isAuthenticated = authService.isAccessTokenValid();

        // Update status
        statusValue.setText(isAuthenticated ? "Authenticated" : "Not Authenticated");
        statusValue.setTextFill(isAuthenticated ? Color.GREEN : Color.RED);

        // Update API Key
        String apiKey = authService.getApiKey();
        apiKeyValue.setText(apiKey != null ? maskString(apiKey) : "Not Set");

        // Update token expiry
        tokenExpiryValue.setText(authService.getTokenExpiry());

        // Update fields with current values
        if (apiKey != null && !apiKey.isEmpty()) {
            apiKeyField.setText(apiKey);
        }
    }

    private String maskString(String input) {
        if (input == null || input.length() <= 4) {
            return input;
        }
        return input.substring(0, 4) + "***";
    }

    private void showError(String message) {
        // Display an error alert dialog
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();

        // Still log to console for debugging
        System.err.println("ERROR: " + message);
    }

    private void showSuccess(String message) {
        // Display a success alert dialog
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();

        // Still log to console for debugging
        System.out.println("SUCCESS: " + message);
    }

    private void showInfo(String message) {
        // Display an information alert dialog
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();

        // Still log to console for debugging
        System.out.println("INFO: " + message);
    }
}