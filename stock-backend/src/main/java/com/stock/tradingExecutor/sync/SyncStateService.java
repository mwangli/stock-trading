// AI_GENERATE_START -
package com.stock.tradingExecutor.sync;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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

    /** 推进数据集成功水位。 */
    @Transactional
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
    @Transactional
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

    /** 关闭指定数据集范围的未解决缺口。 */
    @Transactional
    public void resolveGaps(String dataset, String scopeKey) {
        stateRepository.findByDatasetAndScopeKey(dataset, scopeKey).ifPresent(state -> {
            if (OPEN.equals(state.getGapStatus())) {
                state.setGapStatus(RESOLVED)
                        .setGapStartDate(null)
                        .setGapEndDate(null)
                        .setLastError(null);
                stateRepository.save(state);
            }
        });
    }
}
// AI_GENERATE_END -
