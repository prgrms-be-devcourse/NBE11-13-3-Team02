if not redis.call('ZSCORE', KEYS[1], ARGV[1]) then
    return 0
end

local sequence = redis.call('INCR', KEYS[3])
redis.call('ZREM', KEYS[1], ARGV[1])
redis.call('ZADD', KEYS[2], sequence, ARGV[1])
redis.call('HDEL', KEYS[4], ARGV[1])
return 1
