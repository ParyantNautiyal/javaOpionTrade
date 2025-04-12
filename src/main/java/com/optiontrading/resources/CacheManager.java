package com.optiontrading.resources;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.function.Function;

/**
 * Centralized manager for all cache instances in the application.
 * Provides bounded cache implementations with eviction policies.
 */
public class CacheManager {
    private static final Logger LOGGER = Logger.getLogger(CacheManager.class.getName());
    private static final CacheManager INSTANCE = new CacheManager();

    // Track all created caches
    private final Map<String, BoundedCache<?>> managedCaches = new ConcurrentHashMap<>();

    // Map of cache name to cache
    private final Map<String, Map<Object, Object>> caches = new ConcurrentHashMap<>();

    // Private constructor for singleton pattern
    private CacheManager() {
        LOGGER.info("Initialized CacheManager");
    }

    /**
     * Get the singleton instance of the CacheManager
     */
    public static CacheManager getInstance() {
        return INSTANCE;
    }

    /**
     * Creates a new bounded cache with the specified name, maximum size, and TTL
     * 
     * @param <K>       Key type
     * @param <V>       Value type
     * @param name      The name to identify this cache
     * @param maxSize   The maximum number of entries in the cache
     * @param ttlMillis The time-to-live for cache entries in milliseconds
     * @return A BoundedCache instance
     */
    public <K, V> BoundedCache<V> createCache(String name, int maxSize, long ttlMillis) {
        @SuppressWarnings("unchecked")
        BoundedCache<V> cache = (BoundedCache<V>) managedCaches.computeIfAbsent(name, k -> {
            LOGGER.info("Creating new cache: " + name + " (max size: " + maxSize + ", TTL: " + ttlMillis + "ms)");
            return new BoundedCache<V>(name, maxSize, ttlMillis);
        });
        return cache;
    }

    /**
     * Creates a new bounded cache with the specified name and maximum size
     * 
     * @param <K>     Key type
     * @param <V>     Value type
     * @param name    The name to identify this cache
     * @param maxSize The maximum number of entries in the cache
     * @return A BoundedCache instance
     */
    public <K, V> BoundedCache<V> createCache(String name, int maxSize) {
        return createCache(name, maxSize, -1); // No TTL
    }

    /**
     * Get an existing cache by name
     * 
     * @param <V>  Value type
     * @param name The name of the cache
     * @return The cache, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <V> BoundedCache<V> getCache(String name) {
        return (BoundedCache<V>) managedCaches.get(name);
    }

    /**
     * Check if a cache exists
     * 
     * @param name the name of the cache
     * @return true if the cache exists, false otherwise
     */
    public boolean hasCache(String name) {
        return caches.containsKey(name);
    }

    /**
     * Clear a cache with the specified name
     * 
     * @param name the name of the cache
     * @return true if the cache was cleared, false if it didn't exist
     */
    public boolean clearCache(String name) {
        Map<Object, Object> cache = caches.get(name);
        if (cache != null) {
            cache.clear();
            LOGGER.info("Cleared cache '" + name + "'");
            return true;
        }
        return false;
    }

    /**
     * Remove a cache with the specified name
     * 
     * @param name the name of the cache
     * @return true if the cache was removed, false if it didn't exist
     */
    public boolean removeCache(String name) {
        Map<Object, Object> cache = caches.remove(name);
        if (cache != null) {
            LOGGER.info("Removed cache '" + name + "'");
            return true;
        }
        return false;
    }

    /**
     * Clear all caches
     */
    public void clearAllCaches() {
        LOGGER.info("Clearing all caches (" + managedCaches.size() + " active)");
        for (BoundedCache<?> cache : managedCaches.values()) {
            cache.clear();
        }
    }

    /**
     * Returns metrics about the current cache usage
     * 
     * @return String representation of cache metrics
     */
    public String getCacheMetrics() {
        StringBuilder metrics = new StringBuilder();
        metrics.append("Active caches: ").append(managedCaches.size()).append("\n");

        for (Map.Entry<String, BoundedCache<?>> entry : managedCaches.entrySet()) {
            BoundedCache<?> cache = entry.getValue();
            metrics.append(" - ").append(entry.getKey())
                    .append(" (size: ").append(cache.size())
                    .append("/").append(cache.getMaxSize())
                    .append(", created: ").append(cache.getCreationTime())
                    .append(", hit rate: ").append(String.format("%.2f", cache.getHitRate() * 100)).append("%")
                    .append(")\n");
        }

        return metrics.toString();
    }

    /**
     * Returns the number of active caches
     * 
     * @return The count of active caches
     */
    public int getCacheCount() {
        return managedCaches.size();
    }

    /**
     * Shutdown all caches
     */
    public void shutdown() {
        LOGGER.info("Shutting down all caches");

        for (String name : caches.keySet()) {
            Map<Object, Object> cache = caches.get(name);
            if (cache != null) {
                cache.clear();
                LOGGER.info("Cleared cache '" + name + "'");
            }
        }

        caches.clear();
        LOGGER.info("All caches shutdown");
    }

