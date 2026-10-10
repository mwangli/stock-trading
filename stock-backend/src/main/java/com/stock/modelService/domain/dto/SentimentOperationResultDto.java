package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 情感分析模型通用操作结果 DTO
 * <p>
 * 用于统一封装健康检查、模型重载、模型下载等接口的返回结果，
 * 避免为每个简单操作定义过多细粒度 DTO。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentOperationResultDto {

    /**
     * 是否执行成功
     */
    private boolean success;

    /**
     * 操作或健康检查的提示信息
     */
    private String message;

    /**
     * 服务状态（健康检查使用），如 "UP" / "DOWN"
     */
    private String status;

    /**
     * 服务名称（健康检查使用）
     */
    private String service;

    /**
     * 模型是否已加载
     */
    private Boolean modelLoaded;

    /**
     * 模型最近一次成功加载时间
     */
    private LocalDateTime lastLoadedTime;

    /**
     * 预训练模型或缓存目录路径（下载接口使用）
     */
    private String modelPath;
}

