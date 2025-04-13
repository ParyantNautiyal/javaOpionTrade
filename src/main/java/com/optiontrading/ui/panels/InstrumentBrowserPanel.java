package com.optiontrading.ui.panels;

import com.optiontrading.events.EventBus;
import com.optiontrading.service.api.KiteConnectClient;
import com.optiontrading.service.instrument.InstrumentService;
import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.OrderType;
import com.optiontrading.service.position.PositionWatchlistService;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Callback;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.TableCell;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Panel for browsing and searching for instruments
 */
public class InstrumentBrowserPanel extends BorderPane {

    private final KiteConnectClient kiteClient;
    private final EventBus eventBus;
    private final InstrumentService instrumentService;
    private final PositionWatchlistService positionWatchlistService;

    private TableView<InstrumentEntry> instrumentTable;
    private TextField searchField;
    private ComboBox<String> exchangeFilter;
    private ComboBox<String> instrumentTypeFilter;
    private DatePicker expiryDateFilter;
    private TextField strikePriceFilter;
    private Label statusLabel;

    // Observable list for instruments
    private ObservableList<InstrumentEntry> instrumentData = FXCollections.observableArrayList();
    private FilteredList<InstrumentEntry> filteredData;

    public InstrumentBrowserPanel(KiteConnectClient kiteClient, EventBus eventBus,
            InstrumentService instrumentService, PositionWatchlistService positionWatchlistService) {
        this.kiteClient = kiteClient;
        this.eventBus = eventBus;
        this.instrumentService = instrumentService;
        this.positionWatchlistService = positionWatchlistService;

        setPadding(new Insets(20));

        // Title
        Text title = new Text("Instrument Browser");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        BorderPane.setAlignment(title, Pos.CENTER);
        setTop(title);

        // Main content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20, 0, 0, 0));
        setCenter(content);

        // Search and filter section
        content.getChildren().add(createSearchSection());

        // Instrument table
        content.getChildren().add(createInstrumentTable());
        VBox.setVgrow(instrumentTable, Priority.ALWAYS);

        // Status label
        statusLabel = new Label("Ready");
        statusLabel.setPadding(new Insets(10, 0, 0, 0));
        content.getChildren().add(statusLabel);

        // Action buttons
        HBox actionBox = new HBox(10);
        actionBox.setPadding(new Insets(20, 0, 0, 0));
        actionBox.setAlignment(Pos.CENTER);

        Button refreshButton = new Button("Refresh Instruments");
        refreshButton.setOnAction(e -> refreshInstruments());
        refreshButton.getStyleClass().add("primary");

        actionBox.getChildren().add(refreshButton);
        setBottom(actionBox);

        // Load initial data if authenticated
        if (kiteClient.isAuthenticated()) {
            refreshInstruments();
        }
    }

