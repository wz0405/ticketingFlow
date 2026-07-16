package com.ticketingflow.event;

import java.math.BigDecimal;

/**
 * 주문 확정/취소 이벤트에 실리는 상품 단위. qty는 양수(취소 부호는 워커가 적용).
 */
public record ItemLine(String prdNo, int qty, BigDecimal prdPrc) {
}
