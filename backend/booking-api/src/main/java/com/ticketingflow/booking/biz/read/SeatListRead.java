package com.ticketingflow.booking.biz.read;

import com.ticketingflow.booking.domain.SeatMaster;
import com.ticketingflow.booking.domain.SeatView;
import com.ticketingflow.booking.dto.Requests.SchdUsrReq;
import com.ticketingflow.booking.mapper.EventMapper;
import com.ticketingflow.booking.service.SeatCacheService;
import com.ticketingflow.common.RedisKeys;
import com.ticketingflow.core.ReadTemplate;
import com.ticketingflow.core.TxData;
import com.ticketingflow.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 좌석맵 조회. 마스터(DB) + 실시간 상태(Redis)를 병합한다.
 * 만료된 HOLD는 A로 보정한다(lazy expiry와 동일 규칙).
 */
@Service("read|" + SeatListRead.BEAN_NM)
public class SeatListRead extends ReadTemplate {

    public static final String BEAN_NM = "seatList";

    private final EventMapper eventMapper;
    private final SeatCacheService seatCacheService;
    private final StringRedisTemplate redis;

    public SeatListRead(EventMapper eventMapper, SeatCacheService seatCacheService,
                        @Qualifier(RedisConfig.BOOKING) StringRedisTemplate redis) {
        this.eventMapper = eventMapper;
        this.seatCacheService = seatCacheService;
        this.redis = redis;
    }

    @Override
    protected void doRead(TxData tx) {
        SchdUsrReq req = tx.asReq(SchdUsrReq.class);
        seatCacheService.ensureSeatHash(req.schdNo());

        Map<Object, Object> stateMap = redis.opsForHash().entries(RedisKeys.seatHash(req.schdNo()));
        long now = System.currentTimeMillis();

        List<SeatView> views = eventMapper.selectSeatMaster(req.schdNo()).stream()
                .map(seat -> toView(seat, (String) stateMap.get(seat.seatNo()), req.usrId(), now))
                .toList();
        tx.out("list", views);
    }

    private SeatView toView(SeatMaster seat, String raw, String usrId, long now) {
        String stCd;
        String owner;
        if (raw == null || RedisKeys.SEAT_AVAILABLE.equals(raw)) {
            stCd = "A";
            owner = null;
        } else if (raw.startsWith(RedisKeys.SEAT_SOLD_PREFIX)) {
            stCd = "S";
            owner = raw.substring(2);
        } else {
            String[] parts = raw.split(":");
            boolean expired = parts.length == 3 && Long.parseLong(parts[2]) < now;
            stCd = expired ? "A" : "H";
            owner = expired ? null : parts[1];
        }
        boolean mine = owner != null && owner.equals(usrId);
        return new SeatView(seat.seatNo(), seat.seatGrdCd(), seat.seatPrc(), stCd, mine);
    }
}
