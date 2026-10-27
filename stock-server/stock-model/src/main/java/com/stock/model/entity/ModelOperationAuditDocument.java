// AI_GENERATE_START------
package com.stock.model.entity;

import lombok.Data;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * 模型运维操作审计记录。
 * 记录训练触发、候选激活、回滚和门禁拒绝等高风险操作。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@Entity
@Table(name = "model_operation_audit", indexes = {
        @Index(name = "idx_audit_model_created", columnList = "model_name, created_at")
})
public class ModelOperationAuditDocument {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    private String modelName;

    private String operationType;

    private String operatorName;

    private String sourceVersionId;

    private String targetVersionId;

    private boolean success;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String reason;

    private LocalDateTime createdAt;
}
// AI_GENERATE_END------
