-- KEYS[1]=current
-- ARGV[1]=quantity
-- return: 롤백 후 current (없으면 0으로 보정)
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local quantity = tonumber(ARGV[1])
local nextCount = current - quantity
if nextCount < 0 then
    nextCount = 0
end
redis.call('SET', KEYS[1], nextCount)
return nextCount
