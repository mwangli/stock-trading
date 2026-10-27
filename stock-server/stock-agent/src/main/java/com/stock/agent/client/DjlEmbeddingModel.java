// AI_GENERATE_START---------------
package com.stock.agent.client;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.translator.TextEmbeddingTranslator;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import com.stock.agent.config.AgentProperties;
import com.stock.agent.service.EmbeddingArtifactService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 DJL PyTorch 的固定本地目录 Embedding 模型适配器。
 * 模型目录由发布流程固定，加载完成后由 DJL 常驻内存。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Slf4j
public class DjlEmbeddingModel implements EmbeddingModel {

    private final AgentProperties.EmbeddingProperties properties;
    private final EmbeddingArtifactService artifactService;
    private final Object loadMonitor = new Object();

    private volatile ZooModel<String, float[]> model;
    private volatile String lastLoadError;

    /**
     * 创建 Embedding 适配器。
     *
     * @param properties Embedding 配置
     * @param artifactService 本地模型制品服务
     */
    public DjlEmbeddingModel(AgentProperties.EmbeddingProperties properties,
                             EmbeddingArtifactService artifactService) {
        this.properties = properties;
        this.artifactService = artifactService;
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
            throw new IllegalStateException("Embedding 推理失败", exception);
        }
    }

    /**
     * 批量生成文本向量。
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
     * 返回模型是否已经加载。
     *
     * @return true 表示模型可执行推理
     */
    public boolean isReady() {
        return model != null;
    }

    /**
     * 初始化并从固定本地目录加载模型。
     *
     * @throws IllegalStateException 模型制品缺失或加载失败
     */
    public void ensureReady() {
        getOrLoadModel();
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
            Path modelDirectory = null;
            try {
                modelDirectory = artifactService.resolve(
                        properties.getModel(), properties.getModelVersion(), properties.getModelPath());
                HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.builder()
                        .optTokenizerPath(modelDirectory)
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
                        .optModelUrls(modelDirectory.toUri().toString())
                        .optEngine("PyTorch")
                        .optTranslator(translator)
                        .build()
                        .loadModel();
                lastLoadError = null;
                log.info("本地 Embedding 模型加载成功: model={}, version={}, dimension={}, directory={}",
                        properties.getModel(), properties.getModelVersion(), properties.getDimension(), modelDirectory);
                return model;
            } catch (Exception exception) {
                lastLoadError = exception.getClass().getSimpleName() + ": " + exception.getMessage();
                log.error("本地 Embedding 模型加载底层异常: model={}, version={}, directory={}, errorType={}, cause={}",
                        properties.getModel(), properties.getModelVersion(), modelDirectory,
                        exception.getClass().getName(), describeCauseChain(exception), exception);
                throw new IllegalStateException("本地 Embedding 模型加载失败: "
                        + properties.getModel() + "/" + properties.getModelVersion(), exception);
            }
        }
    }

    private String describeCauseChain(Throwable exception) {
        List<String> causes = new ArrayList<>();
        Throwable current = exception;
        while (current != null && causes.size() < 12) {
            causes.add(current.getClass().getName() + ": " + current.getMessage());
            current = current.getCause();
        }
        return String.join(" <- ", causes);
    }

    /**
     * 关闭模型并释放 PyTorch 原生资源。
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
// AI_GENERATE_END--------------
