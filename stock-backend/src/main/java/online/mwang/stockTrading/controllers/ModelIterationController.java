package online.mwang.stockTrading.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.dto.Response;
import online.mwang.stockTrading.entities.ModelEvaluation;
import online.mwang.stockTrading.entities.ModelVersion;
import online.mwang.stockTrading.entities.PerformanceRecord;
import online.mwang.stockTrading.entities.TrainingTask;
import online.mwang.stockTrading.services.ModelIterationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型迭代控制器
 * 从MySQL读取Python服务写入的模型迭代数据
 */
@Slf4j
@RestController
@RequestMapping("/api/model-iteration")
@RequiredArgsConstructor
public class ModelIterationController {

    private final ModelIterationService modelIterationService;

    // ==================== 表现记录 API ====================

    /**
     * 获取所有表现记录
     */
    @GetMapping("/performance")
    public Response<List<PerformanceRecord>> getAllPerformanceRecords() {
        log.info("API: Getting all performance records");
        List<PerformanceRecord> records = modelIterationService.getAllPerformanceRecords();
        return Response.success(records);
    }

    /**
     * 获取最近N天的表现记录
     */
    @GetMapping("/performance/recent")
    public Response<List<PerformanceRecord>> getRecentPerformanceRecords(
            @RequestParam(value = "days", defaultValue = "30") int days) {
        log.info("API: Getting recent performance records for {} days", days);
        List<PerformanceRecord> records = modelIterationService.getRecentPerformanceRecords(days);
        return Response.success(records);
    }

    /**
     * 获取最新表现记录
     */
    @GetMapping("/performance/latest")
    public Response<PerformanceRecord> getLatestPerformanceRecord() {
        log.info("API: Getting latest performance record");
        PerformanceRecord record = modelIterationService.getLatestPerformanceRecord();
        return Response.success(record);
    }

    // ==================== 模型版本 API ====================

    /**
     * 获取所有模型版本
     */
    @GetMapping("/versions")
    public Response<List<ModelVersion>> getAllModelVersions() {
        log.info("API: Getting all model versions");
        List<ModelVersion> versions = modelIterationService.getAllModelVersions();
        return Response.success(versions);
    }

    /**
     * 获取指定类型的模型版本
     */
    @GetMapping("/versions/{modelType}")
    public Response<List<ModelVersion>> getModelVersionsByType(@PathVariable("modelType") String modelType) {
        log.info("API: Getting model versions for type: {}", modelType);
        List<ModelVersion> versions = modelIterationService.getModelVersionsByType(modelType);
        return Response.success(versions);
    }

    /**
     * 获取当前活跃模型版本
     */
    @GetMapping("/versions/{modelType}/active")
    public Response<ModelVersion> getActiveModelVersion(@PathVariable("modelType") String modelType) {
        log.info("API: Getting active model version for type: {}", modelType);
        ModelVersion version = modelIterationService.getActiveModelVersion(modelType);
        return Response.success(version);
    }

    /**
     * 获取所有活跃模型版本
     */
    @GetMapping("/versions/active/all")
    public Response<List<ModelVersion>> getActiveModelVersions() {
        log.info("API: Getting all active model versions");
        List<ModelVersion> versions = modelIterationService.getActiveModelVersions();
        return Response.success(versions);
    }

    // ==================== 训练任务 API ====================

    /**
     * 获取所有训练任务
     */
    @GetMapping("/tasks")
    public Response<List<TrainingTask>> getAllTrainingTasks() {
        log.info("API: Getting all training tasks");
        List<TrainingTask> tasks = modelIterationService.getAllTrainingTasks();
        return Response.success(tasks);
    }

    /**
     * 获取指定状态的训练任务
     */
    @GetMapping("/tasks/status/{status}")
    public Response<List<TrainingTask>> getTrainingTasksByStatus(@PathVariable("status") String status) {
        log.info("API: Getting training tasks with status: {}", status);
        List<TrainingTask> tasks = modelIterationService.getTrainingTasksByStatus(status);
        return Response.success(tasks);
    }

    /**
     * 获取最近的训练任务
     */
    @GetMapping("/tasks/recent")
    public Response<List<TrainingTask>> getRecentTrainingTasks(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        log.info("API: Getting recent training tasks, limit: {}", limit);
        List<TrainingTask> tasks = modelIterationService.getRecentTrainingTasks(limit);
        return Response.success(tasks);
    }

    // ==================== 模型评估 API ====================

    /**
     * 获取所有模型评估结果
     */
    @GetMapping("/evaluations")
    public Response<List<ModelEvaluation>> getAllModelEvaluations() {
        log.info("API: Getting all model evaluations");
        List<ModelEvaluation> evaluations = modelIterationService.getAllModelEvaluations();
        return Response.success(evaluations);
    }

    /**
     * 获取指定类型的模型评估结果
     */
    @GetMapping("/evaluations/{modelType}")
    public Response<List<ModelEvaluation>> getModelEvaluationsByType(@PathVariable("modelType") String modelType) {
        log.info("API: Getting model evaluations for type: {}", modelType);
        List<ModelEvaluation> evaluations = modelIterationService.getModelEvaluationsByType(modelType);
        return Response.success(evaluations);
    }

    /**
     * 获取指定类型的最新评估结果
     */
    @GetMapping("/evaluations/{modelType}/latest")
    public Response<ModelEvaluation> getLatestEvaluation(@PathVariable("modelType") String modelType) {
        log.info("API: Getting latest evaluation for type: {}", modelType);
        ModelEvaluation evaluation = modelIterationService.getLatestEvaluation(modelType);
        return Response.success(evaluation);
    }

    /**
     * 获取最近N天的评估结果
     */
    @GetMapping("/evaluations/recent")
    public Response<List<ModelEvaluation>> getRecentEvaluations(
            @RequestParam(value = "days", defaultValue = "30") int days) {
        log.info("API: Getting recent evaluations for {} days", days);
        List<ModelEvaluation> evaluations = modelIterationService.getRecentEvaluations(days);
        return Response.success(evaluations);
    }

    /**
     * 获取需要重训练的模型
     */
    @GetMapping("/evaluations/needing-retrain")
    public Response<List<ModelEvaluation>> getModelsNeedingRetrain() {
        log.info("API: Getting models needing retrain");
        List<ModelEvaluation> evaluations = modelIterationService.getModelsNeedingRetrain();
        return Response.success(evaluations);
    }
}
