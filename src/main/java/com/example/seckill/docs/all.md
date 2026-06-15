# 一、全链路最终总结
用户发起秒杀下单请求，接口通过Redis+Lua原子脚本完成商品缓存库存预扣与用户限购拦截，预扣成功后仅向Kafka发送`userId、goodsId`（不传递订单号），即刻返回下单受理。Kafka消费端本地生成全局唯一`orderId`，在MySQL本地事务中完成建单与真实库存扣减，依靠`(user_id, goods_id)`联合唯一索引实现全链路幂等；消费异常执行2次本地重试，失败则写入MQ异常日志并告警。
定时任务统一使用**XXL-Job**调度：每分钟执行异常日志补偿重试任务，达最大重试次数后标记人工处理；每分钟执行超时关单任务，仅扫描已存在的超时未支付订单，通过`status='未支付'`乐观锁关单并归还Redis缓存库存，无订单则不处理，冻结库存定期人工对账清理。
支付回调查询订单时，按`1s、2s、4s`间隔最多重试3次，仍未查到订单则直接原路退款，不新增审核表；支付、关单操作基于状态乐观锁互斥，整体架构轻量化、无分布式锁与额外Redis占位标记。

---

# 二、项目目录结构
```
com.seckill
├── config                # 配置类
│   ├── RedisConfig.java
│   ├── KafkaConfig.java      # Kafka配置
│   └── XxlJobConfig.java     # XXL-Job配置
├── controller            # 接口层
│   └── SeckillController.java
├── service               # 业务服务层
│   ├── impl
│   │   ├── SeckillServiceImpl.java        # 下单服务
│   │   ├── KafkaConsumerImpl.java         # Kafka消费者
│   │   ├── PayCallbackServiceImpl.java    # 支付回调（含重试）
│   │   └── XxlJobTaskImpl.java            # XXL-Job定时任务实现
│   ├── SeckillService.java
│   ├── KafkaConsumerService.java
│   ├── PayCallbackService.java
│   └── XxlJobTaskService.java
├── entity                # 数据库实体
│   ├── Order.java
│   ├── Goods.java
│   └── SeckillMqFailLog.java
├── mapper                # MyBatis
│   ├── OrderMapper.java
│   ├── GoodsMapper.java
│   └── SeckillMqFailLogMapper.java
├── util                  # 工具类
│   ├── IdUtil.java        # 订单号生成
│   └── RetryUtil.java     # 通用重试工具
├── dto                   # 传输对象
│   ├── SeckillReq.java
│   ├── PayCallbackReq.java
│   └── KafkaMsgDTO.java   # Kafka消息体：仅 userId / goodsId
└── constant              # 常量
    ├── OrderStatus.java
    └── RedisKeyConstant.java
```

---

# 三、数据库表（无变更，沿用之前设计）
## 1. 商品表 `t_goods`
```sql
CREATE TABLE t_goods (
    id BIGINT PRIMARY KEY COMMENT '商品ID',
    stock INT NOT NULL DEFAULT 0 COMMENT '真实库存',
    name VARCHAR(100) NOT NULL COMMENT '商品名称',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品表';
```

## 2. 订单表 `t_order`（联合唯一索引做幂等）
```sql
CREATE TABLE t_order (
    order_id VARCHAR(64) PRIMARY KEY COMMENT '订单号（消费端生成）',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    goods_id BIGINT NOT NULL COMMENT '商品ID',
    status VARCHAR(20) NOT NULL DEFAULT 'UN_PAY' COMMENT 'UN_PAY未支付 / PAYED已支付 / CANCELED已取消',
    pay_deadline DATETIME NOT NULL COMMENT '15分钟支付截止时间',
    pay_time DATETIME NULL COMMENT '支付时间',
    close_time DATETIME NULL COMMENT '关单时间',
    out_pay_no VARCHAR(64) NULL COMMENT '外部支付流水号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_goods (user_id, goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';
```

## 3. Kafka消费异常日志表 `t_seckill_mq_fail_log`
```sql
CREATE TABLE t_seckill_mq_fail_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    goods_id BIGINT NOT NULL,
    msg_content TEXT COMMENT '原始消息',
    error_msg TEXT COMMENT '异常信息',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    handle_status TINYINT NOT NULL DEFAULT 0 COMMENT '0未处理 1已处理 2人工处理',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_handle_status (handle_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Kafka消费异常日志';
```

---

# 四、基础常量 & 工具类
## 1. RedisKeyConstant.java
```java
public class RedisKeyConstant {
    public static final String SECKILL_STOCK = "seckill:stock:%s";
    public static final String SECKILL_USER_SET = "seckill:user:%s";
}
```

## 2. OrderStatus.java
```java
public class OrderStatus {
    public static final String UN_PAY  = "UN_PAY";
    public static final String PAYED   = "PAYED";
    public static final String CANCELED= "CANCELED";
}
```

