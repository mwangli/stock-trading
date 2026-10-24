// AI_GENERATE_START---------
package com.stock.execution.entity;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.annotation.Id;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券商账户资金快照实体。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Document(collection = "account_snapshot")
@CompoundIndexes({
        @CompoundIndex(name = "uk_account_snapshot_id", def = "{'snapshotId': 1}", unique = true),
        @CompoundIndex(name = "idx_account_snapshot_account_captured",
                def = "{'brokerCode': 1, 'accountId': 1, 'capturedAt': -1}")
})
@Accessors(chain = true)
public class AccountSnapshotEntity {

    /** 数据库主键。 */
    @Id
    private String id;

    /** 全局快照编号。 */
    private String snapshotId;

    /** 券商编码。 */
    private String brokerCode;

    /** 账户哈希标识，不保存资金账号明文。 */
    private String accountId;

    /** 总资产，单位为元。 */
    private BigDecimal totalAssets;

    /** 可用资金，单位为元。 */
    private BigDecimal availableCash;

    /** 冻结资金，单位为元。 */
    private BigDecimal frozenCash;

    /** 持仓市值，单位为元。 */
    private BigDecimal marketValue;

    /** 当日盈亏，单位为元；券商未提供时为空。 */
    private BigDecimal dailyProfitLoss;

    /** 券商采集时间。 */
    private LocalDateTime capturedAt;

    /** 快照来源，如 CURRENT_SYNC。 */
    private String source;

    /** 脱敏原始事件引用；未落原始事件时为空。 */
    private String rawEventId;

    /** 记录创建时间。 */
    @CreatedDate
    private LocalDateTime createdAt;
}
// AI_GENERATE_END---------
