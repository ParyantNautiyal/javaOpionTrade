package com.optiontrading;

import com.optiontrading.utils.TradeLogManager;

public class OptionTradingApp {

    public static void main(String[] args) {
        // Initialize trade logging
        TradeLogManager.initialize();

        // ... existing code ...
    }

    public static void shutdown() {
        // ... existing shutdown code ...

        // Shutdown trade logging
        TradeLogManager.shutdown();
    }
}