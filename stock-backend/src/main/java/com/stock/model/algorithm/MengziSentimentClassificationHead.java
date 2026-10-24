// AI_GENERATE_START -
package com.stock.model.algorithm;

import ai.djl.nn.Activation;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.nn.norm.Dropout;

/**
 * Mengzi 金融编码器的三分类头。
 * 输入为冻结编码器输出的 768 维 CLS 表示，首版只训练 768 到 256 到 3 的分类参数。
 *
 * @author mwangli
 * @since 2026-10-24
 */
public class MengziSentimentClassificationHead extends SequentialBlock {

    /** 编码器 CLS 向量维度。 */
    public static final int ENCODER_OUTPUT_SIZE = 768;

    /** 隐藏层维度。 */
    public static final int HIDDEN_SIZE = 256;

    /** 情感类别数。 */
    public static final int CLASS_COUNT = 3;

    /**
     * 创建首版冻结编码器分类头。
     */
    public MengziSentimentClassificationHead() {
        add(Linear.builder().setUnits(HIDDEN_SIZE).build());
        add(Activation::gelu);
        add(Dropout.builder().optRate(0.1F).build());
        add(Linear.builder().setUnits(CLASS_COUNT).build());
    }
}
// AI_GENERATE_END -
