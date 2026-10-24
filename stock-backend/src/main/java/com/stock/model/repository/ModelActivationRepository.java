// AI_GENERATE_START------
package com.stock.model.repository;

import com.stock.model.entity.ModelActivationDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 模型激活指针仓库。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Repository
public interface ModelActivationRepository extends JpaRepository<ModelActivationDocument, String> {

    /**
     * 按模型名称查询当前激活指针。
     *
     * @param modelName 模型名称
     * @return 激活指针
     */
    Optional<ModelActivationDocument> findByModelName(String modelName);
}
// AI_GENERATE_END------
