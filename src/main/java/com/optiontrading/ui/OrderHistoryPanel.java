package com.optiontrading.ui;

import com.optiontrading.events.EventBus;
import com.optiontrading.service.order.OrderRepository;
import com.optiontrading.utils.TradeLogManager;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Panel for viewing order history and trade logs
 */
public class OrderHistoryPanel extends BorderPane {

    private final EventBus eventBus;
    private final OrderRepository orderRepository;

    private TextArea logTextArea;
    private ListView<String> ordersListView;
    private Button refreshButton;

    private ScheduledExecutorService logRefresher;
    private static final String LOG_PATH = "logs/optionTrades.log";

    public OrderHistoryPanel(EventBus eventBus, OrderRepository orderRepository) {
        this.eventBus = eventBus;
        this.orderRepository = orderRepository;

        setPadding(new Insets(20));

        // Title
        Text title = new Text("Order History & Trade Logs");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        BorderPane.setAlignment(title, Pos.CENTER);
        setTop(title);

        // Create main content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20, 0, 0, 0));
        setCenter(content);

        // Trade log area
        VBox tradeLogBox = createTradeLogSection();
        content.getChildren().add(tradeLogBox);
        VBox.setVgrow(tradeLogBox, Priority.ALWAYS);

        // Start log monitor
        initializeLogMonitor();
    }

    private VBox createTradeLogSection() {
        VBox logBox = new VBox(10);

        Label logLabel = new Label("Option Chain Calculations & Order Events");
        logLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));

        logTextArea = new TextArea();
        logTextArea.setEditable(false);
        logTextArea.setWrapText(true);
        logTextArea.setFont(Font.font("Monospaced", 12));
        logTextArea.setPrefRowCount(25);

        // Buttons for log control
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        refreshButton = new Button("Refresh Logs");
        refreshButton.setOnAction(e -> refreshLogs());

        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> logTextArea.clear());

        Button exportButton = new Button("Export Logs");
        exportButton.setOnAction(e -> exportLogs());

        buttonBox.getChildren().addAll(refreshButton, clearButton, exportButton);

        logBox.getChildren().addAll(logLabel, logTextArea, buttonBox);
        VBox.setVgrow(logTextArea, Priority.ALWAYS);

        return logBox;
    }

    private void refreshLogs() {
        readTradeLogFile();
    }

    private void readTradeLogFile() {
        File logFile = new File(LOG_PATH);
        if (!logFile.exists()) {
            TradeLogManager.logTradeEvent("Trade log file not found, creating one");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            // Read up to last 500 lines for performance
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() > 500) {
                    lines.remove(0);
                }
            }

            // Update UI on JavaFX thread
            final String logContent = lines.stream().collect(Collectors.joining("\n"));
            Platform.runLater(() -> {
                logTextArea.setText(logContent);
                logTextArea.positionCaret(logTextArea.getText().length());
            });
        } catch (IOException e) {
            e.printStackTrace();
            logTextArea.setText("Error reading trade log: " + e.getMessage());
        }
    }

    private void exportLogs() {
        // In a real implementation, this would save the log to a user-specified
        // location
        TradeLogManager.logTradeEvent("Log export requested by user");
    }

    private void initializeLogMonitor() {
        // Schedule periodic refresh
        logRefresher = Executors.newSingleThreadScheduledExecutor();
        logRefresher.scheduleAtFixedRate(
                this::refreshLogs, 0, 5, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (logRefresher != null) {
            logRefresher.shutdown();
        }
    }
}