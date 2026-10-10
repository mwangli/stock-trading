// AI_GENERATE_START --
package com.stock.dataCollector.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 历史时点股票池实体。
 * 保存股票上市、退市、ST、板块和行业状态的有效期，避免回测使用当前股票池产生幸存者偏差。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "historical_stock_universe")
public class HistoricalStockUniverseEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 股票代码。 */
    @Column(name = "stock_code", nullable = false, length = 16)
    private String stockCode;

    /** 股票名称。 */
    @Column(name = "stock_name", length = 64)
    private String stockName;

    /** 交易市场。 */
    @Column(nullable = false, length = 16)
    private String market;

    /** 历史板块代码。 */
    @Column(name = "board_code", length = 32)
    private String boardCode;

    /** 历史行业代码。 */
    @Column(name = "industry_code", length = 64)
    private String industryCode;

    /** 上市日期。 */
    @Column(name = "listed_date")
    private LocalDate listedDate;

    /** 退市日期。 */
    @Column(name = "delisted_date")
    private LocalDate delistedDate;

    /** ST 状态，例如 NORMAL、ST、STAR_ST、DELISTING。 */
    @Column(name = "st_status", nullable = false, length = 32)
    private String stStatus;

    /** 该版本开始生效日期。 */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** 该版本失效日期；为空表示仍有效。 */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** 数据来源。 */
    @Column(nullable = false, length = 64)
    private String source;

    /** 数据源版本或批次。 */
    @Column(name = "source_version", nullable = false, length = 128)
    private String sourceVersion;

    /** 数据源采集时间。 */
    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    /** 数据库创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 数据库更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END --
