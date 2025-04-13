package com.optiontrading.utils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility to generate sample trade logs for testing purposes
 */
public class TradeLogTester {
    private static final Logger LOGGER = Logger.getLogger(TradeLogTester.class.getName());
    private static final Random random = new Random();

    /**
     * Generate a set of sample trade logs
     */
    public static void generateSampleLogs(boolean minimalMode) {
        try {
            // Initialize the trade log system
            TradeLogManager.initialize();

            // Sample events
            LOGGER.info("Generating sample trade logs" + (minimalMode ? " (minimal mode)" : ""));

            // Generate option service logs
            Logger optionChainLogger = Logger.getLogger("com.optiontrading.service.option.OptionChainService");

            // Option calculations
            for (int i = 0; i < (minimalMode ? 3 : 10); i++) {
                String strikePrice = String.valueOf(17000 + (random.nextInt(20) * 100));
                BigDecimal callPrice = BigDecimal.valueOf(random.nextInt(300) + 100);
                BigDecimal putPrice = BigDecimal.valueOf(random.nextInt(300) + 100);
                BigDecimal difference = BigDecimal.valueOf(random.nextInt(100));

                optionChainLogger.info("Finding best option pair for order ORDER-" + (1000 + i) +
                        " with target premium " + (callPrice.add(putPrice)) +
                        " - monitoring " + (50 + random.nextInt(50)) + " instruments");

                optionChainLogger.info("New best pair found: NIFTY25417" + strikePrice + "CE@" +
                        callPrice + " / NIFTY25417" + strikePrice + "PE@" + putPrice +
                        " - difference: " + difference);
            }

            // Generate order coordinator logs if not in minimal mode
            if (!minimalMode) {
                Logger orderLogger = Logger.getLogger("com.optiontrading.service.order.OrderExecutionCoordinator");

                for (int i = 0; i < 5; i++) {
                    String orderId = "ORDER-" + (1000 + i);

                    orderLogger.info("Starting execution for order: " + orderId);
                    orderLogger.info("Scheduling hedge orders for order: " + orderId);
                    orderLogger.info("Executing hedge orders for order: " + orderId);

                    // Simulate some delay
                    Thread.sleep(100);

                    orderLogger.info("Hedge orders executed successfully for order: " + orderId);
                    orderLogger.info("Executing main order for: " + orderId);

                    // Simulate some delay
                    Thread.sleep(100);

                    orderLogger.info("Publishing MainOrderPlacedEvent for order: " + orderId);
                    orderLogger.info("MainOrderPlacedEvent published successfully");
                    orderLogger.info("Cleaned up resources for order: " + orderId);
                }
            }

            // Add some direct trade events
            TradeLogManager.logTradeEvent("Test completed at: " + new Date());

            LOGGER.info("Sample trade logs generated successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating sample logs", e);
        } finally {
            // Clean up
            TradeLogManager.shutdown();
        }
    }

    /**
     * Main method to run the test
     */
    public static void main(String[] args) {
        boolean minimalMode = (args.length > 0 && args[0].equalsIgnoreCase("minimal"));
        generateSampleLogs(minimalMode);
    }
}