package com.stock.modelService.persistence;

import com.stock.modelService.domain.entity.LstmModelDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * LSTM 模型在 MongoDB 中的存储仓库
 *
 * @author AI Assistant
 * @since 1.0
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

    /**
     * 按模型名称（股票代码）模糊分页查询，忽略大小写
     */
    Page<LstmModelDocument> findByModelNameContainingIgnoreCase(String keyword, Pageable pageable);

    /**
     * 分页列表查询：排除 params、normalizationParams 大字段，避免传输和反序列化耗时
     */
    @Query(value = "{}", fields = "{ 'params' : 0, 'normalizationParams' : 0 }")
    Page<LstmModelDocument> findAllForList(Pageable pageable);

    /**
     * 按关键词分页列表查询：排除大字段
     */
    @Query(value = "{ 'modelName' : { $regex: ?0, $options: 'i' } }", fields = "{ 'params' : 0, 'normalizationParams' : 0 }")
    Page<LstmModelDocument> findByModelNameRegexForList(String keyword, Pageable pageable);

    /**
     * 按 ID 查询模型，仅返回结果展示所需字段（排除 params、normalizationParams）
     */
    @Query(value = "{ '_id' : ?0 }", fields = "{ 'params' : 0, 'normalizationParams' : 0 }")
    Optional<LstmModelDocument> findByIdForResult(String id);
}