## 3. Kafka消息体 `KafkaMsgDTO.java`（无orderId）
```java
import lombok.Data;
import java.io.Serializable;

@Data
public class KafkaMsgDTO implements Serializable {
    private Long userId;
    private Long goodsId;
}
```

## 4. 通用重试工具 `RetryUtil.java`
```java
import org.springframework.stereotype.Component;
import java.util.function.Supplier;

@Component
public class RetryUtil {
    /**
     * 间隔重试
     * @param maxRetry 最大重试次数
     * @param delays 间隔毫秒数组
     * @param task 业务逻辑
     * @return 执行结果
     */
    public <T> T retry(int maxRetry, long[] delays, Supplier<T> task) {
        int count = 0;
        T result = null;
        while (count <= maxRetry) {
            try {
                result = task.get();
                if (result != null && (result instanceof Boolean && (Boolean)result)) {
                    return result;
                }
            } catch (Exception e) {
                // 异常继续重试
            }
            if (count >= delays.length) {
                break;
            }
            try {
                Thread.sleep(delays[count]);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            count++;
        }
        return result;
    }
}
```

## 5. Redis Lua脚本 `resources/lua/seckill_stock.lua`
```lua
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
```

---

# 五、核心配置类
## 1. KafkaConfig.java（SpringBoot 整合Kafka）
```java
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // 生产者配置
    @Bean
    public ProducerFactory<String, KafkaMsgDTO> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, KafkaMsgDTO> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // 消费者配置
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, KafkaMsgDTO> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, KafkaMsgDTO> factory = new ConcurrentKafkaListenerContainerFactory<>();
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); // 手动提交offset
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        return factory;
    }
}
```

## 2. XxlJobConfig.java（XXL-Job 基础配置）
```java
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class XxlJobConfig {

    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;
    @Value("${xxl.job.accessToken}")
    private String accessToken;
    @Value("${xxl.job.executor.appname}")
    private String appname;
    @Value("${xxl.job.executor.address}")
    private String address;
    @Value("${xxl.job.executor.ip}")
    private String ip;
    @Value("${xxl.job.executor.port}")
    private int port;
    @Value("${xxl.job.executor.logpath}")
    private String logPath;
    @Value("${xxl.job.executor.logretentiondays}")
    private int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAccessToken(accessToken);
        executor.setAppname(appname);
        executor.setAddress(address);
        executor.setIp(ip);
        executor.setPort(port);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }
}
```

---

# 六、业务代码实现
## 1. 下单接口 & 服务（发送Kafka，无orderId）
### SeckillController.java
```java
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/seckill")
public class SeckillController {

    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    @PostMapping("/do")
    public R seckill(@RequestBody SeckillReq req) {
        return seckillService.doSeckill(req.getUserId(), req.getGoodsId());
    }
}
```

### SeckillServiceImpl.java
```java
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.util.Arrays;

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

    @PostConstruct
    public void initLua() {
        seckillLua = new DefaultRedisScript<>();
        seckillLua.setScriptSource(new org.springframework.core.io.ResourceScriptSource(
                new ClassPathResource("lua/seckill_stock.lua")));
        seckillLua.setResultType(Long.class);
    }

    @Override
    public R doSeckill(Long userId, Long goodsId) {
        String stockKey = String.format(RedisKeyConstant.SECKILL_STOCK, goodsId);
        String userSetKey = String.format(RedisKeyConstant.SECKILL_USER_SET, goodsId);

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
```

