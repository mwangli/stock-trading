/*
 Navicat Premium Data Transfer

 Source Server         : test
 Source Server Type    : MySQL
 Source Server Version : 80027
 Source Host           : mwang.online:3306
 Source Schema         : found_trading

 Target Server Type    : MySQL
 Target Server Version : 80027
 File Encoding         : 65001

 Date: 21/03/2023 13:53:29
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for found_trading_record
-- ----------------------------
DROP TABLE IF EXISTS `found_trading_record`;
CREATE TABLE `found_trading_record`  (
  `id` bigint(0) NOT NULL COMMENT '自增主键',
  `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '基金代码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '基金名称',
  `buy_amount` decimal(32, 4) NULL DEFAULT NULL COMMENT '买入金额',
  `buy_date` datetime(0) NULL DEFAULT NULL COMMENT '买入时间',
  `sale_amount` decimal(32, 4) NULL DEFAULT NULL COMMENT '卖出金额',
  `sale_date` datetime(0) NULL DEFAULT NULL COMMENT '卖出时间',
  `account_amount` decimal(32, 4) NULL DEFAULT NULL COMMENT '到账金额',
  `account_date` datetime(0) NULL DEFAULT NULL COMMENT '到账时间',
  `expected_income` decimal(32, 4) NULL DEFAULT NULL COMMENT '预计收益',
  `expected_income_rate` decimal(32, 4) NULL DEFAULT NULL COMMENT '预计日收益率',
  `real_income` decimal(32, 4) NULL DEFAULT NULL COMMENT '实际收益',
  `real_income_rate` decimal(32, 4) NULL DEFAULT NULL COMMENT '实际日收益率',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_german2_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
