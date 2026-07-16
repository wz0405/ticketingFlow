package com.ticketingflow.queue.netty;

import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 접속 중인 WS 세션 관리. 채널 close 시 자동 제거된다.
 */
@Component
public class WsSessionRegistry {

    private final Map<Channel, WsSession> sessions = new ConcurrentHashMap<>();

    public void add(WsSession session) {
        sessions.put(session.channel(), session);
        session.channel().closeFuture().addListener(f -> sessions.remove(session.channel()));
    }

    public void remove(Channel channel) {
        sessions.remove(channel);
    }

    /** schdNo별 세션 그룹 (브로드캐스터 순회용) */
    public Map<String, List<WsSession>> bySchd() {
        return sessions.values().stream().collect(Collectors.groupingBy(WsSession::schdNo));
    }

    public int size() {
        return sessions.size();
    }
}
