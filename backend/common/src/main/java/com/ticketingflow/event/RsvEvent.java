package com.ticketingflow.event;

import java.math.BigDecimal;
import java.util.List;

/**
 * 예매 확정/취소 스트림 이벤트 payload.
 * 예매 서버가 Lua 안에서 발행하고 워커가 원장으로 적재한다.
 * 취소 이벤트는 rsvNo=취소번호, orsvNo=원예매번호, totAmt는 음수.
 */
public record RsvEvent(
        String rsvNo,
        String orsvNo,
        String usrId,
        String schdNo,
        long trxDt,
        BigDecimal totAmt,
        List<SeatLine> seats) {
}
