package com.ticketingflow.core;

import com.ticketingflow.auth.AuthInterceptor;
import com.ticketingflow.common.ApiResponse;
import com.ticketingflow.common.RsltCd;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 단일 진입 디스패처. URL 경로의 bizNm이 곧 업무 빈명이다.
 * POST /api/{bizNm} + JSON body → TxData.inData → 빈 실행 → outData 응답
 *
 * 인증된 요청이면 토큰에서 확인된 usrId를 inData에 덮어써, 업무 로직이
 * body가 아니라 검증된 신원을 쓰게 한다.
 */
@RestController
@RequiredArgsConstructor
public class BizDispatchController {

    private final BizRegistry registry;

    @PostMapping("/api/{bizNm}")
    public ApiResponse<Map<String, Object>> dispatch(@PathVariable("bizNm") String bizNm,
                                                     @RequestBody(required = false) Map<String, Object> body,
                                                     HttpServletRequest request)
            throws Exception {
        TxData tx = TxData.of(body);
        Object authUsrId = request.getAttribute(AuthInterceptor.ATTR_USR_ID);
        if (authUsrId != null) {
            tx.getInData().put("usrId", authUsrId);
        }
        registry.get(bizNm).execute(tx);
        String rsltCd = (String) tx.getProcData().getOrDefault("rsltCd", RsltCd.SUCCESS.getCd());
        String rsltMsg = (String) tx.getProcData().getOrDefault("rsltMsg", RsltCd.SUCCESS.getMsg());
        return new ApiResponse<>(rsltCd, rsltMsg, tx.getOutData());
    }
}
