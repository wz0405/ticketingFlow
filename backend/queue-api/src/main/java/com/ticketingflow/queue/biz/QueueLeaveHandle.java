package com.ticketingflow.queue.biz;

import com.ticketingflow.core.HandleTemplate;
import com.ticketingflow.core.Reqs;
import com.ticketingflow.core.StepChain;
import com.ticketingflow.core.TxData;
import com.ticketingflow.queue.support.QueueRedisSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 대기열 이탈.
 */
@Service("handle|" + QueueLeaveHandle.BEAN_NM)
@RequiredArgsConstructor
public class QueueLeaveHandle extends HandleTemplate {

    public static final String BEAN_NM = "queueLeave";

    private final QueueRedisSupport queueRedis;

    @Override
    protected void validate(TxData tx) {
        Reqs.required(tx.inString("schdNo"), "schdNo");
        Reqs.required(tx.inString("usrId"), "usrId");
    }

    @Override
    protected StepChain composeDo(StepChain chain, TxData tx) throws Exception {
        return chain.next(t -> queueRedis.leave(t.inString("schdNo"), t.inString("usrId")));
    }
}
