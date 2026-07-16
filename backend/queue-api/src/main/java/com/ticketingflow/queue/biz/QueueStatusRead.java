package com.ticketingflow.queue.biz;

import com.ticketingflow.core.ReadTemplate;
import com.ticketingflow.core.Reqs;
import com.ticketingflow.core.TxData;
import com.ticketingflow.queue.support.QueueRedisSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 대기 순번/입장 상태 조회 (폴링).
 */
@Service("read|" + QueueStatusRead.BEAN_NM)
@RequiredArgsConstructor
public class QueueStatusRead extends ReadTemplate {

    public static final String BEAN_NM = "queueStatus";

    private final QueueRedisSupport queueRedis;

    @Override
    protected void validate(TxData tx) {
        Reqs.required(tx.inString("schdNo"), "schdNo");
        Reqs.required(tx.inString("usrId"), "usrId");
    }

    @Override
    protected void doRead(TxData tx) {
        tx.getOutData().putAll(queueRedis.status(tx.inString("schdNo"), tx.inString("usrId")));
    }
}
