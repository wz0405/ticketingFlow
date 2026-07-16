package com.ticketingflow.booking.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

/**
 * 좌석/재고 처리는 전부 Lua로 단일 라운드트립·원자 실행한다.
 * 반환값 규약: List — [0]=결과코드(0 정상), [1]=실패 대상(seatNo/prdNo)
 */
@Configuration
@EnableConfigurationProperties(BookingProps.class)
public class LuaScriptConfig {

    @Bean
    public DefaultRedisScript<List> seatHoldScript() {
        return script("lua/seat_hold.lua");
    }

    @Bean
    public DefaultRedisScript<List> rsvConfirmScript() {
        return script("lua/rsv_confirm.lua");
    }

    @Bean
    public DefaultRedisScript<List> seatReleaseScript() {
        return script("lua/seat_release.lua");
    }

    @Bean
    public DefaultRedisScript<List> ordConfirmScript() {
        return script("lua/ord_confirm.lua");
    }

    @Bean
    public DefaultRedisScript<List> stockRestoreScript() {
        return script("lua/stock_restore.lua");
    }

    private DefaultRedisScript<List> script(String path) {
        DefaultRedisScript<List> s = new DefaultRedisScript<>();
        s.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        s.setResultType(List.class);
        return s;
    }
}
