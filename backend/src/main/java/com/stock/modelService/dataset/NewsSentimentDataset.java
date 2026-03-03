package com.stock.modelService.dataset;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.training.dataset.RandomAccessDataset;
import ai.djl.training.dataset.Record;
import ai.djl.util.Progress;
import com.stock.modelService.dto.TrainingSample;

import java.io.IOException;
import java.util.List;

/**
 * 情感分析训练数据集
 * 将 {@link TrainingSample} 文本和标签转换为 BERT 可用的输入张量。
 */
public class NewsSentimentDataset extends RandomAccessDataset {

    private final List<TrainingSample> samples;
    private final HuggingFaceTokenizer tokenizer;
    private final int maxLength;

    protected NewsSentimentDataset(Builder builder) throws IOException {
        super(builder);
        this.samples = builder.samples;
        this.tokenizer = builder.tokenizer;
        this.maxLength = builder.maxLength;
    }

    @Override
    public Record get(NDManager manager, long index) {
        TrainingSample sample = samples.get((int) index);

        // 使用 HuggingFace tokenizer 编码文本
        Encoding encoding = tokenizer.encode(sample.getText());

        long[] inputIds = truncateOrPad(encoding.getIds(), maxLength, 0L);
        long[] attentionMask = truncateOrPad(encoding.getAttentionMask(), maxLength, 0L);

        NDArray inputIdsArray = manager.create(inputIds);
        NDArray attentionMaskArray = manager.create(attentionMask);

        // 将 label 转成单个标量
        NDArray labelArray = manager.create(new long[]{sample.getLabel()});

        NDList data = new NDList(inputIdsArray, attentionMaskArray);
        NDList labels = new NDList(labelArray);
        return new Record(data, labels);
    }

    @Override
    public long size() {
        return samples.size();
    }

    @Override
    protected long availableSize() {
        return samples.size();
    }

    @Override
    public void prepare(Progress progress) {
        // 当前数据集已在内存中，无需额外预处理
    }

    private long[] truncateOrPad(long[] src, int maxLen, long padValue) {
        long[] out = new long[maxLen];
        int len = Math.min(src.length, maxLen);
        System.arraycopy(src, 0, out, 0, len);
        if (len < maxLen) {
            for (int i = len; i < maxLen; i++) {
                out[i] = padValue;
            }
        }
        return out;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends BaseBuilder<Builder> {
        private List<TrainingSample> samples;
        private HuggingFaceTokenizer tokenizer;
        private int maxLength = 128;

        public Builder setSamples(List<TrainingSample> samples) {
            this.samples = samples;
            return this;
        }

        public Builder setTokenizer(HuggingFaceTokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public Builder setMaxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public NewsSentimentDataset build() throws IOException {
            if (samples == null || samples.isEmpty()) {
                throw new IllegalArgumentException("samples 不能为空");
            }
            if (tokenizer == null) {
                throw new IllegalArgumentException("tokenizer 不能为空");
            }
            return new NewsSentimentDataset(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}

