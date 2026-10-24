// AI_GENERATE_START---
package com.stock.model.service;

import com.stock.model.entity.ModelOperationAuditDocument;
import com.stock.model.repository.ModelOperationAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 模型运维审计服务。
 *
 * 负责统一记录训练、快照、激活和回滚操作结果，并限制审计文本长度。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
class ModelOperationAuditService {

    private final ModelOperationAuditRepository auditRepository;

    void record(String modelName, String operationType, String operatorName,
                String sourceVersionId, String targetVersionId,
                boolean success, String reason) {
        ModelOperationAuditDocument audit = new ModelOperationAuditDocument();
        audit.setModelName(modelName);
        audit.setOperationType(operationType);
        audit.setOperatorName(normalizeOperator(operatorName));
        audit.setSourceVersionId(sourceVersionId);
        audit.setTargetVersionId(targetVersionId);
        audit.setSuccess(success);
        audit.setReason(limit(reason));
        audit.setCreatedAt(LocalDateTime.now());
        auditRepository.save(audit);
    }

    private String normalizeOperator(String operatorName) {
        if (operatorName == null || operatorName.isBlank()) {
            return "web-console";
        }
        String normalized = operatorName.trim();
        return normalized.substring(0, Math.min(64, normalized.length()));
    }

    private String limit(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.substring(0, Math.min(1000, message.length()));
    }
}
// AI_GENERATE_END---
