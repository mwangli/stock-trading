package online.mwang.stockTrading.modules.datacollection.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 实时行情数据传输对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuoteDTO {
    
    /** 股票代码 */
    private String stockCode;
    
    /** 股票名称 */
    private String stockName;
    
    /** 当前价格 */
    private BigDecimal currentPrice;
    
    /** 涨跌额 */
    private BigDecimal change;
    
    /** 涨跌幅 */
    private BigDecimal changePercent;
    
    /** 开盘价 */
    private BigDecimal open;
    
    /** 最高价 */
    private BigDecimal high;
    
    /** 最低价 */
    private BigDecimal low;
    
    /** 昨日收盘价 */
    private BigDecimal previousClose;
    
    /** 成交量 */
    private Long volume;
    
    /** 成交额 */
    private BigDecimal amount;
    
    /** 是否实时数据 */
    private Boolean isRealTime;
}
