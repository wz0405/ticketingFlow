package com.ticketingflow.booking.biz.handle;

import com.ticketingflow.booking.biz.read.OrdForCancelMicro;
import com.ticketingflow.booking.domain.OrdForCancel;
import com.ticketingflow.booking.domain.OrdItemRow;
import com.ticketingflow.booking.dto.Requests.OrdCancelReq;
import com.ticketingflow.booking.service.SeatCacheService;
import com.ticketingflow.booking.service.TrxNoGenerator;
import com.ticketingflow.booking.support.LuaSupport;
import com.ticketingflow.common.RedisKeys;
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

import java.util.ArrayList;
import java.util.List;

/**
 * 주문 취소. 재고 원복(Lua) 후 취소 이벤트를 발행한다.
 */
@Service("handle|" + OrdCancelHandle.BEAN_NM)
public class OrdCancelHandle extends HandleTemplate {

    public static final String BEAN_NM = "ordCancel";

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> stockRestoreScript;
    private final SeatCacheService seatCacheService;
    private final TrxNoGenerator trxNoGenerator;
    private final LuaSupport lua;

    public OrdCancelHandle(@Qualifier(RedisConfig.BOOKING) StringRedisTemplate redis,
                           DefaultRedisScript<List> stockRestoreScript, SeatCacheService seatCacheService,
                           TrxNoGenerator trxNoGenerator, LuaSupport lua) {
        this.redis = redis;
        this.stockRestoreScript = stockRestoreScript;
        this.seatCacheService = seatCacheService;
        this.trxNoGenerator = trxNoGenerator;
        this.lua = lua;
    }

    @Override
    protected void validate(TxData tx) {
        OrdCancelReq req = tx.asReq(OrdCancelReq.class);
        Reqs.required(req.ordNo(), "ordNo");
        Reqs.required(req.usrId(), "usrId");
    }

    @Override
    protected StepChain composeDo(StepChain chain, TxData tx) throws Exception {
        return chain
                .next(OrdForCancelMicro.BEAN_NM)
                .next(t -> {
                    OrdCancelReq req = t.asReq(OrdCancelReq.class);
                    OrdForCancel ord = t.proc("ord");
                    List<OrdItemRow> items = t.proc("ordItems");
                    seatCacheService.ensureStockHash(ord.schdNo());

                    String cnclNo = trxNoGenerator.next("O");
                    List<ItemLine> itemLines = items.stream()
                            .map(i -> new ItemLine(i.prdNo(), i.ordQty(), i.prdPrc()))
                            .toList();
                    OrdEvent event = new OrdEvent(cnclNo, req.ordNo(), req.usrId(), ord.schdNo(),
                            System.currentTimeMillis(), ord.totAmt().negate(), -ord.itemCnt(), itemLines);

                    List<String> args = new ArrayList<>(List.of(lua.json(event)));
                    itemLines.forEach(i -> {
                        args.add(i.prdNo());
                        args.add(String.valueOf(i.qty()));
                    });

                    lua.guard(redis.execute(stockRestoreScript,
                            List.of(RedisKeys.stockHash(ord.schdNo()), RedisKeys.RSV_STREAM),
                            args.toArray()));

                    t.out("cnclNo", cnclNo);
                    t.out("oordNo", req.ordNo());
                });
    }
}
