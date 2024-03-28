package online.mwang.stockTrading.bean.query;

import lombok.Data;
import online.mwang.stockTrading.bean.base.BaseQuery;

@Data
public class QuartzJobQuery extends BaseQuery {
    private String name;
    private String className;
    private String cron;
    private String status;
}
