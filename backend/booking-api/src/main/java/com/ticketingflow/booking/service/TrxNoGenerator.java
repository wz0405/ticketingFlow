package com.ticketingflow.booking.service;

import com.ticketingflow.common.RedisKeys;
import com.ticketingflow.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 거래번호 채번. {prefix}{yyMMdd}{seq 7자리} — 예: R2607160000123
 */
@Component
public class TrxNoGenerator {

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    private final StringRedisTemplate redis;

    public TrxNoGenerator(@Qualifier(RedisConfig.BOOKING) StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String next(String prefix) {
        String day = LocalDate.now().format(YYMMDD);
        String key = RedisKeys.rsvSeq(day);
        Long seq = redis.opsForValue().increment(key);
        if (seq != null && seq == 1L) {
            redis.expire(key, 2, TimeUnit.DAYS);
        }
        return "%s%s%07d".formatted(prefix, day, seq);
    }
}
