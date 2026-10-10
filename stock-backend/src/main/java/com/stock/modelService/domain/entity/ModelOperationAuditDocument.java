// AI_GENERATE_START -
package com.stock.modelService.domain.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 模型运维操作审计记录。
 * 记录训练触发、候选激活、回滚和门禁拒绝等高风险操作。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@Document(collection = "model_operation_audits")
@CompoundIndex(name = "idx_audit_model_created", def = "{'modelName': 1, 'createdAt': -1}")
public class ModelOperationAuditDocument {

    @Id
    private String id;

    private String modelName;

    private String operationType;

    private String operatorName;

    private String sourceVersionId;

    private String targetVersionId;

    private boolean success;

    private String reason;

    private LocalDateTime createdAt;
}
// AI_GENERATE_END -
