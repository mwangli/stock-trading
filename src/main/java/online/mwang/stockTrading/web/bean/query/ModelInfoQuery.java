package online.mwang.stockTrading.web.bean.query;

import lombok.Data;
import online.mwang.stockTrading.web.bean.base.BaseQuery;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/30 15:56
 * @description: ScoreStrategy
 */
@Data
public class ModelInfoQuery extends BaseQuery {
    private String code;
    private String name;
    private String params;
    private String description;
    private String status;
}
