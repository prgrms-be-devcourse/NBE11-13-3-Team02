-- KEYS[1]=current, KEYS[2]=target
-- ARGV[1]=currentCount, ARGV[2]=targetCount
-- target 키가 없을 때만 초기화한다 (SETNX). 이미 Redis에 있으면 DB로 덮어쓰지 않는다.
if redis.call('SETNX', KEYS[2], ARGV[2]) == 1 then
    redis.call('SET', KEYS[1], ARGV[1])
    return 1
end
return 0
