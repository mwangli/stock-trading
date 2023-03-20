package online.mwang.foundtrading.bean;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 11:00
 * @description: FoundTradingRecord
 */
@Data
public class FoundTradingRecord {

    private Long id;

    private String code;

    private String name;

    private BigDecimal buyAmount;

    private Date buyDate;

    private BigDecimal saleAmount;

    private Date saleDate;

    private BigDecimal accountAmount;

    private Date accountDate;

    private BigDecimal expectedIncome;

    private BigDecimal expectedIncomeRate;

    private BigDecimal realIncome;

    private BigDecimal realIncomeRate;

    private Date createTime;

    private Date updateTime;
}
