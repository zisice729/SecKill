-- KEYS[1] 商品库存key  KEYS[2] 用户限购集合key
-- ARGV[1] 用户ID
local stockKey = KEYS[1]
local userSetKey = KEYS[2]
local userId = ARGV[1]

if redis.call('sismember', userSetKey, userId) == 1 then
    return 0  -- 用户已限购
end
local stock = tonumber(redis.call('get', stockKey) or 0)
if stock <= 0 then
    return -1 -- 库存不足
end
redis.call('decrby', stockKey, 1)
redis.call('sadd', userSetKey, userId)
return 1 -- 预扣成功