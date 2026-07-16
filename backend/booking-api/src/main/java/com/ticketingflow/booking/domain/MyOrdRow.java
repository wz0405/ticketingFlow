package com.ticketingflow.booking.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MyOrdRow(
        String ordNo,
        String schdNo,
        String evntNm,
        int itemCnt,
        BigDecimal totAmt,
        LocalDateTime trxDt,
        String ordStCd,
        String itemDesc) {
}
