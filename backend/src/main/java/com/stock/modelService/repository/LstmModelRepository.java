package com.stock.modelService.repository;

import com.stock.modelService.entity.LstmModelDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * LSTM 模型在 MongoDB 中的存储仓库
 */
@Repository
public interface LstmModelRepository extends MongoRepository<LstmModelDocument, String> {

    /**
     * 获取最新保存的模型
     */
    LstmModelDocument findTopByOrderByCreatedAtDesc();
    void deleteByModelName(String modelName);
    boolean existsByModelName(String modelName);

    /**
     * 批量查询模型名称是否存在
     * 返回已存在的模型名称集合
     */
    List<String> findByModelNameIn(List<String> modelNames);
}

