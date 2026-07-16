package com.ticketingflow.booking.biz.read;

import com.ticketingflow.booking.domain.PrdView;
import com.ticketingflow.booking.dto.Requests.SchdReq;
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
 * 수량제 상품 목록 + 실시간 잔여재고(Redis).
 */
@Service("read|" + PrdListRead.BEAN_NM)
public class PrdListRead extends ReadTemplate {

    public static final String BEAN_NM = "prdList";

    private final EventMapper eventMapper;
    private final SeatCacheService seatCacheService;
    private final StringRedisTemplate redis;

    public PrdListRead(EventMapper eventMapper, SeatCacheService seatCacheService,
                       @Qualifier(RedisConfig.BOOKING) StringRedisTemplate redis) {
        this.eventMapper = eventMapper;
        this.seatCacheService = seatCacheService;
        this.redis = redis;
    }

    @Override
    protected void doRead(TxData tx) {
        SchdReq req = tx.asReq(SchdReq.class);
        seatCacheService.ensureStockHash(req.schdNo());

        Map<Object, Object> stockMap = redis.opsForHash().entries(RedisKeys.stockHash(req.schdNo()));
        List<PrdView> views = eventMapper.selectPrdList(req.schdNo()).stream()
                .map(prd -> {
                    Object remain = stockMap.get(prd.prdNo());
                    long qty = remain == null ? 0 : Long.parseLong(String.valueOf(remain));
                    return new PrdView(prd.prdNo(), prd.prdNm(), prd.prdTypeCd(), prd.prdPrc(), qty);
                })
                .toList();
        tx.out("list", views);
    }
}
