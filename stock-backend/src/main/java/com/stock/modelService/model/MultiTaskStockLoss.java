// AI_GENERATE_START --
package com.stock.modelService.model;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.training.loss.Loss;

/**
 * 股票多任务组合损失。
 * 收益率和下行风险使用 Huber Loss，方向概率使用 Binary Cross Entropy，
 * 避免异常收益和任务量纲差异主导训练。
 *
 * @author mwangli
 * @since 2026-10-08
 */
public class MultiTaskStockLoss extends Loss {

    private final float returnWeight;
    private final float directionWeight;
    private final float downsideWeight;

    /**
     * 创建多任务损失。
     *
     * @param returnWeight 收益率任务权重
     * @param directionWeight 方向概率任务权重
     * @param downsideWeight 下行风险任务权重
     */
    public MultiTaskStockLoss(float returnWeight, float directionWeight, float downsideWeight) {
        super("multi_task_stock_loss");
        this.returnWeight = returnWeight;
        this.directionWeight = directionWeight;
        this.downsideWeight = downsideWeight;
    }

    /**
     * 计算三个任务的加权组合损失。
     *
     * @param labels 真实标签，形状为 batch x 3
     * @param predictions 模型输出，形状为 batch x 3
     * @return 标量损失
     */
    @Override
    public NDArray evaluate(NDList labels, NDList predictions) {
        NDArray label = labels.singletonOrThrow();
        NDArray prediction = predictions.singletonOrThrow();
        checkLabelShapes(label, prediction);

        NDArray returnLoss = huberLoss(
                prediction.get(new NDIndex(":, 0")),
                label.get(new NDIndex(":, 0")), 0.1F).mul(returnWeight);
        NDArray directionLoss = binaryCrossEntropy(
                prediction.get(new NDIndex(":, 1")),
                label.get(new NDIndex(":, 1"))).mul(directionWeight);
        NDArray downsideLoss = huberLoss(
                prediction.get(new NDIndex(":, 2")),
                label.get(new NDIndex(":, 2")), 0.1F).mul(downsideWeight);
        return returnLoss.add(directionLoss).add(downsideLoss);
    }

    private NDArray huberLoss(NDArray prediction, NDArray label, float delta) {
        NDArray absoluteError = prediction.sub(label).abs();
        NDArray quadratic = absoluteError.minimum(delta);
        NDArray linear = absoluteError.sub(quadratic);
        return quadratic.square().mul(0.5F).add(linear.mul(delta)).mean();
    }

    private NDArray binaryCrossEntropy(NDArray prediction, NDArray label) {
        NDArray safePrediction = prediction.clip(1.0E-7F, 1F - 1.0E-7F);
        NDArray positive = label.mul(safePrediction.log());
        NDArray negative = label.mul(-1F).add(1F)
                .mul(safePrediction.mul(-1F).add(1F).log());
        return positive.add(negative).mul(-1F).mean();
    }
}
// AI_GENERATE_END --