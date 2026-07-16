package com.ticketingflow.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정. secret은 서비스 간 동일해야 하며 환경변수로 주입한다.
 */
@ConfigurationProperties(prefix = "ticketingflow.jwt")
public record JwtProps(String secret, long expiryMinutes) {
}
