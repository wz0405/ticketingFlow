package com.ticketingflow.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * 역할별 Redis 접속 정보.
 * 대기열·입장(active)·예매(좌석/재고/스트림)를 물리적으로 분리해
 * 부하와 장애 도메인을 나눈다.
 */
@ConfigurationProperties(prefix = "ticketingflow.redis")
public class RedisProps {

    @NestedConfigurationProperty
    private Node queue = new Node();
    @NestedConfigurationProperty
    private Node active = new Node();
    @NestedConfigurationProperty
    private Node booking = new Node();

    public Node getQueue() {
        return queue;
    }

    public Node getActive() {
        return active;
    }

    public Node getBooking() {
        return booking;
    }

    public static class Node {
        private String host = "localhost";
        private int port = 6379;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }
}
