package org.kolokolov.fileloader.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

/**
 * The service is designed to provide a method execution in separate thread.
 * It creates thread pool with size depending on set value.
 * If no proper thread number value was passed than default value is used.
 * @author kolokolov
 */
public class ThreadService {
    private final int DEFAULT_THREAD_POOL_SIZE = 5;
    private ExecutorService pool;

    public ThreadService(int threadPoolSize) {
        pool = Executors.newFixedThreadPool(threadPoolSize > 0 ? threadPoolSize : DEFAULT_THREAD_POOL_SIZE);
    }
    
    /**
     * Method provides execution of passed lambda expression within a new thread.
     * The new thread is received from the pool if there is spare one, 
     * and returns to the pool after lambda expression execution.
     * @param suppler  instance of {@link BooleanSupplier} functional interface
     * or lambda expression that is supposed to return a boolean value
     * @return an object with the Future interface that returns the result
     * returned by the lambda expression after its execution. 
     */
    public Future<Boolean> executeInNewThread(BooleanSupplier supplier) {
        return pool.submit(() -> supplier.getAsBoolean());
    }
}