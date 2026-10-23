// AI_GENERATE_START-------
package com.stock.model.repository;

import com.stock.model.entity.LstmModelDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * LSTM 模型元数据 MySQL 仓库
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Repository
public interface LstmModelRepository extends JpaRepository<LstmModelDocument, String> {

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
// AI_GENERATE_END-------
