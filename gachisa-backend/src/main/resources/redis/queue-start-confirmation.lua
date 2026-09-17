if redis.call('ZSCORE', KEYS[2], ARGV[1]) then
    return 1
end

local expiresAt = redis.call('ZSCORE', KEYS[1], ARGV[1])
if not expiresAt or tonumber(expiresAt) <= tonumber(ARGV[2]) then
    return 0
end

redis.call('ZREM', KEYS[1], ARGV[1])
redis.call('ZADD', KEYS[2], expiresAt, ARGV[1])
return 1
