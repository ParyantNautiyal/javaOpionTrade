package com.optiontrading.service.position;

import com.optiontrading.events.EventBus;
import com.optiontrading.service.market.MarketDataProvider;
import com.optiontrading.service.market.MarketDataService;
import com.optiontrading.service.market.MarketDataSubscriber;
import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.OrderType;
import com.optiontrading.service.trading.TradingService;
import com.google.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for managing watched positions
 * Implements dependency injection and subscribes to market data
 */
public class PositionWatchlistService implements MarketDataSubscriber {
    private static final Logger LOGGER = Logger.getLogger(PositionWatchlistService.class.getName());

    // Map of position ID to position
    private final Map<String, WatchedPosition> positions = new ConcurrentHashMap<>();

    // Market data provider, event bus, and trading service
    private final MarketDataService marketDataService;
    private final EventBus eventBus;
    private final TradingService tradingService;
    private final PositionRepository positionRepository;

    // Set of instrument IDs being monitored
    private final Map<String, List<String>> instrumentToPositionMap = new ConcurrentHashMap<>();

    /**
     * Constructor with dependency injection
     */
    @Inject
    public PositionWatchlistService(MarketDataService marketDataService,
            EventBus eventBus,
            TradingService tradingService,
            PositionRepository positionRepository) {
        LOGGER.info("Initializing PositionWatchlistService");

        // Initialize services
        this.marketDataService = marketDataService;
        this.eventBus = eventBus;
        this.tradingService = tradingService;
        this.positionRepository = positionRepository;

        // Load positions from repository if available
        if (this.positionRepository != null) {
            for (WatchedPosition position : positionRepository.getActivePositions()) {
                addPositionWithoutPersisting(position);
            }
            LOGGER.info("Initialized PositionWatchlistService with " + positions.size() + " active positions");
        } else {
            LOGGER.warning("PositionWatchlistService initialized without repository connection");
        }
    }

    /**
     * Add a position to the watchlist
     * 
     * @param position the position to add
     * @return true if the position was added
     */
    public boolean addPosition(WatchedPosition position) {
        if (position == null || position.getInstrument() == null) {
            LOGGER.warning("Cannot add null position or position with null instrument");
            return false;
        }

        String positionId = position.getId();
        if (positions.containsKey(positionId)) {
            LOGGER.warning("Position with ID " + positionId + " already exists");
            return false;
        }

        // Save to repository first
        positionRepository.savePosition(position);

        // Then add to in-memory map and subscribe
        return addPositionWithoutPersisting(position);
    }

    /**
     * Add a position to the watchlist without persisting it
     * Used internally when loading from repository
     * 
     * @param position the position to add
     * @return true if the position was added
     */
    private boolean addPositionWithoutPersisting(WatchedPosition position) {
        if (position == null || position.getInstrument() == null) {
            return false;
        }

        String positionId = position.getId();

        // Add to positions map
        positions.put(positionId, position);

        // Set up market data subscription
        String instrumentId = position.getInstrument().getInstrumentId();
        instrumentToPositionMap.computeIfAbsent(instrumentId, k -> new ArrayList<>()).add(positionId);

        // Subscribe to market data for this instrument if not already subscribed
        marketDataService.subscribe(Collections.singletonList(instrumentId), this);

        LOGGER.info("Added position to watchlist: " + position);

        // Publish event
        eventBus.publishAsync(new PositionAddedEvent(position));

        return true;
    }

