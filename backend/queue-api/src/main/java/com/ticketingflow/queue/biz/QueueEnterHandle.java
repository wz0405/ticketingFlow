package com.ticketingflow.queue.biz;

import com.ticketingflow.core.HandleTemplate;
import com.ticketingflow.core.Reqs;
import com.ticketingflow.core.StepChain;
import com.ticketingflow.core.TxData;
import com.ticketingflow.queue.support.QueueRedisSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 대기열 진입.
 */
@Service("handle|" + QueueEnterHandle.BEAN_NM)
@RequiredArgsConstructor
public class QueueEnterHandle extends HandleTemplate {

    public static final String BEAN_NM = "queueEnter";

    private final QueueRedisSupport queueRedis;

    @Override
    protected void validate(TxData tx) {
        Reqs.required(tx.inString("schdNo"), "schdNo");
        Reqs.required(tx.inString("usrId"), "usrId");
    }

    @Override
    protected StepChain composeDo(StepChain chain, TxData tx) throws Exception {
        return chain.next(t ->
                t.getOutData().putAll(queueRedis.enter(t.inString("schdNo"), t.inString("usrId"))));
    }
}
