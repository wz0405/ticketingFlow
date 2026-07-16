package com.ticketingflow.booking.biz.read;

import com.ticketingflow.booking.mapper.EventMapper;
import com.ticketingflow.core.ReadTemplate;
import com.ticketingflow.core.TxData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 판매중 이벤트/회차 목록.
 */
@Service("read|" + EventListRead.BEAN_NM)
@RequiredArgsConstructor
public class EventListRead extends ReadTemplate {

    public static final String BEAN_NM = "eventList";

    private final EventMapper eventMapper;

    @Override
    protected void doRead(TxData tx) {
        tx.out("list", eventMapper.selectEventList());
    }
}
