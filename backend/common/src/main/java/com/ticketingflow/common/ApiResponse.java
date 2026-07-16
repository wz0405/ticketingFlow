package com.ticketingflow.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 표준 응답 봉투. {rsltCd, rsltMsg, data}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String rsltCd, String rsltMsg, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(RsltCd.SUCCESS.getCd(), RsltCd.SUCCESS.getMsg(), data);
    }

    public static <T> ApiResponse<T> of(RsltCd rslt, T data) {
        return new ApiResponse<>(rslt.getCd(), rslt.getMsg(), data);
    }

    public static ApiResponse<Void> error(RsltCd rslt) {
        return new ApiResponse<>(rslt.getCd(), rslt.getMsg(), null);
    }

    public static ApiResponse<Void> error(RsltCd rslt, String overrideMsg) {
        return new ApiResponse<>(rslt.getCd(), overrideMsg, null);
    }
}
