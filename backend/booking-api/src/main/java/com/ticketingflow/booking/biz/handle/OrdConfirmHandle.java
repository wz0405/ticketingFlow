package com.ticketingflow.booking.biz.handle;

import com.ticketingflow.booking.biz.read.PrdPriceMicro;
import com.ticketingflow.booking.config.BookingProps;
import com.ticketingflow.booking.dto.Requests.OrdReq;
import com.ticketingflow.booking.service.SeatCacheService;
import com.ticketingflow.booking.service.TrxNoGenerator;
import com.ticketingflow.booking.support.ActiveGate;
import com.ticketingflow.booking.support.LuaSupport;
import com.ticketingflow.common.BizException;
import com.ticketingflow.common.RedisKeys;
import com.ticketingflow.common.RsltCd;
import com.ticketingflow.core.HandleTemplate;
import com.ticketingflow.core.Reqs;
import com.ticketingflow.core.StepChain;
import com.ticketingflow.core.TxData;
import com.ticketingflow.event.ItemLine;
import com.ticketingflow.event.OrdEvent;
import com.ticketingflow.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 수량제 상품 주문.
 * 입장 검증(active 계통) → 품목 단가 조회 → Lua(재고 원자 차감, all-or-nothing) → Stream 발행.
 * 오버셀은 Lua 단일 실행 안의 검사-차감으로 차단된다.
 */
@Service("handle|" + OrdConfirmHandle.BEAN_NM)
public class OrdConfirmHandle extends HandleTemplate {

    public static final String BEAN_NM = "ordConfirm";

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> ordConfirmScript;
    private final SeatCacheService seatCacheService;
    private final TrxNoGenerator trxNoGenerator;
    private final ActiveGate activeGate;
    private final LuaSupport lua;
    private final BookingProps props;

    public OrdConfirmHandle(@Qualifier(RedisConfig.BOOKING) StringRedisTemplate redis,
                            DefaultRedisScript<List> ordConfirmScript, SeatCacheService seatCacheService,
                            TrxNoGenerator trxNoGenerator, ActiveGate activeGate, LuaSupport lua,
                            BookingProps props) {
        this.redis = redis;
        this.ordConfirmScript = ordConfirmScript;
        this.seatCacheService = seatCacheService;
        this.trxNoGenerator = trxNoGenerator;
        this.activeGate = activeGate;
        this.lua = lua;
        this.props = props;
    }

    @Override
    protected void validate(TxData tx) {
        OrdReq req = tx.asReq(OrdReq.class);
        Reqs.required(req.schdNo(), "schdNo");
        Reqs.required(req.usrId(), "usrId");
        Reqs.required(req.entryToken(), "entryToken");
        Reqs.notEmpty(req.items(), "items");
    }

    @Override
    protected StepChain composeDo(StepChain chain, TxData tx) throws Exception {
        return chain
                .next(PrdPriceMicro.BEAN_NM)
                .next(t -> {
                    OrdReq req = t.asReq(OrdReq.class);
                    activeGate.verify(req.schdNo(), req.usrId(), req.entryToken());
                    List<ItemLine> itemLines = t.proc("itemLines");
                    BigDecimal totAmt = t.proc("totAmt");
                    int itemCnt = t.proc("itemCnt");
                    if (itemCnt > props.maxQtyPerOrd()) {
                        throw new BizException(RsltCd.INVALID_PARAM,
                                "1회 최대 " + props.maxQtyPerOrd() + "개까지 주문할 수 있습니다");
                    }
                    seatCacheService.ensureStockHash(req.schdNo());

                    String ordNo = trxNoGenerator.next("O");
                    OrdEvent event = new OrdEvent(ordNo, ordNo, req.usrId(), req.schdNo(),
                            System.currentTimeMillis(), totAmt, itemCnt, itemLines);

                    List<String> args = new ArrayList<>(List.of(lua.json(event)));
                    itemLines.forEach(i -> {
                        args.add(i.prdNo());
                        args.add(String.valueOf(i.qty()));
                    });

                    lua.guard(redis.execute(ordConfirmScript,
                            List.of(RedisKeys.stockHash(req.schdNo()), RedisKeys.RSV_STREAM),
                            args.toArray()));

                    t.out("ordNo", ordNo);
                    t.out("totAmt", totAmt);
                    t.out("itemCnt", itemCnt);
                });
    }
}
