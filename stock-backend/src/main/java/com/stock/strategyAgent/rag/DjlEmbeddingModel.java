// AI_GENERATE_START -
package com.stock.strategyAgent.rag;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.translator.TextEmbeddingTranslator;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import com.stock.strategyAgent.config.AgentProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 DJL PyTorch 的本地文本 Embedding 模型适配器。
 * 适配 sentence-transformers 格式的 BGE 模型，并向 LangChain4j 暴露类型化 EmbeddingModel。
 * 模型采用惰性加载，首次真正执行 RAG 或 Memory 向量化时才占用模型内存。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Slf4j
public class DjlEmbeddingModel implements EmbeddingModel {

    private final AgentProperties.EmbeddingProperties properties;
    private final Object loadMonitor = new Object();

    private volatile ZooModel<String, float[]> model;
    private volatile String lastLoadError;

    /**
     * 创建本地 DJL Embedding 适配器。
     *
     * @param properties Embedding 配置
     */
    public DjlEmbeddingModel(AgentProperties.EmbeddingProperties properties) {
        this.properties = properties;
    }

    /**
     * 生成单条文本向量。
     *
     * @param text 待向量化文本
     * @return Embedding 响应
     */
    @Override
    public Response<Embedding> embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Embedding 文本不能为空");
        }
        try (Predictor<String, float[]> predictor = getOrLoadModel().newPredictor()) {
            return Response.from(Embedding.from(predictor.predict(text)));
        } catch (Exception exception) {
            lastLoadError = exception.getMessage();
            throw new IllegalStateException("本地 DJL Embedding 推理失败", exception);
        }
    }

    /**
     * 批量生成文本向量。
     * 当前 RAG 和 Memory 使用单条调用，批量接口保留 LangChain4j 契约并逐条复用同一模型。
     *
     * @param textSegments 文本片段
     * @return Embedding 列表响应
     */
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        if (textSegments == null || textSegments.isEmpty()) {
            return Response.from(List.of());
        }
        List<Embedding> embeddings = new ArrayList<>(textSegments.size());
        for (TextSegment textSegment : textSegments) {
            embeddings.add(embed(textSegment.text()).content());
        }
        return Response.from(embeddings);
    }

    /**
     * 返回模型名称。
     *
     * @return 模型名称
     */
    @Override
    public String modelName() {
        return properties.getModel();
    }

    /**
     * 返回本地模型是否已经完成加载。
     *
     * @return true 表示模型可执行推理
     */
    public boolean isReady() {
        return model != null;
    }

    /**
     * 返回最近一次加载或推理错误。
     *
     * @return 错误摘要
     */
    public String lastLoadError() {
        return lastLoadError;
    }

    private ZooModel<String, float[]> getOrLoadModel() {
        ZooModel<String, float[]> current = model;
        if (current != null) {
            return current;
        }
        synchronized (loadMonitor) {
            if (model != null) {
                return model;
            }
            try {
                Path modelPath = Paths.get(properties.getModelPath()).toAbsolutePath().normalize();
                requireModelFile(modelPath, "config.json");
                requireModelFile(modelPath, "pytorch_model.bin");
                HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.builder()
                        .optTokenizerPath(modelPath)
                        .optTruncation(true)
                        .optMaxLength(properties.getMaxSequenceLength())
                        .build();
                TextEmbeddingTranslator translator = TextEmbeddingTranslator.builder(tokenizer)
                        .optPoolingMode(properties.getPoolingMode())
                        .optNormalize(properties.isNormalize())
                        .optIncludeTokenTypes(true)
                        .build();
                model = Criteria.<String, float[]>builder()
                        .setTypes(String.class, float[].class)
                        .optModelUrls(modelPath.toUri().toString())
                        .optEngine("PyTorch")
                        .optTranslator(translator)
                        .build()
                        .loadModel();
                lastLoadError = null;
                log.info("本地 DJL Embedding 模型加载成功: model={}, path={}, dimension={}, pooling={}, normalize={}",
                        properties.getModel(), modelPath, properties.getDimension(),
                        properties.getPoolingMode(), properties.isNormalize());
                return model;
            } catch (Exception exception) {
                lastLoadError = exception.getMessage();
                throw new IllegalStateException("本地 DJL Embedding 模型加载失败: " + properties.getModelPath(), exception);
            }
        }
    }

    private void requireModelFile(Path modelPath, String fileName) {
        if (!Files.isRegularFile(modelPath.resolve(fileName))) {
            throw new IllegalStateException("本地 Embedding 模型缺少文件: " + modelPath.resolve(fileName));
        }
    }

    /**
     * 关闭本地模型并释放 PyTorch 原生资源。
     */
    @PreDestroy
    public void close() {
        ZooModel<String, float[]> current = model;
        model = null;
        if (current != null) {
            current.close();
        }
    }
}
// AI_GENERATE_END -
