// AI_GENERATE_START -------
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

    /** 是否启用 Provider 熔断。 */
    private boolean circuitBreakerEnabled = true;

    /** 连续失败多少次后打开 Provider 熔断。 */
    private int circuitBreakerFailureThreshold = 3;

    /** Provider 熔断打开持续时间，单位毫秒。 */
    private long circuitBreakerOpenDurationMs = 60000L;

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

    /** Embedding 独立运行配置。 */
    private EmbeddingProperties embedding = new EmbeddingProperties();

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

        /** 是否返回模型思考过程；本地 Agent 默认关闭以降低响应延迟。 */
        private boolean think = false;

        /** Ollama 单次最大输出 token 数。 */
        private int maxTokens = 256;
    }

    /**
     * Embedding 模型连接配置，与 ChatModel Provider 完全隔离。
     *
     * @author mwangli
     * @since 2026-10-18
     */
    @Data
    public static class EmbeddingProperties {

        /** 是否启用 Embedding。 */
        private boolean enabled = false;

        /** Embedding Provider，支持 disabled、ollama、openaiCompatible 和 djl。 */
        private String provider = "disabled";

        /** Embedding 服务地址。 */
        private String baseUrl = "http://localhost:11434";

        /** Embedding 服务 Key。 */
        private String apiKey = "ollama";

        /** Embedding 模型名称。 */
        private String model = "bge-base-zh-v1.5";

        /** 模型托管平台仓库名称。 */
        private String pretrainedModel = "BAAI/bge-base-zh-v1.5";

        /** 固定的本地模型根目录，目录由初始化流程维护。 */
        private String modelPath = "data/models/embedding";

        /** 模型托管平台固定 revision。 */
        private String pretrainedRevision = "main";

        /** 是否允许首次启动从模型托管平台下载并初始化本地目录。 */
        private boolean downloadPretrained = true;

        /** Embedding 模型版本。 */
        private String modelVersion = "bge-base-zh-v1.5";

        /** 预期向量维度。 */
        private int dimension = 768;

        /** Embedding 请求超时时间，单位毫秒。 */
        private long timeoutMs = 30000L;

        /** 本地 tokenizer 最大序列长度。 */
        private int maxSequenceLength = 512;

        /** 本地 Transformer pooling 方式，BGE 默认使用 CLS 向量。 */
        private String poolingMode = "cls";

        /** 是否对本地模型输出执行 L2 归一化。 */
        private boolean normalize = true;

        /** 是否允许使用 hash 降级向量。生产环境默认关闭。 */
        private boolean allowHashFallback = false;
    }
}
// AI_GENERATE_END -------
