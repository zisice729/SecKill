package com.example.seckill.service.impl;

import com.example.seckill.common.response.R;
import com.example.seckill.constant.RedisKeyConstant;
import com.example.seckill.dto.KafkaMsgDTO;
import com.example.seckill.service.SeckillService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;

/**
 * 秒杀服务实现类
 */
@Service
public class SeckillServiceImpl implements SeckillService {

    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, KafkaMsgDTO> kafkaTemplate;
    private DefaultRedisScript<Long> seckillLua;

    public SeckillServiceImpl(RedisTemplate<String, String> redisTemplate,
                              KafkaTemplate<String, KafkaMsgDTO> kafkaTemplate) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 初始化Lua脚本
     */
    @PostConstruct
    public void initLua() {
        seckillLua = new DefaultRedisScript<>();
        seckillLua.setLocation(new ClassPathResource("lua/seckill_stock.lua"));
        seckillLua.setResultType(Long.class);
    }

    @Override
    public R doSeckill(Long userId, Long goodsId) {
        String stockKey = String.format(RedisKeyConstant.SECKILL_STOCK, goodsId);
        String userSetKey = String.format(RedisKeyConstant.SECKILL_USER_SET, goodsId);

        // 执行Lua脚本：预扣库存 + 用户限购检查
        Long result = redisTemplate.execute(seckillLua,
                Arrays.asList(stockKey, userSetKey),
                userId.toString());

        if (result == 0) {
            return R.fail("每人仅限秒杀一单");
        }
        if (result == -1) {
            return R.fail("商品库存不足");
        }

        // 发送Kafka消息：仅传 userId、goodsId
        KafkaMsgDTO msg = new KafkaMsgDTO();
        msg.setUserId(userId);
        msg.setGoodsId(goodsId);
        kafkaTemplate.send("seckill_topic", msg);

        return R.success("下单已受理，请15分钟内完成支付");
    }
}