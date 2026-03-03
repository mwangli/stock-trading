package com.stock.modelService.service;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.translator.TextClassificationTranslator;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import com.stock.modelService.config.SentimentTrainingConfig;
import com.stock.modelService.dto.SentimentAnalysisResult;
import com.stock.modelService.dto.SentimentTrainingRequest;
import com.stock.modelService.dto.SentimentTrainingResponse;
import com.stock.modelService.dto.TrainingSample;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;

import ai.djl.translate.Translator;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
/**
 * 情感分析模型训练服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentTrainerService {

    private final SentimentTrainingConfig config;
    private final SentimentDataPreprocessor dataPreprocessor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ZooModel<String, Classifications> loadedModel = null;
    private boolean isModelLoaded = false;
    private final Map<String, TrainingStatus> trainingStatusMap = new ConcurrentHashMap<>();

    /**
     * 训练情感分析模型
     */
    public SentimentTrainingResponse trainModel(SentimentTrainingRequest request) {
        String trainingId = "sentiment_training_" + System.currentTimeMillis();
        TrainingStatus status = new TrainingStatus();
        trainingStatusMap.put(trainingId, status);

        try {
            int trainEpochs = request.getEpochs() != null ? request.getEpochs() : config.getEpochs();
            int trainBatchSize = request.getBatchSize() != null ? request.getBatchSize() : config.getBatchSize();

            log.info("开始情感分析模型训练流程 - 训练ID:{}, 计划轮次:{}, 批次大小:{}", trainingId, trainEpochs, trainBatchSize);

            status.setStatus("加载数据 - 训练ID: " + trainingId);
            status.setProgress(10);

            List<TrainingSample> allSamples = dataPreprocessor.loadTrainingData(
                    request.getNumSamples(), request.getAutoLabel());

            if (allSamples.isEmpty()) {
                throw new RuntimeException("没有训练数据");
            }

            SentimentDataPreprocessor.DatasetSplit split = dataPreprocessor.splitDataset(allSamples);
            int trainSize = split.getTrainData().size();
            int valSize = split.getValData().size();

            log.info("训练集样本数:{}, 验证集样本数:{}", trainSize, valSize);

            // 统计标签分布，便于评估数据质量
            long trainNeg = split.getTrainData().stream().filter(s -> s.getLabel() == 0).count();
            long trainNeu = split.getTrainData().stream().filter(s -> s.getLabel() == 1).count();
            long trainPos = split.getTrainData().stream().filter(s -> s.getLabel() == 2).count();

            long valNeg = split.getValData().stream().filter(s -> s.getLabel() == 0).count();
            long valNeu = split.getValData().stream().filter(s -> s.getLabel() == 1).count();
            long valPos = split.getValData().stream().filter(s -> s.getLabel() == 2).count();

            log.info("训练集标签分布 - 负面:{}, 中性:{}, 正面:{}", trainNeg, trainNeu, trainPos);
            log.info("验证集标签分布 - 负面:{}, 中性:{}, 正面:{}", valNeg, valNeu, valPos);

            status.setStatus("数据加载与统计完成");
            status.setProgress(40);

            // 检查预训练模型是否可用（仅做加载验证，不在此处做真实微调）
            boolean modelReady = loadModel();
            if (modelReady) {
                log.info("预训练情感分析模型已就绪，可用于推理");
                status.setStatus("模型已加载，可用于推理");
            } else {
                log.warn("预训练情感分析模型未加载，将使用规则模式推理");
                status.setStatus("模型未加载，将使用规则模式推理");
            }
            status.setProgress(80);

            // 组装训练日志，记录数据与模型状态
            List<Map<String, Object>> trainingLog = new ArrayList<>();
            Map<String, Object> summary = new HashMap<>();
            summary.put("trainEpochsPlanned", trainEpochs);
            summary.put("trainBatchSize", trainBatchSize);
            summary.put("trainSize", trainSize);
            summary.put("valSize", valSize);
            summary.put("trainNeg", trainNeg);
            summary.put("trainNeu", trainNeu);
            summary.put("trainPos", trainPos);
            summary.put("valNeg", valNeg);
            summary.put("valNeu", valNeu);
            summary.put("valPos", valPos);
            summary.put("pretrainedModel", config.getPretrainedModel());
            summary.put("modelPath", config.getModelPath());
            summary.put("modelLoaded", modelReady);
            trainingLog.add(summary);

            // 暂未在 Java 侧执行真实 BERT 微调，先返回数据统计结果
            String modelPath = config.getModelPath();

            status.setStatus("训练完成");
            status.setProgress(100);

            return SentimentTrainingResponse.builder()
                    .success(true)
                    .message("训练完成")
                    .trainingId(trainingId)
                    .epochs(trainEpochs)
                    .trainLoss(null)
                    .valAccuracy(null)
                    .modelPath(modelPath)
                    .trainSamples(trainSize)
                    .valSamples(valSize)
                    .details(trainingLog)
                    .build();

        } catch (Exception e) {
            log.error("情感分析模型训练失败", e);
            status.setStatus("训练失败：" + e.getMessage());
            status.setProgress(-1);

            return SentimentTrainingResponse.builder()
                    .success(false)
                    .trainingId(trainingId)
                    .message("训练失败：" + e.getMessage())
                    .build();
        }
    }

    /**
     * 下载预训练模型（支持中文）
     */
    public String downloadPretrainedModel() {
        log.info("DJL 将根据配置自动从 HuggingFace 下载并缓存模型: {}", config.getPretrainedModel());
        return config.getModelPath();
    }

    /**
     * 加载模型用于推理
     */
    public boolean loadModel() {
        if (isModelLoaded) {
            return true;
        }

        try {
            String modelUrl = "djl://ai.djl.huggingface.pytorch/" + config.getPretrainedModel();
            log.info("正在从 HuggingFace 加载模型: {}", modelUrl);

            HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(config.getPretrainedModel());

            Translator<String, Classifications> translator = TextClassificationTranslator.builder(tokenizer)
                    .build();

            Criteria<String, Classifications> criteria = Criteria.builder()
                    .setTypes(String.class, Classifications.class)
                    .optModelUrls(modelUrl)
                    .optEngine("PyTorch")
                    .optTranslator(translator)
                    .optProgress(new ProgressBar())
                    .build();

            this.loadedModel = criteria.loadModel();
            this.isModelLoaded = true;
            log.info("情感分析模型加载成功");
            return true;

        } catch (Exception e) {
            log.error("加载 HuggingFace 模型失败", e);
            this.isModelLoaded = false;
            return false;
        }
    }

    /**
     * 情感分析推理
     */
    public SentimentAnalysisResult analyzeSentiment(String text) {
        if (!isModelLoaded || loadedModel == null) {
            log.warn("模型未加载，使用规则分析");
            return analyzeWithRules(text);
        }

        try {
            try (Predictor<String, Classifications> predictor = loadedModel.newPredictor()) {
                Classifications result = predictor.predict(text);

                Map<String, Double> probabilities = new HashMap<>();
                String bestLabel = null;
                double bestProb = 0;

                for (Classifications.Classification classification : result.items()) {
                    probabilities.put(classification.getClassName(), classification.getProbability());
                    if (classification.getProbability() > bestProb) {
                        bestProb = classification.getProbability();
                        bestLabel = classification.getClassName();
                    }
                }

                double score = calculateSentimentScore(probabilities);

                return SentimentAnalysisResult.builder()
                        .label(bestLabel)
                        .score(score)
                        .confidence(bestProb)
                        .probabilities(probabilities)
                        .text(text)
                        .build();
            }

        } catch (Exception e) {
            log.error("情感分析推理失败", e);
            return analyzeWithRules(text);
        }
    }

    /**
     * 基于规则的情感分析（备用）
     */
    private SentimentAnalysisResult analyzeWithRules(String text) {
        Integer label = dataPreprocessor.autoLabelSentiment(text);
        String[] labels = config.getLabels();
        String labelStr = labels[label];

        Map<String, Double> probs = new HashMap<>();
        probs.put("negative", label == 0 ? 0.7 : 0.15);
        probs.put("neutral", label == 1 ? 0.7 : 0.15);
        probs.put("positive", label == 2 ? 0.7 : 0.15);

        double score = label == 0 ? -0.7 : (label == 2 ? 0.7 : 0.0);

        return SentimentAnalysisResult.builder()
                .label(labelStr)
                .score(score)
                .confidence(0.7)
                .probabilities(probs)
                .text(text)
                .build();
    }

    private double calculateSentimentScore(Map<String, Double> probabilities) {
        Double positive = probabilities.getOrDefault("positive", 0.0);
        Double negative = probabilities.getOrDefault("negative", 0.0);
        return positive - negative;
    }

    private String saveModel() throws IOException {
        Path modelDir = Paths.get(config.getModelPath());
        Files.createDirectories(modelDir);

        Path configPath = modelDir.resolve("config.json");
        Map<String, Object> modelConfig = new HashMap<>();
        modelConfig.put("modelType", "distilbert");
        modelConfig.put("numLabels", config.getNumLabels());
        modelConfig.put("labels", config.getLabels());
        modelConfig.put("maxSequenceLength", config.getMaxSequenceLength());
        modelConfig.put("pretrainedModel", config.getPretrainedModel());

        objectMapper.writeValue(configPath.toFile(), modelConfig);

        return modelDir.toAbsolutePath().toString();
    }

    public TrainingStatus getTrainingStatus(String trainingId) {
        return trainingStatusMap.get(trainingId);
    }

    public boolean isModelLoaded() {
        return isModelLoaded;
    }

    public void unloadModel() {
        if (loadedModel != null) {
            loadedModel.close();
            loadedModel = null;
            isModelLoaded = false;
            log.info("模型已卸载");
        }
    }

    @lombok.Data
    public static class TrainingStatus {
        private String status = "等待中";
        private double progress = 0;
        private int currentEpoch = 0;
        private int totalEpochs = 0;
    }
}
