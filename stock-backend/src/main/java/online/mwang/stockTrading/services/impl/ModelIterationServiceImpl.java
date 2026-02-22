package online.mwang.stockTrading.services.impl;

import lombok.RequiredArgsConstructor;
import online.mwang.stockTrading.entities.ModelEvaluation;
import online.mwang.stockTrading.entities.ModelVersion;
import online.mwang.stockTrading.entities.PerformanceRecord;
import online.mwang.stockTrading.entities.TrainingTask;
import online.mwang.stockTrading.repositories.ModelEvaluationRepository;
import online.mwang.stockTrading.repositories.ModelVersionRepository;
import online.mwang.stockTrading.repositories.PerformanceRecordRepository;
import online.mwang.stockTrading.repositories.TrainingTaskRepository;
import online.mwang.stockTrading.services.ModelIterationService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 模型迭代服务实现
 * 从MySQL读取Python写入的模型迭代数据
 */
@Service
@RequiredArgsConstructor
public class ModelIterationServiceImpl implements ModelIterationService {

    private final PerformanceRecordRepository performanceRecordRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final TrainingTaskRepository trainingTaskRepository;
    private final ModelEvaluationRepository modelEvaluationRepository;

    // ==================== 表现记录 ====================

    @Override
    public List<PerformanceRecord> getAllPerformanceRecords() {
        return performanceRecordRepository.findAll();
    }

    @Override
    public List<PerformanceRecord> getRecentPerformanceRecords(int days) {
        return performanceRecordRepository.findRecentRecords(days);
    }

    @Override
    public PerformanceRecord getLatestPerformanceRecord() {
        return performanceRecordRepository.findLatest();
    }

    // ==================== 模型版本 ====================

    @Override
    public List<ModelVersion> getAllModelVersions() {
        return modelVersionRepository.findAll();
    }

    @Override
    public List<ModelVersion> getModelVersionsByType(String modelType) {
        return modelVersionRepository.findByModelType(modelType);
    }

    @Override
    public ModelVersion getActiveModelVersion(String modelType) {
        return modelVersionRepository.findActiveVersion(modelType);
    }

    @Override
    public List<ModelVersion> getActiveModelVersions() {
        return modelVersionRepository.findActiveVersions();
    }

    // ==================== 训练任务 ====================

    @Override
    public List<TrainingTask> getAllTrainingTasks() {
        return trainingTaskRepository.findAll();
    }

    @Override
    public List<TrainingTask> getTrainingTasksByStatus(String status) {
        return trainingTaskRepository.findByStatus(status);
    }

    @Override
    public List<TrainingTask> getRecentTrainingTasks(int limit) {
        return trainingTaskRepository.findRecentTasks(limit);
    }

    // ==================== 模型评估 ====================

    @Override
    public List<ModelEvaluation> getAllModelEvaluations() {
        return modelEvaluationRepository.findAll();
    }

    @Override
    public List<ModelEvaluation> getModelEvaluationsByType(String modelType) {
        return modelEvaluationRepository.findByModelType(modelType);
    }

    @Override
    public ModelEvaluation getLatestEvaluation(String modelType) {
        return modelEvaluationRepository.findLatestByModelType(modelType);
    }

    @Override
    public List<ModelEvaluation> getRecentEvaluations(int days) {
        return modelEvaluationRepository.findRecentEvaluations(days);
    }

    @Override
    public List<ModelEvaluation> getModelsNeedingRetrain() {
        return modelEvaluationRepository.findNeedingRetrain();
    }
}
