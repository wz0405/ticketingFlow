package com.ticketingflow.queue.netty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketingflow.common.RedisKeys;
import com.ticketingflow.queue.config.QueueProps;
import com.ticketingflow.redis.RedisConfig;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 접속 세션에 순번을 주기 푸시한다.
 * 회차당 ZRANGE 1회로 순번맵을 만들어 세션 수와 무관하게 Redis 부하를 고정한다.
 * 입장이 확인된 세션에는 entryToken을 보내고 연결을 닫는다.
 */
@Slf4j
@Component
public class QueueWsBroadcaster {

    private final WsSessionRegistry registry;
    private final StringRedisTemplate queueRedis;
    private final StringRedisTemplate activeRedis;
    private final QueueProps props;
    private final ObjectMapper objectMapper;

    public QueueWsBroadcaster(WsSessionRegistry registry,
                              @Qualifier(RedisConfig.QUEUE) StringRedisTemplate queueRedis,
                              @Qualifier(RedisConfig.ACTIVE) StringRedisTemplate activeRedis,
                              QueueProps props, ObjectMapper objectMapper) {
        this.registry = registry;
        this.queueRedis = queueRedis;
        this.activeRedis = activeRedis;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${ticketingflow.queue.ws-push-ms}")
    public void pushTick() {
        registry.bySchd().forEach(this::pushForSchd);
    }

    private void pushForSchd(String schdNo, List<WsSession> sessions) {
        List<String> waiting = queueRedis.opsForZSet()
                .range(RedisKeys.waitingQueue(schdNo), 0, -1)
                .stream().toList();
        Map<String, Long> rankMap = new HashMap<>();
        for (int i = 0; i < waiting.size(); i++) {
            rankMap.put(waiting.get(i), (long) i + 1);
        }

        List<String> activeKeys = sessions.stream()
                .map(s -> RedisKeys.active(schdNo, s.usrId()))
                .toList();
        List<String> tokens = activeRedis.opsForValue().multiGet(activeKeys);

        double ratePerSec = props.admitPerTick() * (1000.0 / props.tickMs());
        for (int i = 0; i < sessions.size(); i++) {
            WsSession session = sessions.get(i);
            String token = tokens == null ? null : tokens.get(i);
            Map<String, Object> msg = buildMessage(rankMap, session.usrId(), token, waiting.size(), ratePerSec);
            session.channel().writeAndFlush(new TextWebSocketFrame(toJson(msg)));
            if (token != null) {
                session.channel().close();
            }
        }
    }

    private Map<String, Object> buildMessage(Map<String, Long> rankMap, String usrId,
                                             String token, int totalWaiting, double ratePerSec) {
        Map<String, Object> msg = new LinkedHashMap<>();
        if (token != null) {
            msg.put("admitted", true);
            msg.put("inQueue", true);
            msg.put("entryToken", token);
            return msg;
        }
        Long position = rankMap.get(usrId);
        msg.put("admitted", false);
        msg.put("inQueue", position != null);
        if (position != null) {
            msg.put("position", position);
            msg.put("totalWaiting", totalWaiting);
            msg.put("etaSec", (long) Math.ceil(position / ratePerSec));
        }
        return msg;
    }

    private String toJson(Map<String, Object> msg) {
        try {
            return objectMapper.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
