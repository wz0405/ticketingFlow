package com.ticketingflow.queue.support;

import com.ticketingflow.common.RedisKeys;
import com.ticketingflow.queue.config.QueueProps;
import com.ticketingflow.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 대기열 Redis 조작 단일 창구.
 *
 * 대기열은 MQ가 아닌 ZSET을 쓴다.
 *   1) FE에 내 순번(ZRANK)을 보여줘야 한다 — MQ는 소비 전까지 위치 조회 불가
 *   2) 재접속(새로고침) 시 ZADD NX로 기존 순번이 유지된다 — MQ는 재발행 시 중복
 *   3) 입장 상태(active 키)는 TTL로 수명을 관리한다
 *
 * 순번 확인은 어댑티브 폴링. 응답의 pollAfterMs(순번이 멀수록 김)를 FE가 따라
 * 대기 인원이 늘수록 서버 유입 QPS가 오히려 줄도록 설계했다.
 */
@Component
public class QueueRedisSupport {

    private final StringRedisTemplate queueRedis;
    private final StringRedisTemplate activeRedis;
    private final QueueProps props;

    public QueueRedisSupport(@Qualifier(RedisConfig.QUEUE) StringRedisTemplate queueRedis,
                             @Qualifier(RedisConfig.ACTIVE) StringRedisTemplate activeRedis,
                             QueueProps props) {
        this.queueRedis = queueRedis;
        this.activeRedis = activeRedis;
        this.props = props;
    }

    /** 진입. 이미 대기 중이면 순번 유지(멱등) */
    public Map<String, Object> enter(String schdNo, String usrId) {
        Map<String, Object> admitted = admittedStatus(schdNo, usrId);
        if (admitted != null) {
            return admitted;
        }
        queueRedis.opsForZSet().addIfAbsent(RedisKeys.waitingQueue(schdNo), usrId, System.currentTimeMillis());
        queueRedis.opsForSet().add(RedisKeys.WQ_SCHD_REGISTRY, schdNo);
        return waitingStatus(schdNo, usrId);
    }

    public Map<String, Object> status(String schdNo, String usrId) {
        Map<String, Object> admitted = admittedStatus(schdNo, usrId);
        return admitted != null ? admitted : waitingStatus(schdNo, usrId);
    }

    public void leave(String schdNo, String usrId) {
        queueRedis.opsForZSet().remove(RedisKeys.waitingQueue(schdNo), usrId);
    }

    private Map<String, Object> admittedStatus(String schdNo, String usrId) {
        String token = activeRedis.opsForValue().get(RedisKeys.active(schdNo, usrId));
        if (token == null) {
            return null;
        }
        Long ttl = activeRedis.getExpire(RedisKeys.active(schdNo, usrId), TimeUnit.SECONDS);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("admitted", true);
        out.put("inQueue", true);
        out.put("entryToken", token);
        out.put("ttlSec", ttl == null || ttl < 0 ? 0 : ttl);
        return out;
    }

    private Map<String, Object> waitingStatus(String schdNo, String usrId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("admitted", false);
        Long rank = queueRedis.opsForZSet().rank(RedisKeys.waitingQueue(schdNo), usrId);
        if (rank == null) {
            out.put("inQueue", false);
            return out;
        }
        Long total = queueRedis.opsForZSet().zCard(RedisKeys.waitingQueue(schdNo));
        long position = rank + 1;
        double ratePerSec = effectiveRatePerSec(total == null ? position : total);
        long etaSec = (long) Math.ceil(position / ratePerSec);
        out.put("inQueue", true);
        out.put("position", position);
        out.put("totalWaiting", total == null ? position : total);
        out.put("etaSec", etaSec);
        out.put("pollAfterMs", pollAfterMs(etaSec));
        return out;
    }

    /** 가변 배출 구간에서는 평균 처리율로, 그 외에는 고정 배출 기준으로 ETA를 계산한다 */
    private double effectiveRatePerSec(long totalWaiting) {
        if (props.demoDynamicAdmit() && totalWaiting <= props.demoDynamicMaxQueue()) {
            return props.demoAvgAdmitPerSec();
        }
        return props.admitPerTick() * (1000.0 / props.tickMs());
    }

    /** 입장 예상 시간이 멀수록 폴링 주기를 늘린다 (서버 주도 어댑티브 폴링) */
    private long pollAfterMs(long etaSec) {
        if (etaSec <= 5) {
            return 1000;
        }
        if (etaSec <= 30) {
            return 2000;
        }
        if (etaSec <= 120) {
            return 5000;
        }
        return 10000;
    }
}
