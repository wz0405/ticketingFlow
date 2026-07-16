package com.ticketingflow.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 표준 결과코드. "0000" = 정상, 그 외 = 업무 오류.
 */
@Getter
@RequiredArgsConstructor
public enum RsltCd {

    SUCCESS("0000", "정상 처리되었습니다"),
    ACCEPTED("0001", "예매가 접수되었습니다"),

    INVALID_PARAM("1001", "요청 값이 올바르지 않습니다"),
    USER_NOT_FOUND("1002", "회원을 찾을 수 없습니다"),
    UNAUTHORIZED("1003", "로그인이 필요합니다"),

    NOT_ADMITTED("2001", "아직 입장 순서가 아닙니다"),
    ENTRY_EXPIRED("2002", "입장 유효시간이 만료되었습니다. 대기열에 다시 진입해 주세요"),
    ALREADY_IN_QUEUE("2003", "이미 대기열에 등록되어 있습니다"),

    SEAT_TAKEN("3001", "이미 선점되었거나 판매된 좌석이 있습니다"),
    HOLD_EXPIRED("3002", "좌석 선점 시간이 만료되었습니다"),
    HOLD_NOT_OWNER("3003", "본인이 선점한 좌석이 아닙니다"),
    RSV_NOT_FOUND("3004", "예매 내역을 찾을 수 없습니다"),
    ALREADY_CANCELED("3005", "이미 취소된 예매입니다"),

    SYSTEM_ERROR("9999", "시스템 오류가 발생했습니다");

    private final String cd;
    private final String msg;
}
