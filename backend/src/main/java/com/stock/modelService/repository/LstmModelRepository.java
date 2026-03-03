package com.stock.modelService.repository;

import com.stock.modelService.entity.LstmModelDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

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
}

