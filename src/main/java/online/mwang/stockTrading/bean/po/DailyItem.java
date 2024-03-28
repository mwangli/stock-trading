package online.mwang.stockTrading.bean.po;

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
    private Double item;
}