/*
 Navicat Premium Data Transfer

 Source Server         : 124.220.36.95
 Source Server Type    : MySQL
 Source Server Version : 80027
 Source Host           : 124.220.36.95:3306
 Source Schema         : found_trading

 Target Server Type    : MySQL
 Target Server Version : 80027
 File Encoding         : 65001

 Date: 05/04/2024 17:26:12
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for account_info
-- ----------------------------
DROP TABLE IF EXISTS `account_info`;
CREATE TABLE `account_info`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `available_amount` double(32, 4) NULL DEFAULT NULL COMMENT '可用金额',
  `used_amount` double(32, 4) NULL DEFAULT NULL COMMENT '已用金额',
  `total_amount` double(32, 4) NULL DEFAULT NULL COMMENT '账户余额',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 987 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '账户金额表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for code_message
-- ----------------------------
DROP TABLE IF EXISTS `code_message`;
CREATE TABLE `code_message`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '错误码',
  `message` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL,
  `solution` text CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 347 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_german2_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for predict_price
-- ----------------------------
DROP TABLE IF EXISTS `predict_price`;
CREATE TABLE `predict_price`  (
  `id` bigint(0) NOT NULL COMMENT '主键ID',
  `stock_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '股票代码',
  `date` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '预测日期',
  `actual_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '实际价格',
  `predict_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '预测价格',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_german2_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for quartz_job
-- ----------------------------
DROP TABLE IF EXISTS `quartz_job`;
CREATE TABLE `quartz_job`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '任务名称',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '任务描述',
  `class_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '任务全类名',
  `cron` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '执行表达式',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '启用状态',
  `running` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '运行状态',
  `deleted` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '是否删除',
  `sort` int(0) NULL DEFAULT NULL COMMENT '排列顺序',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `name`(`name`) USING BTREE COMMENT '唯一名称'
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '定时任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for score_strategy
-- ----------------------------
DROP TABLE IF EXISTS `score_strategy`;
CREATE TABLE `score_strategy`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '策略名称',
  `params` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '策略参数',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '参数说明',
  `status` int(0) NULL DEFAULT NULL COMMENT '选中状态',
  `sort` int(0) NULL DEFAULT NULL COMMENT '排序字段',
  `deleted` int(0) NULL DEFAULT 1 COMMENT '删除状态',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '评分策略表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for stock_info
-- ----------------------------
DROP TABLE IF EXISTS `stock_info`;
CREATE TABLE `stock_info`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '股票名称',
  `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '股票代码',
  `market` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '上市地区',
  `increase` double(32, 4) NULL DEFAULT NULL COMMENT '增长幅度',
  `price` double(10, 2) NULL DEFAULT NULL COMMENT '今日价格',
  `prices` json NULL COMMENT '历史价格',
  `increase_rate` json NULL COMMENT '日增长率',
  `score` double(32, 4) NULL DEFAULT NULL COMMENT '评价得分',
  `permission` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '交易权限',
  `buy_sale_count` int(0) NULL DEFAULT NULL COMMENT '交易次数',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '修改时间',
  `deleted` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '退市删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `code`(`code`) USING BTREE COMMENT '唯一索引'
) ENGINE = InnoDB AUTO_INCREMENT = 101394 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '股票信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for trading_record
-- ----------------------------
DROP TABLE IF EXISTS `trading_record`;
CREATE TABLE `trading_record`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '基金代码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '基金名称',
  `buy_date_string` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '买入日期',
  `buy_date` datetime(0) NULL DEFAULT NULL COMMENT '买入时间',
  `buy_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '买入订单编号',
  `buy_price` double(32, 4) NULL DEFAULT NULL COMMENT '买入价格',
  `buy_number` int(0) NULL DEFAULT NULL COMMENT '买入数量',
  `buy_amount` decimal(32, 4) NULL DEFAULT NULL COMMENT '买入金额',
  `sale_date_string` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '卖出日期',
  `sale_date` datetime(0) NULL DEFAULT NULL COMMENT '卖出时间',
  `sale_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '卖出订单编号',
  `sale_price` double(32, 4) NULL DEFAULT NULL COMMENT '卖出价格',
  `sale_number` int(0) NULL DEFAULT NULL COMMENT '卖出数量',
  `sale_amount` decimal(32, 4) NULL DEFAULT NULL COMMENT '卖出金额',
  `sold` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '是否卖出',
  `income` decimal(32, 4) NULL DEFAULT NULL COMMENT '收益金额',
  `income_rate` decimal(32, 4) NULL DEFAULT NULL COMMENT '收益率',
  `hold_days` int(0) NULL DEFAULT NULL COMMENT '持有天数',
  `daily_income_rate` decimal(32, 4) NULL DEFAULT NULL COMMENT '日收益率',
  `strategy_id` bigint(0) NULL DEFAULT NULL COMMENT '选股策略',
  `strategy_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '选股策略',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 222 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_german2_ci COMMENT = '交易记录表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
