// AI_GENERATE_START -
package com.stock.trading.enums;

import com.stock.trading.dto.FiveLevelQuoteDto;

import java.math.BigDecimal;

/**
 * 分阶段交易使用的盘口档位。
 *
 * @author mwangli
 * @since 2026-10-27
 */
public enum TradingPriceLevel {
    BID_ONE,
    BID_THREE,
    BID_FIVE,
    ASK_ONE,
    ASK_THREE,
    ASK_FIVE,
    BEST_FIVE_IOC;

    /**
     * 从五档行情中取得当前档位价格。
     *
     * @param quote 五档行情
     * @return 对应价格；最优五档委托返回空
     */
    public BigDecimal resolve(FiveLevelQuoteDto quote) {
        return switch (this) {
            case BID_ONE -> quote.getBid1();
            case BID_THREE -> quote.getBid3();
            case BID_FIVE -> quote.getBid5();
            case ASK_ONE -> quote.getAsk1();
            case ASK_THREE -> quote.getAsk3();
            case ASK_FIVE -> quote.getAsk5();
            case BEST_FIVE_IOC -> null;
        };
    }
}
// AI_GENERATE_END -
