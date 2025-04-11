package com.optiontrading.di;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * ServiceRegistry - A lightweight dependency injection container for the
 * application.
 * 
 * This registry manages both singleton services (shared instances) and
 * factories
 * for components that need multiple instances.
 */
public class ServiceRegistry {
    private static final Logger LOGGER = Logger.getLogger(ServiceRegistry.class.getName());

    // Maps for storing services and factories
    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();
    private final Map<Class<?>, Supplier<?>> factories = new ConcurrentHashMap<>();

    /**
     * Register a singleton service by its interface type
     * 
     * @param <T>      The service interface type
     * @param type     The class object for the interface
     * @param instance The singleton instance
     */
    public <T> void registerSingleton(Class<T> type, T instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Cannot register null instance for " + type.getName());
        }

        LOGGER.info("Registering singleton for " + type.getName());
        singletons.put(type, instance);
    }

    /**
     * Register a factory for creating new instances of a service
     * 
     * @param <T>     The service interface type
     * @param type    The class object for the interface
     * @param factory A supplier function that creates new instances
     */
    public <T> void registerFactory(Class<T> type, Supplier<T> factory) {
        if (factory == null) {
            throw new IllegalArgumentException("Cannot register null factory for " + type.getName());
        }

        LOGGER.info("Registering factory for " + type.getName());
        factories.put(type, factory);
    }

    /**
     * Get a singleton service by its interface type
     * 
     * @param <T>  The service interface type
     * @param type The class object for the interface
     * @return The singleton instance
     * @throws IllegalStateException if the service is not registered
     */
    @SuppressWarnings("unchecked")
    public <T> T getSingleton(Class<T> type) {
        Object service = singletons.get(type);

        if (service == null) {
            throw new IllegalStateException("No singleton registered for " + type.getName());
        }

        return (T) service;
    }

    /**
     * Create a new instance using a registered factory
     * 
     * @param <T>  The component type
     * @param type The class object for the interface
     * @return A new instance
     * @throws IllegalStateException if no factory is registered for this type
     */
    @SuppressWarnings("unchecked")
    public <T> T createInstance(Class<T> type) {
        Supplier<?> factory = factories.get(type);

        if (factory == null) {
            throw new IllegalStateException("No factory registered for " + type.getName());
        }

        return (T) factory.get();
    }

    /**
     * Check if a singleton service is registered
     * 
     * @param type The class object for the interface
     * @return true if registered, false otherwise
     */
    public boolean hasSingleton(Class<?> type) {
        return singletons.containsKey(type);
    }

    /**
     * Check if a factory is registered
     * 
     * @param type The class object for the interface
     * @return true if registered, false otherwise
     */
    public boolean hasFactory(Class<?> type) {
        return factories.containsKey(type);
    }

    /**
     * Clear all registered services and factories
     */
    public void clear() {
        singletons.clear();
        factories.clear();
    }
}