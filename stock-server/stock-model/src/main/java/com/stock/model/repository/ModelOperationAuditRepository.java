// AI_GENERATE_START-------
package com.stock.model.repository;

import com.stock.model.entity.ModelOperationAuditDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型运维审计仓库。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Repository
public interface ModelOperationAuditRepository extends JpaRepository<ModelOperationAuditDocument, String> {

    /**
     * 查询保留期之前的模型操作审计记录。
     *
     * @param cutoff 保留期截止时间
     * @return 待清理审计记录
     */
    List<ModelOperationAuditDocument> findByCreatedAtBefore(LocalDateTime cutoff);
}
// AI_GENERATE_END-------
