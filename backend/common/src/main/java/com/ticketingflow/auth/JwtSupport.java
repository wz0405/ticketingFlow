package com.ticketingflow.auth;

import com.ticketingflow.common.BizException;
import com.ticketingflow.common.RsltCd;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * 로그인 시 신원 토큰을 발급하고, 요청 토큰을 검증해 usrId를 돌려준다.
 * HS256 대칭키. 만료/서명 위조는 검증 단계에서 걸러진다.
 */
@Component
public class JwtSupport {

    private final SecretKey key;
    private final long expirySeconds;

    public JwtSupport(JwtProps props) {
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.expirySeconds = props.expiryMinutes() * 60;
    }

    public String issue(String usrId, String usrNm) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(usrId)
                .claim("usrNm", usrNm)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirySeconds)))
                .signWith(key)
                .compact();
    }

    /** 유효하면 usrId(subject) 반환, 아니면 인증 예외 */
    public String verifyUsrId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(RsltCd.UNAUTHORIZED);
        }
    }

    public long expirySeconds() {
        return expirySeconds;
    }
}
