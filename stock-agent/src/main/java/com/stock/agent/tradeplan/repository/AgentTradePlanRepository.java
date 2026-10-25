// AI_GENERATE_START--------------
package com.stock.agent.tradeplan.repository;

import com.stock.agent.tradeplan.entity.AgentTradePlanEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;


/**
 * Agent 交易计划仓储。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Repository
public interface AgentTradePlanRepository extends MongoRepository<AgentTradePlanEntity, String> {

    /**
     * 按幂等键查询交易计划。
     *
     * @param idempotencyKey 幂等键
     * @param userScope 服务端认证用户范围
     * @return 交易计划
     */
    Optional<AgentTradePlanEntity> findByIdempotencyKeyAndUserScope(String idempotencyKey, String userScope);

    /**
     * 按计划标识和认证用户范围查询交易计划。
     *
     * @param planId 计划标识
     * @param userScope 服务端认证用户范围
     * @return 用户本人交易计划
     */
    Optional<AgentTradePlanEntity> findByPlanIdAndUserScope(String planId, String userScope);


    /**
     * 查询引用指定预测批次的交易计划。
     *
     * @param predictionBatchId 预测批次 ID
     * @return 关联交易计划
     */
    List<AgentTradePlanEntity> findByPredictionBatchId(String predictionBatchId);

    /**
     * 删除引用指定预测批次的交易计划。
     *
     * @param predictionBatchId 预测批次 ID
     * @return 删除的交易计划数量
     */
    long deleteByPredictionBatchId(String predictionBatchId);

    /**
     * 查询最近一百条包含用户复核意见的交易计划。
     *
     * @return 按更新时间倒序排列的交易计划
     */
    List<AgentTradePlanEntity> findTop100ByReviewCommentIsNotNullOrderByUpdatedAtDesc();

}
// AI_GENERATE_END-------------
