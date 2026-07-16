package com.ticketingflow.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ticketingflow.worker")
public record WorkerProps(int batchSize, long blockMs, long reclaimIdleMs, long maxDelivery) {
}
