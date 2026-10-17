// AI_GENERATE_START ---
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

    /** 模型选择策略，支持 disabled、deepseek、ollama 和 auto。 */
    private String provider = "disabled";

    /** DeepSeek API 配置。 */
    private DeepSeekProperties deepseek = new DeepSeekProperties();

    /** Ollama 本地模型配置。 */
    private OllamaProperties ollama = new OllamaProperties();

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

    /**
     * DeepSeek API 连接配置。
     *
     * @author mwangli
     * @since 2026-10-17
     */
    @Data
    public static class DeepSeekProperties {

        /** 是否启用 DeepSeek。 */
        private boolean enabled = false;

        /** OpenAI-compatible API 地址。 */
        private String baseUrl = "https://api.deepseek.com/v1";

        /** DeepSeek API Key，仅从运行环境注入。 */
        private String apiKey = "";

        /** DeepSeek 模型名称。 */
        private String model = "deepseek-chat";
    }

    /**
     * Ollama 本地模型连接配置。
     *
     * @author mwangli
     * @since 2026-10-17
     */
    @Data
    public static class OllamaProperties {

        /** 是否启用 Ollama。 */
        private boolean enabled = false;

        /** Ollama OpenAI-compatible API 地址。 */
        private String baseUrl = "http://localhost:11434/v1";

        /** Ollama 协议兼容 Key，不作为真实安全凭据。 */
        private String apiKey = "ollama";

        /** Ollama 模型名称。 */
        private String model = "deepseek-r1:7b";
    }
}
// AI_GENERATE_END ---
