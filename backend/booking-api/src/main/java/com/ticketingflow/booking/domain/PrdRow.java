package com.ticketingflow.booking.domain;

import java.math.BigDecimal;

public record PrdRow(String prdNo, String prdNm, String prdTypeCd, BigDecimal prdPrc) {
}
