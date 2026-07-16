package com.ticketingflow.booking.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MyRsvRow(
        String rsvNo,
        String schdNo,
        String evntNm,
        LocalDateTime schdDt,
        int seatCnt,
        BigDecimal totAmt,
        LocalDateTime trxDt,
        String rsvStCd,
        String seatNos) {
}
