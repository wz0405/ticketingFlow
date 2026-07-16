package com.ticketingflow.common;

/**
 * Redis 키 네임스페이스 정의.
 *
 * <pre>
 * wq:{schdNo}                ZSET   대기열 (score = 진입 epoch millis, FIFO)
 * wq:schds                   SET    대기열이 존재하는 회차 레지스트리 (스케줄러 순회용)
 * active:{schdNo}:{usrId}    STRING 입장 허가 (value = entryToken, TTL = 입장 유효시간)
 * seat:{schdNo}              HASH   좌석 상태 (field = seatNo)
 *                                     "A"                     : 예매가능
 *                                     "H:{usrId}:{expireMs}"  : 선점(lazy expiry)
 *                                     "S:{usrId}"             : 판매완료
 * stock:{schdNo}             HASH   수량제 상품 잔여재고 (field = prdNo, value = 잔량)
 * rsvseq:{yyMMdd}            STRING 예매/주문번호 채번 (INCR, TTL 2일)
 * stream:rsv                 STREAM 예매/주문 확정 이벤트 (worker가 RDB 적재)
 * </pre>
 */
public final class RedisKeys {

    public static final String WQ_SCHD_REGISTRY = "wq:schds";
    public static final String RSV_STREAM = "stream:rsv";
    public static final String RSV_STREAM_GROUP = "rsv-workers";

    public static final String SEAT_AVAILABLE = "A";
    public static final String SEAT_HOLD_PREFIX = "H:";
    public static final String SEAT_SOLD_PREFIX = "S:";

    private RedisKeys() {
    }

    public static String waitingQueue(String schdNo) {
        return "wq:" + schdNo;
    }

    public static String active(String schdNo, String usrId) {
        return "active:" + schdNo + ":" + usrId;
    }

    public static String seatHash(String schdNo) {
        return "seat:" + schdNo;
    }

    public static String stockHash(String schdNo) {
        return "stock:" + schdNo;
    }

    public static String rsvSeq(String yyMMdd) {
        return "rsvseq:" + yyMMdd;
    }
}
