-- 예매 취소: 본인 SOLD 좌석을 A로 되돌리고 취소 이벤트 발행 (원자)
-- KEYS[1] = seat:{schdNo}
-- KEYS[2] = stream:rsv
-- ARGV[1] = usrId
-- ARGV[2] = payload (JSON, RSV_CNCL)
-- ARGV[3..] = seatNo...
-- return: {0} 성공 / {3, seatNo} 비소유 좌석
local soldVal = 'S:' .. ARGV[1]
for i = 3, #ARGV do
    if redis.call('HGET', KEYS[1], ARGV[i]) ~= soldVal then
        return {3, ARGV[i]}
    end
end
for i = 3, #ARGV do
    redis.call('HSET', KEYS[1], ARGV[i], 'A')
end
redis.call('XADD', KEYS[2], '*', 'type', 'RSV_CNCL', 'payload', ARGV[2])
return {0}
