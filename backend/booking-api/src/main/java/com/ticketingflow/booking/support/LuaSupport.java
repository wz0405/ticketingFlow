package com.ticketingflow.booking.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketingflow.common.BizException;
import com.ticketingflow.common.RsltCd;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Lua 실행 결과 판정 + Stream payload 직렬화.
 * 결과 규약: [0]=코드, [1]=실패 대상(seatNo/prdNo)
 */
@Component
@RequiredArgsConstructor
public class LuaSupport {

    private final ObjectMapper objectMapper;

    public void guard(List<?> rslt) {
        int code = rslt == null || rslt.isEmpty() ? 9 : ((Number) rslt.get(0)).intValue();
        String detail = rslt != null && rslt.size() > 1 ? String.valueOf(rslt.get(1)) : "";
        switch (code) {
            case 0 -> { }
            case 1 -> throw new BizException(RsltCd.ENTRY_EXPIRED);
            case 2 -> throw new BizException(RsltCd.SEAT_TAKEN, "선점 불가 좌석: " + detail);
            case 3 -> throw new BizException(RsltCd.HOLD_EXPIRED, "선점 만료 또는 미소유 좌석: " + detail);
            case 4 -> throw new BizException(RsltCd.SEAT_TAKEN, "재고 부족 상품: " + detail);
            default -> throw new BizException(RsltCd.SYSTEM_ERROR);
        }
    }

    public String json(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BizException(RsltCd.SYSTEM_ERROR, "payload 직렬화 실패");
        }
    }
}
