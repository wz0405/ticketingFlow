package com.ticketingflow.booking.domain;

import java.time.LocalDateTime;

public record EventRow(
        String evntNo,
        String evntNm,
        String venueNm,
        String evntStCd,
        String schdNo,
        LocalDateTime schdDt,
        LocalDateTime openDt,
        int totSeatCnt) {
}
