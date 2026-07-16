package com.ticketingflow.queue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 대기열 서버.
 * 진입/순번조회 API와 입장 승격 스케줄러를 담당한다.
 * 상태는 전부 Redis에 있으므로 무상태 수평 확장이 가능하다
 * (스케줄러는 다중 인스턴스 시 분산락 필요 — TRADEOFFS.md 참고).
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.ticketingflow", exclude = {RedisAutoConfiguration.class, RedisReactiveAutoConfiguration.class})
public class QueueApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueueApiApplication.class, args);
    }
}
