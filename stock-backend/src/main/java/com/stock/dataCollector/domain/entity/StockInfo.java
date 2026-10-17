// AI_GENERATE_START ----
package com.stock.dataCollector.domain.entity;

import com.stock.dataCollector.domain.enums.NewsSyncStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** 当日成交量，数据源原始单位。 */
    @Column(name = "volume", precision = 24, scale = 4)
    private BigDecimal volume;

    @Column(name = "total_market_value", precision = 20, scale = 2)
    private BigDecimal totalMarketValue;

    @Column(name = "turnover_rate", precision = 10, scale = 4)
    private BigDecimal turnoverRate;

    @Column(name = "volume_ratio", precision = 10, scale = 4)
    private BigDecimal volumeRatio;

    /** 当前买一价。 */
    @Column(name = "bid_price", precision = 10, scale = 3)
    private BigDecimal bidPrice;

    /** 当前卖一价。 */
    @Column(name = "ask_price", precision = 10, scale = 3)
    private BigDecimal askPrice;

    /** 数据源证券类别编码，用于区分主板、创业板等交易规则。 */
    @Column(name = "security_category", length = 32)
    private String securityCategory;

    /** 数据源市场代码，如 SH、SZ、BJ。 */
    @Column(name = "market_code", length = 10)
    private String marketCode;

    /** 涨跌幅、涨跌额、量比、换手率组合得到的动量强度。 */
    @Column(name = "momentum_strength", precision = 12, scale = 6)
    private BigDecimal momentumStrength;

    /** 量价组合信号，正值代表价格与成交量方向一致。 */
    @Column(name = "volume_price_signal", precision = 12, scale = 6)
    private BigDecimal volumePriceSignal;

    /** 换手率异常分数，按当前量比和换手率共同计算。 */
    @Column(name = "turnover_anomaly", precision = 12, scale = 6)
    private BigDecimal turnoverAnomaly;

    /** 是否存在放量下跌或缩量上涨等异常量价关系。 */
    @Column(name = "abnormal_volume_flag", nullable = false)
    private boolean abnormalVolumeFlag;

    /** 买一卖一价差。 */
    @Column(name = "bid_ask_spread", precision = 10, scale = 3)
    private BigDecimal bidAskSpread;

    /** 买卖价差相对最新价的比例。 */
    @Column(name = "bid_ask_spread_rate", precision = 12, scale = 8)
    private BigDecimal bidAskSpreadRate;

    @Column(name = "industry_code")
    private Integer industryCode;

    /**
     * 股票首次在交易所公开上市的日期。
     */
    @Column(name = "listed_date")
    private LocalDate listedDate;

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
// AI_GENERATE_END ----
