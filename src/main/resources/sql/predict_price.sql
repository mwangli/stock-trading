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

 Date: 05/04/2024 16:50:47
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for predict_price
-- ----------------------------
DROP TABLE IF EXISTS `predict_price`;
CREATE TABLE `predict_price`  (
  `id` bigint(0) NOT NULL COMMENT '主键ID',
  `stock_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '股票代码',
  `date_string` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_german2_ci NULL DEFAULT NULL COMMENT '预测日期',
  `actual_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '实际价格',
  `predict_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '预测价格',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `updae_time` datetime(0) NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_german2_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
