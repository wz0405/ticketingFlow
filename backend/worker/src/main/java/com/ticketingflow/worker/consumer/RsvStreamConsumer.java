package com.ticketingflow.worker.consumer;

import com.ticketingflow.common.RedisKeys;
import com.ticketingflow.redis.RedisConfig;
import com.ticketingflow.worker.config.WorkerProps;
import com.ticketingflow.worker.service.LedgerPersistService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * stream:rsv consumer group 소비 루프. 스트림은 예매(booking) Redis 계통에 있다.
 * 정상 처리 시에만 XACK — 실패 건은 pending에 남아 reclaimer가 재처리한다.
 */
@Slf4j
@Component
public class RsvStreamConsumer implements ApplicationRunner {

    private final StringRedisTemplate redis;
    private final LedgerPersistService persistService;
    private final WorkerProps props;

    public RsvStreamConsumer(@Qualifier(RedisConfig.BOOKING) StringRedisTemplate redis,
                             LedgerPersistService persistService, WorkerProps props) {
        this.redis = redis;
        this.persistService = persistService;
        this.props = props;
    }

    private final String consumerNm = "worker-" + UUID.randomUUID().toString().substring(0, 8);
    private volatile boolean running = true;

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        createGroupIfAbsent();
        Thread.ofVirtual().name("rsv-stream-consumer").start(this::consumeLoop);
        log.info("stream consumer started: {}", consumerNm);
    }

    @PreDestroy
    public void shutdown() {
        running = false;
    }

    /**
     * consumer group 생성. 스트림이 아직 없어도 MKSTREAM으로 함께 만든다.
     * Redis가 초기화(FLUSHALL)되거나 스트림이 사라진 뒤에도 재호출로 복구된다.
     */
    private void createGroupIfAbsent() {
        try {
            redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) conn -> {
                conn.streamCommands().xGroupCreate(
                        RedisKeys.RSV_STREAM.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        RedisKeys.RSV_STREAM_GROUP,
                        ReadOffset.from("0"),
                        true);
                return null;
            });
        } catch (RedisSystemException e) {
            log.debug("consumer group already exists");
        }
    }

    private void consumeLoop() {
        StreamReadOptions options = StreamReadOptions.empty()
                .count(props.batchSize())
                .block(Duration.ofMillis(props.blockMs()));
        Consumer consumer = Consumer.from(RedisKeys.RSV_STREAM_GROUP, consumerNm);

        while (running) {
            try {
                List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                        consumer, options,
                        StreamOffset.create(RedisKeys.RSV_STREAM, ReadOffset.lastConsumed()));
                if (records == null || records.isEmpty()) {
                    continue;
                }
                records.forEach(this::handleRecord);
            } catch (Exception e) {
                // 그룹이 사라졌으면(예: Redis 초기화) 재생성 후 계속
                if (isNoGroup(e)) {
                    log.warn("consumer group missing, recreating");
                    createGroupIfAbsent();
                } else {
                    log.error("consume loop error", e);
                }
                sleepQuietly();
            }
        }
    }

    /** 처리 성공 시에만 ack. 예외는 pending에 남긴다 */
    void handleRecord(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        String type = String.valueOf(value.get("type"));
        String payload = String.valueOf(value.get("payload"));
        try {
            persistService.persist(type, payload);
            redis.opsForStream().acknowledge(RedisKeys.RSV_STREAM, RedisKeys.RSV_STREAM_GROUP, record.getId());
        } catch (Exception e) {
            log.error("persist failed: id={} type={}", record.getId(), type, e);
        }
    }

    public String consumerNm() {
        return consumerNm;
    }

    private boolean isNoGroup(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("NOGROUP")) {
                return true;
            }
        }
        return false;
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
