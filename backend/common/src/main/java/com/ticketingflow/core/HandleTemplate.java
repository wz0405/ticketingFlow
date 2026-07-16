package com.ticketingflow.core;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * 처리(CUD·상태전이) 업무 템플릿.
 * 서브클래스는 composeDo에서 스텝 체인만 선언한다.
 * 구조: 공통 선행 스텝 → 분기 → 공통 후행 스텝.
 */
public abstract class HandleTemplate extends BizTemplate {

    @Autowired
    protected BizRegistry registry;

    @Override
    protected final void process(TxData tx) throws Exception {
        composeDo(registry.chainOf(tx), tx);
    }

    protected abstract StepChain composeDo(StepChain chain, TxData tx) throws Exception;
}
