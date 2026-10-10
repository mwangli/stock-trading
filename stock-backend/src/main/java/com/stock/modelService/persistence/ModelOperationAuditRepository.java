// AI_GENERATE_START -
package com.stock.modelService.persistence;

import com.stock.modelService.domain.entity.ModelOperationAuditDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 模型运维审计仓库。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Repository
public interface ModelOperationAuditRepository extends MongoRepository<ModelOperationAuditDocument, String> {
}
// AI_GENERATE_END -
