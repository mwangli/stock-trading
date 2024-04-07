package online.mwang.stockTrading.web.bean.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/22 14:40
 * @description: DailyPrice
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailyItem {
    private String date;
    private String code;
    // price1-4对应上午开盘价，收盘价，下午开盘价，下午收盘价
    private Double price1;
    private Double price2;
    private Double price3;
    private Double price4;
}

