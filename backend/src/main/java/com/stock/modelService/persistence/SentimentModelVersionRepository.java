package com.stock.modelService.persistence;

import com.stock.modelService.domain.entity.SentimentModelVersion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 情感模型版本仓库
 * 负责对 MongoDB 中的 {@link SentimentModelVersion} 文档进行 CRUD 操作。
 *
 * <p>主要用于情感模型版本的管理、查询和状态切换。</p>
 *
 * @author mwangli
 * @since 2026-03-29
 */
@Repository
public interface SentimentModelVersionRepository extends MongoRepository<SentimentModelVersion, String> {

    /**
     * 获取当前指定状态的最新版本
     *
     * @param status 版本状态
     * @return 最新版本（按部署时间倒序）
     */
    Optional<SentimentModelVersion> findTopByStatusOrderByDeployedAtDesc(String status);

    /**
     * 按状态查询所有版本列表
     *
     * @param status 版本状态
     * @return 版本列表（按部署时间倒序）
     */
    List<SentimentModelVersion> findByStatusOrderByDeployedAtDesc(String status);

    /**
     * 按版本号查询
     *
     * @param version 版本号
     * @return 版本信息
     */
    Optional<SentimentModelVersion> findByVersion(String version);
}