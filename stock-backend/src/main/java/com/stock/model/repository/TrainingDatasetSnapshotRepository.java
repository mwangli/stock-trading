// AI_GENERATE_START-------
package com.stock.model.repository;

import com.stock.model.entity.TrainingDatasetSnapshotDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * LSTM 训练数据快照元数据仓库。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Repository
public interface TrainingDatasetSnapshotRepository
        extends JpaRepository<TrainingDatasetSnapshotDocument, String> {

    /**
     * 查询指定契约下最新的可复用快照。
     *
     * @param modelName 模型名称
     * @param contractHash 数据契约摘要
     * @return 最新可用快照
     */
    Optional<TrainingDatasetSnapshotDocument> findFirstByModelNameAndContractHashAndStatusOrderByCreatedAtDesc(
            String modelName, String contractHash, String status);

    /**
     * 查询指定模型的快照历史。
     *
     * @param modelName 模型名称
     * @param status 快照状态
     * @return 快照列表
     */
    List<TrainingDatasetSnapshotDocument> findByModelNameAndStatusOrderByCreatedAtDesc(
            String modelName, String status);

    /**
     * 查询指定状态的全部快照，用于启动阶段清理中断构建。
     *
     * @param status 快照状态
     * @return 指定状态的快照列表
     */
    List<TrainingDatasetSnapshotDocument> findByStatus(String status);
}
// AI_GENERATE_END-------