    /**
     * Remove a position from the watchlist
     * 
     * @param positionId the position ID to remove
     * @return the removed position, or null if not found
     */
    public WatchedPosition removePosition(String positionId) {
        // First remove from repository
        WatchedPosition position = positionRepository.removePosition(positionId);

        if (position == null) {
            position = positions.remove(positionId);
        } else {
            // Also remove from in-memory map
            positions.remove(positionId);
        }

        if (position == null) {
            LOGGER.warning("Position with ID " + positionId + " not found for removal");
            return null;
        }

        // Remove from instrument map
        String instrumentId = position.getInstrument().getInstrumentId();
        List<String> positionIds = instrumentToPositionMap.get(instrumentId);

        if (positionIds != null) {
            positionIds.remove(positionId);

            // If no more positions for this instrument, unsubscribe
            if (positionIds.isEmpty()) {
                instrumentToPositionMap.remove(instrumentId);
                marketDataService.unsubscribe(Collections.singletonList(instrumentId), this);
            }
        }

        LOGGER.info("Removed position from watchlist: " + position);

        // Publish event
        eventBus.publishAsync(new PositionRemovedEvent(position));

        return position;
    }

    /**
     * Get a position by ID
     * 
     * @param positionId the position ID
     * @return the position, or null if not found
     */
    public WatchedPosition getPosition(String positionId) {
        return positions.get(positionId);
    }

    /**
     * Get all positions in the watchlist
     * 
     * @return list of all positions
     */
    public List<WatchedPosition> getAllPositions() {
        return new ArrayList<>(positions.values());
    }

    /**
     * Get active positions only
     * 
     * @return list of active positions
     */
    public List<WatchedPosition> getActivePositions() {
        List<WatchedPosition> active = new ArrayList<>();

        for (WatchedPosition position : positions.values()) {
            if (position.isActive()) {
                active.add(position);
            }
        }

        return active;
    }

    /**
     * Create and add a new price trigger position
     * 
     * @param instrument   the instrument to trade
     * @param orderType    the order type (BUY/SELL)
     *                     For manual positions, this is the direction that will be
     *                     executed
     *                     when the price triggers
     * @param quantity     the quantity to trade
     * @param entryPrice   the current market price
     * @param triggerPrice target price to trigger the position
     * @return the created position
     */
    public WatchedPosition addPriceTriggerPosition(
            Instrument instrument,
            OrderType orderType,
            int quantity,
            BigDecimal entryPrice,
            BigDecimal triggerPrice) {

        WatchedPosition position = WatchedPosition.builder()
                .instrument(instrument)
                .orderType(orderType)
                .quantity(quantity)
                .entryPrice(entryPrice)
                .entryTime(LocalDateTime.now())
                .source(PositionSource.MANUAL)
                .triggerPrice(triggerPrice)
                .status(PositionStatus.ACTIVE)
                .build();

        addPosition(position);
        return position;
    }

    /**
     * Create and add a new position with stop loss
     * 
     * @param instrument         the instrument to trade
     * @param orderType          the order type (BUY/SELL)
     *                           For automated positions, this will execute the
     *                           opposite order
     *                           when the stop loss is triggered
     * @param quantity           the quantity to trade
     * @param entryPrice         the current market price
     * @param stopLossPercentage the stop loss percentage
     * @param moveToBreakeven    whether to move stop to breakeven after sufficient
     *                           profit
     * @param trailingStopLoss   whether to use a trailing stop
     * @param trailingDistance   the distance for trailing stop (percentage)
     * @return the created position
     */
    public WatchedPosition addStopLossPosition(
            Instrument instrument,
            OrderType orderType,
            int quantity,
            BigDecimal entryPrice,
            BigDecimal stopLossPercentage,
            boolean moveToBreakeven,
            boolean trailingStopLoss,
            BigDecimal trailingDistance) {

        // Calculate initial stop price
        BigDecimal stopAmount = entryPrice.multiply(stopLossPercentage).divide(new BigDecimal(100));
        BigDecimal initialStopPrice;

        if (orderType == OrderType.BUY) {
            // For long positions, stop is below entry
            initialStopPrice = entryPrice.subtract(stopAmount);
        } else {
            // For short positions, stop is above entry
            initialStopPrice = entryPrice.add(stopAmount);
        }

        WatchedPosition position = WatchedPosition.builder()
                .instrument(instrument)
                .orderType(orderType)
                .quantity(quantity)
                .entryPrice(entryPrice)
                .entryTime(LocalDateTime.now())
                .source(PositionSource.STRATEGY)
                .stopLossPercentage(stopLossPercentage)
                .moveToBreakeven(moveToBreakeven)
                .trailingStopLoss(trailingStopLoss)
                .trailingDistance(trailingDistance)
                .currentStopPrice(initialStopPrice)
                .status(PositionStatus.ACTIVE)
                .build();

        addPosition(position);
        return position;
    }

