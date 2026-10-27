// AI_GENERATE_START -
package com.stock.model.repository;

import java.time.LocalDateTime;

/**
 * 模型版本列表轻量投影。
 *
 * 只读取模型运维列表展示所需字段，避免分页查询加载训练配置、归一化参数等大字段。
 *
 * @author mwangli
 * @since 2026-10-25
 */
public interface LstmModelListProjection {

    /**
     * 返回模型版本主键。
     *
     * @return 模型版本主键
     */
    String getId();

    /**
     * 返回模型逻辑名称。
     *
     * @return 模型逻辑名称
     */
    String getModelName();

    /**
     * 返回模型业务版本号。
     *
     * @return 模型业务版本号
     */
    String getModelVersion();

    /**
     * 返回模型状态。
     *
     * @return 模型状态
     */
    String getStatus();

    /**
     * 返回特征版本。
     *
     * @return 特征版本
     */
    String getFeatureVersion();

    /**
     * 返回输入契约 JSON。
     *
     * @return 输入契约 JSON
     */
    String getInputContractJson();

    /**
     * 返回模型参数大小，单位为字节。
     *
     * @return 模型参数大小
     */
    long getParameterSize();

    /**
     * 返回最佳训练轮次。
     *
     * @return 最佳训练轮次
     */
    int getEpoch();

    /**
     * 返回训练集损失。
     *
     * @return 训练集损失
     */
    Double getTrainLoss();

    /**
     * 返回验证集损失。
     *
     * @return 验证集损失
     */
    Double getValLoss();

    /**
     * 返回模型版本创建时间。
     *
     * @return 模型版本创建时间
     */
    LocalDateTime getCreatedAt();
}
// AI_GENERATE_END -
