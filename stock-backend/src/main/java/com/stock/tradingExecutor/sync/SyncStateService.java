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

    private final SyncCheckpointRepository checkpointRepository;
    private final SyncGapRepository gapRepository;

    /** 推进数据集成功水位。 */
    @Transactional
    public void markSuccess(String dataset, String scopeKey, LocalDate date) {
        SyncCheckpointEntity checkpoint = checkpointRepository
                .findByDatasetAndScopeKey(dataset, scopeKey)
                .orElseGet(() -> new SyncCheckpointEntity()
                        .setDataset(dataset)
                        .setScopeKey(scopeKey));
        checkpoint.setLastSuccessDate(date);
        checkpointRepository.save(checkpoint);
    }

    /** 创建或更新一个待回补缺口。 */
    @Transactional
    public void recordGap(String dataset, String scopeKey,
                          LocalDate startDate, LocalDate endDate, String error) {
        SyncGapEntity gap = gapRepository
                .findByDatasetAndScopeKeyAndStatusOrderByWindowStartAsc(dataset, scopeKey, OPEN)
                .stream()
                .findFirst()
                .orElseGet(() -> new SyncGapEntity()
                        .setDataset(dataset)
                        .setScopeKey(scopeKey)
                        .setWindowStart(startDate)
                        .setWindowEnd(endDate)
                        .setStatus(OPEN));
        gap.setWindowStart(gap.getWindowStart().isBefore(startDate) ? gap.getWindowStart() : startDate)
                .setWindowEnd(gap.getWindowEnd().isAfter(endDate) ? gap.getWindowEnd() : endDate)
                .setRetryCount(gap.getRetryCount() + 1)
                .setLastError(error);
        gapRepository.save(gap);
    }

    /** 关闭指定数据集范围的未解决缺口。 */
    @Transactional
    public void resolveGaps(String dataset, String scopeKey) {
        gapRepository.findByDatasetAndScopeKeyAndStatusOrderByWindowStartAsc(dataset, scopeKey, OPEN)
                .forEach(gap -> gap.setStatus(RESOLVED));
        gapRepository.flush();
    }
}
// AI_GENERATE_END -
