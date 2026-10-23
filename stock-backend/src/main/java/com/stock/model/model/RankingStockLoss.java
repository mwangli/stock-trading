// AI_GENERATE_START -
package com.stock.model.model;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.ndarray.types.DataType;
import ai.djl.training.loss.Loss;

/**
 * 成本感知横截面排序联合损失。
 * 同日批次内联合优化排序、超额收益、方向、下行分位和输出一致性。
 *
 * @author mwangli
 * @since 2026-10-23
 */
public class RankingStockLoss extends Loss {

    private final float rankingWeight;
    private final float returnWeight;
    private final float directionWeight;
    private final float downsideWeight;
    private final float consistencyWeight;
    private final float scaledCostThreshold;
    private final float neutralDirectionWeight;

    /**
     * 创建横截面排序联合损失。
     *
     * @param rankingWeight 排序损失权重
     * @param returnWeight 超额收益回归损失权重
     * @param directionWeight 成本感知方向损失权重
     * @param downsideWeight 下行分位损失权重
     * @param consistencyWeight 输出一致性损失权重
     * @param scaledCostThreshold 按收益缩放后的成本阈值
     * @param neutralDirectionWeight 中性样本方向损失权重
     */
    public RankingStockLoss(
            float rankingWeight,
            float returnWeight,
            float directionWeight,
            float downsideWeight,
            float consistencyWeight,
            float scaledCostThreshold,
            float neutralDirectionWeight) {
        super("ranking_stock_loss");
        this.rankingWeight = rankingWeight;
        this.returnWeight = returnWeight;
        this.directionWeight = directionWeight;
        this.downsideWeight = downsideWeight;
        this.consistencyWeight = consistencyWeight;
        this.scaledCostThreshold = scaledCostThreshold;
        this.neutralDirectionWeight = neutralDirectionWeight;
    }

    /**
     * 计算四输出联合损失。
     *
     * @param labels 真实标签，列顺序为排序收益、回归收益、方向和下行风险
     * @param predictions 模型输出，列顺序为排序分数、预测收益、上涨概率和下行分位
     * @return 标量损失
     */
    @Override
    public NDArray evaluate(NDList labels, NDList predictions) {
        NDArray label = labels.singletonOrThrow();
        NDArray prediction = predictions.singletonOrThrow();
        checkLabelShapes(label, prediction);

        NDArray rankingLoss = pairwiseRankingLoss(
                prediction.get(new NDIndex(":, 0")), label.get(new NDIndex(":, 0")));
        NDArray returnLoss = huberLoss(
                prediction.get(new NDIndex(":, 1")), label.get(new NDIndex(":, 1")), 0.1F);
        NDArray directionLoss = costAwareBinaryCrossEntropy(
                prediction.get(new NDIndex(":, 2")), label.get(new NDIndex(":, 2")));
        NDArray downsideLoss = quantileLoss(
                prediction.get(new NDIndex(":, 3")), label.get(new NDIndex(":, 3")), 0.8F);
        NDArray consistencyLoss = consistencyLoss(
                prediction.get(new NDIndex(":, 1")), prediction.get(new NDIndex(":, 2")));

        return rankingLoss.mul(rankingWeight)
                .add(returnLoss.mul(returnWeight))
                .add(directionLoss.mul(directionWeight))
                .add(downsideLoss.mul(downsideWeight))
                .add(consistencyLoss.mul(consistencyWeight));
    }

    private NDArray pairwiseRankingLoss(NDArray score, NDArray target) {
        NDArray scoreDiff = score.expandDims(1).sub(score.expandDims(0));
        NDArray targetDiff = target.expandDims(1).sub(target.expandDims(0));
        NDArray mask = targetDiff.abs().gt(scaledCostThreshold).toType(DataType.FLOAT32, false);
        NDArray sign = targetDiff.gt(0F).toType(DataType.FLOAT32, false).mul(2F).sub(1F);
        NDArray logistic = scoreDiff.mul(sign).mul(-1F).exp().add(1F).log().mul(mask);
        return logistic.sum().div(mask.sum().maximum(1F));
    }

    private NDArray huberLoss(NDArray prediction, NDArray label, float delta) {
        NDArray absoluteError = prediction.sub(label).abs();
        NDArray quadratic = absoluteError.minimum(delta);
        NDArray linear = absoluteError.sub(quadratic);
        return quadratic.square().mul(0.5F).add(linear.mul(delta)).mean();
    }

    private NDArray costAwareBinaryCrossEntropy(NDArray prediction, NDArray label) {
        NDArray safePrediction = prediction.clip(1.0E-7F, 1F - 1.0E-7F);
        NDArray binaryLabel = label.gt(0.5F).toType(DataType.FLOAT32, false);
        NDArray neutralMask = label.sub(0.5F).abs().lt(0.25F).toType(DataType.FLOAT32, false);
        NDArray weight = neutralMask.mul(neutralDirectionWeight)
                .add(neutralMask.mul(-1F).add(1F));
        NDArray positive = binaryLabel.mul(safePrediction.log());
        NDArray negative = binaryLabel.mul(-1F).add(1F)
                .mul(safePrediction.mul(-1F).add(1F).log());
        return positive.add(negative).mul(-1F).mul(weight).mean();
    }

    private NDArray quantileLoss(NDArray prediction, NDArray label, float quantile) {
        NDArray error = label.sub(prediction);
        return error.mul(quantile).maximum(error.mul(quantile - 1F)).mean();
    }

    private NDArray consistencyLoss(NDArray expectedReturn, NDArray upProbability) {
        NDArray centeredProbability = upProbability.mul(2F).sub(1F);
        return expectedReturn.mul(centeredProbability).mul(-1F).maximum(0F).mean();
    }
}
// AI_GENERATE_END -
