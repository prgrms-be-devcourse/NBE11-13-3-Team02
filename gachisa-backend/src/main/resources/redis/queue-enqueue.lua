if redis.call('HEXISTS', KEYS[3], ARGV[1]) == 1 then
    return 0
end

local sequence = redis.call('INCR', KEYS[2])
redis.call('ZADD', KEYS[1], sequence, ARGV[1])
redis.call('HSET', KEYS[3], ARGV[1], ARGV[2])
redis.call('SADD', KEYS[4], ARGV[3])
return 1
