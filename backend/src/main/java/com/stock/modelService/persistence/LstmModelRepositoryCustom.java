package com.stock.modelService.persistence;

import java.util.List;

/**
 * LSTM 模型仓库自定义方法
 *
 * @author AI Assistant
 * @since 1.0
 */
public interface LstmModelRepositoryCustom {

    /**
     * 使用 distinct 查询所有已存在的 modelName（股票代码）
     * 比 find + 投影更快，单次往返，走索引
     *
     * @return 去重后的 modelName 列表
     */
    List<String> findAllModelNamesDistinct();
}
