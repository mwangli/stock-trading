package online.mwang.stockTrading.web.bean.query;

import lombok.Data;
import online.mwang.stockTrading.web.bean.base.BaseQuery;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:30
 * @description: FoundTradingQuery
 */

@Data
public class FoundTradingQuery extends BaseQuery {

    private String name;

    private String code;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date buyDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date saleDate;

    private Integer holdDays;

    private String sold;

    private String strategyName;
}
