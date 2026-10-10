// AI_GENERATE_START ---
package com.stock.modelService.persistence;

import com.stock.modelService.domain.entity.LstmModelDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * LSTM 模型在 MongoDB 中的存储仓库
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Repository
public interface LstmModelRepository extends MongoRepository<LstmModelDocument, String> {

    /**
     * 获取最新保存的模型（全局）
     */
    LstmModelDocument findTopByOrderByCreatedAtDesc();

    boolean existsByModelName(String modelName);

    /**
     * 按股票代码（模型名称）查询最近一次保存的 LSTM 模型
     */
    LstmModelDocument findTopByModelNameOrderByCreatedAtDesc(String modelName);

    /**
     * 分页查询指定模型的历史版本。
     *
     * @param modelName 模型名称
     * @param pageable 分页参数
     * @return 模型版本分页结果
     */
    Page<LstmModelDocument> findByModelName(String modelName, Pageable pageable);

}
// AI_GENERATE_END ---
