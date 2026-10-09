// AI_GENERATE_START -----
package com.stock.modelService.service;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.translator.TextClassificationTranslator;
import ai.djl.translate.Translator;
import com.stock.modelService.config.SentimentTrainingConfig;
import com.stock.modelService.domain.vo.SentimentAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Arrays;

import jakarta.annotation.PostConstruct;

/**
 * 情感分析模型训练与推理服务。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentTrainerService {

    private final SentimentTrainingConfig config;
    private final SentimentDataPreprocessor dataPreprocessor;
    private final SentimentOnnxShadowService sentimentOnnxShadowService;

    /**
     * 应用启动初始化
     *
     * 这里只负责打印当前情感模型相关配置，不主动加载模型，实际加载在首次使用时懒加载完成。
     * 模型根目录优先使用配置的 {@code models.sentiment.model-path}，未配置时使用
     * backend 模块下的 {@code backend/models/sentiment} 目录，
     * 自动兼容从项目根目录或 backend 目录启动应用的场景。
     */
    @PostConstruct
    public void init() {
        Path sentimentDir = resolveSentimentModelDir();
        log.info("情感分析模型根目录: {}", sentimentDir.toAbsolutePath());

        if (useRemoteModel()) {
            log.info("已配置从 HuggingFace 加载模型，将在首次使用情感分析服务时按需下载并缓存");
        } else {
            log.info("已配置使用本地模型目录，将尝试从本地加载情感分析模型");
        }
    }

    private ZooModel<String, Classifications> loadedModel = null;
    private boolean isModelLoaded = false;
    private LocalDateTime lastLoadedTime = null;

    /**
     * 解析情感模型所在的本地目录
     * <p>
     * 统一使用 backend 模块下的 {@code backend/models/sentiment} 作为情感模型目录。
     * 支持两种启动方式：
     * 1. 在项目根目录 ai-stock-trading 下运行：此时目录为 {@code ./backend/models/sentiment}
     * 2. 在 backend 目录下运行：此时目录为 {@code ./models/sentiment}
     * </p>
     *
     * @return 情感模型目录路径
     */
    private Path resolveSentimentModelDir() {
        String configuredPath = config.getModelPath();
        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            Path configured = Paths.get(configuredPath.trim());
            if (configured.isAbsolute()) {
                return configured.normalize();
            }
            Path cwd = Paths.get("").toAbsolutePath().normalize();
            return cwd.resolve(configured).normalize();
        }

        Path cwd = Paths.get("").toAbsolutePath().normalize();
        String cwdName = cwd.getFileName() != null ? cwd.getFileName().toString() : "";
        Path baseDir;
        if ("backend".equalsIgnoreCase(cwdName)) {
            // 在 backend 目录下启动：使用 ./models/sentiment
            baseDir = cwd.resolve("models").resolve("sentiment");
        } else {
            // 在项目根目录下启动：使用 ./backend/models/sentiment
            baseDir = cwd.resolve("backend").resolve("models").resolve("sentiment");
        }
        return baseDir.normalize();
    }

    /**
     * 加载模型用于推理
     * <p>
     * 加载顺序：
     * 1. **优先使用本地 models 目录**：尝试从 {@code models/sentiment}（即 {@link SentimentTrainingConfig#getModelPath()}）
     *    加载完整的 Hugging Face 模型文件（至少包含 {@code config.json} 与 {@code tokenizer.json}）。
     * 2. **本地目录缺失或不完整时**：如果配置开启了 {@code downloadPretrained}，则尝试通过
     *    {@code djl://ai.djl.huggingface.pytorch/<model-id>} 从远程 Hugging Face 仓库拉取模型并加载。
     * 3. **远程下载也失败时**：记录错误日志，标记模型未加载，后续情感分析自动回退为规则模式。
     * </p>
     *
     * @return true 表示模型已成功加载，false 表示加载失败（将回退到规则模式）
     */
    public boolean loadModel() {
        if (isModelLoaded) {
            return true;
        }
        try {
            String modelId = config.getPretrainedModel();
            Path modelPath = resolveSentimentModelDir();
            Files.createDirectories(modelPath);

            log.info("准备加载情感分析模型, modelId={}, modelPath={}, modelSource={}",
                    modelId, modelPath.toAbsolutePath(), config.getModelSource());

            String modelUrl;
            HuggingFaceTokenizer tokenizer;

            if (useLocalModel()) {
                // 仅在使用本地模型时尝试补全 tokenizer.json
                ensureLocalTokenizerJson(modelPath);
            }

            // 1. 优先尝试从本地 models/sentiment 目录加载
            boolean hasLocalModelDir = Files.isDirectory(modelPath);
            boolean hasLocalFiles = false;
            if (hasLocalModelDir) {
                try (var stream = Files.list(modelPath)) {
                    hasLocalFiles = stream.findAny().isPresent();
                }
                if (hasLocalFiles) {
                    try (var stream = Files.list(modelPath)) {
                        var fileNames = stream
                                .map(path -> path.getFileName().toString())
                                .toList();
                        log.info("本地情感模型目录存在且非空, 路径={}, 文件列表={}", modelPath.toAbsolutePath(), fileNames);
                    }
                } else {
                    log.info("本地情感模型目录存在但为空, 路径={}", modelPath.toAbsolutePath());
                }
            } else {
                log.warn("本地情感模型目录不存在, 预期路径={}", modelPath.toAbsolutePath());
            }

            boolean canUseLocalModel = hasLocalModelDir && hasLocalFiles;
            if (canUseLocalModel && useLocalModel()) {
                validateLocalModelFiles(modelPath);
                try {
                    modelUrl = modelPath.toUri().toString();
                    tokenizer = HuggingFaceTokenizer.newInstance(modelPath);
                    log.info("检测到本地情感模型目录且校验通过，尝试使用本地模型加载: {}", modelUrl);
                } catch (Exception localEx) {
                    log.error("从本地情感模型目录加载失败", localEx);
                    this.isModelLoaded = false;
                    return false;
                }
            } else if (useRemoteModel()) {
                // 2. 本地目录不可用或未配置本地优先，尝试从 Hugging Face 远程加载
                log.warn("本地情感模型目录不可用(目录存在: {}, 含文件: {})，将尝试从 Hugging Face 远程加载: {}, modelPath={}",
                        hasLocalModelDir, hasLocalFiles, modelId, modelPath.toAbsolutePath());
                tokenizer = HuggingFaceTokenizer.newInstance(modelId);
                modelUrl = "djl://ai.djl.huggingface.pytorch/" + modelId;
            } else {
                // 3. 本地目录不可用且未启用远程下载，直接回退规则模式
                log.warn("本地情感模型目录不可用且已禁用在线下载，将直接使用规则模式分析, modelPath={}", modelPath.toAbsolutePath());
                this.isModelLoaded = false;
                return false;
            }

            Map<String, Object> arguments = new HashMap<>();
            arguments.put("labels", Arrays.asList(config.getLabels()));
            Translator<String, Classifications> translator =
                    TextClassificationTranslator.builder(tokenizer, arguments).build();

            Criteria<String, Classifications> criteria = Criteria.builder()
                    .setTypes(String.class, Classifications.class)
                    .optModelUrls(modelUrl)
                    .optEngine("PyTorch")
                    .optTranslator(translator)
                    .optProgress(new ProgressBar())
                    .build();

            this.loadedModel = criteria.loadModel();
            this.isModelLoaded = true;
            this.lastLoadedTime = LocalDateTime.now();
            log.info("情感分析模型加载成功, modelUrl={}", modelUrl);
            return true;

        } catch (Exception e) {
            log.error("加载情感分析模型失败，将回退到规则模式", e);
            this.isModelLoaded = false;
            return false;
        }
    }

    /**
     * 确保本地情感模型目录下存在 tokenizer.json
     * <p>
     * 如果缺失，则尝试调用项目根目录下 .tmp/export_finbert_tokenizer.py 脚本，
     * 自动从 Hugging Face 下载并导出 tokenizer.json，然后再复制到 models/sentiment 目录。
     * 该逻辑仅在本地缺失 tokenizer.json 且运行环境已安装 python 时生效。
     * </p>
     *
     * @param modelPath 情感模型本地目录
     */
    private void ensureLocalTokenizerJson(Path modelPath) {
        try {
            Path tokenizerPath = modelPath.resolve("tokenizer.json");
            if (Files.exists(tokenizerPath)) {
                return;
            }

            Path projectRoot = Paths.get("").toAbsolutePath().normalize();
            Path scriptPath = projectRoot.resolve(".tmp").resolve("export_finbert_tokenizer.py");
            if (!Files.exists(scriptPath)) {
                log.warn("未找到自动生成 tokenizer.json 的脚本: {}", scriptPath.toAbsolutePath());
                return;
            }

            log.info("检测到缺失 tokenizer.json，尝试通过 Python 脚本自动生成: {}", scriptPath.toAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder("python", scriptPath.toString());
            pb.directory(projectRoot.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("Python 脚本执行失败，退出码: {}", exitCode);
                return;
            }

            // 脚本会将 tokenizer.json 保存在 .tmp/finbert-tokenizer 目录下，这里检查并复制到模型目录
            Path generatedPath = projectRoot.resolve(".tmp")
                    .resolve("finbert-tokenizer")
                    .resolve("tokenizer.json");
            if (Files.exists(generatedPath)) {
                Files.createDirectories(modelPath);
                Files.copy(generatedPath, tokenizerPath);
                log.info("已通过 Python 脚本生成并复制 tokenizer.json 到: {}", tokenizerPath.toAbsolutePath());
            } else {
                log.warn("Python 脚本执行完成但未找到生成的 tokenizer.json, 预期路径: {}", generatedPath.toAbsolutePath());
            }
        } catch (Exception ex) {
            log.error("自动生成 tokenizer.json 过程中发生异常，将继续后续加载流程", ex);
        }
    }

    private boolean useLocalModel() {
        String source = config.getModelSource();
        if (source == null || source.trim().isEmpty()) {
            return !config.isDownloadPretrained();
        }
        return "local".equalsIgnoreCase(source.trim());
    }

    private boolean useRemoteModel() {
        String source = config.getModelSource();
        if (source == null || source.trim().isEmpty()) {
            return config.isDownloadPretrained();
        }
        return "huggingface".equalsIgnoreCase(source.trim());
    }

    private void validateLocalModelFiles(Path modelPath) throws IOException {
        List<String> required = List.of("config.json", "tokenizer.json", "serving.properties");
        List<String> missing = new ArrayList<>();
        for (String fileName : required) {
            if (!Files.exists(modelPath.resolve(fileName))) {
                missing.add(fileName);
            }
        }

        boolean hasModelWeights = false;
        try (var stream = Files.list(modelPath)) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString();
                if (name.endsWith(".safetensors") || name.equals("pytorch_model.bin") || name.endsWith(".bin")
                        || name.endsWith(".pt")) {
                    hasModelWeights = true;
                    break;
                }
            }
        }

        if (!hasModelWeights) {
            missing.add("model weights (.pt TorchScript, .safetensors, or pytorch_model.bin)");
        }

        if (!missing.isEmpty()) {
            throw new IOException("本地情感模型文件不完整，缺少: " + String.join(", ", missing));
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
                double normalizedScore = calculateNormalizedScore(bestLabel, bestProb);

                return SentimentAnalysisResult.builder()
                        .label(bestLabel)
                        .score(score)
                        .normalizedScore(normalizedScore)
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
     * 使用真实情感模型执行交易候选所需的严格推理。
     * 模型无法加载或单次推理失败时直接抛出异常，禁止用规则结果替代。
     *
     * @param text 新闻或公告文本
     * @return 情感标签、连续分数和置信度
     */
    public SentimentAnalysisResult analyzeSentimentRequired(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("情感分析文本不能为空");
        }
        if ((!isModelLoaded || loadedModel == null) && !loadModel()) {
            throw new IllegalStateException("情感分析模型未加载，停止生成交易候选");
        }
        try (Predictor<String, Classifications> predictor = loadedModel.newPredictor()) {
            Classifications classifications = predictor.predict(text);
            Map<String, Double> probabilities = new HashMap<>();
            String bestLabel = null;
            double bestProbability = 0D;
            for (Classifications.Classification classification : classifications.items()) {
                probabilities.put(classification.getClassName(), classification.getProbability());
                if (classification.getProbability() > bestProbability) {
                    bestProbability = classification.getProbability();
                    bestLabel = classification.getClassName();
                }
            }
            SentimentAnalysisResult result = SentimentAnalysisResult.builder()
                    .label(bestLabel)
                    .score(calculateSentimentScore(probabilities))
                    .normalizedScore(calculateNormalizedScore(bestLabel, bestProbability))
                    .confidence(bestProbability)
                    .probabilities(probabilities)
                    .text(text)
                    .build();
            sentimentOnnxShadowService.compare(text, result);
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("情感分析模型推理失败", exception);
        }
    }

    /**
     * 查询情感模型是否已成功加载。
     *
     * @return true 表示当前可执行真实模型推理
     */
    public boolean isModelLoaded() {
        return isModelLoaded;
    }

    /**
     * 卸载当前情感模型并释放底层资源。
     */
    public void unloadModel() {
        if (loadedModel != null) {
            loadedModel.close();
            loadedModel = null;
        }
        isModelLoaded = false;
        lastLoadedTime = null;
        log.info("情感分析模型已卸载");
    }

    private SentimentAnalysisResult analyzeWithRules(String text) {
        Integer label = dataPreprocessor.autoLabelSentiment(text);
        String[] labels = config.getLabels();
        String labelText = labels[label];
        Map<String, Double> probabilities = new HashMap<>();
        probabilities.put("neutral", label == 0 ? 0.7D : 0.15D);
        probabilities.put("positive", label == 1 ? 0.7D : 0.15D);
        probabilities.put("negative", label == 2 ? 0.7D : 0.15D);
        double score = label == 2 ? -0.7D : label == 1 ? 0.7D : 0D;
        return SentimentAnalysisResult.builder()
                .label(labelText)
                .score(score)
                .normalizedScore(calculateNormalizedScore(labelText, 0.7D))
                .confidence(0.7D)
                .probabilities(probabilities)
                .text(text)
                .build();
    }

    private double calculateNormalizedScore(String label, double probability) {
        if ("negative".equalsIgnoreCase(label)) {
            return 40D - probability * 40D;
        }
        if ("neutral".equalsIgnoreCase(label)) {
            return 40D + probability * 20D;
        }
        if ("positive".equalsIgnoreCase(label)) {
            return 60D + probability * 40D;
        }
        return 50D;
    }

    private double calculateSentimentScore(Map<String, Double> probabilities) {
        if (probabilities == null || probabilities.isEmpty()) {
            return 0D;
        }
        double positive = probabilities.entrySet().stream()
                .filter(entry -> "positive".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(0D);
        double negative = probabilities.entrySet().stream()
                .filter(entry -> "negative".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(0D);
        return Math.max(-1D, Math.min(1D, positive - negative));
    }

    /**
     * 返回上次模型加载成功的时间
     */
    public LocalDateTime getLastLoadedTime() {
        return lastLoadedTime;
    }

    /**
     * 情感分析并返回 API 所需的详细 Map（含 label、score、confidence、probabilities、text、modelLoaded）
     * 模型未加载或异常时自动降级为规则分析并设置 modelLoaded=false
     */
    public Map<String, Object> analyzeSentimentWithDetails(String text) {
        if (text == null || text.trim().isEmpty()) {
            return buildDetailsMap(
                    SentimentAnalysisResult.builder()
                            .label("neutral")
                            .score(0.0)
                            .confidence(0.5)
                            .probabilities(Map.of("positive", 0.33, "neutral", 0.34, "negative", 0.33))
                            .text(text != null ? text : "")
                            .build(),
                    false
            );
        }
        if (!isModelLoaded) {
            loadModel();
        }
        try {
            SentimentAnalysisResult result = analyzeSentiment(text);
            return buildDetailsMap(result, isModelLoaded);
        } catch (Exception e) {
            log.error("情感分析失败，降级为规则分析", e);
            return buildDetailsMap(analyzeWithRules(text), false);
        }
    }

    private Map<String, Object> buildDetailsMap(SentimentAnalysisResult result, boolean modelLoaded) {
        Map<String, Object> response = new HashMap<>();
        response.put("label", result.getLabel());
        response.put("score", result.getScore());
        response.put("normalizedScore", result.getNormalizedScore());
        response.put("confidence", result.getConfidence());
        response.put("probabilities", result.getProbabilities() != null ? result.getProbabilities() : Map.of());
        response.put("text", result.getText() != null ? result.getText() : "");
        response.put("modelLoaded", modelLoaded);
        return response;
    }

}
// AI_GENERATE_END -----
