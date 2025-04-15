package com.optiontrading.ui.controller;

import com.optiontrading.events.EventBus;
import com.optiontrading.events.EventSubscriber;
import com.optiontrading.events.MarketDataEvent;
import com.optiontrading.service.position.PositionEvent;
import com.optiontrading.service.position.PositionWatchlistService;
import com.optiontrading.service.position.WatchedPosition;
import com.optiontrading.ui.PositionViewModel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.geometry.Insets;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Optional;

/**
 * Controller for the positions panel UI
 */
public class PositionsController {
    @FXML
    private TableView<PositionViewModel> positionsTable;
    @FXML
    private TableColumn<PositionViewModel, String> instrumentCol;
    @FXML
    private TableColumn<PositionViewModel, String> strategyCol;
    @FXML
    private TableColumn<PositionViewModel, String> quantityCol;
    @FXML
    private TableColumn<PositionViewModel, String> entryPriceCol;
    @FXML
    private TableColumn<PositionViewModel, String> entryTimeCol;
    @FXML
    private TableColumn<PositionViewModel, String> marketPriceCol;
    @FXML
    private TableColumn<PositionViewModel, String> pnlCol;
    @FXML
    private TableColumn<PositionViewModel, String> pnlPercentCol;
    @FXML
    private TableColumn<PositionViewModel, String> statusCol;
    @FXML
    private TableColumn<PositionViewModel, String> targetCol;
    @FXML
    private TableColumn<PositionViewModel, String> stopLossCol;
    @FXML
    private TableColumn<PositionViewModel, String> actionsCol;

    @FXML
    private Label totalPositionsLabel;
    @FXML
    private Label totalPnlLabel;
    @FXML
    private Label lastUpdateLabel;
    @FXML
    private Button refreshButton;
    @FXML
    private ToggleButton autoRefreshToggle;

    @FXML
    private Button editPositionButton;
    @FXML
    private Button closePositionButton;
    @FXML
    private Button deletePositionButton;

    @FXML
    private Label detailInstrument;
    @FXML
    private Label detailStrategy;
    @FXML
    private Label detailEntryPrice;
    @FXML
    private Label detailCurrentPrice;
    @FXML
    private Label detailStopLoss;
    @FXML
    private Label detailTarget;
    @FXML
    private TextArea detailNotes;

    private PositionWatchlistService positionService;
    private EventBus eventBus;

    private final ObservableList<PositionViewModel> positionItems = FXCollections.observableArrayList();
    private final Map<String, PositionViewModel> positionViewModels = new ConcurrentHashMap<>();

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Auto-refresh control
    private final AtomicBoolean refreshScheduled = new AtomicBoolean(false);
    private Timer refreshTimer;
    private static final long REFRESH_INTERVAL_MS = 1000; // 2 seconds
    private static final long THROTTLE_DELAY_MS = 500; // Minimum time between refreshes

