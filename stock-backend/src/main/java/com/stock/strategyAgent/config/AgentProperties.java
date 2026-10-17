// AI_GENERATE_START --
package com.stock.strategyAgent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 模块运行配置。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** Agent 总开关，默认关闭。 */
    private boolean enabled = false;

    /** 模型提供方名称。 */
    private String provider = "disabled";

    /** 外部模型 API 地址。 */
    private String apiUrl = "";

    /** 外部模型 API 密钥。 */
    private String apiKey = "";

    /** 外部模型名称。 */
    private String model = "";

    /** 单次任务最大工具调用次数。 */
    private int maxSteps = 6;

    /** 单次任务最大模型轮次。 */
    private int maxModelRounds = 3;

    /** 模型调用超时时间，单位毫秒。 */
    private long timeoutMs = 30000L;

    /** 单次输出最大字符数。 */
    private int maxOutputChars = 12000;

    /** Agent 最大并发数。 */
    private int maxConcurrency = 1;

    /** 报告任务开关。 */
    private boolean reportEnabled = false;

    /** 交易计划任务开关。 */
    private boolean tradePlanEnabled = false;

    /** RAG 检索开关。 */
    private boolean ragEnabled = false;

    /** Memory 开关。 */
    private boolean memoryEnabled = true;

    /** MongoDB RAG 集合名称。 */
    private String ragCollection = "agent_rag_chunks";

    /** Memory 默认有效期，单位天。 */
    private int memoryDefaultTtlDays = 90;

    /** 短期会话最多保留的消息数量。 */
    private int chatMemoryMaxMessages = 20;

    /** 工具调用超时时间，单位毫秒。 */
    private long toolTimeoutMs = 5000L;

    /** 是否记录 Agent 审计摘要。 */
    private boolean auditEnabled = true;

    /** Embedding 模型名称。 */
    private String embeddingModel = "";

    /** Embedding 模型版本。 */
    private String embeddingModelVersion = "";
}
// AI_GENERATE_END --
