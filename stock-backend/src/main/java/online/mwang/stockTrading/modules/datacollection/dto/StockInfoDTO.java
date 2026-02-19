package online.mwang.stockTrading.modules.datacollection.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 股票信息数据传输对象
 * 用于AKTools API返回的数据映射
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockInfoDTO {
    
    /** 股票代码 */
    private String stockCode;
    
    /** 股票名称 */
    private String stockName;
    
    /** 市场 (SH-上海, SZ-深圳) */
    private String market;
    
    /** 所属行业 */
    private String industry;
    
    /** 上市日期 */
    private LocalDate listingDate;
    
    /** 是否ST股票 */
    private Boolean isSt;
    
    /** 是否可交易 */
    private Boolean isTradable;
}
