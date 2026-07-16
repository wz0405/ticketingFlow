package com.ticketingflow.event;

import java.math.BigDecimal;

/**
 * 예매 확정/취소 이벤트에 실리는 좌석 단위.
 */
public record SeatLine(String seatNo, BigDecimal seatPrc) {
}
