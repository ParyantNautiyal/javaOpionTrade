package com.optiontrading.resources;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Centralized manager for all thread pools and ExecutorServices in the
 * application.
 * Prevents thread leaks by tracking creation and ensuring proper cleanup.
 */
@Singleton
public class ThreadManager {
    private static final Logger LOGGER = Logger.getLogger(ThreadManager.class.getName());

    // Default thread pool configurations
    private static final int DEFAULT_CORE_POOL_SIZE = 2;
    private static final int DEFAULT_MAX_POOL_SIZE = 4;
    private static final long DEFAULT_KEEP_ALIVE_TIME = 60L;

    // Track all created executor services
    private final Map<String, ManagedExecutor> managedExecutors = new ConcurrentHashMap<>();

    /**
     * Constructor with dependency injection
     */
    @Inject
    public ThreadManager() {
        LOGGER.info("ThreadManager initialized with dependency injection");
    }

    /**
     * Creates a fixed size thread pool with the specified name or returns an
     * existing one.
     * 
     * @param name     The name to identify this thread pool
     * @param nThreads The number of threads in the pool
     * @return A managed ExecutorService
     */
    public ExecutorService createFixedThreadPool(String name, int nThreads) {
        return managedExecutors.computeIfAbsent(name, k -> {
            LOGGER.info("Creating fixed thread pool: " + name + " with " + nThreads + " threads");
            ExecutorService executor = Executors.newFixedThreadPool(nThreads,
                    new NamedThreadFactory(name));
            return new ManagedExecutor(executor, name, ExecutorType.FIXED);
        }).getExecutor();
    }

    /**
     * Creates a cached thread pool with the specified name or returns an existing
     * one.
     * 
     * @param name The name to identify this thread pool
     * @return A managed ExecutorService
     */
    public ExecutorService createCachedThreadPool(String name) {
        return managedExecutors.computeIfAbsent(name, k -> {
            LOGGER.info("Creating cached thread pool: " + name);
            ExecutorService executor = Executors.newCachedThreadPool(
                    new NamedThreadFactory(name));
            return new ManagedExecutor(executor, name, ExecutorType.CACHED);
        }).getExecutor();
    }

    /**
     * Creates a single-threaded executor with the specified name or returns an
     * existing one.
     * 
     * @param name The name to identify this executor
     * @return A managed ExecutorService
     */
    public ExecutorService createSingleThreadExecutor(String name) {
        return managedExecutors.computeIfAbsent(name, k -> {
            LOGGER.info("Creating single thread executor: " + name);
            ExecutorService executor = Executors.newSingleThreadExecutor(
                    new NamedThreadFactory(name));
            return new ManagedExecutor(executor, name, ExecutorType.SINGLE);
        }).getExecutor();
    }

    /**
     * Creates a scheduled executor with the specified name or returns an existing
     * one.
     * 
     * @param name         The name to identify this executor
     * @param corePoolSize The number of threads to keep in the pool
     * @return A managed ScheduledExecutorService
     */
    public ScheduledExecutorService createScheduledThreadPool(String name, int corePoolSize) {
        return (ScheduledExecutorService) managedExecutors.computeIfAbsent(name, k -> {
            LOGGER.info("Creating scheduled thread pool: " + name + " with " + corePoolSize + " core threads");
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(corePoolSize,
                    new NamedThreadFactory(name));
            return new ManagedExecutor(executor, name, ExecutorType.SCHEDULED);
        }).getExecutor();
    }

