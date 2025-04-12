package com.optiontrading.ui;

import com.optiontrading.service.instrument.InstrumentService;
import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.OptionType;
import com.optiontrading.service.model.OrderScheduleParams;
import com.optiontrading.service.model.ScheduledOrder;
import com.optiontrading.service.order.OrderRepository;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Simple UI for entering order details with instrument selection dropdowns
 */
public class OrderEntryUI extends JFrame {
    private static final long serialVersionUID = 1L;
    private final InstrumentService instrumentService;
    private final OrderRepository orderRepository;

    // Dropdown components
    private JComboBox<String> indexSelector;
    private JComboBox<String> expirySelector;
    private JComboBox<String> optionTypeSelector;
    private JComboBox<BigDecimal> strikeSelector;
    private JTextField quantityField;
    private JTextField priceField;
    private JSpinner executeAtSpinner;

    // For storing filtered data
    private List<LocalDate> availableExpiries = new ArrayList<>();
    private List<BigDecimal> availableStrikes = new ArrayList<>();
    private List<Instrument> filteredInstruments = new ArrayList<>();

    public OrderEntryUI() {
        this.instrumentService = InstrumentService.getInstance();
        this.orderRepository = OrderRepository.getInstance();

        // Initialize UI
        initializeUI();

        // Ensure instruments are loaded
        if (instrumentService.getAllInstruments().isEmpty()) {
            instrumentService.refreshInstruments(false); // Always use real API data
        }

        // Load initial data
        loadIndexSymbols();
    }

