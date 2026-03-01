package com.stock.databus.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

/**
 * 股票基本信息实体
 */
@Data
@Document(collection = "stock_info")
public class StockInfo {

    @Id
    private String id;

    /**
     * 股票代码
     */
    private String code;

    /**
     * 股票名称
     */
    private String name;

    /**
     * 上市日期
     */
    private LocalDate listDate;

    /**
     * 总股本
     */
    private Long totalShares;

    /**
     * 流通股本
     */
    private Long circulatingShares;
}