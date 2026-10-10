// AI_GENERATE_START -
package com.stock.modelService.persistence;

import com.stock.modelService.domain.entity.ModelActivationDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 模型激活指针仓库。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Repository
public interface ModelActivationRepository extends MongoRepository<ModelActivationDocument, String> {

    /**
     * 按模型名称查询当前激活指针。
     *
     * @param modelName 模型名称
     * @return 激活指针
     */
    Optional<ModelActivationDocument> findByModelName(String modelName);
}
// AI_GENERATE_END -
