package com.ticketingflow.booking.domain;

import java.math.BigDecimal;

/**
 * 좌석맵 응답 단위. 마스터(DB) + 실시간 상태(Redis) 병합 결과.
 * stCd: A=가능 H=선점중 S=판매완료, mine=본인 선점/구매 여부.
 */
public record SeatView(String seatNo, String seatGrdCd, BigDecimal seatPrc, String stCd, boolean mine) {
}
