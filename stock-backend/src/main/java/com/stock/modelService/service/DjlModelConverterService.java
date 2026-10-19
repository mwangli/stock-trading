// AI_GENERATE_START --
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
            throw new IOException("DJL Converter 执行超时: "
                    + Duration.ofSeconds(config.getConverterTimeoutSeconds()));
        }
        if (process.exitValue() != 0) {
            throw new IOException("DJL Converter 执行失败，exitCode=" + process.exitValue()
                    + ", output=" + truncate(output));
        }

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
            log.info("DJL Converter 产物实际加载校验成功: directory={}", modelDirectory);
        }
    }

    private String truncate(String output) {
        return output == null ? "" : output.length() <= 2000 ? output : output.substring(output.length() - 2000);
    }

    private void deleteDirectory(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
// AI_GENERATE_END --
