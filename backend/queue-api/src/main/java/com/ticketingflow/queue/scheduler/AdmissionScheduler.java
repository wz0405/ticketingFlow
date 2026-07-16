package com.ticketingflow.queue.scheduler;

import com.ticketingflow.common.RedisKeys;
import com.ticketingflow.queue.config.QueueProps;
import com.ticketingflow.redis.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * 입장 승격 스케줄러.
 * 틱마다 대기열(ZSET)에서 선두 N명을 꺼내(ZPOPMIN, 원자적)
 * active:{schdNo}:{usrId} = entryToken (TTL) 을 발급한다.
 * 예매 서버는 이 키 존재 여부로 입장을 검증한다.
 *
 * 단일 인스턴스 전제. 다중 인스턴스 확장 시 분산락(ShedLock 등) 또는
 * 스케줄러 전용 프로세스 분리가 필요하다 — TRADEOFFS.md 참고.
 */
@Slf4j
@Component
public class AdmissionScheduler {

    private final StringRedisTemplate queueRedis;
    private final StringRedisTemplate activeRedis;
    private final QueueProps props;

    public AdmissionScheduler(@Qualifier(RedisConfig.QUEUE) StringRedisTemplate queueRedis,
                              @Qualifier(RedisConfig.ACTIVE) StringRedisTemplate activeRedis,
                              QueueProps props) {
        this.queueRedis = queueRedis;
        this.activeRedis = activeRedis;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${ticketingflow.queue.tick-ms}")
    public void admitTick() {
        Set<String> schdNos = queueRedis.opsForSet().members(RedisKeys.WQ_SCHD_REGISTRY);
        if (schdNos == null || schdNos.isEmpty()) {
            return;
        }
        schdNos.forEach(this::admitForSchd);
    }

    private void admitForSchd(String schdNo) {
        Set<TypedTuple<String>> popped =
                queueRedis.opsForZSet().popMin(RedisKeys.waitingQueue(schdNo), props.admitPerTick());
        if (popped == null || popped.isEmpty()) {
            return;
        }
        Duration ttl = Duration.ofSeconds(props.activeTtlSec());
        activeRedis.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) conn -> {
            popped.stream()
                    .map(TypedTuple::getValue)
                    .filter(usrId -> usrId != null)
                    .forEach(usrId -> conn.stringCommands().setEx(
                            RedisKeys.active(schdNo, usrId).getBytes(StandardCharsets.UTF_8),
                            ttl.toSeconds(),
                            UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)));
            return null;
        });
        log.debug("admitted {} users for schd {}", popped.size(), schdNo);
    }
}
