package online.mwang.stockTrading.web.bean.base;

import lombok.Data;

@Data
public class BaseQuery {

    private Integer current;

    private Integer pageSize;

    private String sortKey;

    private String sortOrder;
}