    /**
     * Creates a thread pool with custom configuration
     * 
     * @param name            The name to identify this thread pool
     * @param corePoolSize    The number of threads to keep in the pool, even if
     *                        they are idle
     * @param maximumPoolSize The maximum number of threads to allow in the pool
     * @param keepAliveTime   When the number of threads is greater than the core,
     *                        this is the maximum time
     *                        that excess idle threads will wait for new tasks
     *                        before terminating
     * @param unit            The time unit for the keepAliveTime argument
     * @param workQueue       The queue to use for holding tasks before they are
     *                        executed
     * @return A managed ThreadPoolExecutor
     */
    public ThreadPoolExecutor createCustomThreadPool(String name, int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        return (ThreadPoolExecutor) managedExecutors.computeIfAbsent(name, k -> {
            LOGGER.info("Creating custom thread pool: " + name);
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                    new NamedThreadFactory(name));
            return new ManagedExecutor(executor, name, ExecutorType.CUSTOM);
        }).getExecutor();
    }

    /**
     * Creates a thread pool with sensible defaults for computational tasks
     * 
     * @param name The name to identify this thread pool
     * @return A managed ThreadPoolExecutor
     */
    public ThreadPoolExecutor createComputationThreadPool(String name) {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        return createCustomThreadPool(
                name,
                availableProcessors,
                availableProcessors * 2,
                DEFAULT_KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000));
    }

    /**
     * Creates a thread pool with sensible defaults for IO-bound tasks
     * 
     * @param name The name to identify this thread pool
     * @return A managed ThreadPoolExecutor
     */
    public ThreadPoolExecutor createIOThreadPool(String name) {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        return createCustomThreadPool(
                name,
                availableProcessors * 2,
                availableProcessors * 4,
                DEFAULT_KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000));
    }

    /**
     * Shuts down an executor by name
     * 
     * @param name The name of the executor to shut down
     * @return true if the executor was found and shut down, false otherwise
     */
    public boolean shutdownExecutor(String name) {
        ManagedExecutor managedExecutor = managedExecutors.remove(name);
        if (managedExecutor != null) {
            LOGGER.info("Shutting down executor: " + name);
            managedExecutor.getExecutor().shutdown();
            return true;
        }
        return false;
    }

    /**
     * Shuts down an executor by name and waits for termination
     * 
     * @param name    The name of the executor to shut down
     * @param timeout The maximum time to wait
     * @param unit    The time unit of the timeout argument
     * @return true if the executor terminated, false if the timeout elapsed before
     *         termination
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean shutdownExecutorAndWait(String name, long timeout, TimeUnit unit) throws InterruptedException {
        ManagedExecutor managedExecutor = managedExecutors.get(name);
        if (managedExecutor != null) {
            LOGGER.info("Shutting down executor and waiting: " + name);
            ExecutorService executor = managedExecutor.getExecutor();
            executor.shutdown();
            boolean terminated = executor.awaitTermination(timeout, unit);
            if (terminated) {
                managedExecutors.remove(name);
            }
            return terminated;
        }
        return false;
    }

    /**
     * Shuts down all executors
     */
    public void shutdownAllExecutors() {
        LOGGER.info("Shutting down all executors (" + managedExecutors.size() + " active)");
        for (ManagedExecutor managedExecutor : managedExecutors.values()) {
            managedExecutor.getExecutor().shutdown();
        }
    }

    /**
     * Shuts down all executors and waits for termination
     * 
     * @param timeout The maximum time to wait
     * @param unit    The time unit of the timeout argument
     * @return true if all executors terminated, false if the timeout elapsed before
     *         termination
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean shutdownAllExecutorsAndWait(long timeout, TimeUnit unit) throws InterruptedException {
        shutdownAllExecutors();

        long endTime = System.currentTimeMillis() + unit.toMillis(timeout);
        boolean allTerminated = true;

        for (ManagedExecutor managedExecutor : managedExecutors.values()) {
            long remainingTime = endTime - System.currentTimeMillis();
            if (remainingTime <= 0) {
                return false; // Timeout elapsed
            }

            ExecutorService executor = managedExecutor.getExecutor();
            boolean terminated = executor.awaitTermination(remainingTime, TimeUnit.MILLISECONDS);
            if (!terminated) {
                allTerminated = false;
            }
        }

        if (allTerminated) {
            managedExecutors.clear();
        }

        return allTerminated;
    }

    /**
     * Returns metrics about the current executor usage
     * 
     * @return String representation of executor metrics
     */
    public String getExecutorMetrics() {
        StringBuilder metrics = new StringBuilder();
        metrics.append("Active executors: ").append(managedExecutors.size()).append("\n");

        for (Map.Entry<String, ManagedExecutor> entry : managedExecutors.entrySet()) {
            ManagedExecutor managedExecutor = entry.getValue();
            metrics.append(" - ").append(entry.getKey())
                    .append(" (type: ").append(managedExecutor.getType())
                    .append(", created: ").append(managedExecutor.getCreationTime()).append(")\n");

            // Add more detailed metrics for ThreadPoolExecutor types
            if (managedExecutor.getExecutor() instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor tpe = (ThreadPoolExecutor) managedExecutor.getExecutor();
                metrics.append("   Active: ").append(tpe.getActiveCount())
                        .append(", Pool: ").append(tpe.getPoolSize())
                        .append(", Core: ").append(tpe.getCorePoolSize())
                        .append(", Max: ").append(tpe.getMaximumPoolSize())
                        .append(", Queue: ").append(tpe.getQueue().size())
                        .append("\n");
            }
        }

        return metrics.toString();
    }

    /**
     * Internal class to track an ExecutorService with metadata
     */
    private static class ManagedExecutor {
        private final ExecutorService executor;
        private final String name;
        private final ExecutorType type;
        private final long creationTime;

        public ManagedExecutor(ExecutorService executor, String name, ExecutorType type) {
            this.executor = executor;
            this.name = name;
            this.type = type;
            this.creationTime = System.currentTimeMillis();
        }

        public ExecutorService getExecutor() {
            return executor;
        }

        public String getName() {
            return name;
        }

        public ExecutorType getType() {
            return type;
        }

        public long getCreationTime() {
            return creationTime;
        }
    }

    /**
     * Enum to classify executor types
     */
    private enum ExecutorType {
        FIXED,
        CACHED,
        SINGLE,
        SCHEDULED,
        CUSTOM
    }

    /**
     * Factory for creating named threads
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final String namePrefix;
        private final AtomicLong threadNumber = new AtomicLong(1);

        public NamedThreadFactory(String poolName) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            namePrefix = "pool-" + poolName + "-thread-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}