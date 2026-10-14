// AI_GENERATE_START -
package com.stock.modelService.persistence;

import com.stock.modelService.domain.entity.SentimentEvaluationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 情感模型独立评估结果仓库。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Repository
public interface SentimentEvaluationRepository extends JpaRepository<SentimentEvaluationEntity, Long> {

    /**
     * 查询最近一次评估结果。
     *
     * @return 最近评估结果
     */
    Optional<SentimentEvaluationEntity> findTopByOrderByEvaluatedAtDesc();
}
// AI_GENERATE_END -
