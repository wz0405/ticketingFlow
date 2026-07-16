package com.ticketingflow.booking.biz.handle;

import com.ticketingflow.booking.config.BookingProps;
import com.ticketingflow.booking.dto.Requests.SeatActionReq;
import com.ticketingflow.booking.service.SeatCacheService;
import com.ticketingflow.booking.support.ActiveGate;
import com.ticketingflow.booking.support.LuaSupport;
import com.ticketingflow.common.BizException;
import com.ticketingflow.common.RedisKeys;
import com.ticketingflow.common.RsltCd;
import com.ticketingflow.core.HandleTemplate;
import com.ticketingflow.core.Reqs;
import com.ticketingflow.core.StepChain;
import com.ticketingflow.core.TxData;
import com.ticketingflow.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 좌석 선점. 입장 검증(active 계통) 후, 다좌석 all-or-nothing 선점을 Lua로 원자 처리한다.
 */
@Service("handle|" + RsvHoldHandle.BEAN_NM)
public class RsvHoldHandle extends HandleTemplate {

    public static final String BEAN_NM = "rsvHold";

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> seatHoldScript;
    private final SeatCacheService seatCacheService;
    private final ActiveGate activeGate;
    private final LuaSupport lua;
    private final BookingProps props;

    public RsvHoldHandle(@Qualifier(RedisConfig.BOOKING) StringRedisTemplate redis,
                         DefaultRedisScript<List> seatHoldScript, SeatCacheService seatCacheService,
                         ActiveGate activeGate, LuaSupport lua, BookingProps props) {
        this.redis = redis;
        this.seatHoldScript = seatHoldScript;
        this.seatCacheService = seatCacheService;
        this.activeGate = activeGate;
        this.lua = lua;
        this.props = props;
    }

    @Override
    protected void validate(TxData tx) {
        SeatActionReq req = tx.asReq(SeatActionReq.class);
        Reqs.required(req.schdNo(), "schdNo");
        Reqs.required(req.usrId(), "usrId");
        Reqs.required(req.entryToken(), "entryToken");
        Reqs.notEmpty(req.seatNos(), "seatNos");
        if (req.seatNos().size() > props.maxSeatsPerRsv()) {
            throw new BizException(RsltCd.INVALID_PARAM,
                    "좌석은 최대 " + props.maxSeatsPerRsv() + "석까지 선택할 수 있습니다");
        }
    }

    @Override
    protected StepChain composeDo(StepChain chain, TxData tx) throws Exception {
        return chain.next(t -> {
            SeatActionReq req = t.asReq(SeatActionReq.class);
            activeGate.verify(req.schdNo(), req.usrId(), req.entryToken());
            seatCacheService.ensureSeatHash(req.schdNo());

            List<String> args = new ArrayList<>(List.of(
                    req.usrId(),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(props.holdTtlSec() * 1000)));
            args.addAll(req.seatNos());

            lua.guard(redis.execute(seatHoldScript,
                    List.of(RedisKeys.seatHash(req.schdNo())),
                    args.toArray()));

            t.out("holdTtlSec", props.holdTtlSec());
            t.out("seatNos", req.seatNos());
        });
    }
}
