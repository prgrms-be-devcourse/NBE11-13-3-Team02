-- KEYS[1]=current, KEYS[2]=target
-- ARGV[1]=quantity
-- return: 성공 시 예약 후 current, 정원 초과 0, 미초기화 -1
if redis.call('EXISTS', KEYS[2]) == 0 then
    return -1
end

local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local target = tonumber(redis.call('GET', KEYS[2]))
local quantity = tonumber(ARGV[1])

if current + quantity > target then
    return 0
end

return redis.call('INCRBY', KEYS[1], quantity)
