package com.ticketingflow.booking.domain;

import java.math.BigDecimal;

public record OrdItemRow(String prdNo, int ordQty, BigDecimal prdPrc) {
}