## 2. Kafka消费者（消费端生成orderId + 本地重试 + 异常落库）
```java
import com.alibaba.fastjson.JSON;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class KafkaConsumerImpl implements KafkaConsumerService {

    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final SeckillMqFailLogMapper failLogMapper;
    private final IdUtil idUtil;
    private final RetryUtil retryUtil;

    // 本地最大重试2次，间隔 1s、3s
    private static final int LOCAL_MAX_RETRY = 2;
    private static final long[] LOCAL_DELAYS = {1000, 3000};

    public KafkaConsumerImpl(OrderMapper orderMapper,
                             GoodsMapper goodsMapper,
                             SeckillMqFailLogMapper failLogMapper,
                             IdUtil idUtil,
                             RetryUtil retryUtil) {
        this.orderMapper = orderMapper;
        this.goodsMapper = goodsMapper;
        this.failLogMapper = failLogMapper;
        this.idUtil = idUtil;
        this.retryUtil = retryUtil;
    }

    @KafkaListener(topics = "seckill_topic", groupId = "seckill_group")
    public void listen(ConsumerRecord<String, KafkaMsgDTO> record, Acknowledgment ack) {
        KafkaMsgDTO msg = record.value();
        Long userId = msg.getUserId();
        Long goodsId = msg.getGoodsId();

        try {
            // 幂等判断：根据 user+goods 查询
            Order existOrder = orderMapper.selectByUserAndGoods(userId, goodsId);
            if (existOrder != null) {
                ack.acknowledge();
                return;
            }

            // 本地重试执行建单+扣库存
            Boolean success = retryUtil.retry(LOCAL_MAX_RETRY, LOCAL_DELAYS,
                    () -> createOrderAndStock(userId, goodsId));

            if (Boolean.TRUE.equals(success)) {
                ack.acknowledge();
            } else {
                saveFailLog(msg, "本地重试2次执行失败");
                ack.acknowledge();
            }
        } catch (Exception e) {
            saveFailLog(msg, e.getMessage());
            ack.acknowledge();
        }
    }

    /**
     * 事务：消费端生成订单号，创建订单 + 扣真实库存
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean createOrderAndStock(Long userId, Long goodsId) {
        String orderId = idUtil.nextOrderId();
        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setGoodsId(goodsId);
        order.setStatus(OrderStatus.UN_PAY);
        order.setPayDeadline(LocalDateTime.now().plusMinutes(15));
        orderMapper.insert(order);

        int rows = goodsMapper.deductStock(goodsId);
        if (rows <= 0) {
            throw new RuntimeException("真实库存不足");
        }
        return true;
    }

    private void saveFailLog(KafkaMsgDTO msg, String errorMsg) {
        SeckillMqFailLog log = new SeckillMqFailLog();
        log.setUserId(msg.getUserId());
        log.setGoodsId(msg.getGoodsId());
        log.setMsgContent(JSON.toJSONString(msg));
        log.setErrorMsg(errorMsg);
        log.setRetryCount(0);
        log.setHandleStatus(0);
        failLogMapper.insert(log);
        // 此处添加监控告警
    }
}
```

## 3. 支付回调（3次重试：1s/2s/4s，查无订单直接退款）
```java
import org.springframework.stereotype.Service;

@Service
public class PayCallbackServiceImpl implements PayCallbackService {

    private final OrderMapper orderMapper;
    private final RetryUtil retryUtil;

    // 支付查询：最多3次重试，间隔 1s、2s、4s
    private static final int PAY_MAX_RETRY = 3;
    private static final long[] PAY_DELAYS = {1000, 2000, 4000};

    public PayCallbackServiceImpl(OrderMapper orderMapper, RetryUtil retryUtil) {
        this.orderMapper = orderMapper;
        this.retryUtil = retryUtil;
    }

    @Override
    public R payCallback(PayCallbackReq req) {
        String orderId = req.getOrderId();
        String outPayNo = req.getOutPayNo();

        // 重试查询订单
        Order order = retryUtil.retry(PAY_MAX_RETRY, PAY_DELAYS,
                () -> orderMapper.selectByOrderId(orderId));

        if (order == null) {
            return R.fail("订单不存在，执行退款");
        }

        // 乐观锁更新支付状态
        int rows = orderMapper.payOrder(orderId, outPayNo, OrderStatus.UN_PAY);
        if (rows > 0) {
            return R.success("支付处理成功");
        } else {
            return R.fail("订单状态已变更");
        }
    }
}
```

