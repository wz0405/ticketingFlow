package com.ticketingflow.booking.lua;

import com.ticketingflow.common.RedisKeys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * rsv_confirm.lua 검증.
 * 핵심 계약: HOLD 검증 → SOLD 전환 → Stream 발행이 전부 성공하거나 전부 없던 일이 된다.
 * "좌석은 팔렸는데 이벤트가 없다"는 상태가 나오면 원장 유실이므로,
 * 성공/실패 각각에서 좌석 상태와 스트림을 함께 확인한다.
 */
@Testcontainers(disabledWithoutDocker = true)
class RsvConfirmScriptTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static final String KEY = RedisKeys.seatHash("SCHD1");
    static final String PAYLOAD = "{\"rsvNo\":\"R1\"}";

    static StringRedisTemplate redis;
    static DefaultRedisScript<List> rsvConfirm;

    @BeforeAll
    static void init() {
        LettuceConnectionFactory cf =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        cf.afterPropertiesSet();
        redis = new StringRedisTemplate(cf);
        redis.afterPropertiesSet();

        rsvConfirm = new DefaultRedisScript<>();
        rsvConfirm.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/rsv_confirm.lua")));
        rsvConfirm.setResultType(List.class);
    }

    @BeforeEach
    void clean() {
        redis.delete(List.of(KEY, RedisKeys.RSV_STREAM));
    }

    @Test
    @DisplayName("본인의 유효한 HOLD는 SOLD로 전환되고 확정 이벤트가 발행된다")
    void ownLiveHold_confirmedAndEventPublished() {
        long now = System.currentTimeMillis();
        redis.opsForHash().put(KEY, "A-01", "H:me:" + (now + 60_000));

        List<?> rslt = confirm("me", now, "A-01");

        assertThat(code(rslt)).isZero();
        assertThat(redis.opsForHash().get(KEY, "A-01")).isEqualTo("S:me");

        List<MapRecord<String, Object, Object>> events = streamEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getValue())
                .containsEntry("type", "RSV")
                .containsEntry("payload", PAYLOAD);
    }

    @Test
    @DisplayName("만료된 HOLD는 확정 불가(3)이고 좌석·스트림 모두 변하지 않는다")
    void expiredHold_rejectedWithoutSideEffect() {
        long now = System.currentTimeMillis();
        String expired = "H:me:" + (now - 1);
        redis.opsForHash().put(KEY, "A-01", expired);

        List<?> rslt = confirm("me", now, "A-01");

        assertThat(code(rslt)).isEqualTo(3);
        assertThat(redis.opsForHash().get(KEY, "A-01")).isEqualTo(expired);
        assertThat(streamEvents()).isEmpty();
    }

    @Test
    @DisplayName("타인의 HOLD는 본인이 확정할 수 없다")
    void foreignHold_rejected() {
        long now = System.currentTimeMillis();
        redis.opsForHash().put(KEY, "A-01", "H:other:" + (now + 60_000));

        List<?> rslt = confirm("me", now, "A-01");

        assertThat(code(rslt)).isEqualTo(3);
        assertThat(streamEvents()).isEmpty();
    }

    private List<?> confirm(String usrId, long now, String... seatNos) {
        Object[] args = new Object[3 + seatNos.length];
        args[0] = usrId;
        args[1] = String.valueOf(now);
        args[2] = PAYLOAD;
        System.arraycopy(seatNos, 0, args, 3, seatNos.length);
        return redis.execute(rsvConfirm, List.of(KEY, RedisKeys.RSV_STREAM), args);
    }

    private int code(List<?> rslt) {
        return ((Number) rslt.get(0)).intValue();
    }

    private List<MapRecord<String, Object, Object>> streamEvents() {
        return redis.opsForStream().range(RedisKeys.RSV_STREAM, Range.unbounded());
    }
}
