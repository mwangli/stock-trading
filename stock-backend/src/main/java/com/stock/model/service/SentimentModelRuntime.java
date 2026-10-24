// AI_GENERATE_START -
package com.stock.model.service;

import com.stock.model.dto.SentimentModelInput;
import com.stock.model.vo.SentimentAnalysisResult;

/**
 * 情感模型运行时统一接口。
 * FinBERT 与 Mengzi 分类器必须通过同一输入和输出契约提供推理能力。
 *
 * @author mwangli
 * @since 2026-10-24
 */
public interface SentimentModelRuntime {

    /**
     * 返回运行时模型名称。
     *
     * @return 稳定模型名称
     */
    String modelName();

    /**
     * 对统一分块输入执行严格推理。
     *
     * @param input 已完成标题优先和关键句分块的输入
     * @return 归一化三分类结果
     * @throws IllegalStateException 模型未加载或推理失败时抛出
     */
    SentimentAnalysisResult predict(SentimentModelInput input);
}
// AI_GENERATE_END -
