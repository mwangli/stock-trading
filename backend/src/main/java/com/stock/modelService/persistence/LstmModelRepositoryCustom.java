package com.stock.modelService.persistence;

import com.stock.modelService.domain.entity.LstmModelDocument;

import java.util.List;
import java.util.Map;

/**
 * LSTM 模型仓库自定义方法
 *
 * @author mwangli
 * @since 2026-03-10
 */
public interface LstmModelRepositoryCustom {

    /**
     * 使用 distinct 查询所有已存在的 modelName（股票代码）
     * 比 find + 投影更快，单次往返，走索引
     *
     * @return 去重后的 modelName 列表
     */
    List<String> findAllModelNamesDistinct();

    /**
     * 批量查询指定股票代码对应的最新 LSTM 模型（按 createdAt 降序取第一条）
     * 使用 modelName + createdAt 复合索引，单次聚合查询，避免 N 次往返
     *
     * @param modelNames 股票代码集合
     * @return modelName -> 最新 LstmModelDocument 的映射
     */
    Map<String, LstmModelDocument> findLatestByModelNames(List<String> modelNames);
}
