// AI_GENERATE_START -
package com.stock.dataCollector.domain.entity;

import com.stock.dataCollector.domain.enums.NewsSyncStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 股票基本信息实体，用于存储股票基础数据及新闻同步水位。
 *
 * @author mwangli
 * @since 2026-10-13
 */
@Data
@Entity
@Table(name = "stock_info", indexes = {
    @Index(name = "idx_stock_code", columnList = "code", unique = true),
    @Index(name = "idx_stock_name", columnList = "name"),
    @Index(name = "idx_market", columnList = "market")
})
public class StockInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 10, nullable = false, unique = true)
    private String code;

    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "market", length = 10)
    private String market;

    @Column(name = "price", precision = 10, scale = 3)
    private BigDecimal price;

    @Column(name = "change_amount", precision = 10, scale = 3)
    private BigDecimal changeAmount;

    @Column(name = "change_percent", precision = 10, scale = 4)
    private BigDecimal changePercent;

    @Column(name = "total_market_value", precision = 20, scale = 2)
    private BigDecimal totalMarketValue;

    @Column(name = "turnover_rate", precision = 10, scale = 4)
    private BigDecimal turnoverRate;

    @Column(name = "volume_ratio", precision = 10, scale = 4)
    private BigDecimal volumeRatio;

    @Column(name = "industry_code")
    private Integer industryCode;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 最近一次开始新闻同步的时间。
     */
    @Column(name = "news_last_attempt_at")
    private LocalDateTime newsLastAttemptAt;

    /**
     * 新闻和公告两个来源最近一次全部同步成功的时间。
     */
    @Column(name = "news_last_success_at")
    private LocalDateTime newsLastSuccessAt;

    /**
     * MongoDB 中该股票已知新闻和公告的最大发布时间。
     */
    @Column(name = "news_last_publish_time")
    private LocalDateTime newsLastPublishTime;

    /**
     * 最近一次新闻同步状态。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "news_sync_status", nullable = false, length = 32)
    private NewsSyncStatus newsSyncStatus = NewsSyncStatus.NEVER_SYNCED;

    /**
     * 最近一次新闻同步批次 ID。
     */
    @Column(name = "news_sync_batch_id", length = 64)
    private String newsSyncBatchId;

    /**
     * 最近一次失败或部分失败的标准错误码。
     */
    @Column(name = "news_sync_error_code", length = 128)
    private String newsSyncErrorCode;

    /**
     * 连续同步成功但无新增数据的次数。
     */
    @Column(name = "news_consecutive_no_change_count", nullable = false)
    private Integer newsConsecutiveNoChangeCount = 0;

    /**
     * 下次允许执行增量同步的时间，当前阶段仅记录，不用于跳过交易日全量检查。
     */
    @Column(name = "news_next_sync_at")
    private LocalDateTime newsNextSyncAt;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
// AI_GENERATE_END -
