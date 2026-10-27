// AI_GENERATE_START -
package com.stock.model.service;

/**
 * 训练数据无法形成可用快照时抛出的业务异常。
 * 用于把可修复的数据缺口与存储、代码和运行时故障区分开。
 *
 * @author mwangli
 * @since 2026-10-25
 */
public class ModelTrainingDataNotReadyException extends RuntimeException {

    /**
     * 创建训练数据未就绪异常。
     *
     * @param message 数据缺口说明
     * @param cause 原始数据处理异常
     */
    public ModelTrainingDataNotReadyException(String message, Throwable cause) {
        super(message, cause);
    }
}
// AI_GENERATE_END -
