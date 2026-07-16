package com.ticketingflow.redis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 역할별 Redis 3계통을 각각의 커넥션 팩토리·템플릿으로 연결한다.
 *
 *   queueRedis   대기열(ZSET). 진입 폭주를 받아내는 계통.
 *   activeRedis  입장 완료 회원(active, TTL). 예매 가능 여부의 게이트.
 *   bookingRedis 좌석/재고(Hash+Lua) + 확정 스트림. 원자 거래 계통.
 *
 * 물리 분리로 대기열 트래픽이 좌석 원자연산이나 스트림 내구성에 영향을 주지 않게 한다.
 * 커넥션 팩토리는 Spring 빈으로 등록해 라이프사이클(start/stop)을 컨테이너가 관리한다.
 */
@Configuration
@EnableConfigurationProperties(RedisProps.class)
public class RedisConfig {

    public static final String QUEUE = "queueRedis";
    public static final String ACTIVE = "activeRedis";
    public static final String BOOKING = "bookingRedis";

    @Bean
    public LettuceConnectionFactory queueConnectionFactory(RedisProps props) {
        return factory(props.getQueue());
    }

    @Bean
    public LettuceConnectionFactory activeConnectionFactory(RedisProps props) {
        return factory(props.getActive());
    }

    @Bean
    public LettuceConnectionFactory bookingConnectionFactory(RedisProps props) {
        return factory(props.getBooking());
    }

    @Bean(QUEUE)
    public StringRedisTemplate queueRedisTemplate(LettuceConnectionFactory queueConnectionFactory) {
        return new StringRedisTemplate(queueConnectionFactory);
    }

    @Bean(ACTIVE)
    public StringRedisTemplate activeRedisTemplate(LettuceConnectionFactory activeConnectionFactory) {
        return new StringRedisTemplate(activeConnectionFactory);
    }

    @Bean(BOOKING)
    public StringRedisTemplate bookingRedisTemplate(LettuceConnectionFactory bookingConnectionFactory) {
        return new StringRedisTemplate(bookingConnectionFactory);
    }

    private LettuceConnectionFactory factory(RedisProps.Node node) {
        return new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(node.getHost(), node.getPort()));
    }
}