    /**
     * Implementation of a bounded cache with eviction policies
     */
    public static class BoundedCache<V> {
        private final String name;
        private final int maxSize;
        private final long ttlMillis;
        private final long creationTime;

        // Cache statistics
        private long hits = 0;
        private long misses = 0;

        // The actual cache storage
        private final Map<Object, CacheEntry<V>> cache = new ConcurrentHashMap<>();

        public BoundedCache(String name, int maxSize, long ttlMillis) {
            this.name = name;
            this.maxSize = maxSize;
            this.ttlMillis = ttlMillis;
            this.creationTime = System.currentTimeMillis();
        }

        /**
         * Get a value from the cache, or compute it if not present
         * 
         * @param key             The cache key
         * @param mappingFunction Function to compute the value if not present
         * @return The cached or computed value
         */
        public V computeIfAbsent(Object key, Function<Object, V> mappingFunction) {
            // Clean expired entries if TTL is enabled
            if (ttlMillis > 0) {
                cleanExpiredEntries();
            }

            // Check if the key exists in the cache
            CacheEntry<V> entry = cache.get(key);
            if (entry != null && !isExpired(entry)) {
                hits++;
                return entry.getValue();
            }

            // Key not in cache, compute the value
            misses++;
            V value = mappingFunction.apply(key);

            // If at capacity, don't add new entry
            if (cache.size() >= maxSize && !cache.containsKey(key)) {
                return value;
            }

            // Store the new value
            cache.put(key, new CacheEntry<>(value, ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : -1));
            return value;
        }

        /**
         * Get a value from the cache
         * 
         * @param key The cache key
         * @return The cached value, or null if not present
         */
        public V get(Object key) {
            CacheEntry<V> entry = cache.get(key);
            if (entry != null && !isExpired(entry)) {
                hits++;
                return entry.getValue();
            }

            // Remove expired entry if found
            if (entry != null && isExpired(entry)) {
                cache.remove(key);
            }

            misses++;
            return null;
        }

        /**
         * Put a value in the cache
         * 
         * @param key   The cache key
         * @param value The value to cache
         */
        public void put(Object key, V value) {
            // If at capacity and this is a new key, don't add
            if (cache.size() >= maxSize && !cache.containsKey(key)) {
                return;
            }

            cache.put(key, new CacheEntry<>(value, ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : -1));
        }

        /**
         * Remove a value from the cache
         * 
         * @param key The cache key
         * @return The removed value, or null if not present
         */
        public V remove(Object key) {
            CacheEntry<V> entry = cache.remove(key);
            return entry != null ? entry.getValue() : null;
        }

        /**
         * Check if a key is in the cache
         * 
         * @param key The cache key
         * @return true if the key is in the cache and not expired
         */
        public boolean containsKey(Object key) {
            CacheEntry<V> entry = cache.get(key);
            if (entry != null && !isExpired(entry)) {
                return true;
            }

            // Remove expired entry if found
            if (entry != null && isExpired(entry)) {
                cache.remove(key);
            }

            return false;
        }

        /**
         * Get the size of the cache
         * 
         * @return The number of entries in the cache
         */
        public int size() {
            if (ttlMillis > 0) {
                // For TTL caches, count only non-expired entries
                return (int) cache.entrySet().stream()
                        .filter(e -> !isExpired(e.getValue()))
                        .count();
            }
            return cache.size();
        }

        /**
         * Clear the cache
         */
        public void clear() {
            cache.clear();
        }

        /**
         * Get the maximum size of the cache
         * 
         * @return The maximum number of entries in the cache
         */
        public int getMaxSize() {
            return maxSize;
        }

        /**
         * Get the creation time of the cache
         * 
         * @return The creation time in milliseconds
         */
        public long getCreationTime() {
            return creationTime;
        }

        /**
         * Get the hit rate of the cache
         * 
         * @return The hit rate as a number between 0 and 1
         */
        public double getHitRate() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0;
        }

        /**
         * Get the TTL of the cache
         * 
         * @return The TTL in milliseconds, or -1 if no TTL
         */
        public long getTtlMillis() {
            return ttlMillis;
        }

        /**
         * Check if a cache entry is expired
         * 
         * @param entry The cache entry
         * @return true if the entry is expired
         */
        private boolean isExpired(CacheEntry<V> entry) {
            return entry.getExpirationTime() > 0 && System.currentTimeMillis() > entry.getExpirationTime();
        }

        /**
         * Clean expired entries from the cache
         */
        private void cleanExpiredEntries() {
            if (ttlMillis <= 0) {
                return;
            }

            long now = System.currentTimeMillis();
            cache.entrySet()
                    .removeIf(e -> e.getValue().getExpirationTime() > 0 && now > e.getValue().getExpirationTime());
        }
    }

    /**
     * Internal class to represent a cache entry with expiration
     */
    private static class CacheEntry<V> {
        private final V value;
        private final long expirationTime;

        public CacheEntry(V value, long expirationTime) {
            this.value = value;
            this.expirationTime = expirationTime;
        }

        public V getValue() {
            return value;
        }

        public long getExpirationTime() {
            return expirationTime;
        }
    }
}