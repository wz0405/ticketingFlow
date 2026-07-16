package com.ticketingflow.booking.lua;

import com.ticketingflow.common.RedisKeys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * seat_hold.lua 동시성·상태전이 검증.
 * 오버셀 방지는 이 스크립트의 원자성에 전적으로 의존하므로,
 * 모의 객체가 아닌 실제 Redis(Testcontainers)에서 검증한다.
 * embedded-redis는 Windows 바이너리가 3.x라 Streams(XADD)를 지원하지 않아 배제했다.
 */
@Testcontainers(disabledWithoutDocker = true)
class SeatHoldScriptTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static final String KEY = RedisKeys.seatHash("SCHD1");
    static final long HOLD_MS = 60_000;

    static StringRedisTemplate redis;
    static DefaultRedisScript<List> seatHold;

    @BeforeAll
    static void init() {
        LettuceConnectionFactory cf =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        cf.afterPropertiesSet();
        redis = new StringRedisTemplate(cf);
        redis.afterPropertiesSet();

        seatHold = new DefaultRedisScript<>();
        seatHold.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/seat_hold.lua")));
        seatHold.setResultType(List.class);
    }

    @BeforeEach
    void clean() {
        redis.delete(KEY);
    }

    @Test
    @DisplayName("같은 좌석에 50명이 동시 선점하면 정확히 1명만 성공한다")
    void concurrentHold_exactlyOneWinner() throws Exception {
        redis.opsForHash().put(KEY, "A-01", RedisKeys.SEAT_AVAILABLE);

        int contenders = 50;
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Integer>> outcomes = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            String usrId = "u" + i;
            outcomes.add(pool.submit(() -> {
                ready.countDown();
                start.await();        // 전원이 출발선에 설 때까지 실행을 막아 실제 경합을 만든다
                return code(hold(usrId, System.currentTimeMillis(), "A-01"));
            }));
        }
        ready.await();
        start.countDown();

        int winners = 0;
        for (Future<Integer> f : outcomes) {
            if (f.get() == 0) {
                winners++;
            }
        }
        pool.shutdown();

        assertThat(winners).isEqualTo(1);
        assertThat(value("A-01")).startsWith(RedisKeys.SEAT_HOLD_PREFIX);
    }

    @Test
    @DisplayName("만료된 타인 HOLD는 즉시 재선점된다 (lazy expiry)")
    void expiredHold_isReholdable() {
        long now = System.currentTimeMillis();
        redis.opsForHash().put(KEY, "A-01", "H:other:" + (now - 1));

        List<?> rslt = hold("me", now, "A-01");

        assertThat(code(rslt)).isZero();
        assertThat(value("A-01")).startsWith("H:me:");
    }

    @Test
    @DisplayName("본인의 유효한 HOLD는 재선점되며 만료시각이 갱신된다 (좌석 추가 선택 플로우)")
    void ownLiveHold_isReholdableWithRefreshedTtl() {
        long now = System.currentTimeMillis();
        redis.opsForHash().put(KEY, "A-01", "H:me:" + (now + 1_000));
        redis.opsForHash().put(KEY, "A-02", RedisKeys.SEAT_AVAILABLE);

        List<?> rslt = hold("me", now, "A-01", "A-02");

        assertThat(code(rslt)).isZero();
        assertThat(value("A-01")).isEqualTo("H:me:" + (now + HOLD_MS));
        assertThat(value("A-02")).isEqualTo("H:me:" + (now + HOLD_MS));
    }

    @Test
    @DisplayName("유효한 타인 HOLD는 선점 불가(2)로 거절되고 상태가 보존된다")
    void liveForeignHold_isRejected() {
        long now = System.currentTimeMillis();
        String held = "H:other:" + (now + HOLD_MS);
        redis.opsForHash().put(KEY, "A-01", held);

        List<?> rslt = hold("me", now, "A-01");

        assertThat(code(rslt)).isEqualTo(2);
        assertThat(rslt.get(1)).hasToString("A-01");
        assertThat(value("A-01")).isEqualTo(held);
    }

    @Test
    @DisplayName("다좌석 중 한 석이라도 선점 불가면 전체가 실패한다 (all-or-nothing)")
    void multiSeat_allOrNothing() {
        long now = System.currentTimeMillis();
        redis.opsForHash().put(KEY, "A-01", RedisKeys.SEAT_AVAILABLE);
        redis.opsForHash().put(KEY, "A-02", "S:someone");

        List<?> rslt = hold("me", now, "A-01", "A-02");

        assertThat(code(rslt)).isEqualTo(2);
        assertThat(rslt.get(1)).hasToString("A-02");
        // 검증을 통과한 A-01에도 부분 선점이 남으면 안 된다
        assertThat(value("A-01")).isEqualTo(RedisKeys.SEAT_AVAILABLE);
    }

    private List<?> hold(String usrId, long now, String... seatNos) {
        Object[] args = new Object[3 + seatNos.length];
        args[0] = usrId;
        args[1] = String.valueOf(now);
        args[2] = String.valueOf(HOLD_MS);
        System.arraycopy(seatNos, 0, args, 3, seatNos.length);
        return redis.execute(seatHold, List.of(KEY), args);
    }

    private int code(List<?> rslt) {
        return ((Number) rslt.get(0)).intValue();
    }

    private String value(String seatNo) {
        return (String) redis.opsForHash().get(KEY, seatNo);
    }
}
