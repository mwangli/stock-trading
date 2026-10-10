// AI_GENERATE_START ---
package com.stock.modelService.service;

import com.stock.modelService.domain.vo.SentimentAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 情感模型在线推理门面。
 * 统一委托给 DJL PyTorch 模型服务，避免 Controller 和策略层依赖具体引擎实现。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Service
@RequiredArgsConstructor
public class SentimentInferenceService {

    private final SentimentTrainerService sentimentTrainerService;

    /**
     * 加载本地或显式允许下载的 Hugging Face PyTorch 模型。
     *
     * @return true 表示加载成功
     */
    public boolean loadModel() {
        return sentimentTrainerService.loadModel();
    }

    /**
     * 执行情感分析，非交易场景允许规则降级。
     *
     * @param text 待分析文本
     * @return 情感分析结果
     */
    public SentimentAnalysisResult analyzeSentiment(String text) {
        return sentimentTrainerService.analyzeSentiment(text);
    }

    /**
     * 执行情感分析，交易候选场景禁止规则降级。
     *
     * @param text 待分析文本
     * @return 情感分析结果
     */
    public SentimentAnalysisResult analyzeSentimentRequired(String text) {
        return sentimentTrainerService.analyzeSentimentRequired(text);
    }

    /**
     * 返回接口层需要的情感分析详情。
     *
     * @param text 待分析文本
     * @return 情感分析详情
     */
    public Map<String, Object> analyzeSentimentWithDetails(String text) {
        return sentimentTrainerService.analyzeSentimentWithDetails(text);
    }

    /**
     * 查询模型是否已加载。
     *
     * @return true 表示模型已加载
     */
    public boolean isModelLoaded() {
        return sentimentTrainerService.isModelLoaded();
    }

    /**
     * 返回最近一次成功加载时间。
     *
     * @return 加载时间，未加载时为空
     */
    public LocalDateTime getLastLoadedTime() {
        return sentimentTrainerService.getLastLoadedTime();
    }
}
// AI_GENERATE_END ---
