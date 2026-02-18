package online.mwang.stockTrading.core.dto;

import lombok.Data;

/**
 * 基础查询对象
 */
@Data
public class BaseQuery {
    private Integer current;
    private Integer pageSize;
    private String sortKey;
    private String sortOrder;
}
