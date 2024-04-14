package online.mwang.stockTrading.web.bean.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.mwang.stockTrading.web.bean.base.BaseQuery;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:30
 * @description: FoundTradingQuery
 */

@Data
public class OrderInfoQuery extends BaseQuery {
    private String answerNo;
    private String code;
    private String name;
    private String date;
    private String type;
    private Double number;
}
