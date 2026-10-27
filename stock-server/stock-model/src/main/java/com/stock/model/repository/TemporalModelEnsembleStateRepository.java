// AI_GENERATE_START --
package com.stock.model.repository;

import com.stock.model.entity.TemporalModelEnsembleStateDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 时序模型融合状态仓储。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Repository
public interface TemporalModelEnsembleStateRepository
        extends MongoRepository<TemporalModelEnsembleStateDocument, String> {
}
// AI_GENERATE_END --
