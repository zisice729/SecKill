-- 秒杀系统数据库表结构

-- 1. 商品表 t_goods
CREATE TABLE t_goods (
    id BIGINT PRIMARY KEY COMMENT '商品ID',
    stock INT NOT NULL DEFAULT 0 COMMENT '真实库存',
    name VARCHAR(100) NOT NULL COMMENT '商品名称',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品表';

-- 2. 订单表 t_order（联合唯一索引做幂等）
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
    UNIQUE KEY uk_user_goods (user_id, goods_id),
    INDEX idx_status (status),
    INDEX idx_pay_deadline (pay_deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';

-- 3. Kafka消费异常日志表 t_seckill_mq_fail_log
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
    INDEX idx_handle_status (handle_status),
    INDEX idx_user_goods (user_id, goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Kafka消费异常日志';

-- 插入测试数据
INSERT INTO t_goods (id, stock, name) VALUES
(1, 100, 'iPhone 15 Pro'),
(2, 50, 'MacBook Pro M3'),
(3, 200, 'AirPods Pro 2');