package com.ticketingflow.booking.dto;

import java.util.List;

/**
 * 예매 서버 요청 DTO 모음. dispatcher가 inData(map)를 이 타입으로 변환한다.
 */
public final class Requests {

    private Requests() {
    }

    public record LoginReq(String usrNm) {
    }

    public record SchdReq(String schdNo) {
    }

    public record SchdUsrReq(String schdNo, String usrId) {
    }

    public record UsrReq(String usrId) {
    }

    public record SeatActionReq(String schdNo, String usrId, String entryToken, List<String> seatNos) {
    }

    public record RsvCancelReq(String rsvNo, String usrId) {
    }

    public record OrdItemReq(String prdNo, int qty) {
    }

    public record OrdReq(String schdNo, String usrId, String entryToken, List<OrdItemReq> items) {
    }

    public record OrdCancelReq(String ordNo, String usrId) {
    }
}