    private GridPane createSearchSection() {
        GridPane filterGrid = new GridPane();
        filterGrid.setHgap(10);
        filterGrid.setVgap(10);
        filterGrid.setPadding(new Insets(10));
        filterGrid.setAlignment(Pos.CENTER_LEFT);

        // Row 0: Search and Exchange
        Label searchLabel = new Label("Search:");
        searchField = new TextField();
        searchField.setPromptText("Search by name, symbol, or token");
        searchField.setPrefWidth(300);
        searchField.textProperty().addListener((obs, oldText, newText) -> filterInstruments());
        GridPane.setHgrow(searchField, Priority.ALWAYS);

        Label exchangeLabel = new Label("Exchange:");
        exchangeFilter = new ComboBox<>();
        exchangeFilter.getItems().addAll("All", "NFO", "BFO");
        exchangeFilter.setValue("All");
        exchangeFilter.setPrefWidth(100);
        exchangeFilter.setOnAction(e -> filterInstruments());

        filterGrid.add(searchLabel, 0, 0);
        filterGrid.add(searchField, 1, 0);
        filterGrid.add(exchangeLabel, 2, 0);
        filterGrid.add(exchangeFilter, 3, 0);

        // Row 1: Type and Expiry
        Label typeLabel = new Label("Type:");
        instrumentTypeFilter = new ComboBox<>();
        instrumentTypeFilter.getItems().addAll("All", "CE", "PE");
        instrumentTypeFilter.setValue("All");
        instrumentTypeFilter.setPrefWidth(100);
        instrumentTypeFilter.setOnAction(e -> filterInstruments());

        Label expiryLabel = new Label("Expiry:");
        expiryDateFilter = new DatePicker();
        expiryDateFilter.setPromptText("Filter by expiry");
        expiryDateFilter.setPrefWidth(150);
        expiryDateFilter.valueProperty().addListener((obs, oldDate, newDate) -> filterInstruments());

        filterGrid.add(typeLabel, 0, 1);
        filterGrid.add(instrumentTypeFilter, 1, 1);
        filterGrid.add(expiryLabel, 2, 1);
        filterGrid.add(expiryDateFilter, 3, 1);

        // Row 2: Strike price
        Label strikeLabel = new Label("Strike:");
        strikePriceFilter = new TextField();
        strikePriceFilter.setPromptText("Filter by strike price");
        strikePriceFilter.setPrefWidth(150);
        strikePriceFilter.textProperty().addListener((obs, oldText, newText) -> filterInstruments());

        filterGrid.add(strikeLabel, 0, 2);
        filterGrid.add(strikePriceFilter, 1, 2);

        Button clearFiltersButton = new Button("Clear Filters");
        clearFiltersButton.setOnAction(e -> clearFilters());
        filterGrid.add(clearFiltersButton, 3, 2);

        return filterGrid;
    }

    private void clearFilters() {
        searchField.clear();
        exchangeFilter.setValue("All");
        instrumentTypeFilter.setValue("All");
        expiryDateFilter.setValue(null);
        strikePriceFilter.clear();
        filterInstruments();
    }

    private TableView<InstrumentEntry> createInstrumentTable() {
        instrumentTable = new TableView<>();
        instrumentTable.setPlaceholder(new Label("No instruments loaded. Click 'Refresh Instruments' to load data."));

        // Set up filtered list
        filteredData = new FilteredList<>(instrumentData, p -> true);
        instrumentTable.setItems(filteredData);

        // Create columns
        TableColumn<InstrumentEntry, String> exchangeCol = new TableColumn<>("Exchange");
        exchangeCol.setCellValueFactory(new PropertyValueFactory<>("exchange"));
        exchangeCol.setPrefWidth(60);
        exchangeCol.setSortable(true);

        TableColumn<InstrumentEntry, String> symbolCol = new TableColumn<>("Symbol");
        symbolCol.setCellValueFactory(new PropertyValueFactory<>("tradingSymbol"));
        symbolCol.setPrefWidth(120);
        symbolCol.setSortable(true);

        TableColumn<InstrumentEntry, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(120);
        nameCol.setSortable(true);

        TableColumn<InstrumentEntry, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("instrumentType"));
        typeCol.setPrefWidth(60);
        typeCol.setSortable(true);

        TableColumn<InstrumentEntry, LocalDate> expiryCol = new TableColumn<>("Expiry");
        expiryCol.setCellValueFactory(new PropertyValueFactory<>("expiryDate"));
        expiryCol.setPrefWidth(100);
        expiryCol.setSortable(true);

        TableColumn<InstrumentEntry, BigDecimal> strikeCol = new TableColumn<>("Strike");
        strikeCol.setCellValueFactory(new PropertyValueFactory<>("strikePrice"));
        strikeCol.setPrefWidth(80);
        strikeCol.setSortable(true);

        TableColumn<InstrumentEntry, Double> lotSizeCol = new TableColumn<>("Lot Size");
        lotSizeCol.setCellValueFactory(new PropertyValueFactory<>("lotSize"));
        lotSizeCol.setPrefWidth(80);
        lotSizeCol.setSortable(true);

