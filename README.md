# 秒杀系统

基于 Spring Boot + Kafka + Redis + XXL-Job 的极简架构秒杀系统

## 项目特点

- **极简架构**：无分布式锁、无额外审核表、无Redis占位标记
- **Kafka异步处理**：MQ仅传递userId和goodsId，消费端生成订单号
- **幂等性保证**：通过(user_id, goods_id)联合唯一索引实现全链路幂等
- **重试机制**：支付回调3次重试(1s/2s/4s)，消费端2次本地重试
- **定时任务**：使用XXL-Job调度异常补偿和超时关单任务
- **乐观锁**：支付、关单操作基于状态乐观锁互斥

## 技术栈

- Spring Boot 2.7.18
- Kafka 2.8.1
- Redis
- MyBatis 2.2.2
- XXL-Job 2.4.0
- MySQL
- Lombok

## 项目结构

```
com.example.seckill
├── config                # 配置类
│   ├── KafkaConfig.java      # Kafka配置
│   ├── RedisConfig.java      # Redis配置
│   └── XxlJobConfig.java     # XXL-Job配置
├── controller            # 接口层
│   ├── SeckillController.java     # 秒杀接口
│   └── PaymentController.java     # 支付回调接口
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
│   ├── RetryUtil.java     # 通用重试工具
│   ├── SnowflakeIdGenerator.java  # 雪花算法
│   └── RedisStockInitializer.java  # Redis库存初始化
├── dto                   # 传输对象
│   ├── SeckillReq.java
│   ├── PayCallbackReq.java
│   └── KafkaMsgDTO.java   # Kafka消息体：仅 userId / goodsId
├── constant              # 常量
│   ├── OrderStatus.java
│   └── RedisKeyConstant.java
└── common
    └── response
        └── R.java        # 通用响应结果
```

## 数据库表

### 1. 商品表 t_goods
```sql
CREATE TABLE t_goods (
    id BIGINT PRIMARY KEY COMMENT '商品ID',
    stock INT NOT NULL DEFAULT 0 COMMENT '真实库存',
    name VARCHAR(100) NOT NULL COMMENT '商品名称',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品表';
```

### 2. 订单表 t_order
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

### 3. Kafka消费异常日志表 t_seckill_mq_fail_log
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

## 核心流程

### 1. 秒杀下单流程
1. 用户发起秒杀请求
2. Redis+Lua脚本原子操作：预扣库存 + 用户限购检查
3. 预扣成功后，向Kafka发送仅包含userId和goodsId的消息
4. 即刻返回"下单已受理，请15分钟内完成支付"
5. Kafka消费端生成全局唯一orderId，在本地事务中完成建单+扣真实库存
6. 依靠(user_id, goods_id)联合唯一索引实现幂等性

### 2. 支付回调流程
1. 接收支付回调请求
2. 按间隔1s、2s、4s最多重试3次查询订单
3. 仍查不到订单则直接原路退款
4. 乐观锁更新订单支付状态

### 3. 定时任务流程
1. **异常补偿任务**：每分钟执行一次，重试失败的MQ消息，达最大重试次数后标记人工处理
2. **超时关单任务**：每分钟执行一次，扫描超时未支付订单，乐观锁关单并归还Redis缓存库存

## 接口文档

### 1. 秒杀下单接口
- **URL**: `POST /seckill/do`
- **请求体**:
```json
{
  "userId": 123456,
  "goodsId": 1
}
```
- **响应**:
```json
{
  "code": 200,
  "message": "下单已受理，请15分钟内完成支付",
  "data": null
}
```

### 2. 支付回调接口
- **URL**: `POST /payment/callback`
- **请求体**:
```json
{
  "orderId": "1234567890123456789",
  "outPayNo": "PAY123456"
}
```
- **响应**:
```json
{
  "code": 200,
  "message": "支付处理成功",
  "data": null
}
```

## 部署说明

### 1. 环境要求
- JDK 1.8+
- MySQL 5.7+
- Redis 3.0+
- Kafka 2.8+
- XXL-Job Admin

### 2. 配置文件
修改 `application.yml` 中的数据库、Redis、Kafka、XXL-Job配置。

### 3. 初始化数据库
执行 `src/main/resources/sql/schema.sql` 初始化数据库表和测试数据。

### 4. 启动应用
```bash
mvn clean package
java -jar target/seckill-1.0.0.jar
```

### 5. 配置XXL-Job任务
在XXL-Job管理后台配置以下任务：
- **mqFailRetryJobHandler**: Cron表达式 `0 */1 * * * ?` (每分钟执行)
- **timeoutCloseOrderJobHandler**: Cron表达式 `0 */1 * * * ?` (每分钟执行)

## 注意事项

1. 应用启动时会自动初始化Redis库存，确保Redis已启动
2. 确保Kafka topic `seckill_topic` 已创建
3. XXL-Job任务需要在管理后台配置后才能正常执行
4. 生产环境建议配置监控告警，关注MQ消费异常日志

## 许可证

MIT License