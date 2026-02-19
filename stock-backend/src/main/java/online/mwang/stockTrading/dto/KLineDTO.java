package online.mwang.stockTrading.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * K线数据传输对象
 * 用于历史行情数据映射
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KLineDTO {
    
    /** 交易日期 */
    private LocalDate tradeDate;
    
    /** 开盘价 */
    private BigDecimal open;
    
    /** 最高价 */
    private BigDecimal high;
    
    /** 最低价 */
    private BigDecimal low;
    
    /** 收盘价 */
    private BigDecimal close;
    
    /** 成交量 */
    private Long volume;
    
    /** 成交额 */
    private BigDecimal amount;
    
    /** 涨跌幅 */
    private BigDecimal changePct;
    
    /** 换手率 */
    private BigDecimal turnoverRate;
}
