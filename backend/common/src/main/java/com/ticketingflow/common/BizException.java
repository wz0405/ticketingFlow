package com.ticketingflow.common;

import lombok.Getter;

/**
 * 업무 예외. RsltCd를 실어 표준 응답으로 변환된다.
 */
@Getter
public class BizException extends RuntimeException {

    private final RsltCd rsltCd;

    public BizException(RsltCd rsltCd) {
        super(rsltCd.getMsg());
        this.rsltCd = rsltCd;
    }

    public BizException(RsltCd rsltCd, String detailMsg) {
        super(detailMsg);
        this.rsltCd = rsltCd;
    }
}
