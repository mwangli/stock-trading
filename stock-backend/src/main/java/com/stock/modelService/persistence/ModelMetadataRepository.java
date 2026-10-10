// AI_GENERATE_START -
package com.stock.modelService.persistence;

import com.stock.modelService.domain.entity.ModelMetadataDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 外部模型元数据仓库。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Repository
public interface ModelMetadataRepository extends MongoRepository<ModelMetadataDocument, String> {

    /**
     * 按模型名称查询元数据。
     *
     * @param modelName 模型名称
     * @return 模型元数据
     */
    Optional<ModelMetadataDocument> findByModelName(String modelName);
}
// AI_GENERATE_END -
