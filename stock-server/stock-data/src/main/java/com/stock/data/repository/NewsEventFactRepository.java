// AI_GENERATE_START-----
package com.stock.data.repository;

import com.stock.data.entity.StockNewsEventFact;
import com.stock.data.enums.NewsDocumentRole;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 股票新闻规范事件事实仓储。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Repository
public interface NewsEventFactRepository extends MongoRepository<StockNewsEventFact, String> {

    /**
     * 查询多个股票在点时窗口内可供模型消费的事件事实。
     *
     * @param stockCodes 股票代码集合
     * @param startTime 窗口开始时间，包含
     * @param endTime 信息截止时间，包含
     * @param roles 允许进入模型或风险门禁的业务角色
     * @return 按发布时间倒序排列的事件事实
     */
    List<StockNewsEventFact>
            findByStockCodeInAndPublishTimeBetweenAndDocumentRoleInOrderByPublishTimeDesc(
                    Collection<String> stockCodes, LocalDateTime startTime, LocalDateTime endTime,
                    Collection<NewsDocumentRole> roles);

    /**
     * 查询可用于情感训练的全部事件事实。
     *
     * @param roles 允许进入训练的业务角色
     * @return 事件事实列表
     */
    List<StockNewsEventFact> findByDocumentRoleIn(Collection<NewsDocumentRole> roles);

    /**
     * 按发布时间顺序加载指定角色的事件事实，用于可续跑影子推理。
     *
     * @param role 业务角色
     * @param startTime 开始时间，包含
     * @param endTime 结束时间，包含
     * @param pageable 分页与数量限制
     * @return 事件事实列表
     */
    List<StockNewsEventFact> findByDocumentRoleAndPublishTimeBetweenOrderByPublishTimeAscIdAsc(
            NewsDocumentRole role, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 删除指定股票的全部事件事实。
     *
     * @param stockCode 股票代码
     */
    void deleteByStockCode(String stockCode);
}
// AI_GENERATE_END-----
