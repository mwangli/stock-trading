package online.mwang.foundtrading.bean.query;

import lombok.Data;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:30
 * @description: FoundTradingQuery
 */

@Data
public class FoundTradingQuery {

    private Integer current;

    private Integer pageSize;

    private String name;

    private String code;
}
