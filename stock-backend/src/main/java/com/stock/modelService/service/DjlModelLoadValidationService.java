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

import java.nio.file.Path;

/**
 * 使用与线上推理一致的 DJL 配置校验固定情感模型制品是否可加载。
 * 模型格式转换由制品构建流程完成，运行时不再执行外部转换命令。
 *
 * @author mwangli
 * @since 2026-10-20
 */
@Slf4j
@Service
public class DjlModelLoadValidationService {

    /**
     * 在固定本地制品切换为正式目录前，用 DJL 实际加载情感模型。
     *
     * @param modelDirectory 待验证的固定模型目录
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
}
// AI_GENERATE_END ------