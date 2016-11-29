package org.kolokolov.fileloader.service;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The service is designed to provide a method execution in separate thread. It creates thread pool with size depending
 * on set value. If no proper thread number value was passed than default value is used.
 * 
 * @author kolokolov
 */
public class ThreadService {
    private final int DEFAULT_THREAD_POOL_SIZE = 5;
    private ExecutorService downloadThreadPool;

    public ThreadService(int threadPoolSize) {
        this.downloadThreadPool = Executors
                .newFixedThreadPool(threadPoolSize > 0 ? threadPoolSize : DEFAULT_THREAD_POOL_SIZE);
    }

    /**
     * Provides execution of passed lambda expression within a new thread. The new thread is received from the
     * pool if there is spare one, and returns to the pool after lambda expression execution.
     * 
     * @param callable instance of {@link Callable<Boolean>} functional interface or lambda expression that is supposed to
     *            return a boolean value
     * @return an object with the Future interface that returns the result returned by the lambda expression after its
     *         execution.
     */
    public Future<Boolean> executeInNewThread(Callable<Boolean> collable) {
        return downloadThreadPool.submit(collable);
    }

    /**
     * Provides execution of passed lambda expression within a new daemon thread.
     * 
     * @param runnable instance of {@link Runnable} functional interface or void lambda expression.
     * @return a reference to new daemon thread
     */ 
    public Thread startNewDaemon(Runnable runnable) {
        Thread newDaemon = new Thread(runnable);
        newDaemon.setDaemon(true);
        newDaemon.start();
        return newDaemon;
    }
}