    /**
     * Initialize the controller
     */
    @FXML
    private void initialize() {
        // Set cell value factories
        instrumentCol.setCellValueFactory(cellData -> cellData.getValue().symbolProperty());
        strategyCol.setCellValueFactory(cellData -> cellData.getValue().typeProperty());
        quantityCol.setCellValueFactory(cellData -> cellData.getValue().quantityProperty());
        entryPriceCol.setCellValueFactory(cellData -> cellData.getValue().entryPriceProperty());
        entryTimeCol.setCellValueFactory(cellData -> cellData.getValue().entryTimeProperty());
        marketPriceCol.setCellValueFactory(cellData -> cellData.getValue().currentPriceProperty());
        pnlCol.setCellValueFactory(cellData -> cellData.getValue().pnlAmountProperty());
        pnlPercentCol.setCellValueFactory(cellData -> cellData.getValue().pnlPercentProperty());
        stopLossCol.setCellValueFactory(cellData -> cellData.getValue().stopLossProperty());
        targetCol.setCellValueFactory(cellData -> cellData.getValue().targetProperty());
        statusCol.setCellValueFactory(cellData -> cellData.getValue().statusProperty());

        // Setup action buttons column
        actionsCol.setCellFactory(col -> new TableCell<PositionViewModel, String>() {
            private final Button closeButton = new Button("Close");
            private final Button editButton = new Button("Edit");

            {
                closeButton.getStyleClass().add("small-button");
                editButton.getStyleClass().add("small-button");

                closeButton.setOnAction(e -> handleClosePosition(getTableRow().getItem()));
                editButton.setOnAction(e -> handleEditPosition(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    PositionViewModel position = getTableRow().getItem();
                    if (position != null) {
                        // Create action buttons container
                        ButtonBar actions = new ButtonBar();
                        actions.getButtons().addAll(editButton, closeButton);
                        setGraphic(actions);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });

        // Set custom cell factories for color-coding
        pnlCol.setCellFactory(column -> new TableCell<PositionViewModel, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    // Set style based on profit/loss
                    if (item.startsWith("+")) {
                        setStyle("-fx-text-fill: green;");
                    } else if (item.startsWith("-")) {
                        setStyle("-fx-text-fill: red;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        pnlPercentCol.setCellFactory(column -> new TableCell<PositionViewModel, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    // Set style based on profit/loss
                    if (item.startsWith("+")) {
                        setStyle("-fx-text-fill: green;");
                    } else if (item.startsWith("-")) {
                        setStyle("-fx-text-fill: red;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        statusCol.setCellFactory(column -> new TableCell<PositionViewModel, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    // Highlight critical statuses
                    if (item.contains("NEAR SL")) {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    } else if (item.contains("SL@Cost")) {
                        setStyle("-fx-text-fill: blue;");
                    } else if (item.contains("Trailing")) {
                        setStyle("-fx-text-fill: purple;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // Set the items
        positionsTable.setItems(positionItems);

        // Setup selection listener for detail view
        positionsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateDetailView(newVal);
        });

        // Set button states
        editPositionButton.setDisable(true);
        closePositionButton.setDisable(true);

        // Set initial labels
        totalPositionsLabel.setText("0");
        totalPnlLabel.setText("₹0.00");
        lastUpdateLabel.setText("Last Update: Never");

        // Initialize auto-refresh toggle with improved logic
        setupAutoRefresh();
    }

    /**
     * Setup auto-refresh functionality
     */
    private void setupAutoRefresh() {
        refreshTimer = new Timer(true); // Create as daemon timer

        // Add listener for toggle button changes
        autoRefreshToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                // Schedule immediate refresh when turned on
                scheduleRefresh();
                System.out.println("Auto-refresh enabled");
            } else {
                System.out.println("Auto-refresh disabled");
            }
        });
    }

    /**
     * Schedule a UI refresh with throttling
     */
    private void scheduleRefresh() {
        // Only schedule if not already scheduled
        if (!refreshScheduled.getAndSet(true)) {
            // Use Platform.runLater to ensure UI updates happen on JavaFX thread
            Platform.runLater(() -> {
                try {
                    // Refresh the data
                    refreshData();
                } catch (Exception e) {
                    System.err.println("Error during auto-refresh: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    // Reset the flag after throttle delay
                    refreshTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            refreshScheduled.set(false);
                        }
                    }, THROTTLE_DELAY_MS);
                }
            });
        }
    }

    /**
     * Check if market data event is relevant to our positions
     */
    private boolean isRelevantMarketData(MarketDataEvent event) {
        if (event == null || event.getSymbol() == null) {
            return false;
        }

        // Check if any position has this instrument
        for (PositionViewModel vm : positionViewModels.values()) {
            WatchedPosition position = vm.getPosition();
            if (position != null &&
                    position.getInstrument() != null &&
                    position.getInstrument().getTradingSymbol() != null &&
                    position.getInstrument().getTradingSymbol().equals(event.getSymbol())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Initialize with services
     */
    public void initServices(PositionWatchlistService positionService, EventBus eventBus) {
        this.positionService = positionService;
        this.eventBus = eventBus;

        // Subscribe to position-related events
        eventBus.subscribe(PositionEvent.class, new EventSubscriber<PositionEvent>() {
            @Override
            public void onEvent(PositionEvent event) {
                handlePositionEvent(event);
            }
        });

        // Subscribe to market data events with improved refresh logic
        eventBus.subscribe(MarketDataEvent.class, new EventSubscriber<MarketDataEvent>() {
            @Override
            public void onEvent(MarketDataEvent event) {
                // Only trigger refresh if:
                // 1. Auto-refresh is enabled
                // 2. The event is for an instrument we're tracking
                // 3. We're not already processing a refresh
                if (autoRefreshToggle.isSelected() && isRelevantMarketData(event)) {
                    scheduleRefresh();
                }
            }
        });

        // Setup periodic refresh timer (every REFRESH_INTERVAL_MS) for when
        // auto-refresh is on
        refreshTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (autoRefreshToggle.isSelected()) {
                    scheduleRefresh();
                }
            }
        }, REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS);

        // Load initial data
        refreshData();
    }

    /**
     * Handle position events
     */
    private void handlePositionEvent(PositionEvent event) {
        // Update UI on JavaFX thread
        Platform.runLater(() -> {
            // Only respond to active position changes
            if (event.getPositionId() != null) {
                refreshData();
            }
        });
    }

    /**
     * Handle refresh button click
     */
    @FXML
    private void handleRefresh() {
        refreshData();
    }

    /**
     * Handle edit position button click
     */
    @FXML
    private void handleEditPosition() {
        PositionViewModel selectedPosition = positionsTable.getSelectionModel().getSelectedItem();
        if (selectedPosition != null) {
            handleEditPosition(selectedPosition);
        }
    }

    /**
     * Handle close position button click
     */
    @FXML
    private void handleClosePosition() {
        PositionViewModel selectedPosition = positionsTable.getSelectionModel().getSelectedItem();
        if (selectedPosition != null) {
            handleClosePosition(selectedPosition);
        }
    }

    /**
     * Handle edit position for a specific position
     */
    private void handleEditPosition(PositionViewModel position) {
        if (position != null) {
            WatchedPosition pos = position.getPosition();

            // Create dialog
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Edit Position");
            dialog.setHeaderText("Edit position: " + pos.getInstrument().getTradingSymbol());

            // Set dialog buttons
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            // Create form layout
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            // Stop Loss field
            TextField stopLossField = new TextField();
            if (pos.getStopLoss() != null) {
                stopLossField.setText(pos.getStopLoss().toString());
            }
            grid.add(new Label("Stop Loss:"), 0, 0);
            grid.add(stopLossField, 1, 0);

            // Target field
            TextField targetField = new TextField();
            if (pos.getTarget() != null) {
                targetField.setText(pos.getTarget().toString());
            }
            grid.add(new Label("Target:"), 0, 1);
            grid.add(targetField, 1, 1);

            // Move SL to cost checkbox
            CheckBox moveSlToCostCheckbox = new CheckBox("Move stop loss to cost when in profit");
            moveSlToCostCheckbox.setSelected(pos.isMoveToBreakeven());
            grid.add(moveSlToCostCheckbox, 0, 2, 2, 1);

            // Trailing SL checkbox
            CheckBox trailingSlCheckbox = new CheckBox("Enable trailing stop loss");
            trailingSlCheckbox.setSelected(pos.isTrailingStopLoss());
            grid.add(trailingSlCheckbox, 0, 3, 2, 1);

            dialog.getDialogPane().setContent(grid);

            // Handle result
            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {
                    // Parse inputs
                    BigDecimal stopLoss = null;
                    BigDecimal target = null;

                    if (!stopLossField.getText().trim().isEmpty()) {
                        stopLoss = new BigDecimal(stopLossField.getText().trim());
                    }

                    if (!targetField.getText().trim().isEmpty()) {
                        target = new BigDecimal(targetField.getText().trim());
                    }

                    boolean moveSlToCost = moveSlToCostCheckbox.isSelected();
                    boolean trailingSl = trailingSlCheckbox.isSelected();

                    // Update position through service
                    // Note: Implementation needed in PositionWatchlistService
                    positionService.updatePosition(
                            pos.getId(),
                            stopLoss,
                            target,
                            moveSlToCost,
                            trailingSl);

                    // Refresh data
                    refreshData();

                } catch (NumberFormatException e) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Invalid Input");
                    alert.setHeaderText("Invalid numeric input");
                    alert.setContentText("Please enter valid numbers for stop loss and target.");
                    alert.showAndWait();
                }
            }
        }
    }

    /**
     * Handle close position for a specific position
     */
    private void handleClosePosition(PositionViewModel position) {
        if (position != null) {
            WatchedPosition pos = position.getPosition();

            // Show confirmation dialog
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Close Position");
            alert.setHeaderText("Close position: " + pos.getInstrument().getTradingSymbol());
            alert.setContentText(
                    "Are you sure you want to close this position? This will place a market order to close your position.");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                // Close the position with reason
                boolean success = positionService.closePosition(pos.getId(), "Manual close from UI");

                if (success) {
                    // Show success message
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Position Closed");
                    successAlert.setHeaderText("Position closed successfully");
                    successAlert.setContentText("Position has been closed with a market order.");
                    successAlert.showAndWait();

                    // Refresh data
                    refreshData();
                } else {
                    // Show error message
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Error Closing Position");
                    errorAlert.setHeaderText("Failed to close position");
                    errorAlert
                            .setContentText("There was an error closing the position. Please check logs for details.");
                    errorAlert.showAndWait();
                }
            }
        }
    }

    /**
     * Handle delete position button click
     */
    @FXML
    private void handleDeletePosition() {
        PositionViewModel selectedPosition = positionsTable.getSelectionModel().getSelectedItem();
        if (selectedPosition != null) {
            handleDeletePosition(selectedPosition);
        }
    }

    /**
     * Handle delete position for a specific position
     */
    private void handleDeletePosition(PositionViewModel position) {
        if (position != null) {
            WatchedPosition pos = position.getPosition();

            // Show confirmation dialog
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Delete Position");
            alert.setHeaderText("Delete position: " + pos.getInstrument().getTradingSymbol());
            alert.setContentText(
                    "Are you sure you want to delete this position from the watchlist? This will not close any actual positions with your broker.");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                // Remove the position
                WatchedPosition removed = positionService.removePosition(pos.getId());

                if (removed != null) {
                    // Show success message
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Position Deleted");
                    successAlert.setHeaderText("Position removed from watchlist");
                    successAlert.setContentText("Position has been removed from the watchlist.");
                    successAlert.showAndWait();

                    // Refresh data
                    refreshData();
                } else {
                    // Show error message
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Error Deleting Position");
                    errorAlert.setHeaderText("Failed to delete position");
                    errorAlert.setContentText("There was an error removing the position from the watchlist.");
                    errorAlert.showAndWait();
                }
            }
        }
    }

