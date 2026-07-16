package com.ticketingflow.booking.domain;

import java.math.BigDecimal;

public record OrdForCancel(
        String ordNo,
        String usrId,
        String schdNo,
        BigDecimal totAmt,
        int itemCnt,
        int cnclCnt) {
}
