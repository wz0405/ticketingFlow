package com.ticketingflow.booking.domain;

import java.math.BigDecimal;

public record PrdView(String prdNo, String prdNm, String prdTypeCd, BigDecimal prdPrc, long remainQty) {
}
