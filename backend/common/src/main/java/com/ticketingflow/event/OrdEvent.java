package com.ticketingflow.event;

import java.math.BigDecimal;
import java.util.List;

/**
 * 주문 확정/취소 스트림 이벤트 payload.
 * 취소 이벤트는 ordNo=취소번호, oordNo=원주문번호, totAmt/itemCnt는 음수.
 */
public record OrdEvent(
        String ordNo,
        String oordNo,
        String usrId,
        String schdNo,
        long trxDt,
        BigDecimal totAmt,
        int itemCnt,
        List<ItemLine> items) {
}
