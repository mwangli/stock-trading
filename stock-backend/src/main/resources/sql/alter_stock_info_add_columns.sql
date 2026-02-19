-- ============================================
-- 数据采集模块 - 数据库迁移脚本
-- 为stock_info表添加新字段
-- ============================================

-- 添加industry字段 (所属行业)
ALTER TABLE `stock_info` 
ADD COLUMN IF NOT EXISTS `industry` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '所属行业' AFTER `market`;

-- 添加is_st字段 (是否ST股票)
ALTER TABLE `stock_info` 
ADD COLUMN IF NOT EXISTS `is_st` tinyint(1) NULL DEFAULT 0 COMMENT '是否ST股票 0-否 1-是' AFTER `industry`;

-- 添加is_tradable字段 (是否可交易)
ALTER TABLE `stock_info` 
ADD COLUMN IF NOT EXISTS `is_tradable` tinyint(1) NULL DEFAULT 1 COMMENT '是否可交易 0-否 1-是' AFTER `is_st`;

-- 添加listing_date字段 (上市日期)
ALTER TABLE `stock_info` 
ADD COLUMN IF NOT EXISTS `listing_date` date NULL DEFAULT NULL COMMENT '上市日期' AFTER `is_tradable`;

-- 添加selected字段 (是否已选择)
ALTER TABLE `stock_info` 
ADD COLUMN IF NOT EXISTS `selected` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '是否选中 0-否 1-是' AFTER `deleted`;

-- 创建索引
CREATE INDEX IF NOT EXISTS `idx_industry` ON `stock_info` (`industry`) USING BTREE;
CREATE INDEX IF NOT EXISTS `idx_is_tradable` ON `stock_info` (`is_tradable`) USING BTREE;
CREATE INDEX IF NOT EXISTS `idx_is_st` ON `stock_info` (`is_st`) USING BTREE;

-- 验证字段添加
DESCRIBE `stock_info`;