    /**
     * Refresh position data
     */
    private void refreshData() {
        if (positionService == null) {
            return;
        }

        try {
            // Get active positions
            List<WatchedPosition> positions = positionService.getActivePositions();

            // Update the view models
            updateViewModels(positions);

            // Update summary data
            updateSummary(positions);

            // Update last update time
            lastUpdateLabel.setText("Last Update: " + LocalDateTime.now().format(TIME_FORMATTER));
        } catch (Exception e) {
            System.err.println("Error refreshing positions data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Update view models from positions
     */
    private void updateViewModels(List<WatchedPosition> positions) {
        // Track IDs to remove
        positionViewModels.keySet().removeIf(id -> positions.stream().noneMatch(p -> p.getId().equals(id)));

        // Update or add view models
        for (WatchedPosition position : positions) {
            String id = position.getId();
            PositionViewModel viewModel = positionViewModels.get(id);

            if (viewModel == null) {
                viewModel = new PositionViewModel(position);
                positionViewModels.put(id, viewModel);
            } else {
                viewModel.updateFromPosition(position);
            }
        }

        // Sync with observable list
        positionItems.setAll(positionViewModels.values());
    }

    /**
     * Update position summary data
     */
    private void updateSummary(List<WatchedPosition> positions) {
        totalPositionsLabel.setText(String.valueOf(positions.size()));

        BigDecimal totalPnl = BigDecimal.ZERO;
        for (WatchedPosition position : positions) {
            totalPnl = totalPnl.add(position.getPnl());
        }

        String pnlDisplay = (totalPnl.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") +
                "₹" + String.format("%,.2f", totalPnl);
        totalPnlLabel.setText(pnlDisplay);
    }

    /**
     * Update the detail view with selected position
     */
    private void updateDetailView(PositionViewModel position) {
        boolean hasSelection = position != null;

        // Enable/disable buttons based on selection
        editPositionButton.setDisable(!hasSelection);
        closePositionButton.setDisable(!hasSelection);
        deletePositionButton.setDisable(!hasSelection);

        if (hasSelection) {
            WatchedPosition pos = position.getPosition();

            // Update detail fields
            detailInstrument.setText(pos.getInstrument().getTradingSymbol());
            detailStrategy.setText(pos.getSource().toString());
            detailEntryPrice.setText(pos.getEntryPrice().toString());
            detailCurrentPrice.setText(pos.getCurrentPrice().toString());
            detailStopLoss.setText(pos.getStopLoss() != null ? pos.getStopLoss().toString() : "None");
            detailTarget.setText(pos.getTarget() != null ? pos.getTarget().toString() : "None");

            // Set notes if available
            detailNotes.setText("Position ID: " + pos.getId());
        } else {
            // Clear detail fields
            detailInstrument.setText("-");
            detailStrategy.setText("-");
            detailEntryPrice.setText("-");
            detailCurrentPrice.setText("-");
            detailStopLoss.setText("-");
            detailTarget.setText("-");
            detailNotes.setText("");
        }
    }

    /**
     * Cleanup resources when controller is no longer needed
     */
    public void shutdown() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer = null;
        }
    }
}