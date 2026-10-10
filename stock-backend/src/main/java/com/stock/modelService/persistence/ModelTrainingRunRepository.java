// AI_GENERATE_START -
package com.stock.modelService.persistence;

import com.stock.modelService.domain.entity.ModelTrainingRunDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

/**
 * 模型训练运行记录仓库。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Repository
public interface ModelTrainingRunRepository extends MongoRepository<ModelTrainingRunDocument, String> {

    /**
     * 判断指定模型是否存在运行中或排队中的训练任务。
     *
     * @param modelName 模型名称
     * @param statuses 运行状态集合
     * @return true 表示存在未结束任务
     */
    boolean existsByModelNameAndStatusIn(String modelName, Collection<String> statuses);

    /**
     * 分页查询指定模型的训练记录。
     *
     * @param modelName 模型名称
     * @param pageable 分页参数
     * @return 训练记录分页结果
     */
    Page<ModelTrainingRunDocument> findByModelName(String modelName, Pageable pageable);

    /**
     * 查询指定模型最近一次训练记录。
     *
     * @param modelName 模型名称
     * @return 最近一次训练记录，不存在时返回 null
     */
    ModelTrainingRunDocument findTopByModelNameOrderByCreatedAtDesc(String modelName);
}
// AI_GENERATE_END -
