// AI_GENERATE_START ----
package com.stock.strategyAgent.api;

import com.stock.strategyAgent.domain.AgentEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AGENT 模块接口 DTO 集合。
 *
 * @author mwangli
 * @since 2026-10-17
 */
public final class AgentDtos {

    private AgentDtos() {
    }

    /** 创建会话请求。 */
    @Data
    @NoArgsConstructor
    public static class CreateSessionRequest {
        /** 用户范围标识。 */
        private String userScope;
        /** 会话标题。 */
        private String title;
    }

    /** 会话响应。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionResponse {
        /** 会话标识。 */
        private String sessionId;
        /** 用户范围。 */
        private String userScope;
        /** 会话标题。 */
        private String title;
        /** 会话状态。 */
        private String status;
        /** 创建时间。 */
        private LocalDateTime createdAt;
        /** 更新时间。 */
        private LocalDateTime updatedAt;
    }

    /** Research 任务请求。 */
    @Data
    @NoArgsConstructor
    public static class ResearchRequest {
        /** 会话标识，可为空。 */
        private String sessionId;
        /** 用户问题。 */
        private String question;
        /** 用户范围。 */
        private String userScope;
    }

    /** Diagnostic 任务请求。 */
    @Data
    @NoArgsConstructor
    public static class DiagnosticRequest {
        /** 会话标识，可为空。 */
        private String sessionId;
        /** 需要诊断的任务名称，可为空。 */
        private String jobName;
        /** 诊断说明。 */
        private String question;
        /** 用户范围。 */
        private String userScope;
    }

    /** 日报生成请求。 */
    @Data
    @NoArgsConstructor
    public static class ReportRequest {
        /** 会话标识，可为空。 */
        private String sessionId;
        /** 报告日期，格式为 yyyy-MM-dd。 */
        private String reportDate;
        /** 用户范围。 */
        private String userScope;
    }

    /** 任务响应。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskResponse {
        /** 任务标识。 */
        private String taskId;
        /** 会话标识。 */
        private String sessionId;
        /** 任务类型。 */
        private String taskType;
        /** 任务状态。 */
        private String status;
        /** 原始问题。 */
        private String question;
        /** 任务结果摘要。 */
        private String resultSummary;
        /** 结构化结果类型。 */
        private String resultType;
        /** 脱敏后的结构化结果 JSON。 */
        private String structuredResult;
        /** 证据摘要。 */
        private String evidenceSummary;
        /** 证据编号列表。 */
        private String evidenceRefs;
        /** 不确定性摘要。 */
        private String uncertainty;
        /** 错误摘要。 */
        private String errorMessage;
        /** 创建时间。 */
        private LocalDateTime createdAt;
        /** 更新时间。 */
        private LocalDateTime updatedAt;
    }

    /** Memory 创建请求。 */
    @Data
    @NoArgsConstructor
    public static class CreateMemoryRequest {
        /** 用户范围。 */
        private String userScope;
        /** 记忆类型。 */
        private AgentEnums.MemoryType memoryType;
        /** 来源类型。 */
        private AgentEnums.MemorySourceType sourceType;
        /** 记忆内容。 */
        private String content;
        /** 来源引用。 */
        private String sourceRef;
        /** 置信度。 */
        private Double confidence;
        /** 有效天数。 */
        private Integer ttlDays;
    }

    /** Memory 纠错请求。 */
    @Data
    @NoArgsConstructor
    public static class CorrectMemoryRequest {
        /** 修正后的记忆内容。 */
        private String content;
        /** 修正来源。 */
        private String sourceRef;
    }

    /** Memory 响应。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryResponse {
        /** 记忆标识。 */
        private String memoryId;
        /** 用户范围。 */
        private String userScope;
        /** 记忆类型。 */
        private String memoryType;
        /** 来源类型。 */
        private String sourceType;
        /** 记忆内容。 */
        private String content;
        /** 来源引用。 */
        private String sourceRef;
        /** 置信度。 */
        private Double confidence;
        /** 有效期。 */
        private LocalDateTime expiresAt;
        /** 记忆状态。 */
        private String status;
        /** 更新时间。 */
        private LocalDateTime updatedAt;
    }

    /** RAG 文档写入请求。 */
    @Data
    @NoArgsConstructor
    public static class RagIngestRequest {
        /** 文档所属用户范围，PRIVATE 文档只允许同范围检索。 */
        private String userScope;
        /** 来源类型。 */
        private String sourceType;
        /** 来源标识。 */
        private String sourceId;
        /** 来源版本。 */
        private String version;
        /** 文档内容。 */
        private String content;
        /** 股票代码。 */
        private List<String> stockCodes;
        /** 文档标签。 */
        private List<String> tags;
        /** 可见范围。 */
        private String visibility;
    }

    /** RAG 检索请求。 */
    @Data
    @NoArgsConstructor
    public static class RagSearchRequest {
        /** 检索问题。 */
        private String query;
        /** 用户范围。 */
        private String userScope;
        /** 最大结果数。 */
        private Integer limit;
        /** 来源类型，可选。 */
        private String sourceType;
        /** 来源版本，可选。 */
        private String version;
        /** 文档最早时间，可选。 */
        private LocalDateTime asOfFrom;
        /** 文档最晚时间，可选。 */
        private LocalDateTime asOfTo;
    }

    /** RAG 检索结果。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagResult {
        /** 文档分块标识。 */
        private String chunkId;
        /** 来源类型。 */
        private String sourceType;
        /** 来源标识。 */
        private String sourceId;
        /** 来源版本。 */
        private String version;
        /** 内容摘要。 */
        private String content;
        /** 相似度分数。 */
        private Double score;
        /** 内容时间。 */
        private LocalDateTime asOf;
    }

    /** Agent 运行状态。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuntimeStatus {
        /** Agent 是否启用。 */
        private boolean enabled;
        /** 当前模型提供方。 */
        private String provider;
        /** RAG 是否启用。 */
        private boolean ragEnabled;
        /** Memory 是否启用。 */
        private boolean memoryEnabled;
        /** 当前运行任务数。 */
        private long runningTasks;
        /** 当前时间。 */
        private LocalDateTime checkedAt;
    }
}
// AI_GENERATE_END ----
