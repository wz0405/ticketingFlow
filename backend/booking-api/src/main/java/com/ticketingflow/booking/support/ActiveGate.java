package com.ticketingflow.booking.support;

import com.ticketingflow.common.BizException;
import com.ticketingflow.common.RedisKeys;
import com.ticketingflow.common.RsltCd;
import com.ticketingflow.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 입장 검증. 입장(active) 계통은 대기열/예매와 분리된 별도 Redis라
 * 좌석 Lua와 한 스크립트로 묶을 수 없어, 예매 직전 선행 단계로 확인한다.
 * 토큰이 없거나 다르면 입장 만료로 본다.
 */
@Component
public class ActiveGate {

    private final StringRedisTemplate activeRedis;

    public ActiveGate(@Qualifier(RedisConfig.ACTIVE) StringRedisTemplate activeRedis) {
        this.activeRedis = activeRedis;
    }

    public void verify(String schdNo, String usrId, String entryToken) {
        String token = activeRedis.opsForValue().get(RedisKeys.active(schdNo, usrId));
        if (token == null || !token.equals(entryToken)) {
            throw new BizException(RsltCd.ENTRY_EXPIRED);
        }
    }
}
