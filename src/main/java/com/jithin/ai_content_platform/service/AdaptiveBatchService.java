package com.jithin.ai_content_platform.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class AdaptiveBatchService {

    @Value("${trend.analysis.batch.size}")
    private int defaultBatchSize;

    @Value("${trend.analysis.max.items}")
    private int maxItems;

    private final MemoryMXBean memoryBean;
    private final AtomicInteger currentBatchSize;
    private static final double MEMORY_THRESHOLD = 0.75; // 75% memory usage threshold
    private static final int MIN_BATCH_SIZE = 10;
    private static final int MAX_BATCH_SIZE = 200;

    public AdaptiveBatchService() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.currentBatchSize = new AtomicInteger(defaultBatchSize);
    }

    public int getOptimalBatchSize(double avgContentSize) {
        // Get current memory usage
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long usedMemory = heapUsage.getUsed();
        long maxMemory = heapUsage.getMax();
        double memoryUsageRatio = (double) usedMemory / maxMemory;

        // Calculate optimal batch size based on memory usage
        int optimalBatchSize = calculateOptimalBatchSize(memoryUsageRatio, avgContentSize);

        // Update current batch size with some smoothing to avoid dramatic changes
        int newBatchSize = (currentBatchSize.get() + optimalBatchSize) / 2;
        currentBatchSize.set(boundBatchSize(newBatchSize));

        log.debug("Memory usage: {}%, Optimal batch size: {}", 
                 (memoryUsageRatio * 100), currentBatchSize.get());

        return currentBatchSize.get();
    }

    private int calculateOptimalBatchSize(double memoryUsageRatio, double avgContentSize) {
        if (memoryUsageRatio > MEMORY_THRESHOLD) {
            // Reduce batch size when memory usage is high
            return (int) (currentBatchSize.get() * (1 - (memoryUsageRatio - MEMORY_THRESHOLD)));
        } else {
            // Increase batch size when memory usage is low
            double availableMemoryRatio = 1 - memoryUsageRatio;
            return (int) (currentBatchSize.get() * (1 + (availableMemoryRatio * 0.5)));
        }
    }

    private int boundBatchSize(int batchSize) {
        return Math.min(MAX_BATCH_SIZE, Math.max(MIN_BATCH_SIZE, batchSize));
    }

    public void adjustBatchSize(long processingTime, int itemsProcessed) {
        // Adjust batch size based on processing performance
        double itemsPerSecond = itemsProcessed / (processingTime / 1000.0);
        int targetProcessingTime = 5; // target 5 seconds per batch

        int optimalItems = (int) (itemsPerSecond * targetProcessingTime);
        int newBatchSize = boundBatchSize(optimalItems);

        // Smooth the transition
        currentBatchSize.set((currentBatchSize.get() + newBatchSize) / 2);

        log.debug("Processing performance: {} items/sec, Adjusted batch size: {}", 
                 itemsPerSecond, currentBatchSize.get());
    }

    public void resetBatchSize() {
        currentBatchSize.set(defaultBatchSize);
    }

    public int getCurrentBatchSize() {
        return currentBatchSize.get();
    }

    public void reportError() {
        // Reduce batch size on error
        int reduced = (int) (currentBatchSize.get() * 0.8);
        currentBatchSize.set(boundBatchSize(reduced));
    }

    public boolean shouldProcessBatch(int remainingItems) {
        return remainingItems >= MIN_BATCH_SIZE;
    }
}
