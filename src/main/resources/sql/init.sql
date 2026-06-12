-- 秒杀商品表
CREATE TABLE `seckill_goods` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `goods_id` bigint(20) NOT NULL COMMENT '商品ID',
  `goods_name` varchar(255) NOT NULL COMMENT '商品名称',
  `seckill_price` decimal(10,2) NOT NULL COMMENT '秒杀价格',
  `original_price` decimal(10,2) NOT NULL COMMENT '原价',
  `total_stock` int(11) NOT NULL COMMENT '总库存',
  `remaining_stock` int(11) NOT NULL COMMENT '剩余库存',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态 0-未开始 1-进行中 2-已结束',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品表';

-- 秒杀订单表
CREATE TABLE `seckill_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` varchar(64) NOT NULL COMMENT '订单ID',
  `goods_id` bigint(20) NOT NULL COMMENT '商品ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `price` decimal(10,2) NOT NULL COMMENT '商品单价',
  `quantity` int(11) NOT NULL DEFAULT '1' COMMENT '购买数量',
  `total_amount` decimal(10,2) NOT NULL COMMENT '订单总金额',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态 0-未支付 1-已支付 2-已取消',
  `pay_timeout` datetime NOT NULL COMMENT '支付超时时间',
  `close_time` datetime DEFAULT NULL COMMENT '关单时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_goods_id` (`goods_id`),
  KEY `idx_status_pay_timeout` (`status`, `pay_timeout`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';

-- 秒杀MQ消费失败表
CREATE TABLE `seckill_mq_fail` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` varchar(64) NOT NULL COMMENT '订单ID',
  `message_body` text NOT NULL COMMENT 'MQ消息体',
  `error_stack` text COMMENT '错误堆栈',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `retry_count` int(11) NOT NULL DEFAULT '0' COMMENT '重试次数',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态 0-未处理 1-已处理 2-人工处理',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_status_create_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀MQ消费失败表';

-- 初始化测试数据
INSERT INTO `seckill_goods` (`goods_id`, `goods_name`, `seckill_price`, `original_price`, `total_stock`, `remaining_stock`, `status`, `start_time`, `end_time`) VALUES
(1001, 'iPhone 15 Pro', 6999.00, 8999.00, 100, 100, 1, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY)),
(1002, 'MacBook Pro', 12999.00, 16999.00, 50, 50, 1, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY)),
(1003, 'AirPods Pro', 1499.00, 1999.00, 200, 200, 1, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY));