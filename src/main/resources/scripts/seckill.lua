-- 秒杀库存 key
local stockKey = KEYS[1]
-- 用户已抢购集合 key
local userKey = KEYS[2]
-- 当前用户ID
local userId = ARGV[1]

-- 判断用户是否已抢购
if redis.call('sismember', userKey, userId) == 1 then
    return 0 -- 已抢过，返回失败
end
-- 判断库存
local stock = tonumber(redis.call('get', stockKey))
if stock <= 0 then
    return 0 -- 库存不足
end
-- 扣库存 + 标记用户
redis.call('decr', stockKey)
redis.call('sadd', userKey, userId)
return 1 -- 抢购成功