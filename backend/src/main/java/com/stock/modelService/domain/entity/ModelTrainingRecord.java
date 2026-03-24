package com.stock.modelService.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * LSTM 模型训练记录实体
 * 用于在 MySQL 中记录每只股票的模型训练状态及最近一次训练信息，
 * 方便前端分页展示量化模型列表和训练情况。
 *
 * <p>一条记录对应一只股票代码，包含最近一次训练时间、训练时长、
 * 训练轮次、损失指标以及对应的 MongoDB 模型标识等信息。</p>
 *
 * @author AI Assistant
 * @since 1.0
 */
@Entity
@Table(name = "model_training_record", indexes = {
        @Index(name = "idx_stock_code", columnList = "stock_code"),
        @Index(name = "idx_last_train_time", columnList = "last_train_time")
})
public class ModelTrainingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 股票代码，如 600519、000858
     */
    @Column(name = "stock_code", nullable = false, unique = true, length = 16)
    private String stockCode;

    /**
     * 股票名称，冗余存储用于列表展示
     */
    @Column(name = "stock_name", length = 64)
    private String stockName;

    /**
     * 是否已有对应 LSTM 模型
     */
    @Column(name = "trained", nullable = false)
    private Boolean trained = Boolean.FALSE;

    /**
     * 当前是否处于训练中状态
     * <p>
     * 在训练任务开始时置为 true，训练结束（成功或失败）后置为 false，
     * 便于前端展示“训练中”的动态状态。
     * </p>
     */
    @Column(name = "training", nullable = false)
    private Boolean training = Boolean.FALSE;

    /**
     * 最近一次训练完成时间
     */
    @Column(name = "last_train_time")
    private LocalDateTime lastTrainTime;

    /**
     * 最近一次训练耗时（秒）
     */
    @Column(name = "last_duration_seconds")
    private Long lastDurationSeconds;

    /**
     * 最近一次训练轮次
     */
    @Column(name = "last_epochs")
    private Integer lastEpochs;

    /**
     * 最近一次训练集损失
     */
    @Column(name = "last_train_loss")
    private Double lastTrainLoss;

    /**
     * 最近一次验证集损失
     */
    @Column(name = "last_val_loss")
    private Double lastValLoss;

    /**
     * 最近一次训练对应的 MongoDB 模型 ID（不含 mongo: 前缀）
     */
    @Column(name = "last_model_id", length = 64)
    private String lastModelId;

    /**
     * 记录创建时间
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 记录最近更新时间
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStockCode() {
        return stockCode;
    }

    public void setStockCode(String stockCode) {
        this.stockCode = stockCode;
    }

    public String getStockName() {
        return stockName;
    }

    public void setStockName(String stockName) {
        this.stockName = stockName;
    }

    public Boolean getTrained() {
        return trained;
    }

    public void setTrained(Boolean trained) {
        this.trained = trained;
    }

    public Boolean getTraining() {
        return training;
    }

    public void setTraining(Boolean training) {
        this.training = training;
    }

    public LocalDateTime getLastTrainTime() {
        return lastTrainTime;
    }

    public void setLastTrainTime(LocalDateTime lastTrainTime) {
        this.lastTrainTime = lastTrainTime;
    }

    public Long getLastDurationSeconds() {
        return lastDurationSeconds;
    }

    public void setLastDurationSeconds(Long lastDurationSeconds) {
        this.lastDurationSeconds = lastDurationSeconds;
    }

    public Integer getLastEpochs() {
        return lastEpochs;
    }

    public void setLastEpochs(Integer lastEpochs) {
        this.lastEpochs = lastEpochs;
    }

    public Double getLastTrainLoss() {
        return lastTrainLoss;
    }

    public void setLastTrainLoss(Double lastTrainLoss) {
        this.lastTrainLoss = lastTrainLoss;
    }

    public Double getLastValLoss() {
        return lastValLoss;
    }

    public void setLastValLoss(Double lastValLoss) {
        this.lastValLoss = lastValLoss;
    }

    public String getLastModelId() {
        return lastModelId;
    }

    public void setLastModelId(String lastModelId) {
        this.lastModelId = lastModelId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

