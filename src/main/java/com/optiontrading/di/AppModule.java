package com.optiontrading.di;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

import com.optiontrading.config.ConfigurationManager;
import com.optiontrading.events.EventBus;
import com.optiontrading.resources.CacheManager;
import com.optiontrading.resources.ResourceManager;
import com.optiontrading.resources.ThreadManager;
import com.optiontrading.resources.TimerManager;
import com.optiontrading.service.api.KiteConnectClient;
import com.optiontrading.service.api.TradingApiClient;
import com.optiontrading.service.auth.AuthService;
import com.optiontrading.service.auth.KiteAuthService;
import com.optiontrading.service.instrument.InstrumentService;
import com.optiontrading.service.market.MarketDataProvider;
import com.optiontrading.service.market.MarketDataService;
import com.optiontrading.service.order.MainOrderPlacedEventHandler;
import com.optiontrading.service.order.OrderExecutionCoordinator;
import com.optiontrading.service.order.OrderLoggerService;
import com.optiontrading.service.order.OrderRepository;
import com.optiontrading.service.position.PositionRepository;
import com.optiontrading.service.position.PositionWatchlistService;
import com.optiontrading.service.trading.TradingService;
import com.optiontrading.service.option.OptionChainService;

/**
 * Main Guice module that configures all application dependencies.
 */
public class AppModule extends AbstractModule {

    @Override
    protected void configure() {
        // Configuration
        bind(ConfigurationManager.class).in(Singleton.class);

        // Resource bindings - all now using DI
        bind(ThreadManager.class).in(Singleton.class);
        bind(TimerManager.class).in(Singleton.class);
        bind(CacheManager.class).in(Singleton.class);
        bind(ResourceManager.class).in(Singleton.class);

        // Core service bindings
        bind(EventBus.class).in(Singleton.class);

        // Auth services
        bind(AuthService.class).to(KiteAuthService.class).in(Singleton.class);

        // API services
        bind(TradingApiClient.class).to(KiteConnectClient.class).in(Singleton.class);

        // Market data services
        bind(MarketDataService.class).to(MarketDataProvider.class).in(Singleton.class);

        // Instrument services
        bind(InstrumentService.class).in(Singleton.class);

        // Order services
        bind(OrderLoggerService.class).in(Singleton.class);
        bind(OrderRepository.class).in(Singleton.class);
        bind(OrderExecutionCoordinator.class).in(Singleton.class);
        bind(MainOrderPlacedEventHandler.class).in(Singleton.class);

        // Trading services
        bind(TradingService.class).in(Singleton.class);

        // Position services
        bind(PositionRepository.class).in(Singleton.class);
        bind(PositionWatchlistService.class).in(Singleton.class);

        // Option services
        bind(OptionChainService.class).in(Singleton.class);
    }
}