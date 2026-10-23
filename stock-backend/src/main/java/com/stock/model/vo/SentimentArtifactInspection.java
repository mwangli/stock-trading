// AI_GENERATE_START---
package com.stock.model.vo;

import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;

/**
 * 情感模型制品检查结果。
 * 用于在加载和健康检查之间传递已验证的目录、摘要和评估信息。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Value
@Builder
public class SentimentArtifactInspection {

    /** 制品是否满足当前检查要求。 */
    boolean available;

    /** manifest 是否存在并通过结构检查。 */
    boolean manifestPresent;

    /** 是否已完成全部文件 SHA-256 校验。 */
    boolean checksumsVerified;

    /** 实际使用的模型版本目录。 */
    Path modelDirectory;

    /** 实际模型版本。 */
    String modelVersion;

    /** 清单内文件总字节数。 */
    long totalSizeBytes;

    /** manifest 文件 SHA-256。 */
    String manifestSha256;

    /** 离线评估摘要。 */
    SentimentArtifactManifest.EvaluationMetrics metrics;

    /** 制品不可用时的原因。 */
    String errorMessage;
}
// AI_GENERATE_END---
