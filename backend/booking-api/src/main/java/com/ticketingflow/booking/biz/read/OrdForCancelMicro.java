package com.ticketingflow.booking.biz.read;

import com.ticketingflow.booking.domain.OrdForCancel;
import com.ticketingflow.booking.dto.Requests.OrdCancelReq;
import com.ticketingflow.booking.mapper.OrdMapper;
import com.ticketingflow.common.BizException;
import com.ticketingflow.common.RsltCd;
import com.ticketingflow.core.ReadTemplate;
import com.ticketingflow.core.TxData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 취소 대상 주문 원장 조회 + 소유/중복취소 검증.
 * procData(ord, ordItems) 적재.
 */
@Service("read|" + OrdForCancelMicro.BEAN_NM)
@RequiredArgsConstructor
public class OrdForCancelMicro extends ReadTemplate {

    public static final String BEAN_NM = "ordForCancelMicro";

    private final OrdMapper ordMapper;

    @Override
    protected void doRead(TxData tx) {
        OrdCancelReq req = tx.asReq(OrdCancelReq.class);
        OrdForCancel ord = ordMapper.selectOrdForCancel(req.ordNo());
        if (ord == null) {
            throw new BizException(RsltCd.RSV_NOT_FOUND, "주문 내역을 찾을 수 없습니다");
        }
        if (!req.usrId().equals(ord.usrId())) {
            throw new BizException(RsltCd.HOLD_NOT_OWNER, "본인 주문만 취소할 수 있습니다");
        }
        if (ord.cnclCnt() > 0) {
            throw new BizException(RsltCd.ALREADY_CANCELED);
        }
        tx.proc("ord", ord);
        tx.proc("ordItems", ordMapper.selectOrdItems(req.ordNo()));
    }
}
