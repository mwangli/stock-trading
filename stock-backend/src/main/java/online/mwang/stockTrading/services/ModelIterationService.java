package online.mwang.stockTrading.services;

import online.mwang.stockTrading.entities.ModelEvaluation;
import online.mwang.stockTrading.entities.ModelVersion;
import online.mwang.stockTrading.entities.PerformanceRecord;
import online.mwang.stockTrading.entities.TrainingTask;

import java.util.List;

/**
 * 模型迭代服务接口
 * 从MySQL读取Python写入的模型迭代数据
 */
public interface ModelIterationService {

    // ==================== 表现记录 ====================

    /**
     * 获取所有表现记录
     */
    List<PerformanceRecord> getAllPerformanceRecords();

    /**
     * 获取最近N天的表现记录
     */
    List<PerformanceRecord> getRecentPerformanceRecords(int days);

    /**
     * 获取最新表现记录
     */
    PerformanceRecord getLatestPerformanceRecord();

    // ==================== 模型版本 ====================

    /**
     * 获取所有模型版本
     */
    List<ModelVersion> getAllModelVersions();

    /**
     * 获取指定类型的模型版本
     */
    List<ModelVersion> getModelVersionsByType(String modelType);

    /**
     * 获取当前活跃的模型版本
     */
    ModelVersion getActiveModelVersion(String modelType);

    /**
     * 获取所有活跃模型版本
     */
    List<ModelVersion> getActiveModelVersions();

    // ==================== 训练任务 ====================

    /**
     * 获取所有训练任务
     */
    List<TrainingTask> getAllTrainingTasks();

    /**
     * 获取指定状态的训练任务
     */
    List<TrainingTask> getTrainingTasksByStatus(String status);

    /**
     * 获取最近的训练任务
     */
    List<TrainingTask> getRecentTrainingTasks(int limit);

    // ==================== 模型评估 ====================

    /**
     * 获取所有模型评估结果
     */
    List<ModelEvaluation> getAllModelEvaluations();

    /**
     * 获取指定类型的模型评估结果
     */
    List<ModelEvaluation> getModelEvaluationsByType(String modelType);

    /**
     * 获取指定类型的最新评估结果
     */
    ModelEvaluation getLatestEvaluation(String modelType);

    /**
     * 获取最近N天的评估结果
     */
    List<ModelEvaluation> getRecentEvaluations(int days);

    /**
     * 获取需要重训练的模型评估结果
     */
    List<ModelEvaluation> getModelsNeedingRetrain();
}
