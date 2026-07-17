package com.ticketingflow.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대기열 운영 파라미터.
 * admitPerTick x (1000/tickMs) = 초당 입장 처리량(admission rate).
 *
 * demoDynamicAdmit이 켜져 있으면 대기 인원이 demoDynamicMaxQueue 이하일 때
 * 고정 배출 대신 가변 배출(정체~버스트)로 승격한다 — 데모 체감용.
 * 대기 인원이 그보다 크면(부하테스트) 고정 admitPerTick으로 동작한다.
 * demoAvgAdmitPerSec는 가변 배출의 평균 처리율로, ETA 계산에만 쓴다.
 */
@ConfigurationProperties(prefix = "ticketingflow.queue")
public record QueueProps(int admitPerTick, long tickMs, long activeTtlSec, int wsPort, long wsPushMs,
                         boolean demoDynamicAdmit, int demoDynamicMaxQueue, double demoAvgAdmitPerSec) {
}
