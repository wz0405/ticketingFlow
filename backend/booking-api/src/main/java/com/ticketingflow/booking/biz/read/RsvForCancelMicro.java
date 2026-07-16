package com.ticketingflow.booking.biz.read;

import com.ticketingflow.booking.domain.RsvForCancel;
import com.ticketingflow.booking.dto.Requests.RsvCancelReq;
import com.ticketingflow.booking.mapper.RsvMapper;
import com.ticketingflow.common.BizException;
import com.ticketingflow.common.RsltCd;
import com.ticketingflow.core.ReadTemplate;
import com.ticketingflow.core.TxData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 취소 대상 예매 원장 조회 + 소유/중복취소 검증.
 * procData(rsv, rsvSeats) 적재.
 */
@Service("read|" + RsvForCancelMicro.BEAN_NM)
@RequiredArgsConstructor
public class RsvForCancelMicro extends ReadTemplate {

    public static final String BEAN_NM = "rsvForCancelMicro";

    private final RsvMapper rsvMapper;

    @Override
    protected void doRead(TxData tx) {
        RsvCancelReq req = tx.asReq(RsvCancelReq.class);
        RsvForCancel rsv = rsvMapper.selectRsvForCancel(req.rsvNo());
        if (rsv == null) {
            throw new BizException(RsltCd.RSV_NOT_FOUND);
        }
        if (!req.usrId().equals(rsv.usrId())) {
            throw new BizException(RsltCd.HOLD_NOT_OWNER, "본인 예매만 취소할 수 있습니다");
        }
        if (rsv.cnclCnt() > 0) {
            throw new BizException(RsltCd.ALREADY_CANCELED);
        }
        tx.proc("rsv", rsv);
        tx.proc("rsvSeats", rsvMapper.selectRsvSeats(req.rsvNo()));
    }
}
