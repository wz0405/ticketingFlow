package com.ticketingflow.queue.biz;

import com.ticketingflow.common.BizException;
import com.ticketingflow.common.RedisKeys;
import com.ticketingflow.common.RsltCd;
import com.ticketingflow.core.HandleTemplate;
import com.ticketingflow.core.Reqs;
import com.ticketingflow.core.StepChain;
import com.ticketingflow.core.TxData;
import com.ticketingflow.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 데모용 대기열 부하 투입.
 * 가상 대기자 N명을 ZSET에 직접 적재해, 이후 로그인한 실사용자가
 * 그 뒤에 줄 서서 입장 스케줄링이 소화되는 과정을 눈으로 볼 수 있게 한다.
 *
 * 봇은 로그인·예매를 하지 않으므로 DB에 흔적이 없고, 입장 승격 후에는
 * active TTL 만료로 자연 소멸한다. 로그인 화면(무인증)에서 쓰는 기능이라
 * PUBLIC_BIZ로 공개하되 1회 투입량을 제한한다.
 */
@Service("handle|" + DemoLoadHandle.BEAN_NM)
public class DemoLoadHandle extends HandleTemplate {

    public static final String BEAN_NM = "demoLoad";

    private static final int MAX_COUNT = 1000;

    private final StringRedisTemplate queueRedis;

    public DemoLoadHandle(@Qualifier(RedisConfig.QUEUE) StringRedisTemplate queueRedis) {
        this.queueRedis = queueRedis;
    }

    @Override
    protected void validate(TxData tx) {
        Reqs.required(tx.inString("schdNo"), "schdNo");
        int count = countOf(tx);
        if (count < 1 || count > MAX_COUNT) {
            throw new BizException(RsltCd.INVALID_PARAM,
                    "count는 1~" + MAX_COUNT + " 사이여야 합니다");
        }
    }

    @Override
    protected StepChain composeDo(StepChain chain, TxData tx) throws Exception {
        return chain.next(t -> {
            String schdNo = t.inString("schdNo");
            int count = countOf(t);
            long now = System.currentTimeMillis();
            String batch = Long.toString(now, 36);

            // score = 현재시각 + i → 실사용자와 같은 규칙(진입시각 FIFO)으로 줄 선다
            Set<TypedTuple<String>> bots = new LinkedHashSet<>();
            for (int i = 0; i < count; i++) {
                bots.add(TypedTuple.of("bot-" + batch + "-" + i, (double) (now + i)));
            }
            queueRedis.opsForZSet().add(RedisKeys.waitingQueue(schdNo), bots);
            queueRedis.opsForSet().add(RedisKeys.WQ_SCHD_REGISTRY, schdNo);

            Long total = queueRedis.opsForZSet().zCard(RedisKeys.waitingQueue(schdNo));
            t.out("added", count);
            t.out("totalWaiting", total == null ? count : total);
        });
    }

    private int countOf(TxData tx) {
        Object raw = tx.getInData().get("count");
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new BizException(RsltCd.INVALID_PARAM, "count가 숫자가 아닙니다");
        }
    }
}
