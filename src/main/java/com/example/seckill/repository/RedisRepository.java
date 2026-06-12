package com.example.seckill.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.Arrays;

@Slf4j
@Repository
public class RedisRepository {

    @Resource
    private StringRedisTemplate redisTemplate;

    @Resource
    private DefaultRedisScript<Long> seckillScript;

    @Resource
    private DefaultRedisScript<Long> returnStockScript;

    public boolean doSeckill(Long goodsId, Long userId) {
        String stockKey = "seckill:stock:" + goodsId;
        String userKey = "seckill:user:" + goodsId;

        Long result = redisTemplate.execute(seckillScript,
                Arrays.asList(stockKey, userKey), userId.toString());

        return !ObjectUtils.isEmpty(result) && result.equals(1L);
    }

    /**
     * 归还库存
     */
    public void returnStock(Long goodsId) {
        String stockKey = "seckill:stock:" + goodsId;
        redisTemplate.opsForValue().increment(stockKey);
        log.info("Returned stock for goodsId: {}", goodsId);
    }

    /**
     * 删除用户限购记录
     */
    public void removeUserLimit(Long goodsId, Long userId) {
        String userKey = "seckill:user:" + goodsId;
        redisTemplate.opsForSet().remove(userKey, userId.toString());
        log.info("Removed user limit for goodsId: {}, userId: {}", goodsId, userId);
    }

    /**
     * 归还库存并删除用户限购记录（原子操作，使用Lua脚本）
     */
    public void returnStockAndRemoveUserLimit(Long goodsId, Long userId) {
        String stockKey = "seckill:stock:" + goodsId;
        String userKey = "seckill:user:" + goodsId;

        // 使用Lua脚本实现原子操作
        Long result = redisTemplate.execute(returnStockScript,
                Arrays.asList(stockKey, userKey), userId.toString());

        if (!ObjectUtils.isEmpty(result) && result.equals(1L)) {
            log.info("Returned stock and removed user limit for goodsId: {}, userId: {}", goodsId, userId);
        } else {
            log.warn("Failed to return stock and remove user limit for goodsId: {}, userId: {}", goodsId, userId);
        }
    }

    /**
     * 分布式锁 - 获取锁
     * @param key 锁的key
     * @param value 锁的值（用于标识持有者）
     * @param expireSeconds 过期时间（秒）
     * @return 是否获取成功
     */
    public Boolean tryLock(String key, String value, long expireSeconds) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, expireSeconds, java.util.concurrent.TimeUnit.SECONDS);
    }

    /**
     * 分布式锁 - 释放锁（使用Lua脚本保证原子性）
     * @param key 锁的key
     * @param value 锁的值（必须与获取锁时一致）
     */
    public void unlock(String key, String value) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Arrays.asList(key), value);
    }
}