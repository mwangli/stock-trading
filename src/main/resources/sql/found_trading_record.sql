/*
 Navicat Premium Data Transfer

 Source Server         : test
 Source Server Type    : MySQL
 Source Server Version : 80027
 Source Host           : 124.220.36.95:3306
 Source Schema         : found_trading

 Target Server Type    : MySQL
 Target Server Version : 80027
 File Encoding         : 65001

 Date: 26/05/2023 09:47:18
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for found_trading_record
-- ----------------------------
DROP TABLE IF EXISTS `found_trading_record`;
CREATE TABLE `found_trading_record`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '基金代码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '基金名称',
  `buy_date` datetime(0) NULL DEFAULT NULL COMMENT '买入时间',
  `buy_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '买入订单编号',
  `buy_number` int(0) NULL DEFAULT NULL COMMENT '买入数量',
  `buy_price` double(32, 4) NULL DEFAULT NULL COMMENT '买入价格',
  `buy_amount` decimal(32, 4) NULL DEFAULT NULL COMMENT '买入金额',
  `sale_date` datetime(0) NULL DEFAULT NULL COMMENT '卖出时间',
  `sale_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '卖出订单编号',
  `sale_number` int(0) NULL DEFAULT NULL COMMENT '卖出数量',
  `sale_price` double(32, 4) NULL DEFAULT NULL COMMENT '卖出价格',
  `sale_amount` decimal(32, 4) NULL DEFAULT NULL COMMENT '卖出金额',
  `sold` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '是否卖出',
  `income` decimal(32, 4) NULL DEFAULT NULL COMMENT '收益金额',
  `hold_days` int(0) NULL DEFAULT NULL COMMENT '持有天数',
  `daily_income_rate` decimal(32, 4) NULL DEFAULT NULL COMMENT '日收益率',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 127 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_german2_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
