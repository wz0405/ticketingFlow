-- 주문 취소: 재고 복원 → 취소 이벤트 발행 (원자)
-- KEYS[1] = stock:{schdNo}
-- KEYS[2] = stream:rsv
-- ARGV[1] = payload (JSON, ORD_CNCL)
-- ARGV[2..] = prdNo, qty 반복쌍
-- return: {0}
for i = 2, #ARGV, 2 do
    redis.call('HINCRBY', KEYS[1], ARGV[i], tonumber(ARGV[i + 1]))
end
redis.call('XADD', KEYS[2], '*', 'type', 'ORD_CNCL', 'payload', ARGV[1])
return {0}
