package org.kolokolov.fileloader.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

public class ThreadService {
    private static final int DEFAULT_THREAD_POOL_SIZE = 5;
    private ExecutorService pool;
    
    public ThreadService() {
        this(DEFAULT_THREAD_POOL_SIZE);
    }
    
    public ThreadService(int threadPoolSize) {
        pool = Executors.newFixedThreadPool(threadPoolSize);
    }
    
    public Future<Boolean> performInNewThread(BooleanSupplier supplier) {       
        return pool.submit(() -> supplier.getAsBoolean());
    }
}
