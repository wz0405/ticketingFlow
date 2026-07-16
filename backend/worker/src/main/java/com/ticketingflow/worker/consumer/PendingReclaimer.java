package com.ticketingflow.worker.consumer;

import com.ticketingflow.common.RedisKeys;
import com.ticketingflow.redis.RedisConfig;
import com.ticketingflow.worker.config.WorkerProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 죽은 컨슈머의 pending 메시지 회수.
 * idle 기준 초과 건을 claim해 재처리하고, 재전달 한도를 넘긴 건은 DLQ로 옮긴다.
 */
@Slf4j
@Component
public class PendingReclaimer {

    private static final String DLQ_STREAM = RedisKeys.RSV_STREAM + ":dlq";

    private final StringRedisTemplate redis;
    private final RsvStreamConsumer consumer;
    private final WorkerProps props;

    public PendingReclaimer(@Qualifier(RedisConfig.BOOKING) StringRedisTemplate redis,
                            RsvStreamConsumer consumer, WorkerProps props) {
        this.redis = redis;
        this.consumer = consumer;
        this.props = props;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void reclaim() {
        PendingMessages pendings = redis.opsForStream().pending(
                RedisKeys.RSV_STREAM,
                RedisKeys.RSV_STREAM_GROUP,
                Range.unbounded(), 100);
        if (pendings == null || pendings.isEmpty()) {
            return;
        }
        pendings.forEach(this::reclaimOne);
    }

    private void reclaimOne(PendingMessage pending) {
        if (pending.getElapsedTimeSinceLastDelivery().toMillis() < props.reclaimIdleMs()) {
            return;
        }
        List<MapRecord<String, Object, Object>> claimed = redis.opsForStream().claim(
                RedisKeys.RSV_STREAM,
                RedisKeys.RSV_STREAM_GROUP,
                consumer.consumerNm(),
                Duration.ofMillis(props.reclaimIdleMs()),
                pending.getId());
        if (claimed == null || claimed.isEmpty()) {
            return;
        }
        MapRecord<String, Object, Object> record = claimed.get(0);
        if (pending.getTotalDeliveryCount() > props.maxDelivery()) {
            moveToDlq(record);
            return;
        }
        log.warn("reclaiming pending message: id={} deliveries={}",
                pending.getId(), pending.getTotalDeliveryCount());
        consumer.handleRecord(record);
    }

    private void moveToDlq(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        redis.opsForStream().add(StreamRecords.newRecord().in(DLQ_STREAM).ofMap(value));
        redis.opsForStream().acknowledge(RedisKeys.RSV_STREAM, RedisKeys.RSV_STREAM_GROUP, record.getId());
        log.error("moved to dlq: id={}", record.getId());
    }
}
