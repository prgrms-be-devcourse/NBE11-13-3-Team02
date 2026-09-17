local members = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
local result = {}

for _, member in ipairs(members) do
    local attemptId = redis.call('HGET', KEYS[4], member)
    local sequence = redis.call('INCR', KEYS[3])
    redis.call('ZREM', KEYS[1], member)
    redis.call('ZADD', KEYS[2], sequence, member)
    redis.call('HDEL', KEYS[4], member)
    table.insert(result, member)
    table.insert(result, attemptId or '')
end
return result
