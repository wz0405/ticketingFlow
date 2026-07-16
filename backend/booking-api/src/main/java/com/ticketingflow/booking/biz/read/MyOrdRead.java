package com.ticketingflow.booking.biz.read;

import com.ticketingflow.booking.dto.Requests.UsrReq;
import com.ticketingflow.booking.mapper.OrdMapper;
import com.ticketingflow.core.ReadTemplate;
import com.ticketingflow.core.TxData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 내 주문 내역.
 */
@Service("read|" + MyOrdRead.BEAN_NM)
@RequiredArgsConstructor
public class MyOrdRead extends ReadTemplate {

    public static final String BEAN_NM = "myOrd";

    private final OrdMapper ordMapper;

    @Override
    protected void doRead(TxData tx) {
        tx.out("list", ordMapper.selectMyOrds(tx.asReq(UsrReq.class).usrId()));
    }
}
