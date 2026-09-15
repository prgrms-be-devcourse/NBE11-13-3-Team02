local occupied = redis.call('ZCARD', KEYS[2]) + redis.call('ZCARD', KEYS[3])
local available = tonumber(ARGV[1]) - occupied
local batchSize = tonumber(ARGV[2])

if available <= 0 then
    return {}
end
if available > batchSize then
    available = batchSize
end

local members = redis.call('ZRANGE', KEYS[1], 0, available - 1)
for _, member in ipairs(members) do
    redis.call('ZREM', KEYS[1], member)
    redis.call('ZADD', KEYS[2], ARGV[3], member)
end
return members
