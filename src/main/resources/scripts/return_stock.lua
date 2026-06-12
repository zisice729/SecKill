-- 归还库存并删除用户限购记录
local stockKey = KEYS[1]
local userKey = KEYS[2]
local userId = ARGV[1]

-- 归还库存
redis.call('incr', stockKey)
-- 删除用户限购记录
redis.call('srem', userKey, userId)

return 1