    /**
     * Implement MarketDataSubscriber interface
     * Receive price updates for subscribed instruments
     */
    @Override
    public void onPriceUpdate(String instrumentId, BigDecimal price) {
        List<String> positionIds = instrumentToPositionMap.get(instrumentId);

        if (positionIds == null || positionIds.isEmpty()) {
            return;
        }

        double priceValue = price.doubleValue();

        for (String positionId : positionIds) {
            WatchedPosition position = positions.get(positionId);

            if (position == null || !position.isActive()) {
                continue;
            }

            try {
                // Update price and P&L
                position.updatePrice(priceValue);

                // Check if we need to update high/low prices for trailing stops
                position.updateHighestPrice(price);
                position.updateLowestPrice(price);

                // Check for trigger conditions
                checkTriggerConditions(position, price);

                // Check for stop loss conditions
                checkStopLossConditions(position, price);

                // Adjust stop loss if needed
                adjustStopLoss(position, price);

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error processing price update for position " + positionId, e);
                position.updateStatus(PositionStatus.ERROR, "Error processing price update: " + e.getMessage());
            }
        }
    }

    /**
     * Check if a price trigger condition is met
     */
    private void checkTriggerConditions(WatchedPosition position, BigDecimal price) {
        // Special handling for manual trigger positions
        if (position.isPriceTrigger() && position.getSource() == PositionSource.MANUAL) {
            checkManualTriggerConditions(position, price);
            return;
        }

        // Original logic for non-manual triggers
        if (!position.isPriceTrigger() || !position.isActive()) {
            return;
        }

        BigDecimal triggerPrice = position.getTriggerPrice();
        if (triggerPrice == null) {
            return;
        }

        boolean triggered = false;

        if (position.getOrderType() == OrderType.BUY) {
            // For BUY orders, trigger when price rises above trigger price
            triggered = price.compareTo(triggerPrice) >= 0;
        } else {
            // For SELL orders, trigger when price falls below trigger price
            triggered = price.compareTo(triggerPrice) <= 0;
        }

        if (triggered) {
            position.updateStatus(PositionStatus.TRIGGERED,
                    "Price " + price + " crossed trigger price " + triggerPrice);

            // Save status change to repository
            positionRepository.savePosition(position);

            LOGGER.info("Position triggered: " + position);

            // Execute order when triggered (opposite direction for automatic positions)
            executeTriggeredOrder(position, price);
        }
    }

    /**
     * Check if a manual price trigger condition is met.
     * Manual triggers execute orders in the same direction as specified by the
     * user.
     */
    private void checkManualTriggerConditions(WatchedPosition position, BigDecimal price) {
        if (!position.isActive()) {
            return;
        }

        BigDecimal triggerPrice = position.getTriggerPrice();
        if (triggerPrice == null) {
            return;
        }

        boolean triggered = false;
        OrderType orderType = position.getOrderType();

        if (orderType == OrderType.BUY) {
            // For manual BUY triggers, activate when price is AT or ABOVE trigger price
            triggered = price.compareTo(triggerPrice) >= 0;
        } else {
            // For manual SELL triggers, activate when price is AT or BELOW trigger price
            triggered = price.compareTo(triggerPrice) <= 0;
        }

        if (triggered) {
            position.updateStatus(PositionStatus.TRIGGERED,
                    "Manual trigger: Price " + price + " crossed trigger price " + triggerPrice);

            // Save status change to repository
            positionRepository.savePosition(position);

            LOGGER.info("Manual position triggered: " + position);

            // Execute order in the SAME direction as specified
            executeManualTriggeredOrder(position, price);
        }
    }

