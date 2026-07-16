-- 예매 확정: 본인 HOLD 검증 → SOLD 전환 → Stream 발행 (전 과정 원자)
-- 입장 검증은 별도 Redis(active) 계통이라 Java 선행 단계에서 처리한다.
-- KEYS[1] = seat:{schdNo}
-- KEYS[2] = stream:rsv
-- ARGV[1] = usrId
-- ARGV[2] = now (epoch millis)
-- ARGV[3] = payload (JSON, worker 적재용)
-- ARGV[4..] = seatNo...
-- return: {0} 성공 / {3, seatNo} 선점만료·비소유 좌석
local now = tonumber(ARGV[2])

for i = 4, #ARGV do
    local v = redis.call('HGET', KEYS[1], ARGV[i])
    local owner, expire = string.match(v or '', '^H:([^:]+):(%d+)$')
    if owner ~= ARGV[1] or tonumber(expire) < now then
        return {3, ARGV[i]}
    end
end

local soldVal = 'S:' .. ARGV[1]
for i = 4, #ARGV do
    redis.call('HSET', KEYS[1], ARGV[i], soldVal)
end
redis.call('XADD', KEYS[2], '*', 'type', 'RSV', 'payload', ARGV[3])
return {0}
