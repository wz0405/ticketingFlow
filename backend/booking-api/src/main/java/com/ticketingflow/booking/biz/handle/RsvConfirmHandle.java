package com.ticketingflow.booking.biz.handle;

import com.ticketingflow.booking.biz.read.SeatPriceMicro;
import com.ticketingflow.booking.dto.Requests.SeatActionReq;
import com.ticketingflow.booking.service.SeatCacheService;
import com.ticketingflow.booking.service.TrxNoGenerator;
import com.ticketingflow.booking.support.ActiveGate;
import com.ticketingflow.booking.support.LuaSupport;
import com.ticketingflow.common.RedisKeys;
import com.ticketingflow.core.HandleTemplate;
import com.ticketingflow.core.Reqs;
import com.ticketingflow.core.StepChain;
import com.ticketingflow.core.TxData;
import com.ticketingflow.event.RsvEvent;
import com.ticketingflow.event.SeatLine;
import com.ticketingflow.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 예매 확정.
 * 입장 검증(active 계통) → 좌석단가 조회 → 채번/이벤트 구성
 *   → Lua(HOLD 검증·SOLD 전환·Stream 발행). RDB 원장 적재는 worker가 수행한다.
 */
@Service("handle|" + RsvConfirmHandle.BEAN_NM)
public class RsvConfirmHandle extends HandleTemplate {

    public static final String BEAN_NM = "rsvConfirm";

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> rsvConfirmScript;
    private final SeatCacheService seatCacheService;
    private final TrxNoGenerator trxNoGenerator;
    private final ActiveGate activeGate;
    private final LuaSupport lua;

    public RsvConfirmHandle(@Qualifier(RedisConfig.BOOKING) StringRedisTemplate redis,
                            DefaultRedisScript<List> rsvConfirmScript, SeatCacheService seatCacheService,
                            TrxNoGenerator trxNoGenerator, ActiveGate activeGate, LuaSupport lua) {
        this.redis = redis;
        this.rsvConfirmScript = rsvConfirmScript;
        this.seatCacheService = seatCacheService;
        this.trxNoGenerator = trxNoGenerator;
        this.activeGate = activeGate;
        this.lua = lua;
    }

    @Override
    protected void validate(TxData tx) {
        SeatActionReq req = tx.asReq(SeatActionReq.class);
        Reqs.required(req.schdNo(), "schdNo");
        Reqs.required(req.usrId(), "usrId");
        Reqs.required(req.entryToken(), "entryToken");
        Reqs.notEmpty(req.seatNos(), "seatNos");
    }

    @Override
    protected StepChain composeDo(StepChain chain, TxData tx) throws Exception {
        return chain
                .next(SeatPriceMicro.BEAN_NM)
                .next(t -> {
                    SeatActionReq req = t.asReq(SeatActionReq.class);
                    activeGate.verify(req.schdNo(), req.usrId(), req.entryToken());
                    List<SeatLine> seatLines = t.proc("seatLines");
                    BigDecimal totAmt = t.proc("totAmt");
                    seatCacheService.ensureSeatHash(req.schdNo());

                    String rsvNo = trxNoGenerator.next("R");
                    long trxDt = System.currentTimeMillis();
                    RsvEvent event = new RsvEvent(rsvNo, rsvNo, req.usrId(), req.schdNo(),
                            trxDt, totAmt, seatLines);

                    List<String> args = new ArrayList<>(List.of(
                            req.usrId(), String.valueOf(trxDt), lua.json(event)));
                    args.addAll(req.seatNos());

                    lua.guard(redis.execute(rsvConfirmScript,
                            List.of(RedisKeys.seatHash(req.schdNo()), RedisKeys.RSV_STREAM),
                            args.toArray()));

                    t.out("rsvNo", rsvNo);
                    t.out("totAmt", totAmt);
                    t.out("seatCnt", seatLines.size());
                });
    }
}