        // Action column with buttons
        TableColumn<InstrumentEntry, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(200);
        actionCol.setCellFactory(createActionButtonCellFactory());

        instrumentTable.getColumns().addAll(
                exchangeCol, symbolCol, nameCol, typeCol, expiryCol, strikeCol, lotSizeCol, actionCol);

        // Configure sorting functionality
        instrumentTable.getSortOrder().add(symbolCol); // Default sort by symbol

        // Enable sorting through SortedList
        SortedList<InstrumentEntry> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(instrumentTable.comparatorProperty());
        instrumentTable.setItems(sortedData);

        return instrumentTable;
    }

    private Callback<TableColumn<InstrumentEntry, Void>, TableCell<InstrumentEntry, Void>> createActionButtonCellFactory() {
        return param -> new TableCell<InstrumentEntry, Void>() {
            private final Button triggerButton = new Button("Price Trigger");
            private final Button stopLossButton = new Button("Stop Loss");
            private final HBox pane = new HBox(5, triggerButton, stopLossButton);

            {
                // Configure button size and layout
                triggerButton.setMinWidth(90);
                stopLossButton.setMinWidth(90);
                pane.setAlignment(Pos.CENTER);

                // Set action handlers
                triggerButton.setOnAction(event -> {
                    InstrumentEntry instrument = getTableView().getItems().get(getIndex());
                    showPriceTriggerDialog(instrument);
                });

                stopLossButton.setOnAction(event -> {
                    InstrumentEntry instrument = getTableView().getItems().get(getIndex());
                    showStopLossDialog(instrument);
                });
            }

            @Override
            public void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(pane);
                }
            }
        };
    }

    private void showPriceTriggerDialog(InstrumentEntry instrumentEntry) {
        // Create a custom dialog
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Price Trigger Position");
        dialog.setHeaderText("Create Price Trigger for " + instrumentEntry.getTradingSymbol());

        // Get current LTP (in a real app, this would come from market data)
        BigDecimal ltp = getLastTradedPrice(instrumentEntry);

        // Create the dialog content
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // Instrument information
        grid.add(new Label("Instrument:"), 0, 0);
        grid.add(new Label(instrumentEntry.getTradingSymbol()), 1, 0);

        grid.add(new Label("Current LTP:"), 0, 1);
        grid.add(new Label(ltp.toString()), 1, 1);

        grid.add(new Label("Lot Size:"), 0, 2);
        grid.add(new Label(instrumentEntry.getLotSize().toString()), 1, 2);

        // Input fields
        grid.add(new Label("Trigger Price:"), 0, 3);
        TextField triggerPriceField = new TextField();
        triggerPriceField.setText(ltp.toString()); // Default to current price
        grid.add(triggerPriceField, 1, 3);

        grid.add(new Label("Quantity (Lots):"), 0, 4);
        TextField quantityField = new TextField("1");
        grid.add(quantityField, 1, 4);

        grid.add(new Label("Buy/Sell:"), 0, 5);
        ComboBox<String> directionCombo = new ComboBox<>();
        directionCombo.getItems().addAll("BUY", "SELL");
        directionCombo.setValue("BUY");
        grid.add(directionCombo, 1, 5);

        dialog.getDialogPane().setContent(grid);

        // Add buttons
        ButtonType createButtonType = new ButtonType("Create", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        // Show the dialog and process the result
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == createButtonType) {
            try {
                BigDecimal triggerPrice = new BigDecimal(triggerPriceField.getText());
                int lots = Integer.parseInt(quantityField.getText());
                int quantity = lots * instrumentEntry.getLotSize().intValue();
                String directionStr = directionCombo.getValue();
                OrderType orderType = OrderType.valueOf(directionStr);

                // Get the actual Instrument object from the service
                Instrument instrument = getInstrumentFromEntry(instrumentEntry);

                if (instrument != null) {
                    // Create the actual position in the watchlist service
                    positionWatchlistService.addPriceTriggerPosition(
                            instrument,
                            orderType,
                            quantity,
                            ltp, // Use current price as entry price
                            triggerPrice);

                    // Show success message
                    statusLabel.setText("Created price trigger position for " + instrumentEntry.getTradingSymbol() +
                            " at " + triggerPrice + " for " + quantity + " shares (" + directionStr + ")");
                } else {
                    showError("Instrument Not Found", "Could not find the instrument in the service.");
                }

            } catch (NumberFormatException e) {
                showError("Invalid Input", "Please enter valid numeric values for price and quantity.");
            } catch (Exception e) {
                showError("Error Creating Position", "An error occurred: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void showStopLossDialog(InstrumentEntry instrumentEntry) {
        // Create a custom dialog
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Stop Loss Position");
        dialog.setHeaderText("Create Stop Loss for " + instrumentEntry.getTradingSymbol());

        // Get current LTP (in a real app, this would come from market data)
        BigDecimal ltp = getLastTradedPrice(instrumentEntry);

        // Create the dialog content
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // Instrument information
        grid.add(new Label("Instrument:"), 0, 0);
        grid.add(new Label(instrumentEntry.getTradingSymbol()), 1, 0);

        grid.add(new Label("Current LTP:"), 0, 1);
        grid.add(new Label(ltp.toString()), 1, 1);

        grid.add(new Label("Lot Size:"), 0, 2);
        grid.add(new Label(instrumentEntry.getLotSize().toString()), 1, 2);

        // Input fields
        grid.add(new Label("Stop Loss Price:"), 0, 3);
        TextField stopLossPriceField = new TextField();
        grid.add(stopLossPriceField, 1, 3);

        grid.add(new Label("Stop Loss %:"), 0, 4);
        TextField stopLossPercentField = new TextField("1.0"); // Default 1%
        grid.add(stopLossPercentField, 1, 4);

        grid.add(new Label("Quantity (Lots):"), 0, 5);
        TextField quantityField = new TextField("1");
        grid.add(quantityField, 1, 5);

        grid.add(new Label("Buy/Sell:"), 0, 6);
        ComboBox<String> directionCombo = new ComboBox<>();
        directionCombo.getItems().addAll("BUY", "SELL");
        directionCombo.setValue("SELL");
        grid.add(directionCombo, 1, 6);

        grid.add(new Label("Move to Breakeven:"), 0, 7);
        ComboBox<String> moveToBreakevenCombo = new ComboBox<>();
        moveToBreakevenCombo.getItems().addAll("Yes", "No");
        moveToBreakevenCombo.setValue("No");
        grid.add(moveToBreakevenCombo, 1, 7);

        grid.add(new Label("Trailing Stop:"), 0, 8);
        ComboBox<String> trailingStopCombo = new ComboBox<>();
        trailingStopCombo.getItems().addAll("Yes", "No");
        trailingStopCombo.setValue("No");
        grid.add(trailingStopCombo, 1, 8);

        grid.add(new Label("Trailing Distance:"), 0, 9);
        TextField trailingDistanceField = new TextField("0.5"); // Default 0.5%
        grid.add(trailingDistanceField, 1, 9);

        dialog.getDialogPane().setContent(grid);

        // Add buttons
        ButtonType createButtonType = new ButtonType("Create", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        // Show the dialog and process the result
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == createButtonType) {
            try {
                int lots = Integer.parseInt(quantityField.getText());
                int quantity = lots * instrumentEntry.getLotSize().intValue();
                String directionStr = directionCombo.getValue();
                OrderType orderType = OrderType.valueOf(directionStr);
                BigDecimal stopLossPercent = new BigDecimal(stopLossPercentField.getText());
                boolean moveToBreakeven = "Yes".equals(moveToBreakevenCombo.getValue());
                boolean trailingStop = "Yes".equals(trailingStopCombo.getValue());
                BigDecimal trailingDistance = new BigDecimal(trailingDistanceField.getText());

                // Get the actual Instrument object from the service
                Instrument instrument = getInstrumentFromEntry(instrumentEntry);

                if (instrument != null) {
                    // Create the actual position in the watchlist service
                    positionWatchlistService.addStopLossPosition(
                            instrument,
                            orderType,
                            quantity,
                            ltp, // Use current price as entry price
                            stopLossPercent,
                            moveToBreakeven,
                            trailingStop,
                            trailingDistance);

                    // Show success message
                    statusLabel.setText("Created stop loss position for " + instrumentEntry.getTradingSymbol() +
                            " with " + stopLossPercent + "% stop loss for " + quantity + " shares (" + directionStr
                            + ")");
                } else {
                    showError("Instrument Not Found", "Could not find the instrument in the service.");
                }

            } catch (NumberFormatException e) {
                showError("Invalid Input", "Please enter valid numeric values.");
            } catch (Exception e) {
                showError("Error Creating Position", "An error occurred: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Convert InstrumentEntry from the UI to an Instrument object from the service
     */
    private Instrument getInstrumentFromEntry(InstrumentEntry entry) {
        // First try to get the instrument by direct ID (exchange:symbol format)
        String instrumentId = entry.getExchange() + ":" + entry.getTradingSymbol();
        Instrument instrument = instrumentService.getInstrument(instrumentId);

        if (instrument == null) {
            // If not found by direct ID, search by trading symbol
            List<Instrument> instruments = instrumentService.getAllInstruments();
            for (Instrument inst : instruments) {
                if (inst.getTradingSymbol().equals(entry.getTradingSymbol()) &&
                        inst.getExchange().equals(entry.getExchange())) {
                    instrument = inst;
                    break;
                }
            }
        }

        return instrument;
    }

    private BigDecimal getLastTradedPrice(InstrumentEntry instrument) {
        // In a real implementation, this would get the actual LTP from a market data
        // service
        // For demonstration, we'll return a simulated price based on strike price or a
        // default value
        if (instrument.getStrikePrice() != null) {
            // For options, use a value near the strike price
            double randomFactor = 0.9 + Math.random() * 0.2; // Between 0.9 and 1.1
            return instrument.getStrikePrice().multiply(new BigDecimal(randomFactor)).setScale(2,
                    BigDecimal.ROUND_HALF_UP);
        } else {
            // For non-options, use a random price
            return new BigDecimal(Math.round(1000 + Math.random() * 9000)).setScale(2, BigDecimal.ROUND_HALF_UP);
        }
    }

    private void refreshInstruments() {
        if (!kiteClient.isAuthenticated()) {
            showError("Authentication Required", "Please authenticate with Kite API first.");
            return;
        }

        instrumentData.clear();
        statusLabel.setText("Loading instruments...");

        try {
            List<Instrument> instruments = instrumentService.getAllInstruments();

            // If no instruments are loaded yet, try to refresh the instrument data from API
            if (instruments == null || instruments.isEmpty()) {
                statusLabel.setText("No instruments found in memory. Fetching from API...");
                instrumentService.refreshInstruments();
                instruments = instrumentService.getAllInstruments();
            }

            if (instruments == null || instruments.isEmpty()) {
                statusLabel.setText("No instruments found. Using sample data instead.");
                addSampleData();
            } else {
                // Load real instrument data
                for (Instrument instrument : instruments) {
                    addInstrumentToData(instrument);
                }
                statusLabel.setText("Loaded " + instruments.size() + " instruments");
            }

            updateExpiryDateFilter();
            filterInstruments();
        } catch (Exception e) {
            statusLabel.setText("Error loading instruments: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateExpiryDateFilter() {
        Set<LocalDate> availableExpiryDates = new HashSet<>();
        for (InstrumentEntry entry : instrumentData) {
            if (entry.getExpiryDate() != null) {
                availableExpiryDates.add(entry.getExpiryDate());
            }
        }
        // We don't need to do anything with the dates here as DatePicker doesn't have a
        // restrictive items list
        // But we could use this set of dates to create a more sophisticated date filter
        // if needed
    }

    private void addSampleData() {
        // Add sample data as fallback
        instrumentData.addAll(
                new InstrumentEntry("NSE", "RELIANCE", "Reliance Industries Ltd.", "STOCK", 256265L, 0.05, 1.0, null,
                        null),
                new InstrumentEntry("NSE", "INFY", "Infosys Ltd.", "STOCK", 408065L, 0.05, 1.0, null, null),
                new InstrumentEntry("NSE", "TCS", "Tata Consultancy Services Ltd.", "STOCK", 2953217L, 0.05, 1.0, null,
                        null),
                new InstrumentEntry("NFO", "NIFTY23MAYFUT", "NIFTY", "FUT", 56789L, 0.05, 50.0,
                        LocalDate.of(2025, 5, 29), null),
                new InstrumentEntry("NFO", "BANKNIFTY23MAYFUT", "BANKNIFTY", "FUT", 67890L, 0.05, 25.0,
                        LocalDate.of(2025, 5, 29), null),
                new InstrumentEntry("NFO", "NIFTY23MAY18000CE", "NIFTY", "CE", 78901L, 0.05, 50.0,
                        LocalDate.of(2025, 5, 29), new BigDecimal("18000")),
                new InstrumentEntry("NFO", "NIFTY23MAY18000PE", "NIFTY", "PE", 89012L, 0.05, 50.0,
                        LocalDate.of(2025, 5, 29), new BigDecimal("18000")),
                new InstrumentEntry("BFO", "BANKEX23MAY40000CE", "BANKEX", "CE", 90123L, 0.05, 25.0,
                        LocalDate.of(2025, 5, 27), new BigDecimal("40000")),
                new InstrumentEntry("BFO", "BANKEX23MAY40000PE", "BANKEX", "PE", 12345L, 0.05, 25.0,
                        LocalDate.of(2025, 5, 27), new BigDecimal("40000")));
    }

    private void addInstrumentToData(Instrument instrument) {
        String exchange = instrument.getExchange();
        String tradingSymbol = instrument.getTradingSymbol();
        String name = instrument.getUnderlyingSymbol() != null ? instrument.getUnderlyingSymbol() : tradingSymbol;

        // Get the correct instrument type (CE/PE for options)
        String instrumentType;
        if (instrument.isOption()) {
            instrumentType = instrument.isCall() ? "CE" : "PE";
        } else {
            instrumentType = instrument.getType().toString();
        }

        LocalDate expiryDate = instrument.getExpiryDate();
        BigDecimal strikePrice = instrument.getStrikePrice();

        // Handle non-numeric instrument IDs
        Long instrumentToken;
        try {
            // For IDs like "BFO:BANKEX25APR55400PE", we'll use a hash code
            if (instrument.getInstrumentId().contains(":")) {
                instrumentToken = (long) instrument.getInstrumentId().hashCode();
            } else {
                instrumentToken = Long.parseLong(instrument.getInstrumentId());
            }
        } catch (NumberFormatException e) {
            // For non-numeric IDs, use a hash code as a numeric representation
            instrumentToken = (long) instrument.getInstrumentId().hashCode();
        }

        Double tickSize = 0.05; // Default tick size
        Double lotSize = (double) instrument.getLotSize();

        instrumentData.add(new InstrumentEntry(
                exchange, tradingSymbol, name, instrumentType,
                instrumentToken, tickSize, lotSize, expiryDate, strikePrice));
    }

    private void filterInstruments() {
        String searchText = searchField.getText().toLowerCase();
        String exchangeValue = exchangeFilter.getValue();
        String typeValue = instrumentTypeFilter.getValue();
        LocalDate expiryDate = expiryDateFilter.getValue();
        String strikePriceText = strikePriceFilter.getText().trim();

        // Use a wrapper for the BigDecimal to make it effectively final
        final BigDecimal[] strikePriceHolder = new BigDecimal[1];
        strikePriceHolder[0] = null;

        if (!strikePriceText.isEmpty()) {
            try {
                strikePriceHolder[0] = new BigDecimal(strikePriceText);
            } catch (NumberFormatException e) {
                // Invalid number, ignore
            }
        }

        filteredData.setPredicate(instrument -> {
            // Filter by search text
            boolean matchesSearch = searchText.isEmpty() ||
                    instrument.getTradingSymbol().toLowerCase().contains(searchText) ||
                    instrument.getName().toLowerCase().contains(searchText) ||
                    String.valueOf(instrument.getInstrumentToken()).contains(searchText);

            // Filter by exchange
            boolean matchesExchange = "All".equals(exchangeValue) ||
                    instrument.getExchange().equals(exchangeValue);

            // Filter by instrument type (CE/PE)
            boolean matchesType = "All".equals(typeValue) ||
                    instrument.getInstrumentType().equals(typeValue);

            // Filter by expiry date
            boolean matchesExpiry = expiryDate == null ||
                    (instrument.getExpiryDate() != null &&
                            instrument.getExpiryDate().equals(expiryDate));

            // Filter by strike price
            BigDecimal strikeValue = strikePriceHolder[0];
            boolean matchesStrike = strikeValue == null ||
                    (instrument.getStrikePrice() != null &&
                            instrument.getStrikePrice().compareTo(strikeValue) == 0);

            return matchesSearch && matchesExchange && matchesType && matchesExpiry && matchesStrike;
        });
    }

    private void showError(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Model class for instrument entries in the table
     */
    public static class InstrumentEntry {
        private String exchange;
        private String tradingSymbol;
        private String name;
        private String instrumentType;
        private Long instrumentToken;
        private Double tickSize;
        private Double lotSize;
        private LocalDate expiryDate;
        private BigDecimal strikePrice;

        public InstrumentEntry(String exchange, String tradingSymbol, String name, String instrumentType,
                Long instrumentToken, Double tickSize, Double lotSize, LocalDate expiryDate, BigDecimal strikePrice) {
            this.exchange = exchange;
            this.tradingSymbol = tradingSymbol;
            this.name = name;
            this.instrumentType = instrumentType;
            this.instrumentToken = instrumentToken;
            this.tickSize = tickSize;
            this.lotSize = lotSize;
            this.expiryDate = expiryDate;
            this.strikePrice = strikePrice;
        }

        // Getters and setters
        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getTradingSymbol() {
            return tradingSymbol;
        }

        public void setTradingSymbol(String tradingSymbol) {
            this.tradingSymbol = tradingSymbol;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getInstrumentType() {
            return instrumentType;
        }

        public void setInstrumentType(String instrumentType) {
            this.instrumentType = instrumentType;
        }

        public Long getInstrumentToken() {
            return instrumentToken;
        }

        public void setInstrumentToken(Long instrumentToken) {
            this.instrumentToken = instrumentToken;
        }

        public Double getTickSize() {
            return tickSize;
        }

        public void setTickSize(Double tickSize) {
            this.tickSize = tickSize;
        }

        public Double getLotSize() {
            return lotSize;
        }

        public void setLotSize(Double lotSize) {
            this.lotSize = lotSize;
        }

        public LocalDate getExpiryDate() {
            return expiryDate;
        }

        public void setExpiryDate(LocalDate expiryDate) {
            this.expiryDate = expiryDate;
        }

        public BigDecimal getStrikePrice() {
            return strikePrice;
        }

        public void setStrikePrice(BigDecimal strikePrice) {
            this.strikePrice = strikePrice;
        }
    }
}