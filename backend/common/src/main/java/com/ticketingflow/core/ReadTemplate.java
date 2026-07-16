package com.ticketingflow.core;

/**
 * 조회 업무 템플릿. 단건/목록 결과를 outData에 적재한다.
 */
public abstract class ReadTemplate extends BizTemplate {

    @Override
    protected final void process(TxData tx) throws Exception {
        doRead(tx);
    }

    protected abstract void doRead(TxData tx) throws Exception;
}
