package com.ticketingflow.core;

/**
 * 업무 템플릿 최상위. 파라미터 검증 → 본처리 순서를 고정한다.
 */
public abstract class BizTemplate implements IBiz {

    @Override
    public final void execute(TxData tx) throws Exception {
        validate(tx);
        process(tx);
    }

    /** 필수 파라미터 검증. 기본은 무검증 */
    protected void validate(TxData tx) {
    }

    protected abstract void process(TxData tx) throws Exception;
}
