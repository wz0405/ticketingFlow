-- 좌석 다건 원자 선점 (all-or-nothing)
-- 입장 검증은 별도 Redis(active) 계통이라 Java 선행 단계에서 처리한다.
-- KEYS[1] = seat:{schdNo}
-- ARGV[1] = usrId
-- ARGV[2] = now (epoch millis)
-- ARGV[3] = holdMs
-- ARGV[4..] = seatNo...
-- return: {0} 성공 / {2, seatNo} 선점불가 좌석
local now = tonumber(ARGV[2])

local function holdable(v)
    if not v then return false end
    if v == 'A' then return true end
    local owner, expire = string.match(v, '^H:([^:]+):(%d+)$')
    if not owner then return false end
    -- 본인 HOLD는 재선점 허용 (좌석 추가 선택 시 전체 재선점 → TTL 갱신)
    if owner == ARGV[1] then return true end
    -- 만료된 타인 HOLD는 즉시 재선점 가능 (lazy expiry)
    return tonumber(expire) < now
end

for i = 4, #ARGV do
    if not holdable(redis.call('HGET', KEYS[1], ARGV[i])) then
        return {2, ARGV[i]}
    end
end

local holdVal = 'H:' .. ARGV[1] .. ':' .. (now + tonumber(ARGV[3]))
for i = 4, #ARGV do
    redis.call('HSET', KEYS[1], ARGV[i], holdVal)
end
return {0}
