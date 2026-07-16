package com.ticketingflow.booking.service;

import com.ticketingflow.booking.mapper.EventMapper;
import com.ticketingflow.common.RedisKeys;
import com.ticketingflow.redis.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 좌석/재고 해시 lazy 초기화.
 * 키가 없으면 RDB 원장으로부터 재구성한다. 원장이 INSERT-only이므로
 * (마스터 - 유효거래 합) 만으로 언제든 현재 상태를 복원할 수 있다.
 * 동시 재구성 경합은 동일 데이터 덮어쓰기라 무해하다.
 */
@Slf4j
@Service
public class SeatCacheService {

    private final StringRedisTemplate redis;
    private final EventMapper eventMapper;

    public SeatCacheService(@Qualifier(RedisConfig.BOOKING) StringRedisTemplate redis,
                            EventMapper eventMapper) {
        this.redis = redis;
        this.eventMapper = eventMapper;
    }

    public void ensureSeatHash(String schdNo) {
        String key = RedisKeys.seatHash(schdNo);
        if (Boolean.TRUE.equals(redis.hasKey(key))) {
            return;
        }
        List<String> seatNos = eventMapper.selectSeatNos(schdNo);
        if (seatNos.isEmpty()) {
            return;
        }
        Map<String, String> entries = new HashMap<>();
        seatNos.forEach(no -> entries.put(no, RedisKeys.SEAT_AVAILABLE));
        eventMapper.selectSoldSeats(schdNo)
                .forEach(sold -> entries.put(sold.seatNo(), RedisKeys.SEAT_SOLD_PREFIX + sold.usrId()));
        redis.opsForHash().putAll(key, entries);
        log.info("seat hash rebuilt: schdNo={} seats={}", schdNo, entries.size());
    }

    public void ensureStockHash(String schdNo) {
        String key = RedisKeys.stockHash(schdNo);
        if (Boolean.TRUE.equals(redis.hasKey(key))) {
            return;
        }
        var remains = eventMapper.selectPrdRemains(schdNo);
        if (remains.isEmpty()) {
            return;
        }
        Map<String, String> entries = new HashMap<>();
        remains.forEach(r -> entries.put(r.prdNo(), String.valueOf(r.remainQty())));
        redis.opsForHash().putAll(key, entries);
        log.info("stock hash rebuilt: schdNo={} prds={}", schdNo, entries.size());
    }
}
