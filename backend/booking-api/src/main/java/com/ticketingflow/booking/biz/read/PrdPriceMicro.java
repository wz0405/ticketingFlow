package com.ticketingflow.booking.biz.read;

import com.ticketingflow.booking.domain.PrdPrice;
import com.ticketingflow.booking.dto.Requests.OrdItemReq;
import com.ticketingflow.booking.dto.Requests.OrdReq;
import com.ticketingflow.booking.mapper.EventMapper;
import com.ticketingflow.common.BizException;
import com.ticketingflow.common.RsltCd;
import com.ticketingflow.core.ReadTemplate;
import com.ticketingflow.core.TxData;
import com.ticketingflow.event.ItemLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 주문 품목 단가를 조회해 procData(itemLines, totAmt, itemCnt)에 적재한다.
 */
@Service("read|" + PrdPriceMicro.BEAN_NM)
@RequiredArgsConstructor
public class PrdPriceMicro extends ReadTemplate {

    public static final String BEAN_NM = "prdPriceMicro";

    private final EventMapper eventMapper;

    @Override
    protected void doRead(TxData tx) {
        OrdReq req = tx.asReq(OrdReq.class);
        List<String> prdNos = req.items().stream().map(OrdItemReq::prdNo).toList();
        Map<String, BigDecimal> prcMap = eventMapper.selectPrdPrices(req.schdNo(), prdNos).stream()
                .collect(Collectors.toMap(PrdPrice::prdNo, PrdPrice::prdPrc));

        BigDecimal totAmt = BigDecimal.ZERO;
        int itemCnt = 0;
        List<ItemLine> itemLines = new java.util.ArrayList<>();
        for (OrdItemReq item : req.items()) {
            BigDecimal prc = prcMap.get(item.prdNo());
            if (prc == null) {
                throw new BizException(RsltCd.INVALID_PARAM, "존재하지 않는 상품: " + item.prdNo());
            }
            if (item.qty() < 1) {
                throw new BizException(RsltCd.INVALID_PARAM, "잘못된 수량: " + item.prdNo());
            }
            itemLines.add(new ItemLine(item.prdNo(), item.qty(), prc));
            totAmt = totAmt.add(prc.multiply(BigDecimal.valueOf(item.qty())));
            itemCnt += item.qty();
        }
        tx.proc("itemLines", itemLines);
        tx.proc("totAmt", totAmt);
        tx.proc("itemCnt", itemCnt);
    }
}
