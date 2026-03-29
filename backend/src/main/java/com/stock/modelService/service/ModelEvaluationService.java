package com.stock.modelService.service;

import com.stock.modelService.config.SentimentEvaluationConfig;
import com.stock.modelService.domain.entity.SentimentAutoLabel;
import com.stock.modelService.domain.entity.SentimentEvaluation;
import com.stock.modelService.persistence.SentimentAutoLabelRepository;
import com.stock.modelService.persistence.SentimentEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 情感模型评估服务
 * <p>
 * 负责对情感分析模型进行评估，计算准确率、F1分数、夏普比率等关键指标，
 * 并根据阈值判断是否需要触发模型微调。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelEvaluationService {

    private final SentimentEvaluationRepository evaluationRepository;
    private final SentimentAutoLabelRepository sentimentAutoLabelRepository;
    private final SentimentTrainerService sentimentTrainerService;
    private final SentimentEvaluationConfig config;

    /**
     * 执行模型评估
     * <p>
     * 基于测试数据集计算各项评估指标，包括准确率、F1分数、精确率、召回率、
     * 方向准确率、夏普比率和最大回撤。评估结果将保存到 MongoDB 并返回评估报告。
     * </p>
     *
     * @param triggerSource 触发来源：scheduled/manual/threshold
     * @return 评估报告
     */
    public EvaluationResult evaluateModel(String triggerSource) {
        log.info("开始执行模型评估，触发来源: {}", triggerSource);

        EvaluationResult result = new EvaluationResult();
        result.setTriggerSource(triggerSource);
        result.setModelVersion(getLatestModelVersion());
        result.setCreatedAt(LocalDateTime.now());

        try {
            List<TestSample> testSamples = loadTestDataset();
            if (testSamples.isEmpty()) {
                result.setSuccess(false);
                result.setErrorMessage("测试数据集为空，无法执行评估");
                log.warn("情感模型评估失败：测试数据集为空");
                return result;
            }

            result.setSampleCount(testSamples.size());
            log.info("加载测试数据集完成，样本数量: {}", testSamples.size());

            int tp = 0, tn = 0, fp = 0, fn = 0;
            int correctDirection = 0;
            List<Double> returns = new java.util.ArrayList<>();
            double cumulativeReturn = 0.0;
            double peak = 0.0;
            double maxDrawdown = 0.0;

            for (TestSample sample : testSamples) {
                var prediction = sentimentTrainerService.analyzeSentimentWithDetails(sample.getText());
                String predictedLabel = (String) prediction.get("label");
                double predictedScore = getSentimentScore(prediction);

                String actualLabel = sample.getActualLabel();
                double actualScore = sample.getActualScore();

                if (predictedLabel.equals(actualLabel)) {
                    if ("positive".equals(actualLabel)) {
                        tp++;
                    } else if ("negative".equals(actualLabel)) {
                        tn++;
                    } else {
                        tn++;
                    }
                } else {
                    if ("positive".equals(predictedLabel) && !"positive".equals(actualLabel)) {
                        fp++;
                    } else if ("negative".equals(predictedLabel) && !"negative".equals(actualLabel)) {
                        fn++;
                    } else if ("neutral".equals(predictedLabel)) {
                        if ("positive".equals(actualLabel)) {
                            fn++;
                        } else {
                            fp++;
                        }
                    }
                }

                boolean directionMatch = (predictedScore > 0 && actualScore > 0)
                        || (predictedScore < 0 && actualScore < 0)
                        || (predictedScore == 0 && actualScore == 0);
                if (directionMatch) {
                    correctDirection++;
                }

                double sampleReturn = calculateSampleReturn(predictedScore, actualScore);
                returns.add(sampleReturn);
                cumulativeReturn += sampleReturn;

                if (cumulativeReturn > peak) {
                    peak = cumulativeReturn;
                }
                double drawdown = (peak - cumulativeReturn) / (peak == 0 ? 1.0 : peak);
                if (drawdown > maxDrawdown) {
                    maxDrawdown = drawdown;
                }
            }

            int total = tp + tn + fp + fn;
            double accuracy = total > 0 ? (double) (tp + tn) / total : 0.0;
            double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
            double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
            double f1Score = (precision + recall) > 0
                    ? 2 * precision * recall / (precision + recall) : 0.0;
            double directionAccuracy = testSamples.size() > 0
                    ? (double) correctDirection / testSamples.size() : 0.0;

            double sharpeRatio = calculateSharpeRatio(returns);
            double maxDrawdownValue = maxDrawdown;

            result.setAccuracy(accuracy);
            result.setPrecision(precision);
            result.setRecall(recall);
            result.setF1Score(f1Score);
            result.setDirectionAccuracy(directionAccuracy);
            result.setSharpeRatio(sharpeRatio);
            result.setMaxDrawdown(maxDrawdownValue);
            result.setThresholdStatus(calculateThresholdStatus(result));
            result.setShouldFineTune(shouldTriggerFineTune(result));

            SentimentEvaluation evaluation = convertToEntity(result);
            evaluationRepository.save(evaluation);

            result.setSuccess(true);
            log.info("模型评估完成 | 准确率: {}, F1: {}, 方向准确率: {}, 夏普比率: {}, 最大回撤: {}, 阈值状态: {}, 建议微调: {}",
                    String.format("%.4f", accuracy),
                    String.format("%.4f", f1Score),
                    String.format("%.4f", directionAccuracy),
                    String.format("%.4f", sharpeRatio),
                    String.format("%.4f", maxDrawdownValue),
                    result.getThresholdStatus(),
                    result.isShouldFineTune());

        } catch (Exception e) {
            log.error("模型评估执行异常", e);
            result.setSuccess(false);
            result.setErrorMessage("评估执行异常: " + e.getMessage());
        }

        return result;
    }

    /**
     * 检查是否需要触发微调
     * <p>
     * 根据评估结果判断是否需要触发模型微调。满足以下任一条件时返回 true：
     * <ul>
     *   <li>准确率小于 70%</li>
     *   <li>F1 分数小于 60%</li>
     *   <li>连续 3 个评估周期指标下降</li>
     * </ul>
     * </p>
     *
     * @param result 评估结果
     * @return true 如果需要触发微调
     */
    public boolean shouldTriggerFineTune(EvaluationResult result) {
        if (result == null || !result.isSuccess()) {
            return false;
        }

        if (result.getAccuracy() < config.getAccuracyThreshold() / 100.0) {
            log.info("准确率 {} 低于阈值 {}，建议触发微调",
                    String.format("%.4f", result.getAccuracy()), config.getAccuracyThreshold() / 100.0);
            return true;
        }

        if (result.getF1Score() < config.getF1Threshold() / 100.0) {
            log.info("F1分数 {} 低于阈值 {}，建议触发微调",
                    String.format("%.4f", result.getF1Score()), config.getF1Threshold() / 100.0);
            return true;
        }

        if (checkConsecutiveDecline() >= config.getConsecutiveDeclineTrigger()) {
            log.info("连续下降周期数达到{}次，建议触发微调", config.getConsecutiveDeclineTrigger());
            return true;
        }

        return false;
    }

    /**
     * 获取最新评估结果
     *
     * @return 最新评估结果
     */
    public EvaluationResult getLatestResult() {
        Optional<SentimentEvaluation> latest = evaluationRepository.findTopByOrderByCreatedAtDesc();
        if (latest.isPresent()) {
            return convertFromEntity(latest.get());
        }
        return null;
    }

    /**
     * 按模型版本查询评估记录
     *
     * @param modelVersion 模型版本号
     * @return 该版本的评估记录列表
     */
    public List<SentimentEvaluation> findByModelVersion(String modelVersion) {
        return evaluationRepository.findByModelVersion(modelVersion);
    }

    /**
     * 获取最新评估记录列表
     *
     * @param limit 返回数量限制
     * @return 评估记录列表（按时间倒序）
     */
    public List<SentimentEvaluation> findLatestEvaluations(int limit) {
        org.springframework.data.domain.Page<SentimentEvaluation> page = evaluationRepository
                .findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")));
        return page.getContent();
    }

    private List<TestSample> loadTestDataset() {
        List<SentimentAutoLabel> validatedLabels = sentimentAutoLabelRepository
                .findByStatusAndConfidenceGreaterThanEqual("validated", config.getMinConfidenceForLabel());

        if (validatedLabels.size() >= 100) {
            log.info("使用{}条真实标注数据进行评估", validatedLabels.size());
            return validatedLabels.stream()
                    .map(this::convertToTestSample)
                    .collect(Collectors.toList());
        }

        log.warn("标注数据不足100条，当前{}条，回退到模拟数据", validatedLabels.size());
        return createMockDataset();
    }

    private TestSample convertToTestSample(SentimentAutoLabel label) {
        String actualLabel = label.getLabel();
        double actualScore = normalizeReturnToScore(label.getReturnRate());
        return new TestSample(label.getText(), actualLabel, actualScore);
    }

    private double normalizeReturnToScore(Double returnRate) {
        if (returnRate == null) {
            return 0.0;
        }
        return Math.max(-1.0, Math.min(1.0, returnRate));
    }

    private List<TestSample> createMockDataset() {
        List<TestSample> samples = new java.util.ArrayList<>();
        samples.add(new TestSample("央行宣布降息，利好市场流动性", "positive", 0.8));
        samples.add(new TestSample("公司业绩超预期，营收增长20%", "positive", 0.7));
        samples.add(new TestSample("行业景气度提升，企业订单增加", "positive", 0.6));
        samples.add(new TestSample("政策支持新能源产业发展", "positive", 0.75));
        samples.add(new TestSample("公司推出新产品，市场反响热烈", "positive", 0.65));
        samples.add(new TestSample("经济增长放缓，企业盈利下降", "negative", -0.7));
        samples.add(new TestSample("行业竞争加剧，市场份额下滑", "negative", -0.6));
        samples.add(new TestSample("公司财务造假被曝光", "negative", -0.9));
        samples.add(new TestSample("市场需求疲软，订单减少", "negative", -0.65));
        samples.add(new TestSample("原材料价格上涨压缩利润空间", "negative", -0.55));
        samples.add(new TestSample("市场整体平稳，无明显波动", "neutral", 0.0));
        samples.add(new TestSample("公司召开年度股东大会", "neutral", 0.1));
        samples.add(new TestSample("行业数据发布，符合市场预期", "neutral", 0.05));
        samples.add(new TestSample("公司进行正常业务调整", "neutral", 0.0));
        samples.add(new TestSample("市场观望情绪浓厚", "neutral", -0.05));
        return samples;
    }

    private String getLatestModelVersion() {
        Optional<SentimentEvaluation> latest = evaluationRepository.findTopByOrderByCreatedAtDesc();
        if (latest.isPresent()) {
            String currentVersion = latest.get().getModelVersion();
            if (currentVersion != null && currentVersion.startsWith("v")) {
                String[] parts = currentVersion.substring(1).split("\\.");
                if (parts.length >= 3) {
                    try {
                        int patch = Integer.parseInt(parts[2]) + 1;
                        return String.format("v%s.%s.%d", parts[0], parts[1], patch);
                    } catch (NumberFormatException e) {
                        return "v1.0.1";
                    }
                }
            }
        }
        return "v1.0.1";
    }

    private double getSentimentScore(java.util.Map<String, Object> prediction) {
        Object scoreObj = prediction.get("score");
        if (scoreObj instanceof Number) {
            return ((Number) scoreObj).doubleValue();
        }
        return 0.0;
    }

    private double calculateSampleReturn(double predictedScore, double actualScore) {
        double directionPrediction = predictedScore > 0 ? 1.0 : (predictedScore < 0 ? -1.0 : 0.0);
        double directionActual = actualScore > 0 ? 1.0 : (actualScore < 0 ? -1.0 : 0.0);
        return directionPrediction == directionActual ? 0.05 : -0.05;
    }

    private double calculateSharpeRatio(List<Double> returns) {
        if (returns.isEmpty()) {
            return 0.0;
        }

        double meanReturn = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - meanReturn, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0) {
            return 0.0;
        }

        return (meanReturn - config.getRiskFreeRate() / 252) / stdDev;
    }

    private int checkConsecutiveDecline() {
        org.springframework.data.domain.Page<SentimentEvaluation> page = evaluationRepository
                .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<SentimentEvaluation> recentEvaluations = page.getContent();

        if (recentEvaluations.size() < 2) {
            return 0;
        }

        int consecutiveDecline = 0;
        Double previousAccuracy = null;

        for (SentimentEvaluation evaluation : recentEvaluations) {
            Double currentAccuracy = evaluation.getAccuracy();
            if (previousAccuracy != null && currentAccuracy < previousAccuracy) {
                consecutiveDecline++;
            } else {
                break;
            }
            previousAccuracy = currentAccuracy;
        }

        return consecutiveDecline;
    }

    private String calculateThresholdStatus(EvaluationResult result) {
        int warningCount = 0;
        int criticalCount = 0;
        double accuracyThreshold = config.getAccuracyThreshold() / 100.0;
        double f1Threshold = config.getF1Threshold() / 100.0;
        double directionAccuracyThreshold = config.getDirectionAccuracyThreshold() / 100.0;
        double sharpeRatioThreshold = config.getSharpeRatioThreshold();

        if (result.getAccuracy() < accuracyThreshold) {
            criticalCount++;
        } else if (result.getAccuracy() < accuracyThreshold + 0.05) {
            warningCount++;
        }

        if (result.getF1Score() < f1Threshold) {
            criticalCount++;
        } else if (result.getF1Score() < f1Threshold + 0.05) {
            warningCount++;
        }

        if (result.getDirectionAccuracy() < directionAccuracyThreshold) {
            criticalCount++;
        } else if (result.getDirectionAccuracy() < directionAccuracyThreshold + 0.05) {
            warningCount++;
        }

        if (result.getSharpeRatio() < sharpeRatioThreshold) {
            criticalCount++;
        } else if (result.getSharpeRatio() < sharpeRatioThreshold + 0.2) {
            warningCount++;
        }

        if (criticalCount > 0) {
            return "critical";
        } else if (warningCount > 0) {
            return "warning";
        }
        return "normal";
    }

    private SentimentEvaluation convertToEntity(EvaluationResult result) {
        SentimentEvaluation evaluation = new SentimentEvaluation();
        evaluation.setModelVersion(result.getModelVersion());
        evaluation.setAccuracy(result.getAccuracy());
        evaluation.setF1Score(result.getF1Score());
        evaluation.setPrecision(result.getPrecision());
        evaluation.setRecall(result.getRecall());
        evaluation.setDirectionAccuracy(result.getDirectionAccuracy());
        evaluation.setSharpeRatio(result.getSharpeRatio());
        evaluation.setMaxDrawdown(result.getMaxDrawdown());
        evaluation.setSampleCount(result.getSampleCount());
        evaluation.setThresholdStatus(result.getThresholdStatus());
        evaluation.setTriggerSource(result.getTriggerSource());
        evaluation.setCreatedAt(result.getCreatedAt());
        return evaluation;
    }

    private EvaluationResult convertFromEntity(SentimentEvaluation entity) {
        EvaluationResult result = new EvaluationResult();
        result.setModelVersion(entity.getModelVersion());
        result.setAccuracy(entity.getAccuracy());
        result.setF1Score(entity.getF1Score());
        result.setPrecision(entity.getPrecision());
        result.setRecall(entity.getRecall());
        result.setDirectionAccuracy(entity.getDirectionAccuracy());
        result.setSharpeRatio(entity.getSharpeRatio());
        result.setMaxDrawdown(entity.getMaxDrawdown());
        result.setSampleCount(entity.getSampleCount());
        result.setThresholdStatus(entity.getThresholdStatus());
        result.setTriggerSource(entity.getTriggerSource());
        result.setCreatedAt(entity.getCreatedAt());
        result.setSuccess(true);
        result.setShouldFineTune(shouldTriggerFineTune(result));
        return result;
    }

    /**
     * 评估结果内部类
     */
    public static class EvaluationResult {
        private String modelVersion;
        private Double accuracy;
        private Double f1Score;
        private Double precision;
        private Double recall;
        private Double rocAuc;
        private Double sharpeRatio;
        private Double directionAccuracy;
        private Double maxDrawdown;
        private Integer sampleCount;
        private String thresholdStatus;
        private String triggerSource;
        private LocalDateTime createdAt;
        private boolean success;
        private boolean shouldFineTune;
        private String errorMessage;

        public String getModelVersion() {
            return modelVersion;
        }

        public void setModelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
        }

        public Double getAccuracy() {
            return accuracy;
        }

        public void setAccuracy(Double accuracy) {
            this.accuracy = accuracy;
        }

        public Double getF1Score() {
            return f1Score;
        }

        public void setF1Score(Double f1Score) {
            this.f1Score = f1Score;
        }

        public Double getPrecision() {
            return precision;
        }

        public void setPrecision(Double precision) {
            this.precision = precision;
        }

        public Double getRecall() {
            return recall;
        }

        public void setRecall(Double recall) {
            this.recall = recall;
        }

        public Double getRocAuc() {
            return rocAuc;
        }

        public void setRocAuc(Double rocAuc) {
            this.rocAuc = rocAuc;
        }

        public Double getSharpeRatio() {
            return sharpeRatio;
        }

        public void setSharpeRatio(Double sharpeRatio) {
            this.sharpeRatio = sharpeRatio;
        }

        public Double getDirectionAccuracy() {
            return directionAccuracy;
        }

        public void setDirectionAccuracy(Double directionAccuracy) {
            this.directionAccuracy = directionAccuracy;
        }

        public Double getMaxDrawdown() {
            return maxDrawdown;
        }

        public void setMaxDrawdown(Double maxDrawdown) {
            this.maxDrawdown = maxDrawdown;
        }

        public Integer getSampleCount() {
            return sampleCount;
        }

        public void setSampleCount(Integer sampleCount) {
            this.sampleCount = sampleCount;
        }

        public String getThresholdStatus() {
            return thresholdStatus;
        }

        public void setThresholdStatus(String thresholdStatus) {
            this.thresholdStatus = thresholdStatus;
        }

        public String getTriggerSource() {
            return triggerSource;
        }

        public void setTriggerSource(String triggerSource) {
            this.triggerSource = triggerSource;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public boolean isShouldFineTune() {
            return shouldFineTune;
        }

        public void setShouldFineTune(boolean shouldFineTune) {
            this.shouldFineTune = shouldFineTune;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

    /**
     * 测试样本内部类
     */
    private static class TestSample {
        private final String text;
        private final String actualLabel;
        private final double actualScore;

        public TestSample(String text, String actualLabel, double actualScore) {
            this.text = text;
            this.actualLabel = actualLabel;
            this.actualScore = actualScore;
        }

        public String getText() {
            return text;
        }

        public String getActualLabel() {
            return actualLabel;
        }

        public double getActualScore() {
            return actualScore;
        }
    }
}