    /**
     * Execute an order for a manually triggered position in the same direction as
     * specified
     */
    private void executeManualTriggeredOrder(WatchedPosition position, BigDecimal currentPrice) {
        Instrument instrument = position.getInstrument();

        if (instrument == null) {
            LOGGER.warning("Cannot execute manual order for position " + position.getId() + ": instrument is null");
            return;
        }

        try {
            // For manual triggers, use the SAME order type as specified (not opposite)
            OrderType executionOrderType = position.getOrderType();

            LOGGER.info("Executing MANUAL " + executionOrderType + " order for triggered position " +
                    position.getId() + " at price " + currentPrice);

            // Place the order through trading service
            String brokerId = tradingService.placeOrder(
                    instrument,
                    position.getQuantity(),
                    currentPrice, // Use current market price
                    executionOrderType,
                    "MANUAL-TRIGGER-" + position.getId());

            if (brokerId != null) {
                position.setBrokerId(brokerId);

                // Save broker ID to repository
                positionRepository.savePosition(position);

                LOGGER.info("Manual order placed successfully for triggered position " +
                        position.getId() + ", broker order ID: " + brokerId);

                // Publish event about the order execution
                eventBus.publishAsync(new PositionOrderExecutedEvent(position, executionOrderType, currentPrice));
            } else {
                LOGGER.warning("Failed to place manual order for triggered position " + position.getId());
                position.updateStatus(PositionStatus.ERROR, "Failed to place manual trigger order");
                positionRepository.savePosition(position);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing manual order for triggered position " + position.getId(), e);
            position.updateStatus(PositionStatus.ERROR, "Error executing manual trigger order: " + e.getMessage());
            positionRepository.savePosition(position);
        }
    }

    /**
     * Check if stop loss condition is met
     */
    private void checkStopLossConditions(WatchedPosition position, BigDecimal price) {
        if (!position.isStopLoss() || !position.isActive()) {
            return;
        }

        BigDecimal stopPrice = position.getCurrentStopPrice();
        if (stopPrice == null) {
            return;
        }

        boolean stopped = false;

        if (position.getOrderType() == OrderType.BUY) {
            // For BUY orders, stop when price falls below stop price
            stopped = price.compareTo(stopPrice) <= 0;
        } else {
            // For SELL orders, stop when price rises above stop price
            stopped = price.compareTo(stopPrice) >= 0;
        }

        if (stopped) {
            position.updateStatus(PositionStatus.STOPPED_OUT,
                    "Price " + price + " crossed stop price " + stopPrice);

            // Save status change to repository
            positionRepository.savePosition(position);

            LOGGER.info("Position stopped out: " + position);

            // Execute stop loss order (opposite direction for automatic positions)
            executeStopLossOrder(position, price);
        }
    }

    /**
     * Adjust stop loss based on position settings
     */
    private void adjustStopLoss(WatchedPosition position, BigDecimal price) {
        if (!position.isStopLoss() || !position.isActive()) {
            return;
        }

        // Breakeven adjustment
        if (position.isMoveToBreakeven()) {
            moveStopToBreakeven(position, price);
        }

        // Trailing stop adjustment
        if (position.isTrailingStopLoss()) {
            adjustTrailingStop(position);
        }
    }

    /**
     * Move stop loss to breakeven if profit is sufficient
     */
    private void moveStopToBreakeven(WatchedPosition position, BigDecimal currentPrice) {
        BigDecimal entryPrice = position.getEntryPrice();
        BigDecimal stopPrice = position.getCurrentStopPrice();
        BigDecimal trailingDistance = position.getTrailingDistance();

        if (trailingDistance == null || stopPrice == null) {
            return;
        }

        // Check if we haven't already moved to breakeven and have sufficient profit
        if (position.getOrderType() == OrderType.BUY) {
            // For long positions
            if (stopPrice.compareTo(entryPrice) < 0 && // Stop is still below entry
                    currentPrice.subtract(entryPrice).compareTo(trailingDistance.multiply(new BigDecimal(2))) > 0) { // Sufficient
                                                                                                                     // profit
                                                                                                                     // (2x
                                                                                                                     // trailing
                                                                                                                     // distance)

                position.setCurrentStopPrice(entryPrice);
                LOGGER.info("Moved stop to breakeven for position: " + position);
            }
        } else {
            // For short positions
            if (stopPrice.compareTo(entryPrice) > 0 && // Stop is still above entry
                    entryPrice.subtract(currentPrice).compareTo(trailingDistance.multiply(new BigDecimal(2))) > 0) { // Sufficient
                                                                                                                     // profit
                                                                                                                     // (2x
                                                                                                                     // trailing
                                                                                                                     // distance)

                position.setCurrentStopPrice(entryPrice);
                LOGGER.info("Moved stop to breakeven for position: " + position);
            }
        }
    }

    /**
     * Adjust trailing stop based on highest/lowest seen prices
     */
    private void adjustTrailingStop(WatchedPosition position) {
        if (position.getOrderType() == OrderType.BUY) {
            // For long positions, adjust based on highest price seen
            BigDecimal highestSeen = position.getHighestSeen();
            BigDecimal trailingDistance = position.getTrailingDistance();
            BigDecimal currentStop = position.getCurrentStopPrice();

            if (highestSeen != null && trailingDistance != null && currentStop != null) {
                BigDecimal newStop = highestSeen.subtract(trailingDistance);

                // Only move stop up, never down
                if (newStop.compareTo(currentStop) > 0) {
                    position.setCurrentStopPrice(newStop);
                    LOGGER.fine("Adjusted trailing stop for position " + position.getId() +
                            " to " + newStop + " based on highest price " + highestSeen);
                }
            }
        } else {
            // For short positions, adjust based on lowest price seen
            BigDecimal lowestSeen = position.getLowestSeen();
            BigDecimal trailingDistance = position.getTrailingDistance();
            BigDecimal currentStop = position.getCurrentStopPrice();

            if (lowestSeen != null && trailingDistance != null && currentStop != null) {
                BigDecimal newStop = lowestSeen.add(trailingDistance);

                // Only move stop down, never up
                if (newStop.compareTo(currentStop) < 0) {
                    position.setCurrentStopPrice(newStop);
                    LOGGER.fine("Adjusted trailing stop for position " + position.getId() +
                            " to " + newStop + " based on lowest price " + lowestSeen);
                }
            }
        }
    }

    /**
     * Execute an order when a price trigger is hit
     * 
     * @param position     the triggered position
     * @param currentPrice the current price
     */
    private void executeTriggeredOrder(WatchedPosition position, BigDecimal currentPrice) {
        Instrument instrument = position.getInstrument();

        if (instrument == null) {
            LOGGER.warning("Cannot execute order for position " + position.getId() + ": instrument is null");
            return;
        }

        try {
            // Determine order type (opposite of position type for closing)
            OrderType executionOrderType = (position.getOrderType() == OrderType.BUY)
                    ? OrderType.SELL
                    : OrderType.BUY;

            LOGGER.info("Executing " + executionOrderType + " order for triggered position " +
                    position.getId() + " at price " + currentPrice);

            // Place the order through trading service
            String brokerId = tradingService.placeOrder(
                    instrument,
                    position.getQuantity(),
                    currentPrice, // Use current market price
                    executionOrderType,
                    "TRIGGER-" + position.getId());

            if (brokerId != null) {
                position.setBrokerId(brokerId);

                // Save broker ID to repository
                positionRepository.savePosition(position);

                LOGGER.info("Order placed successfully for triggered position " +
                        position.getId() + ", broker order ID: " + brokerId);

                // Publish event about the order execution
                eventBus.publishAsync(new PositionOrderExecutedEvent(position, executionOrderType, currentPrice));
            } else {
                LOGGER.warning("Failed to place order for triggered position " + position.getId());
                position.updateStatus(PositionStatus.ERROR, "Failed to place trigger order");
                positionRepository.savePosition(position);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing order for triggered position " + position.getId(), e);
            position.updateStatus(PositionStatus.ERROR, "Error executing trigger order: " + e.getMessage());
            positionRepository.savePosition(position);
        }
    }

    /**
     * Execute a stop loss order
     * 
     * @param position     the stopped out position
     * @param currentPrice the current price
     */
    private void executeStopLossOrder(WatchedPosition position, BigDecimal currentPrice) {
        Instrument instrument = position.getInstrument();

        if (instrument == null) {
            LOGGER.warning("Cannot execute stop loss for position " + position.getId() + ": instrument is null");
            return;
        }

        try {
            // Determine order type (opposite of position type for closing)
            OrderType executionOrderType = (position.getOrderType() == OrderType.BUY)
                    ? OrderType.SELL
                    : OrderType.BUY;

            LOGGER.info("Executing stop loss " + executionOrderType + " order for position " +
                    position.getId() + " at price " + currentPrice);

            // Place the order through trading service
            String brokerId = tradingService.placeOrder(
                    instrument,
                    position.getQuantity(),
                    currentPrice, // Use current market price
                    executionOrderType,
                    "STOPLOSS-" + position.getId());

            if (brokerId != null) {
                position.setBrokerId(brokerId);
                LOGGER.info("Stop loss order placed successfully for position " +
                        position.getId() + ", broker order ID: " + brokerId);

                // Calculate the P&L for logging
                double entryValue = position.getEntryPrice().doubleValue() * position.getQuantity();
                double exitValue = currentPrice.doubleValue() * position.getQuantity();
                double pnl = position.getOrderType() == OrderType.BUY ? (exitValue - entryValue)
                        : (entryValue - exitValue);

                LOGGER.info("Position " + position.getId() + " stopped out with P&L: " +
                        String.format("%.2f", pnl));

                // Publish event about the order execution
                eventBus.publishAsync(new PositionOrderExecutedEvent(position, executionOrderType, currentPrice));
            } else {
                LOGGER.warning("Failed to place stop loss order for position " + position.getId());
                position.updateStatus(PositionStatus.ERROR, "Failed to place stop loss order");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing stop loss for position " + position.getId(), e);
            position.updateStatus(PositionStatus.ERROR, "Error executing stop loss: " + e.getMessage());
        }
    }

    /**
     * Close a position manually
     * 
     * @param positionId the position ID to close
     * @param reason     the reason for closing
     * @return true if position was closed
     */
    public boolean closePosition(String positionId, String reason) {
        WatchedPosition position = positions.get(positionId);

        if (position == null) {
            LOGGER.warning("Position with ID " + positionId + " not found for closing");
            return false;
        }

        if (!position.isActive()) {
            LOGGER.warning("Cannot close position with ID " + positionId + " as it is not active");
            return false;
        }

        try {
            // Get current market price
            Instrument instrument = position.getInstrument();
            String instrumentId = instrument.getInstrumentId();
            BigDecimal currentPrice = marketDataService.getLastPrice(instrumentId);

            if (currentPrice == null) {
                LOGGER.warning("Cannot close position " + positionId + ": no current price available");
                position.updateStatus(PositionStatus.ERROR, "Cannot close: no market price available");
                positionRepository.savePosition(position);
                return false;
            }

            // Determine order type (opposite of position type for closing)
            OrderType executionOrderType = (position.getOrderType() == OrderType.BUY)
                    ? OrderType.SELL
                    : OrderType.BUY;

            LOGGER.info("Executing manual close " + executionOrderType + " order for position " +
                    positionId + " at price " + currentPrice);

            // Place the order through trading service
            String brokerId = tradingService.placeOrder(
                    instrument,
                    position.getQuantity(),
                    currentPrice, // Use current market price
                    executionOrderType,
                    "MANUAL-" + positionId);

            if (brokerId != null) {
                position.setBrokerId(brokerId);
                position.updateStatus(PositionStatus.CLOSED, reason);

                // Save to repository
                positionRepository.savePosition(position);

                LOGGER.info("Manually closed position: " + position + ", broker order ID: " + brokerId);

                // Calculate the P&L for logging
                double entryValue = position.getEntryPrice().doubleValue() * position.getQuantity();
                double exitValue = currentPrice.doubleValue() * position.getQuantity();
                double pnl = position.getOrderType() == OrderType.BUY ? (exitValue - entryValue)
                        : (entryValue - exitValue);

                LOGGER.info("Position " + positionId + " closed with P&L: " +
                        String.format("%.2f", pnl));

                // Publish event about the order execution
                eventBus.publishAsync(new PositionOrderExecutedEvent(position, executionOrderType, currentPrice));

                return true;
            } else {
                LOGGER.warning("Failed to place close order for position " + positionId);
                position.updateStatus(PositionStatus.ERROR, "Failed to place close order");
                positionRepository.savePosition(position);
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error closing position " + positionId, e);
            position.updateStatus(PositionStatus.ERROR, "Error closing position: " + e.getMessage());
            positionRepository.savePosition(position);
            return false;
        }
    }

    /**
     * Shutdown the service
     */
    public void shutdown() {
        LOGGER.info("Shutting down PositionWatchlistService, closing " + positions.size() + " positions");

        // Close all active positions with appropriate reason
        for (WatchedPosition position : positions.values()) {
            if (position.isActive()) {
                position.updateStatus(PositionStatus.CLOSED, "Service shutdown");
                positionRepository.savePosition(position);
            }
        }

        // Clear collections
        positions.clear();
        instrumentToPositionMap.clear();
    }

    /**
     * Force refresh of all positions with latest market data
     * This method will fetch the latest prices and update all positions
     */
    public void refreshPositions() {
        LOGGER.info("Refreshing all active positions with latest prices");

        // Get all active positions
        List<WatchedPosition> activePositions = getActivePositions();

        if (activePositions.isEmpty()) {
            LOGGER.info("No active positions to refresh");
            return;
        }

        // Build a list of instruments to refresh
        List<String> instrumentIds = new ArrayList<>();
        Map<String, List<String>> instrumentToPositionsMap = new HashMap<>();

        for (WatchedPosition position : activePositions) {
            if (position.getInstrument() != null) {
                String instrumentId = position.getInstrument().getInstrumentId();
                instrumentIds.add(instrumentId);

                // Group positions by instrument ID for processing
                instrumentToPositionsMap.computeIfAbsent(instrumentId, k -> new ArrayList<>())
                        .add(position.getId());
            }
        }

        if (instrumentIds.isEmpty()) {
            return;
        }

        // Refresh prices through market data service
        try {
            Map<String, BigDecimal> prices = marketDataService.refreshPrices(instrumentIds);

            LOGGER.info("Refreshed prices for " + prices.size() + " instruments");

            // Process each price update - this will trigger the normal price update flow
            // through the MarketDataSubscriber interface (onPriceUpdate method)
            for (Map.Entry<String, BigDecimal> entry : prices.entrySet()) {
                String instrumentId = entry.getKey();
                BigDecimal price = entry.getValue();

                // The onPriceUpdate method will handle updating positions
                // as this class implements MarketDataSubscriber
                onPriceUpdate(instrumentId, price);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error refreshing position prices", e);
        }
    }
}