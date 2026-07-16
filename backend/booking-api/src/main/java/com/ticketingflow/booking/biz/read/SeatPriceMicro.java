package com.ticketingflow.booking.biz.read;

import com.ticketingflow.booking.domain.SeatPrice;
import com.ticketingflow.booking.dto.Requests.SeatActionReq;
import com.ticketingflow.booking.mapper.EventMapper;
import com.ticketingflow.common.BizException;
import com.ticketingflow.common.RsltCd;
import com.ticketingflow.core.ReadTemplate;
import com.ticketingflow.core.TxData;
import com.ticketingflow.event.SeatLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 선택 좌석의 단가를 조회해 procData(seatLines, totAmt)에 적재한다.
 * 존재하지 않는 좌석이 섞여 있으면 실패.
 */
@Service("read|" + SeatPriceMicro.BEAN_NM)
@RequiredArgsConstructor
public class SeatPriceMicro extends ReadTemplate {

    public static final String BEAN_NM = "seatPriceMicro";

    private final EventMapper eventMapper;

    @Override
    protected void doRead(TxData tx) {
        SeatActionReq req = tx.asReq(SeatActionReq.class);
        List<SeatPrice> prices = eventMapper.selectSeatPrices(req.schdNo(), req.seatNos());
        if (prices.size() != req.seatNos().size()) {
            throw new BizException(RsltCd.INVALID_PARAM, "존재하지 않는 좌석이 포함되어 있습니다");
        }
        List<SeatLine> seatLines = prices.stream()
                .map(p -> new SeatLine(p.seatNo(), p.seatPrc()))
                .toList();
        BigDecimal totAmt = seatLines.stream()
                .map(SeatLine::seatPrc)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        tx.proc("seatLines", seatLines);
        tx.proc("totAmt", totAmt);
    }
}
