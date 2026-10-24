// AI_GENERATE_START ---
package com.stock.model.service;

import com.stock.model.config.SentimentShadowConfig;
import com.stock.model.dto.SentimentModelInput;
import com.stock.model.entity.SentimentModelRoutingStateDocument;
import com.stock.model.repository.SentimentModelRoutingStateRepository;
import com.stock.model.vo.SentimentAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * FinBERT 与 Mengzi 的生产推理路由服务。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Service
@RequiredArgsConstructor
public class SentimentModelRouterService {

    /** 全局情感模型路由状态主键。 */
    public static final String ROUTING_STATE_ID = "GLOBAL_SENTIMENT_ROUTING";

    private final SentimentInferenceService finbertInferenceService;
    private final MengziModelRuntimeService mengziRuntimeService;
    private final SentimentModelRoutingStateRepository routingStateRepository;
    private final SentimentShadowConfig shadowConfig;
    private final SentimentInputAssembler inputAssembler;

    /**
     * 使用当前生产路由执行严格情感推理。
     *
     * @param text 规范化事件文本
     * @return 情感分析结果
     */
    public SentimentAnalysisResult analyzeRequired(String text) {
        return analyzeRequired(inputAssembler.assembleText(text));
    }

    /**
     * 使用明确标题和正文执行当前生产情感模型推理。
     *
     * @param title 新闻或公告标题
     * @param content 新闻或公告正文
     * @return 情感分析结果
     */
    public SentimentAnalysisResult analyzeRequired(String title, String content) {
        return analyzeRequired(inputAssembler.assemble(title, content));
    }

    /**
     * 使用当前生产路由执行统一输入推理；Mengzi 运行故障时自动回滚 FinBERT。
     *
     * @param input 标题优先的统一模型输入
     * @return 情感分析结果
     */
    public SentimentAnalysisResult analyzeRequired(SentimentModelInput input) {
        if (isMengziActive()) {
            try {
                return mengziRuntimeService.predict(input);
            } catch (RuntimeException exception) {
                rollbackToFinbert("Mengzi 生产推理故障: " + exception.getClass().getSimpleName());
            }
        }
        return finbertInferenceService.analyzeSentimentRequired(input);
    }

    /**
     * 返回当前生产情感模型版本。
     *
     * @return 可追溯模型版本
     */
    public String activeModelVersion() {
        if (isMengziActive()) {
            return shadowConfig.getModelVersion();
        }
        return finbertInferenceService.getArtifactInspection().getModelVersion();
    }

    /**
     * 查询当前是否由 Mengzi 提供生产推理。
     *
     * @return 是否激活 Mengzi
     */
    public boolean isMengziActive() {
        return routingStateRepository.findById(ROUTING_STATE_ID)
                .map(state -> "MENGZI".equals(state.getActiveModel()) && "ACTIVE".equals(state.getStatus()))
                .orElse(false);
    }

    /**
     * 初始化缺失的路由状态，默认使用 FinBERT。
     *
     * @return 当前路由状态
     */
    public SentimentModelRoutingStateDocument currentState() {
        return routingStateRepository.findById(ROUTING_STATE_ID).orElseGet(() -> {
            SentimentModelRoutingStateDocument state = new SentimentModelRoutingStateDocument();
            state.setId(ROUTING_STATE_ID);
            state.setActiveModel("FINBERT");
            state.setStatus("SHADOW");
            state.setDecisionReason("Mengzi 尚未完成影子门槛验证");
            state.setUpdatedAt(LocalDateTime.now());
            return routingStateRepository.save(state);
        });
    }

    /**
     * 人工确认激活已通过影子门槛的 Mengzi 候选。
     *
     * @param decisionReason 人工确认原因或变更单标识
     * @return 激活后的路由状态
     */
    public synchronized SentimentModelRoutingStateDocument activateEligibleMengzi(String decisionReason) {
        SentimentModelRoutingStateDocument state = currentState();
        if (!"ELIGIBLE".equals(state.getStatus())) {
            throw new IllegalStateException("Mengzi 尚未进入 ELIGIBLE，禁止激活");
        }
        mengziRuntimeService.loadModel();
        state.setPreviousModel(state.getActiveModel());
        state.setActiveModel("MENGZI");
        state.setStatus("ACTIVE");
        state.setActivatedAt(LocalDateTime.now());
        state.setDecisionReason(decisionReason == null || decisionReason.isBlank()
                ? "人工确认激活 Mengzi" : decisionReason.trim());
        state.setUpdatedAt(LocalDateTime.now());
        return routingStateRepository.save(state);
    }

    private synchronized void rollbackToFinbert(String reason) {
        SentimentModelRoutingStateDocument state = currentState();
        state.setPreviousModel("MENGZI");
        state.setActiveModel("FINBERT");
        state.setStatus("ROLLBACK");
        state.setDecisionReason(reason);
        state.setUpdatedAt(LocalDateTime.now());
        routingStateRepository.save(state);
        mengziRuntimeService.unloadModel();
    }
}
// AI_GENERATE_END ---
