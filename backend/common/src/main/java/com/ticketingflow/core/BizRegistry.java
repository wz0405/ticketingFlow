package com.ticketingflow.core;

import com.ticketingflow.common.BizException;
import com.ticketingflow.common.RsltCd;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * IBiz 빈 레지스트리. 기동 시 "{type}|{bizNm}" 빈명을 전수 색인하고
 * 실제 빈은 호출 시점에 조회한다(즉시 인스턴스화 시 템플릿과 순환 참조).
 * bizNm 중복은 기동 실패.
 */
@Slf4j
@Component
public class BizRegistry {

    private final ConfigurableListableBeanFactory beanFactory;
    private final Map<String, String> byBizNm = new HashMap<>();

    public BizRegistry(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        for (String beanNm : beanFactory.getBeanNamesForType(IBiz.class, true, false)) {
            if (!beanNm.contains("|")) {
                continue;
            }
            String bizNm = beanNm.substring(beanNm.indexOf('|') + 1);
            String dup = byBizNm.putIfAbsent(bizNm, beanNm);
            if (dup != null) {
                throw new IllegalStateException("bizNm 중복: " + bizNm + " (" + dup + ", " + beanNm + ")");
            }
        }
        log.info("biz beans registered: {}", byBizNm.keySet().stream().sorted().toList());
    }

    public IBiz get(String nm) {
        String beanNm = nm.contains("|") ? nm : byBizNm.get(nm);
        if (beanNm == null || !beanFactory.containsBean(beanNm)) {
            throw new BizException(RsltCd.INVALID_PARAM, "미등록 업무: " + nm);
        }
        return (IBiz) beanFactory.getBean(beanNm);
    }

    public StepChain chainOf(TxData tx) {
        return new StepChain(tx, this);
    }
}
