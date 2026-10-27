// AI_GENERATE_START------
package com.stock.model.repository;

import com.stock.model.entity.ModelMetadataDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 外部模型元数据仓库。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Repository
public interface ModelMetadataRepository extends JpaRepository<ModelMetadataDocument, String> {

    /**
     * 按模型名称查询元数据。
     *
     * @param modelName 模型名称
     * @return 模型元数据
     */
    Optional<ModelMetadataDocument> findByModelName(String modelName);
}
// AI_GENERATE_END------
