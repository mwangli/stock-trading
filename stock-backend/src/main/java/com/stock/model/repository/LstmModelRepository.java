// AI_GENERATE_START-----------
package com.stock.model.repository;

import com.stock.model.entity.LstmModelDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * LSTM 模型元数据 MySQL 仓库
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Repository
public interface LstmModelRepository extends JpaRepository<LstmModelDocument, String> {

    /**
     * 查询全局最近创建的模型版本。
     *
     * @return 最近创建的模型版本，不存在时为空
     */
    LstmModelDocument findTopByOrderByCreatedAtDesc();

    /**
     * 判断指定模型名称是否已有版本记录。
     *
     * @param modelName 模型名称
     * @return 存在版本记录时返回 true
     */
    boolean existsByModelName(String modelName);

    /**
     * 查询指定模型名称最近创建的模型版本。
     *
     * @param modelName 模型名称
     * @return 最近创建的模型版本，不存在时为空
     */
    LstmModelDocument findTopByModelNameOrderByCreatedAtDesc(String modelName);

    /**
     * 分页查询指定模型的历史版本。
     *
     * @param modelName 模型名称
     * @param pageable 分页参数
     * @return 模型版本分页结果
     */
    @Query(value = """
            select item.id as id,
                   item.modelName as modelName,
                   item.modelVersion as modelVersion,
                   item.status as status,
                   item.featureVersion as featureVersion,
                   item.inputContractJson as inputContractJson,
                   item.parameterSize as parameterSize,
                   item.epoch as epoch,
                   item.trainLoss as trainLoss,
                   item.valLoss as valLoss,
                   item.createdAt as createdAt
            from LstmModelDocument item
            where item.modelName = :modelName
            """,
            countQuery = """
            select count(item)
            from LstmModelDocument item
            where item.modelName = :modelName
            """)
    Page<LstmModelListProjection> findVersionListByModelName(
            @Param("modelName") String modelName, Pageable pageable);

    /**
     * 按主键查询模型版本页面摘要。
     *
     * @param id 模型版本主键
     * @return 不包含参数和归一化大字段的模型版本摘要
     */
    @Query("""
            select item.id as id,
                   item.modelName as modelName,
                   item.modelVersion as modelVersion,
                   item.status as status,
                   item.featureVersion as featureVersion,
                   item.inputContractJson as inputContractJson,
                   item.parameterSize as parameterSize,
                   item.epoch as epoch,
                   item.trainLoss as trainLoss,
                   item.valLoss as valLoss,
                   item.createdAt as createdAt
            from LstmModelDocument item
            where item.id = :id
            """)
    Optional<LstmModelListProjection> findVersionSummaryById(@Param("id") String id);

}
// AI_GENERATE_END-----------
