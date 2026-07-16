package com.ticketingflow.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 원장 적재 워커.
 * stream:rsv 를 consumer group으로 소비해 TBTR_* 원장에 INSERT-only 적재한다.
 * 멱등성은 원장 PK(INSERT IGNORE)로 보장하고, 유실은 pending 재처리로 방지한다.
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.ticketingflow", exclude = {RedisAutoConfiguration.class, RedisReactiveAutoConfiguration.class})
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
