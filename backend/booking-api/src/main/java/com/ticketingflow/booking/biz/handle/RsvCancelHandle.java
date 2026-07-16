package com.ticketingflow.booking.biz.handle;

import com.ticketingflow.booking.biz.read.RsvForCancelMicro;
import com.ticketingflow.booking.domain.RsvForCancel;
import com.ticketingflow.booking.dto.Requests.RsvCancelReq;
import com.ticketingflow.booking.service.SeatCacheService;
import com.ticketingflow.booking.service.TrxNoGenerator;
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

import java.util.ArrayList;
import java.util.List;

/**
 * 예매 취소.
 * 원장 검증 → 좌석 원복(Lua) → 취소 이벤트 발행.
 * 취소 row(ORSV_NO=원예매번호, 음수 금액) 적재는 worker가 수행한다.
 */
@Service("handle|" + RsvCancelHandle.BEAN_NM)
public class RsvCancelHandle extends HandleTemplate {

    public static final String BEAN_NM = "rsvCancel";

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> seatReleaseScript;
    private final SeatCacheService seatCacheService;
    private final TrxNoGenerator trxNoGenerator;
    private final LuaSupport lua;

    public RsvCancelHandle(@Qualifier(RedisConfig.BOOKING) StringRedisTemplate redis,
                           DefaultRedisScript<List> seatReleaseScript, SeatCacheService seatCacheService,
                           TrxNoGenerator trxNoGenerator, LuaSupport lua) {
        this.redis = redis;
        this.seatReleaseScript = seatReleaseScript;
        this.seatCacheService = seatCacheService;
        this.trxNoGenerator = trxNoGenerator;
        this.lua = lua;
    }

    @Override
    protected void validate(TxData tx) {
        RsvCancelReq req = tx.asReq(RsvCancelReq.class);
        Reqs.required(req.rsvNo(), "rsvNo");
        Reqs.required(req.usrId(), "usrId");
    }

    @Override
    protected StepChain composeDo(StepChain chain, TxData tx) throws Exception {
        return chain
                .next(RsvForCancelMicro.BEAN_NM)
                .next(t -> {
                    RsvCancelReq req = t.asReq(RsvCancelReq.class);
                    RsvForCancel rsv = t.proc("rsv");
                    List<SeatLine> seats = t.proc("rsvSeats");
                    seatCacheService.ensureSeatHash(rsv.schdNo());

                    String cnclNo = trxNoGenerator.next("R");
                    RsvEvent event = new RsvEvent(cnclNo, req.rsvNo(), req.usrId(), rsv.schdNo(),
                            System.currentTimeMillis(), rsv.totAmt().negate(), seats);

                    List<String> args = new ArrayList<>(List.of(req.usrId(), lua.json(event)));
                    seats.forEach(s -> args.add(s.seatNo()));

                    lua.guard(redis.execute(seatReleaseScript,
                            List.of(RedisKeys.seatHash(rsv.schdNo()), RedisKeys.RSV_STREAM),
                            args.toArray()));

                    t.out("cnclNo", cnclNo);
                    t.out("orsvNo", req.rsvNo());
                });
    }
}