    private void initializeUI() {
        setTitle("Option Trading - Order Entry");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(550, 450);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Title panel
        JPanel titlePanel = new JPanel();
        JLabel titleLabel = new JLabel("Order Entry Form");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));

        // Add refresh button to title panel
        JButton refreshButton = new JButton("Refresh Instruments");
        refreshButton.addActionListener(e -> refreshInstruments());

        titlePanel.add(titleLabel);
        titlePanel.add(refreshButton);
        mainPanel.add(titlePanel, BorderLayout.NORTH);

        // Form panel
        JPanel formPanel = new JPanel(new GridLayout(0, 2, 10, 15));

        // Index selector
        formPanel.add(new JLabel("Select Index:"));
        indexSelector = new JComboBox<>();
        indexSelector.addActionListener(e -> updateExpiryDates());
        formPanel.add(indexSelector);

        // Expiry date selector
        formPanel.add(new JLabel("Select Expiry:"));
        expirySelector = new JComboBox<>();
        expirySelector.addActionListener(e -> updateOptionTypes());
        formPanel.add(expirySelector);

        // Option type selector
        formPanel.add(new JLabel("Option Type:"));
        optionTypeSelector = new JComboBox<>(new String[] { "CALL", "PUT" });
        optionTypeSelector.addActionListener(e -> updateStrikes());
        formPanel.add(optionTypeSelector);

        // Strike price selector
        formPanel.add(new JLabel("Strike Price:"));
        strikeSelector = new JComboBox<>();
        formPanel.add(strikeSelector);

        // Quantity field
        formPanel.add(new JLabel("Quantity (Lots):"));
        quantityField = new JTextField("1");
        formPanel.add(quantityField);

        // Price field
        formPanel.add(new JLabel("Price (or 0 for market):"));
        priceField = new JTextField("0");
        formPanel.add(priceField);

        // Execute at time spinner
        formPanel.add(new JLabel("Execute at (Time):"));
        SpinnerDateModel timeModel = new SpinnerDateModel();
        executeAtSpinner = new JSpinner(timeModel);
        JSpinner.DateEditor timeEditor = new JSpinner.DateEditor(executeAtSpinner, "HH:mm:ss");
        executeAtSpinner.setEditor(timeEditor);
        executeAtSpinner.setValue(new java.util.Date());
        formPanel.add(executeAtSpinner);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel();
        JButton placeOrderButton = new JButton("Place Order");
        placeOrderButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                placeOrder();
            }
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(placeOrderButton);
        buttonPanel.add(cancelButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Add main panel to frame
        add(mainPanel);
    }

    private void loadIndexSymbols() {
        // Get all unique underlying symbols from options
        List<String> indices = instrumentService.loadAvailableSymbols();

        if (indices.isEmpty()) {
            // If no symbols are available from files, refresh from API
            instrumentService.refreshInstrumentsAndSaveToFiles();
            indices = instrumentService.loadAvailableSymbols();
        }

        indexSelector.removeAllItems();
        for (String index : indices) {
            indexSelector.addItem(index);
        }
    }

    private void updateExpiryDates() {
        String selectedIndex = (String) indexSelector.getSelectedItem();
        if (selectedIndex == null)
            return;

        // Get all expiry dates for the selected index from files
        availableExpiries = instrumentService.loadAvailableExpiries(selectedIndex);

        if (availableExpiries.isEmpty()) {
            // If no expiries are available from files, get them from memory
            // This is a fallback and should not happen if files are properly initialized
            availableExpiries = instrumentService.getAllOptionInstruments().stream()
                    .filter(i -> selectedIndex.equals(i.getUnderlyingSymbol()))
                    .map(Instrument::getExpiryDate)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        }

        // Log available expiry dates for debugging
        System.out.println("Available expiry dates for " + selectedIndex + ":");
        for (LocalDate expiry : availableExpiries) {
            System.out.println(" - " + expiry.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")));
        }

        expirySelector.removeAllItems();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        for (LocalDate expiry : availableExpiries) {
            expirySelector.addItem(expiry.format(formatter));
        }
    }

    private void updateOptionTypes() {
        updateStrikes();
    }

    private void updateStrikes() {
        String selectedIndex = (String) indexSelector.getSelectedItem();
        int expiryIdx = expirySelector.getSelectedIndex();
        String selectedOptionType = (String) optionTypeSelector.getSelectedItem();

        if (selectedIndex == null || expiryIdx < 0 || expiryIdx >= availableExpiries.size())
            return;

        LocalDate selectedExpiry = availableExpiries.get(expiryIdx);
        OptionType optionType = OptionType.valueOf(selectedOptionType);

        // Load specific instruments for this symbol and expiry
        List<Instrument> loadedInstruments = instrumentService.loadInstrumentsForSymbolAndExpiry(selectedIndex,
                selectedExpiry);

        // Filter instruments by option type
        filteredInstruments = loadedInstruments.stream()
                .filter(i -> optionType.equals(i.getOptionType()))
                .collect(Collectors.toList());

        // Get available strike prices
        availableStrikes = filteredInstruments.stream()
                .map(Instrument::getStrikePrice)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        strikeSelector.removeAllItems();
        for (BigDecimal strike : availableStrikes) {
            strikeSelector.addItem(strike);
        }
    }

    private void placeOrder() {
        try {
            // Validate fields
            if (indexSelector.getSelectedItem() == null ||
                    expirySelector.getSelectedItem() == null ||
                    strikeSelector.getSelectedItem() == null) {
                JOptionPane.showMessageDialog(this, "Please select all required fields", "Validation Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Get selected values
            String selectedIndex = (String) indexSelector.getSelectedItem();
            int expiryIdx = expirySelector.getSelectedIndex();
            LocalDate selectedExpiry = availableExpiries.get(expiryIdx);
            OptionType optionType = OptionType.valueOf((String) optionTypeSelector.getSelectedItem());
            BigDecimal selectedStrike = (BigDecimal) strikeSelector.getSelectedItem();

            // Find the selected instrument
            Instrument selectedInstrument = filteredInstruments.stream()
                    .filter(i -> selectedStrike.equals(i.getStrikePrice()))
                    .findFirst()
                    .orElse(null);

            if (selectedInstrument == null) {
                JOptionPane.showMessageDialog(this, "Could not find selected instrument", "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Get quantity and price
            int quantity;
            try {
                quantity = Integer.parseInt(quantityField.getText());
                if (quantity <= 0)
                    throw new NumberFormatException();
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Please enter a valid quantity", "Validation Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            BigDecimal price;
            try {
                price = new BigDecimal(priceField.getText());
                if (price.compareTo(BigDecimal.ZERO) < 0)
                    throw new NumberFormatException();
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Please enter a valid price", "Validation Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Get execution time
            java.util.Date spinnerValue = (java.util.Date) executeAtSpinner.getValue();
            LocalTime executionTime = LocalTime.ofInstant(spinnerValue.toInstant(), java.time.ZoneId.systemDefault());
            LocalDateTime executionDateTime = LocalDateTime.of(LocalDate.now(), executionTime);

            // If time is in the past for today, assume tomorrow
            if (executionDateTime.isBefore(LocalDateTime.now())) {
                executionDateTime = executionDateTime.plusDays(1);
            }

            // Create order parameters
            OrderScheduleParams params = OrderScheduleParams.builder()
                    .indexSymbol(selectedIndex)
                    .expiryDate(selectedExpiry)
                    .targetPremium(price)
                    .lots(quantity)
                    .executionTime(executionDateTime)
                    .threshold(5.0) // Default threshold
                    .build();

            // Create the order using the repository
            ScheduledOrder order = orderRepository.createOrder(params);

            // Show confirmation
            JOptionPane.showMessageDialog(this,
                    String.format("Order scheduled: %s %s %s at strike %.1f\nQuantity: %d, Execution time: %s",
                            selectedIndex, selectedExpiry.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")),
                            optionType, selectedStrike.doubleValue(), quantity,
                            executionDateTime.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss"))),
                    "Order Scheduled", JOptionPane.INFORMATION_MESSAGE);

            // Clear form
            quantityField.setText("1");
            priceField.setText("0");

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error placing order: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * Force refresh of index instruments from API
     */
    private void refreshInstruments() {
        try {
            // Show status dialog
            JDialog statusDialog = new JDialog(this, "Refreshing Instruments", true);
            statusDialog.setLayout(new FlowLayout());
            statusDialog.add(new JLabel("Downloading instruments from API..."));
            statusDialog.setSize(300, 100);
            statusDialog.setLocationRelativeTo(this);

            // Create a worker thread to avoid freezing UI
            SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    // Force refresh of index instruments
                    instrumentService.forceRefreshIndexInstruments();
                    return null;
                }

                @Override
                protected void done() {
                    // Close the dialog
                    statusDialog.dispose();

                    // Reload UI components
                    loadIndexSymbols();

                    // Show success message
                    JOptionPane.showMessageDialog(OrderEntryUI.this,
                            "Successfully refreshed instruments.\nAvailable indices and expiry dates have been updated.",
                            "Refresh Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            };

            // Start the worker
            worker.execute();

            // Show the dialog (will block until disposed by the worker)
            statusDialog.setVisible(true);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error refreshing instruments: " + e.getMessage(),
                    "Refresh Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Display the order entry UI
     */
    public static void showOrderEntryUI() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            OrderEntryUI orderEntryUI = new OrderEntryUI();
            orderEntryUI.setVisible(true);
        });
    }
}