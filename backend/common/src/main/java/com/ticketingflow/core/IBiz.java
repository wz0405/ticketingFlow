package com.ticketingflow.core;

/**
 * 업무 빈 공통 인터페이스.
 * 빈명은 "{type}|{bizNm}" 규약을 따른다. 예) handle|rsvConfirm, read|seatList
 */
public interface IBiz {

    void execute(TxData tx) throws Exception;
}
