package com.ticketingflow.core;

/**
 * 스텝 체인. HandleTemplate.composeDo에서 업무 빈들을 순차 연결한다.
 * 스텝은 next() 호출 시점에 즉시 실행되며, 예외는 그대로 전파되어 체인을 중단한다.
 */
public final class StepChain {

    private final TxData tx;
    private final BizRegistry registry;

    StepChain(TxData tx, BizRegistry registry) {
        this.tx = tx;
        this.registry = registry;
    }

    /** 빈명("read|seatPriceMicro" 또는 bizNm 단축형)으로 스텝 실행 */
    public StepChain next(String beanNm) throws Exception {
        registry.get(beanNm).execute(tx);
        return this;
    }

    /** 조건부 스텝. cond=false면 건너뛴다 */
    public StepChain nextIf(boolean cond, String beanNm) throws Exception {
        return cond ? next(beanNm) : this;
    }

    /** 인라인 스텝. 별도 빈으로 승격할 정도가 아닌 소규모 가공에 사용 */
    public StepChain next(IBiz step) throws Exception {
        step.execute(tx);
        return this;
    }
}
