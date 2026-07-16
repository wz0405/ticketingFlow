-- 수량제 상품 주문: 다품목 원자 재고 차감 (오버셀 방지, all-or-nothing) → Stream 발행
-- 입장 검증은 별도 Redis(active) 계통이라 Java 선행 단계에서 처리한다.
-- KEYS[1] = stock:{schdNo}
-- KEYS[2] = stream:rsv
-- ARGV[1] = payload (JSON, ORD)
-- ARGV[2..] = prdNo, qty 반복쌍
-- return: {0} 성공 / {4, prdNo} 재고부족 상품
for i = 2, #ARGV, 2 do
    local remain = tonumber(redis.call('HGET', KEYS[1], ARGV[i]))
    local qty = tonumber(ARGV[i + 1])
    if not remain or remain < qty then
        return {4, ARGV[i]}
    end
end

for i = 2, #ARGV, 2 do
    redis.call('HINCRBY', KEYS[1], ARGV[i], -tonumber(ARGV[i + 1]))
end
redis.call('XADD', KEYS[2], '*', 'type', 'ORD', 'payload', ARGV[1])
return {0}
