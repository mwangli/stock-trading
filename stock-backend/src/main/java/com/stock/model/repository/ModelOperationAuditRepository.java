// AI_GENERATE_START------
package com.stock.model.repository;

import com.stock.model.entity.ModelOperationAuditDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 模型运维审计仓库。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Repository
public interface ModelOperationAuditRepository extends JpaRepository<ModelOperationAuditDocument, String> {
}
// AI_GENERATE_END------
