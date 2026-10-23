// AI_GENERATE_START -
package com.stock.trading.service;

import com.stock.base.api.ResponseDTO;
import com.stock.base.config.InternalApiProperties;
import com.stock.base.trading.TradeDecisionGatePort;
import com.stock.base.trading.TradeDecisionGateResult;
import com.stock.trading.config.AgentServiceProperties;
import com.stock.trading.dto.AgentTradeDecisionGateRequest;
import com.stock.trading.dto.AgentTradeDecisionGateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;

/**
 * 通过内部 HTTP 接口读取 Agent 交易门禁结果。
 * Agent 不可用时保留 Java 基线并记录降级原因。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Service
public class HttpTradeDecisionGateAdapter implements TradeDecisionGatePort {

    private final AgentServiceProperties properties;
    private final RestClient restClient;

    /**
     * 创建 Agent 交易门禁客户端。
     *
     * @param properties Agent 服务连接配置
     * @param internalApiProperties 内部令牌配置
     */
    public HttpTradeDecisionGateAdapter(AgentServiceProperties properties,
                                        InternalApiProperties internalApiProperties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Token", internalApiProperties.getToken())
                .build();
    }

    /**
     * 调用 Agent 读取已持久化门禁结果。
     *
     * @param candidateBatchId 候选批次标识
     * @param stockCode 股票代码
     * @param strategyVersion 策略版本
     * @param baselineEligible Java 基线是否允许
     * @param evaluatedAt 评估时间
     * @return 门禁结果；服务关闭或不可用时返回 Java 基线结果
     */
    @Override
    public TradeDecisionGateResult evaluate(String candidateBatchId, String stockCode,
                                            String strategyVersion, boolean baselineEligible,
                                            LocalDateTime evaluatedAt) {
        if (!properties.isEnabled()) {
            return TradeDecisionGateResult.baselineAllowed();
        }
        try {
            ResponseDTO<AgentTradeDecisionGateResponse> response = restClient.post()
                    .uri("/api/internal/tradeDecisionGate/evaluate")
                    .body(new AgentTradeDecisionGateRequest(candidateBatchId, stockCode,
                            strategyVersion, baselineEligible, evaluatedAt))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new IllegalStateException(response == null ? "Agent 未返回响应" : response.getMessage());
            }
            AgentTradeDecisionGateResponse result = response.getData();
            return new TradeDecisionGateResult(result.allowed(), result.outcome(), result.reason());
        } catch (RuntimeException exception) {
            log.warn("Agent 交易门禁不可用，采用 Java 基线: stockCode={}, causeType={}",
                    stockCode, exception.getClass().getSimpleName());
            return TradeDecisionGateResult.baselineAllowed();
        }
    }
}
// AI_GENERATE_END -
