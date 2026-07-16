package com.ticketingflow.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;

/**
 * 예매 서버.
 * 좌석 선점/확정(Lua 원자 처리), 수량제 상품 재고 차감, 조회 API를 담당한다.
 * 확정 건은 Redis Streams로 발행만 하고 응답하며(202), RDB 적재는 worker가 수행한다.
 */
@SpringBootApplication(scanBasePackages = "com.ticketingflow", exclude = {RedisAutoConfiguration.class, RedisReactiveAutoConfiguration.class})
public class BookingApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingApiApplication.class, args);
    }
}
