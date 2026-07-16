package com.ticketingflow.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대기열 운영 파라미터.
 * admitPerTick x (1000/tickMs) = 초당 입장 처리량(admission rate).
 */
@ConfigurationProperties(prefix = "ticketingflow.queue")
public record QueueProps(int admitPerTick, long tickMs, long activeTtlSec, int wsPort, long wsPushMs) {
}
