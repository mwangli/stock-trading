// AI_GENERATE_START------------------------
package com.stock.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

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

    /** 是否在 Agent 启动且 Embedding 就绪后自动初始化业务知识。 */
    private boolean ragAutoInitializationEnabled = true;

    /** 项目工程文档检索配置。 */
    private ProjectDocumentsProperties projectDocuments = new ProjectDocumentsProperties();

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

    /** 公开网页搜索配置。 */
    private WebSearchProperties webSearch = new WebSearchProperties();

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
        private String model = "deepseek-flash";

        /** DeepSeek 单次最大输出 token 数。 */
        private int maxTokens = 1024;

        /** 是否启用DeepSeek思考模式；短输出场景默认关闭，避免推理Token挤占最终答案。 */
        private boolean thinkingEnabled = false;

        /** DeepSeek 每日费用预算配置。 */
        private DailyBudgetProperties dailyBudget = new DailyBudgetProperties();
    }

    /**
     * DeepSeek 每日费用预算配置。
     *
     * @author mwangli
     * @since 2026-10-22
     */
    @Data
    public static class DailyBudgetProperties {

        /** 是否启用每日费用预算门禁。 */
        private boolean enabled = true;

        /** 每日费用上限，单位人民币元。 */
        private BigDecimal dailyLimitCny = new BigDecimal("1.00");

        /** 输入 token 峰值单价，单位人民币元/百万 token。 */
        private BigDecimal inputPriceCnyPerMillion = new BigDecimal("2.10");

        /** 输出 token 峰值单价，单位人民币元/百万 token。 */
        private BigDecimal outputPriceCnyPerMillion = new BigDecimal("8.40");

        /** 费用估算安全系数，用于覆盖汇率和计费口径误差。 */
        private BigDecimal safetyFactor = new BigDecimal("1.10");

        /** 每次请求额外预留的协议 token 数。 */
        private int reservationOverheadTokens = 1024;
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

        /** 已发布 Embedding 模型制品的本地根目录。 */
        private String modelPath = "./model-files/embedding";

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

    /**
     * 公开网页搜索配置。
     *
     * @author mwangli
     * @since 2026-10-25
     */
    @Data
    public static class WebSearchProperties {

        /** 是否启用网页搜索。 */
        private boolean enabled = true;

        /** DeepSeek 网页搜索接口地址。 */
        private String endpoint = "https://api.deepseek.com/v1/tools/search";

        /** 单次搜索超时时间，单位毫秒。 */
        private long timeoutMs = 10000L;

        /** 单次最多返回的搜索结果数量，最大值为 5。 */
        private int maxResults = 5;

        /** 搜索问题最大字符数。 */
        private int maxQueryChars = 300;

        /** 原始响应最大字符数。 */
        private int maxResponseChars = 100000;
    }

    /**
     * 项目工程文档文件系统配置。
     *
     * @author mwangli
     * @since 2026-10-25
     */
    @Data
    public static class ProjectDocumentsProperties {

        /** 是否启用项目工程文档检索。 */
        private boolean enabled = true;

        /** 项目工程文档根目录，支持绝对路径和相对当前工作目录的路径。 */
        private String path = "./documents";
    }
}
// AI_GENERATE_END-----------------------