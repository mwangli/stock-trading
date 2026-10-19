// AI_GENERATE_START ------
package com.stock.modelService.service;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.translator.TextClassificationTranslator;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import com.stock.modelService.config.SentimentTrainingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.HexFormat;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 调用 DJL Hugging Face Converter 将情感模型转换为 DJL PyTorch 可加载的 TorchScript 制品。
 * 转换命令失败、超时或未生成权重文件时，初始化流程必须失败，不得把原始模型误标记为可用。
 *
 * @author mwangli
 * @since 2026-10-19
 */
@Slf4j
@Service
public class DjlModelConverterService {

    private static final Pattern SHA256_PATTERN = Pattern.compile("[a-fA-F0-9]{64}");

    /**
     * 在暂存目录中执行情感模型转换，并将生成的 TorchScript 权重归一化为 model.pt。
     *
     * @param sourceDirectory Hugging Face 原始模型目录
     * @param config 情感模型配置
     * @throws IOException 命令不可执行或转换产物无效
     * @throws InterruptedException 转换进程被中断
     */
    public void convertSentiment(Path sourceDirectory, SentimentTrainingConfig config)
            throws IOException, InterruptedException {
        Path preconvertedWeight;
        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            preconvertedWeight = paths.filter(Files::isRegularFile)
                    .filter(path -> isPreconvertedWeight(path.getFileName().toString()))
                    .findFirst().orElse(null);
        }
        if (preconvertedWeight != null) {
            Path weight = preconvertedWeight;
            String expectedSha256 = config.getPretrainedModelSha256();
            if (expectedSha256 != null && !expectedSha256.isBlank()) {
                if (!SHA256_PATTERN.matcher(expectedSha256).matches()
                        || !expectedSha256.equalsIgnoreCase(sha256(weight))) {
                    throw new IOException("预转换 model.pt SHA-256 校验失败: " + weight);
                }
                log.info("[模型初始化][来源校验] model.pt SHA-256 校验通过: {}", weight);
            } else {
                log.warn("[模型初始化][来源校验] 未配置预转换 model.pt SHA-256，仅依赖固定 revision 和 DJL 加载校验: {}",
                        weight);
            }
            log.info("[模型初始化][转换跳过] 已发现预转换 TorchScript 权重: directory={}", sourceDirectory);
            return;
        }
        Path outputDirectory = sourceDirectory.resolve(".djl-converted").normalize();
        Files.createDirectories(outputDirectory);
        List<String> command = new ArrayList<>();
        command.add(config.getConverterCommand());
        command.add("-m");
        command.add(sourceDirectory.toString());
        command.add("-o");
        command.add(outputDirectory.toString());
        command.add("-f");
        command.add(config.getConverterOutputFormat());

        Path logFile = outputDirectory.resolve("converter.log");
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
        } catch (IOException exception) {
            throw new IOException("DJL Converter 不可用，请安装并配置 djl-convert: "
                    + config.getConverterCommand(), exception);
        }
        boolean completed = process.waitFor(config.getConverterTimeoutSeconds(), TimeUnit.SECONDS);
        String output = Files.exists(logFile)
                ? Files.readString(logFile, java.nio.charset.StandardCharsets.UTF_8) : "";
        if (!completed) {
            process.destroyForcibly();
            throw new IOException("DJL Converter 执行超时，elapsedSeconds="
                    + Duration.ofSeconds(config.getConverterTimeoutSeconds()));
        }
        if (process.exitValue() != 0) {
            throw new IOException("DJL Converter 执行失败，exitCode=" + process.exitValue()
                    + ", output=" + truncate(output));
        }
        log.info("[模型初始化][转换完成] directory={}, output={}",
                sourceDirectory, outputDirectory);

        Path convertedWeight;
        try (Stream<Path> paths = Files.walk(outputDirectory)) {
            convertedWeight = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pt"))
                    .sorted(Comparator.comparing(Path::toString))
                    .findFirst()
                    .orElse(null);
        }
        if (convertedWeight == null) {
            throw new IOException("DJL Converter 未生成 TorchScript .pt 权重: " + outputDirectory);
        }
        Files.copy(convertedWeight, sourceDirectory.resolve("model.pt"),
                StandardCopyOption.REPLACE_EXISTING);
        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString().toLowerCase();
                if ((name.endsWith(".bin") || name.endsWith(".safetensors"))
                        && !"model.pt".equals(name)) {
                    Files.deleteIfExists(path);
                }
            }
        }
        deleteDirectory(outputDirectory);
        log.info("DJL Converter 情感模型转换完成: source={}, weight={}",
                sourceDirectory, sourceDirectory.resolve("model.pt"));
    }

    /**
     * 在制品尚未切换为正式目录前，用 DJL 实际加载转换结果。
     *
     * @param modelDirectory 待验证模型目录
     * @param config 情感模型配置
     * @throws Exception DJL 无法构建 Tokenizer、Translator 或模型时抛出
     */
    public void validateSentimentLoad(Path modelDirectory, SentimentTrainingConfig config) throws Exception {
        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.builder()
                .optTokenizerPath(modelDirectory)
                .optTruncation(true)
                .optMaxLength(config.getMaxSequenceLength())
                .build();
        TextClassificationTranslator translator = TextClassificationTranslator.builder(tokenizer)
                .optIncludeTokenTypes(true)
                .build();
        Criteria<String, Classifications> criteria = Criteria.builder()
                .setTypes(String.class, Classifications.class)
                .optModelUrls(modelDirectory.toUri().toString())
                .optEngine("PyTorch")
                .optTranslator(translator)
                .build();
        try (ZooModel<String, Classifications> ignored = criteria.loadModel()) {
            log.info("[模型初始化][加载校验完成] directory={}", modelDirectory);
        }
    }

    private String truncate(String output) {
        return output == null ? "" : output.length() <= 2000 ? output : output.substring(output.length() - 2000);
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[1024 * 1024];
                int length;
                while ((length = input.read(buffer)) >= 0) {
                    if (length > 0) {
                        digest.update(buffer, 0, length);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private boolean isPreconvertedWeight(String fileName) {
        return "model.pt".equalsIgnoreCase(fileName)
                || "sentiment-finbert.pt".equalsIgnoreCase(fileName);
    }

    private void deleteDirectory(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
// AI_GENERATE_END ------
