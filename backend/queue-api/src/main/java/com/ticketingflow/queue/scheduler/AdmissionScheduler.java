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
import java.util.concurrent.ThreadLocalRandom;

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
        int admitN = admitCountFor(schdNo);
        if (admitN <= 0) {
            return;
        }
        Set<TypedTuple<String>> popped =
                queueRedis.opsForZSet().popMin(RedisKeys.waitingQueue(schdNo), admitN);
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

    /**
     * 이번 틱의 배출 인원 결정.
     * 데모 모드에서는 고정 배출 대신 [정체 / 찔끔 / 웨이브 / 버스트]를 확률적으로 섞어
     * 대기열이 살아있는 것처럼 불규칙하게 줄어들게 한다. 잔여가 적으면 시원하게 마무리.
     * 대기 인원이 demoDynamicMaxQueue를 넘으면(부하테스트) 기존 고정 배출을 유지한다.
     */
    private int admitCountFor(String schdNo) {
        if (!props.demoDynamicAdmit()) {
            return props.admitPerTick();
        }
        Long total = queueRedis.opsForZSet().zCard(RedisKeys.waitingQueue(schdNo));
        long waiting = total == null ? 0 : total;
        if (waiting == 0) {
            return 0;
        }
        if (waiting > props.demoDynamicMaxQueue()) {
            return props.admitPerTick();
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (waiting <= 30) {
            return 7 + r.nextInt(9);             // 막판 가속: 7~15명씩 시원하게
        }
        double p = r.nextDouble();
        if (p < 0.12) {
            return 0;                            // 정체 — 멈칫하는 긴장감
        }
        if (p < 0.75) {
            return 2 + r.nextInt(5);             // 평상: 2~6명 찔끔찔끔
        }
        if (p < 0.93) {
            return 8 + r.nextInt(8);             // 웨이브: 8~15명
        }
        // 버스트: 우르르 빠지되, 잔여의 절반을 넘지 않게 — 작은 큐에서도 여러 번 낙폭이 보이게
        return (int) Math.min(25 + r.nextInt(21), Math.max(12, waiting / 2));
    }
}
