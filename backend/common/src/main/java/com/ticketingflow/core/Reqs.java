package com.ticketingflow.core;

import com.ticketingflow.common.BizException;
import com.ticketingflow.common.RsltCd;

import java.util.List;

/**
 * 요청 DTO 검증 공용 헬퍼.
 */
public final class Reqs {

    private Reqs() {
    }

    public static void required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(RsltCd.INVALID_PARAM, "필수 값 누락: " + field);
        }
    }

    public static <T> void notEmpty(List<T> value, String field) {
        if (value == null || value.isEmpty()) {
            throw new BizException(RsltCd.INVALID_PARAM, "필수 목록 누락: " + field);
        }
    }
}
