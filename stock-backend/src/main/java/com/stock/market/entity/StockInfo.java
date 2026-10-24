// AI_GENERATE_START-----------
package com.stock.market.entity;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;

import com.stock.market.enums.NewsSyncStatus;
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
@Document(collection = "stock_info")
public class StockInfo {

    @Id
    private String id;

    @Indexed(unique = true, name = "uk_stock_info_code")
    private String code;

    private String name;

    private String market;

    private BigDecimal price;

    private BigDecimal changeAmount;

    private BigDecimal changePercent;

    /** 当日成交量，数据源原始单位。 */
    private BigDecimal volume;

    private BigDecimal totalMarketValue;

    private BigDecimal turnoverRate;

    private BigDecimal volumeRatio;

    /** 当前买一价。 */
    private BigDecimal bidPrice;

    /** 当前卖一价。 */
    private BigDecimal askPrice;

    /** 数据源证券类别编码，用于区分主板、创业板等交易规则。 */
    private String securityCategory;

    /** 数据源市场代码，如 SH、SZ、BJ。 */
    private String marketCode;

    /** 涨跌幅、涨跌额、量比、换手率组合得到的动量强度。 */
    private BigDecimal momentumStrength;

    /** 量价组合信号，正值代表价格与成交量方向一致。 */
    private BigDecimal volumePriceSignal;

    /** 换手率异常分数，按当前量比和换手率共同计算。 */
    private BigDecimal turnoverAnomaly;

    /** 是否存在放量下跌或缩量上涨等异常量价关系。 */
    private boolean abnormalVolumeFlag;

    /** 买一卖一价差。 */
    private BigDecimal bidAskSpread;

    /** 买卖价差相对最新价的比例。 */
    private BigDecimal bidAskSpreadRate;

    private Integer industryCode;

    /**
     * 股票首次在交易所公开上市的日期。
     */
    private LocalDate listedDate;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;

    /**
     * 最近一次开始新闻同步的时间。
     */
    private LocalDateTime newsLastAttemptAt;

    /**
     * 新闻和公告两个来源最近一次全部同步成功的时间。
     */
    private LocalDateTime newsLastSuccessAt;

    /**
     * MongoDB 中该股票已知新闻和公告的最大发布时间。
     */
    private LocalDateTime newsLastPublishTime;

    /**
     * 最近一次新闻同步状态。
     */
    private NewsSyncStatus newsSyncStatus = NewsSyncStatus.NEVER_SYNCED;

    /**
     * 最近一次新闻同步批次 ID。
     */
    private String newsSyncBatchId;

    /**
     * 最近一次失败或部分失败的标准错误码。
     */
    private String newsSyncErrorCode;

    /**
     * 连续同步成功但无新增数据的次数。
     */
    private Integer newsConsecutiveNoChangeCount = 0;

    /**
     * 下次允许执行增量同步的时间，当前阶段仅记录，不用于跳过交易日全量检查。
     */
    private LocalDateTime newsNextSyncAt;
}
// AI_GENERATE_END-----------
