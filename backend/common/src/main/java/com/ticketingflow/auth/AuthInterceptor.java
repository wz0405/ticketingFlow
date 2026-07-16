package com.ticketingflow.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketingflow.common.ApiResponse;
import com.ticketingflow.common.RsltCd;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * /api/{bizNm} 진입 전 신원 확인.
 * 공개 업무를 제외한 나머지는 Bearer 토큰을 요구하고,
 * 검증된 usrId를 request 속성으로 넘겨 dispatcher가 신뢰하게 한다.
 * body의 usrId는 신뢰하지 않는다(위조 방지).
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USR_ID = "authUsrId";

    // demoLoad: 로그인 화면의 대기열 부하 데모 — 무인증 허용 (투입량은 핸들에서 제한)
    private static final Set<String> PUBLIC_BIZ = Set.of("usrLogin", "eventList", "demoLoad");

    private final JwtSupport jwtSupport;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String bizNm = bizNmOf(request);
        if (bizNm == null || PUBLIC_BIZ.contains(bizNm)) {
            return true;
        }
        String token = bearer(request);
        if (token == null) {
            return reject(response);
        }
        request.setAttribute(ATTR_USR_ID, jwtSupport.verifyUsrId(token));
        return true;
    }

    private String bizNmOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int idx = uri.indexOf("/api/");
        return idx < 0 ? null : uri.substring(idx + 5);
    }

    private String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }

    private boolean reject(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(RsltCd.UNAUTHORIZED)));
        return false;
    }
}
