package com.ticketingflow.booking.domain;

import java.math.BigDecimal;

public record RsvForCancel(
        String rsvNo,
        String usrId,
        String schdNo,
        BigDecimal totAmt,
        int seatCnt,
        int cnclCnt) {
}
