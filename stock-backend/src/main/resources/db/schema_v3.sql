-- ========================================================
-- V3.0 架构扩展表结构
-- 版本: 2.0
-- 创建日期: 2026-02-19
-- 说明: Python服务直接写入，Java服务仅读取
-- ========================================================

-- ========================================================
-- 1. 情感分析结果表 (Python写入 → Java读取)
-- ========================================================

-- 股票情感分析结果表
CREATE TABLE IF NOT EXISTS `stock_sentiment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stock_code` varchar(10) NOT NULL COMMENT '股票代码',
  `stock_name` varchar(50) DEFAULT NULL COMMENT '股票名称',
  `sentiment_score` double DEFAULT NULL COMMENT '情感得分(-1到1)',
  `positive_ratio` double DEFAULT NULL COMMENT '正面新闻比例',
  `negative_ratio` double DEFAULT NULL COMMENT '负面新闻比例',
  `neutral_ratio` double DEFAULT NULL COMMENT '中性新闻比例',
  `news_count` int(11) DEFAULT '0' COMMENT '新闻数量',
  `source` varchar(50) DEFAULT NULL COMMENT '数据来源',
  `analyze_date` date DEFAULT NULL COMMENT '分析日期',
  `analyze_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '分析时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_date` (`stock_code`, `analyze_date`),
  KEY `idx_sentiment_score` (`sentiment_score`),
  KEY `idx_analyze_date` (`analyze_date`),
  KEY `idx_news_count` (`news_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='股票情感分析结果表';

-- 新闻情感详情表
CREATE TABLE IF NOT EXISTS `news_sentiment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stock_code` varchar(10) NOT NULL COMMENT '股票代码',
  `news_title` varchar(255) DEFAULT NULL COMMENT '新闻标题',
  `news_content` text COMMENT '新闻内容摘要',
  `sentiment_score` double DEFAULT NULL COMMENT '情感得分(-1到1)',
  `sentiment_label` varchar(10) DEFAULT NULL COMMENT '情感标签(POSITIVE/NEGATIVE/NEUTRAL)',
  `confidence` double DEFAULT NULL COMMENT '置信度',
  `news_date` date DEFAULT NULL COMMENT '新闻日期',
  `source` varchar(50) DEFAULT NULL COMMENT '新闻来源',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_stock_code` (`stock_code`),
  KEY `idx_news_date` (`news_date`),
  KEY `idx_sentiment_label` (`sentiment_label`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新闻情感详情表';

-- ========================================================
-- 2. LSTM预测结果表 (Python写入 → Java读取)
-- ========================================================

-- 股票价格预测结果表
CREATE TABLE IF NOT EXISTS `stock_prediction` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stock_code` varchar(10) NOT NULL COMMENT '股票代码',
  `stock_name` varchar(50) DEFAULT NULL COMMENT '股票名称',
  `predict_date` date NOT NULL COMMENT '预测日期',
  `predict_price` double DEFAULT NULL COMMENT '预测价格',
  `predict_direction` varchar(10) DEFAULT NULL COMMENT '预测方向(UP/DOWN/HOLD)',
  `confidence` double DEFAULT NULL COMMENT '预测置信度',
  `model_version` varchar(20) DEFAULT NULL COMMENT '模型版本',
  `test_deviation` double DEFAULT NULL COMMENT '测试偏差率(%)',
  `features` json DEFAULT NULL COMMENT '特征数据(JSON)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_date` (`stock_code`, `predict_date`),
  KEY `idx_predict_direction` (`predict_direction`),
  KEY `idx_predict_date` (`predict_date`),
  KEY `idx_confidence` (`confidence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='股票价格预测结果表';

-- 预测模型性能表
CREATE TABLE IF NOT EXISTS `model_performance` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_version` varchar(20) NOT NULL COMMENT '模型版本',
  `stock_code` varchar(10) DEFAULT NULL COMMENT '股票代码(通用模型为NULL)',
  `train_start_date` date DEFAULT NULL COMMENT '训练开始日期',
  `train_end_date` date DEFAULT NULL COMMENT '训练结束日期',
  `test_accuracy` double DEFAULT NULL COMMENT '测试准确率',
  `test_deviation` double DEFAULT NULL COMMENT '测试偏差率(%)',
  `rmse` double DEFAULT NULL COMMENT '均方根误差',
  `mae` double DEFAULT NULL COMMENT '平均绝对误差',
  `sample_count` int(11) DEFAULT '0' COMMENT '样本数量',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DEPRECATED)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_model_version` (`model_version`),
  KEY `idx_stock_code` (`stock_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预测模型性能表';

-- ========================================================
-- 3. 股票综合评分排名表 (Python写入 → Java读取)
-- ========================================================

-- 股票综合评分表
CREATE TABLE IF NOT EXISTS `stock_ranking` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stock_code` varchar(10) NOT NULL COMMENT '股票代码',
  `stock_name` varchar(50) DEFAULT NULL COMMENT '股票名称',
  `composite_score` double DEFAULT NULL COMMENT '综合评分(0-100)',
  `sentiment_score` double DEFAULT NULL COMMENT '情感得分',
  `momentum_score` double DEFAULT NULL COMMENT '动量得分',
  `valuation_score` double DEFAULT NULL COMMENT '估值得分',
  `technical_score` double DEFAULT NULL COMMENT '技术得分',
  `rank_date` date NOT NULL COMMENT '排名日期',
  `rank_position` int(11) DEFAULT NULL COMMENT '排名位置',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_date` (`stock_code`, `rank_date`),
  KEY `idx_rank_position` (`rank_position`),
  KEY `idx_rank_date` (`rank_date`),
  KEY `idx_composite_score` (`composite_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='股票综合评分表';

-- ========================================================
-- 4. 任务执行日志表 (Python写入 → Java读取)
-- ========================================================

-- Python定时任务执行日志表
CREATE TABLE IF NOT EXISTS `task_execution_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_name` varchar(100) NOT NULL COMMENT '任务名称',
  `task_type` varchar(50) DEFAULT NULL COMMENT '任务类型(DATA_COLLECTION/SENTIMENT/PREDICTION/RANKING)',
  `module_id` varchar(20) DEFAULT NULL COMMENT '模块ID(MOD-001到MOD-004)',
  `status` varchar(20) DEFAULT NULL COMMENT '执行状态(STARTED/SUCCESS/FAILED)',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration` int(11) DEFAULT NULL COMMENT '执行时长(毫秒)',
  `records_processed` int(11) DEFAULT '0' COMMENT '处理记录数',
  `error_message` text COMMENT '错误信息',
  `execute_info` json DEFAULT NULL COMMENT '执行详情(JSON)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_name` (`task_name`),
  KEY `idx_status` (`status`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_module_id` (`module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行日志表';

-- ========================================================
-- 5. 更新现有表结构 (兼容V3.0)
-- ========================================================

-- 为stock_info表添加V3.0所需字段
ALTER TABLE `stock_info` 
  ADD COLUMN IF NOT EXISTS `sentiment_score` double DEFAULT NULL COMMENT '情感得分' AFTER `predict_price`,
  ADD COLUMN IF NOT EXISTS `ranking_position` int(11) DEFAULT NULL COMMENT '综合排名' AFTER `sentiment_score`,
  ADD COLUMN IF NOT EXISTS `last_sentiment_date` date DEFAULT NULL COMMENT '最后情感分析日期' AFTER `ranking_position`,
  ADD COLUMN IF NOT EXISTS `last_prediction_date` date DEFAULT NULL COMMENT '最后预测日期' AFTER `last_sentiment_date`;

-- 为model_info表添加V3.0所需字段
ALTER TABLE `model_info` 
  ADD COLUMN IF NOT EXISTS `model_version` varchar(20) DEFAULT NULL COMMENT '模型版本' AFTER `status`,
  ADD COLUMN IF NOT EXISTS `model_path` varchar(255) DEFAULT NULL COMMENT '模型文件路径' AFTER `model_version`,
  ADD COLUMN IF NOT EXISTS `train_start_date` date DEFAULT NULL COMMENT '训练开始日期' AFTER `train_period`,
  ADD COLUMN IF NOT EXISTS `last_train_date` date DEFAULT NULL COMMENT '最后训练日期' AFTER `train_start_date`;

-- ========================================================
-- 6. 初始化V3.0定时任务配置
-- ========================================================

-- 注意: V3.0中Python服务使用APScheduler, 以下配置仅供Java端参考
INSERT INTO `quartz_job` (`name`, `description`, `class_name`, `cron`, `status`, `sort`) VALUES
('情感分析任务', '每日收盘后分析股票情感', 'Python-APScheduler', '0 30 15 * * ?', '1', 4),
('LSTM价格预测', '每日收盘后预测次日价格', 'Python-APScheduler', '0 45 15 * * ?', '1', 5),
('股票综合评分', '每日收盘后计算综合评分', 'Python-APScheduler', '0 0 16 * * ?', '1', 6)
ON DUPLICATE KEY UPDATE `id` = `id`;

-- ========================================================
-- 7. V3.0 常用查询示例
-- ========================================================

-- 查询当日情感得分排名TOP20
-- SELECT * FROM stock_sentiment WHERE analyze_date = CURDATE() ORDER BY sentiment_score DESC LIMIT 20;

-- 查询当日预测结果(上涨方向)
-- SELECT * FROM stock_prediction WHERE predict_date = CURDATE() AND predict_direction = 'UP' ORDER BY confidence DESC;

-- 查询当日综合评分TOP20
-- SELECT * FROM stock_ranking WHERE rank_date = CURDATE() ORDER BY rank_position LIMIT 20;

-- 查询任务执行状态
-- SELECT * FROM task_execution_log WHERE task_type IN ('SENTIMENT', 'PREDICTION', 'RANKING') ORDER BY start_time DESC LIMIT 20;

-- 查询最近7天预测准确率
-- SELECT 
--   predict_date,
--   COUNT(*) as total,
--   SUM(CASE WHEN predict_direction = 'UP' THEN 1 ELSE 0 END) as up_count,
--   SUM(CASE WHEN predict_direction = 'DOWN' THEN 1 ELSE 0 END) as down_count
-- FROM stock_prediction 
-- WHERE predict_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
-- GROUP BY predict_date;
