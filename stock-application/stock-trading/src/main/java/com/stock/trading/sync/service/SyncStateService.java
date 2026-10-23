// AI_GENERATE_START--------
package com.stock.trading.sync.service;

import com.stock.trading.sync.entity.SyncStateEntity;
import com.stock.trading.sync.repository.SyncStateRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 保存同步水位和缺口，供不同数据集共用。
 *
 * @author mwangli
 * @since 2026-10-16
 */
@Service
@RequiredArgsConstructor
public class SyncStateService {

    private static final String OPEN = "OPEN";
    private static final String RESOLVED = "RESOLVED";

    private final SyncStateRepository stateRepository;

    /**
     * 查询指定数据集范围的同步状态快照。
     *
     * 调度任务使用不可变快照规划缺口请求，避免把持久化文档暴露到并发业务线程。
     *
     * @param dataset 数据集名称
     * @param scopeKey 业务范围，例如股票代码
     * @return 同步状态快照，不存在时返回空
     */
    public Optional<SyncStateSnapshot> findState(String dataset, String scopeKey) {
        return stateRepository.findByDatasetAndScopeKey(dataset, scopeKey)
                .map(state -> new SyncStateSnapshot(
                        state.getGapStartDate(),
                        state.getGapEndDate(),
                        OPEN.equals(state.getGapStatus())));
    }

    /** 推进数据集成功水位。 */
    public void markSuccess(String dataset, String scopeKey, LocalDate date) {
        SyncStateEntity state = stateRepository
                .findByDatasetAndScopeKey(dataset, scopeKey)
                .orElseGet(() -> new SyncStateEntity()
                        .setDataset(dataset)
                        .setScopeKey(scopeKey));
        state.setLastSuccessDate(date)
                .setGapStatus(RESOLVED)
                .setGapStartDate(null)
                .setGapEndDate(null)
                .setLastError(null);
        stateRepository.save(state);
    }

    /** 创建或更新一个待回补缺口。 */
    public void recordGap(String dataset, String scopeKey,
                          LocalDate startDate, LocalDate endDate, String error) {
        SyncStateEntity state = stateRepository
                .findByDatasetAndScopeKey(dataset, scopeKey)
                .orElseGet(() -> new SyncStateEntity()
                        .setDataset(dataset)
                        .setScopeKey(scopeKey)
                        .setGapStartDate(startDate)
                        .setGapEndDate(endDate));
        LocalDate currentStart = state.getGapStartDate();
        LocalDate currentEnd = state.getGapEndDate();
        state.setGapStartDate(currentStart == null || startDate.isBefore(currentStart)
                        ? startDate : currentStart)
                .setGapEndDate(currentEnd == null || endDate.isAfter(currentEnd)
                        ? endDate : currentEnd)
                .setGapStatus(OPEN)
                .setRetryCount((state.getRetryCount() == null ? 0 : state.getRetryCount()) + 1)
                .setLastError(error);
        stateRepository.save(state);
    }

    /**
     * 同步状态只读快照。
     *
     * @param gapStartDate 待回补缺口开始日期
     * @param gapEndDate 待回补缺口结束日期
     * @param openGap 是否存在待回补缺口
     */
    public record SyncStateSnapshot(LocalDate gapStartDate,
                                    LocalDate gapEndDate,
                                    boolean openGap) {
    }
}
// AI_GENERATE_END--------
