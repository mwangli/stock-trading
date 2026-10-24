// AI_GENERATE_START----
package com.stock.agent.tooling.evidence;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Agent 任务使用的脱敏事实证据。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Value
@Builder
public class EvidenceItem {

    /** 应用服务生成的证据编号。 */
    String evidenceId;

    /** 证据来源类型。 */
    String sourceType;

    /** 证据来源标识。 */
    String sourceId;

    /** 证据来源版本。 */
    String sourceVersion;

    /** 事实采集时间。 */
    LocalDateTime asOf;

    /** 证据状态。 */
    String status;

    /** 脱敏事实摘要。 */
    String summary;

    /** 失败或未知时的错误码。 */
    String errorCode;
}
// AI_GENERATE_END---
