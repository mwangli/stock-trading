// AI_GENERATE_START----------
package com.stock.model.repository;

import com.stock.model.entity.ModelTrainingRunDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

/**
 * 模型训练运行记录仓库。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Repository
public interface ModelTrainingRunRepository extends JpaRepository<ModelTrainingRunDocument, String> {

    /**
     * 判断指定模型是否存在尚未写入结束时间的训练任务。
     *
     * @param modelName 模型名称
     * @param statuses 未结束状态集合
     * @return true 表示存在真实未结束任务
     */
    boolean existsByModelNameAndStatusInAndFinishedAtIsNull(
            String modelName, Collection<String> statuses);

    /**
     * 分页查询指定模型的训练记录。
     *
     * @param modelName 模型名称
     * @param pageable 分页参数
     * @return 训练记录分页结果
     */
    Page<ModelTrainingRunDocument> findByModelName(String modelName, Pageable pageable);

    /**
     * 查询指定模型尚未结束的训练任务。
     *
     * @param modelName 模型名称
     * @param statuses 未结束状态集合
     * @return 未结束训练任务
     */
    List<ModelTrainingRunDocument> findByModelNameAndStatusIn(String modelName, Collection<String> statuses);

    /**
     * 查询指定模型最近一次训练记录。
     *
     * @param modelName 模型名称
     * @return 最近一次训练记录，不存在时返回 null
     */
    ModelTrainingRunDocument findTopByModelNameOrderByCreatedAtDesc(String modelName);

    /**
     * 查询保留期之前的终态训练运行。
     *
     * @param statuses 训练终态集合
     * @param cutoff 保留期截止时间
     * @return 待清理训练运行
     */
    List<ModelTrainingRunDocument> findByStatusInAndCreatedAtBefore(
            Collection<String> statuses, LocalDateTime cutoff);

    /**
     * 判断训练快照是否仍被训练运行引用。
     *
     * @param datasetSnapshotId 训练快照 ID
     * @return true 表示仍存在引用
     */
    boolean existsByDatasetSnapshotId(String datasetSnapshotId);
}
// AI_GENERATE_END----------
