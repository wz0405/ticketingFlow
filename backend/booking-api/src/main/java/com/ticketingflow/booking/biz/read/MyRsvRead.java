package com.ticketingflow.booking.biz.read;

import com.ticketingflow.booking.dto.Requests.UsrReq;
import com.ticketingflow.booking.mapper.RsvMapper;
import com.ticketingflow.core.ReadTemplate;
import com.ticketingflow.core.TxData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 내 예매 내역. 취소 row 존재 여부로 표시 상태를 계산한다(원장 UPDATE 없음).
 */
@Service("read|" + MyRsvRead.BEAN_NM)
@RequiredArgsConstructor
public class MyRsvRead extends ReadTemplate {

    public static final String BEAN_NM = "myRsv";

    private final RsvMapper rsvMapper;

    @Override
    protected void doRead(TxData tx) {
        tx.out("list", rsvMapper.selectMyRsvs(tx.asReq(UsrReq.class).usrId()));
    }
}