## 4. XXL-Job 定时任务（替代Spring定时任务）
```java
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class XxlJobTaskImpl implements XxlJobTaskService {

    private final SeckillMqFailLogMapper failLogMapper;
    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final StringRedisTemplate redisTemplate;
    private final RetryUtil retryUtil;
    private final IdUtil idUtil;

    // 异常记录最大自动重试次数
    private static final int TASK_MAX_RETRY = 5;

    public XxlJobTaskImpl(SeckillMqFailLogMapper failLogMapper,
                          OrderMapper orderMapper,
                          GoodsMapper goodsMapper,
                          StringRedisTemplate redisTemplate,
                          RetryUtil retryUtil,
                          IdUtil idUtil) {
        this.failLogMapper = failLogMapper;
        this.orderMapper = orderMapper;
        this.goodsMapper = goodsMapper;
        this.redisTemplate = redisTemplate;
        this.retryUtil = retryUtil;
        this.idUtil = idUtil;
    }

    /**
     * XXL-Job 任务1：Kafka异常记录补偿重试
     * 执行频率：每分钟一次（在XXL-Job管理后台配置Cron）
     */
    @XxlJob("mqFailRetryJobHandler")
    @Override
    public void mqFailRetryJob() {
        List<SeckillMqFailLog> failList = failLogMapper.selectUnHandleLog(TASK_MAX_RETRY);
        for (SeckillMqFailLog log : failList) {
            Long userId = log.getUserId();
            Long goodsId = log.getGoodsId();

            // 幂等判断
            Order exist = orderMapper.selectByUserAndGoods(userId, goodsId);
            if (exist != null) {
                failLogMapper.updateHandleStatus(log.getId(), 1);
                continue;
            }

            boolean ok = createOrderAndStock(userId, goodsId);
            if (ok) {
                failLogMapper.updateHandleStatus(log.getId(), 1);
            } else {
                failLogMapper.incrRetryCount(log.getId());
                if (log.getRetryCount() + 1 >= TASK_MAX_RETRY) {
                    failLogMapper.updateHandleStatus(log.getId(), 2);
                }
            }
        }
    }

    /**
     * XXL-Job 任务2：超时未支付关单任务
     * 执行频率：每分钟一次（在XXL-Job管理后台配置Cron）
     */
    @XxlJob("closeExpireOrderJobHandler")
    @Override
    public void closeExpireOrderJob() {
        List<Order> expireOrders = orderMapper.selectExpireUnPayOrder();
        for (Order order : expireOrders) {
            int rows = orderMapper.closeOrder(order.getOrderId(), OrderStatus.UN_PAY);
            if (rows > 0) {
                // 关单成功，归还Redis库存 + 移除用户限购
                String stockKey = String.format(RedisKeyConstant.SECKILL_STOCK, order.getGoodsId());
                String userKey = String.format(RedisKeyConstant.SECKILL_USER_SET, order.getGoodsId());
                redisTemplate.opsForValue().increment(stockKey, 1);
                redisTemplate.opsForSet().remove(userKey, order.getUserId().toString());
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean createOrderAndStock(Long userId, Long goodsId) {
        String orderId = idUtil.nextOrderId();
        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setGoodsId(goodsId);
        order.setStatus(OrderStatus.UN_PAY);
        order.setPayDeadline(java.time.LocalDateTime.now().plusMinutes(15));
        orderMapper.insert(order);

        int rows = goodsMapper.deductStock(goodsId);
        if (rows <= 0) {
            throw new RuntimeException("库存不足");
        }
        return true;
    }
}
```

---

# 七、MyBatis 核心SQL片段
```xml
<!-- 根据用户+商品查询订单 -->
<select id="selectByUserAndGoods" resultType="Order">
    SELECT * FROM t_order WHERE user_id = #{userId} AND goods_id = #{goodsId}
</select>

<!-- 根据orderId查询订单 -->
<select id="selectByOrderId" resultType="Order">
    SELECT * FROM t_order WHERE order_id = #{orderId}
</select>

<!-- 扣减真实库存 -->
<update id="deductStock">
    UPDATE t_goods SET stock = stock - 1 WHERE id = #{goodsId} AND stock > 0
</update>

<!-- 乐观锁-支付 -->
<update id="payOrder">
    UPDATE t_order
    SET status = 'PAYED', pay_time = NOW(), out_pay_no = #{outPayNo}
    WHERE order_id = #{orderId} AND status = #{oldStatus}
</update>

<!-- 乐观锁-关单 -->
<update id="closeOrder">
    UPDATE t_order
    SET status = 'CANCELED', close_time = NOW()
    WHERE order_id = #{orderId} AND status = #{oldStatus}
</update>

<!-- 查询超时未支付订单 -->
<select id="selectExpireUnPayOrder" resultType="Order">
    SELECT * FROM t_order
    WHERE status = 'UN_PAY' AND pay_deadline < NOW()
</select>

<!-- 查询未处理、未达重试上限的异常日志 -->
<select id="selectUnHandleLog" resultType="SeckillMqFailLog">
    SELECT * FROM t_seckill_mq_fail_log
    WHERE handle_status = 0 AND retry_count &lt; #{maxRetry}
</select>

<!-- 增加重试次数 -->
<update id="incrRetryCount">
    UPDATE t_seckill_mq_fail_log SET retry_count = retry_count + 1 WHERE id = #{id}
</update>

<!-- 更新处理状态 -->
<update id="updateHandleStatus">
    UPDATE t_seckill_mq_fail_log SET handle_status = #{status} WHERE id = #{id}
</update>
```

---

# 八、部署&配置说明
1. **Kafka**
   - 新建 Topic：`seckill_topic`，配置分区、副本；
   - 项目 `application.yml` 配置 Kafka 地址、分组。
2. **XXL-Job**
   - 部署 XXL-Job 调度中心；
   - 在管理后台新增两个任务，Cron 表达式配置为 `0/60 * * * * ?`（每分钟执行），分别绑定 `mqFailRetryJobHandler`、`closeExpireOrderJobHandler`；
3. **幂等保障**
   - 依靠 `(user_id, goods_id)` 联合唯一索引，杜绝重复建单、重复扣库存；
4. **异常兜底**
   - Kafka消费失败落异常表，XXL-Job定时补偿；
   - 支付3次短重试，无额外审核表，保持极简；
   - 无订单导致的Redis库存冻结，定期人工对账清理。