package com.stock.modelService.test;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Translator;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.translator.TextClassificationTranslator;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

/**
 * 情感分析模型加载测试
 */
public class SentimentModelTest {
    
    public static void main(String[] args) {
        try {
            System.out.println("🚀 开始测试情感分析模型加载...");
            
            // 模型路径
            Path modelPath = Paths.get("D:/ai-stock-trading/backend/models/sentiment");
            System.out.println("模型路径: " + modelPath.toAbsolutePath());
            
            // 验证模型目录存在
            if (!modelPath.toFile().exists()) {
                System.err.println("❌ 模型目录不存在: " + modelPath.toAbsolutePath());
                return;
            }
            
            // 验证必需文件存在
            String[] requiredFiles = {"sentiment.pt", "tokenizer.json", "config.json", "serving.properties"};
            for (String file : requiredFiles) {
                Path filePath = modelPath.resolve(file);
                if (!filePath.toFile().exists()) {
                    System.err.println("❌ 必需文件缺失: " + filePath.toAbsolutePath());
                    return;
                } else {
                    System.out.println("✅ 文件存在: " + file);
                }
            }
            
            // 加载 tokenizer
            System.out.println("正在加载 tokenizer...");
            HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(modelPath);
            System.out.println("✅ Tokenizer 加载成功");
            
            // 创建 translator
            System.out.println("正在创建 translator...");
            Map<String, Object> arguments = Map.of("labels", Arrays.asList("neutral", "positive", "negative"));
            Translator<String, Classifications> translator = 
                TextClassificationTranslator.builder(tokenizer, arguments).build();
            System.out.println("✅ Translator 创建成功");
            
            // 构建 Criteria
            System.out.println("正在构建模型加载条件...");
            Criteria<String, Classifications> criteria = Criteria.builder()
                .setTypes(String.class, Classifications.class)
                .optModelUrls(modelPath.toUri().toString())
                .optEngine("PyTorch")
                .optTranslator(translator)
                .build();
            System.out.println("✅ Criteria 构建成功");
            
            // 加载模型
            System.out.println("正在加载模型...");
            ZooModel<String, Classifications> model = criteria.loadModel();
            System.out.println("✅ 模型加载成功!");
            
            // 测试推理
            System.out.println("正在测试推理...");
            try (Predictor<String, Classifications> predictor = model.newPredictor()) {
                Classifications result = predictor.predict("股价上涨了10%，市场表现良好！");
                System.out.println("✅ 推理成功!");
                System.out.println("结果: " + result.toString());
            }
            
            // 关闭模型
            model.close();
            System.out.println("✅ 测试完成！模型加载和推理功能正常。");
            
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}