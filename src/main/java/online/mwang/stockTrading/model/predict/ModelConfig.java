package online.mwang.stockTrading.model.predict;

import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.BackpropType;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.springframework.stereotype.Component;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2024/4/28 10:00
 * @description: ModelConfig
 */
@Component
public class ModelConfig {

    private static final double LEARNING_RATE = 0.05;
    private static final int ITERATIONS = 1;
    private static final int SEED = 9786;
    private static final int LSTM_LAYER_1_SIZE = 128;
    private static final int LSTM_LAYER_2_SIZE = 128;
    private static final int DENSE_LAYER_SIZE = 32;
    private static final double DROPOUT_RATIO = 0.2;
    private static final int TRUNCATED_BPTT_LENGTH = 22;
    private static final int SCORE_ITERATIONS = 100;

    public MultiLayerNetwork getModel(int nIn, int nOut) {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(SEED)
                .iterations(ITERATIONS)
                .learningRate(LEARNING_RATE)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .weightInit(WeightInit.XAVIER)
                .updater(Updater.RMSPROP)
                .regularization(true)
                .l2(1e-4)
                .list()
                .layer(0, new GravesLSTM.Builder()
                        .nIn(nIn)
                        .nOut(LSTM_LAYER_1_SIZE)
                        .activation(Activation.TANH)
                        .gateActivationFunction(Activation.HARDSIGMOID)
                        .dropOut(DROPOUT_RATIO)
                        .build())
                .layer(1, new GravesLSTM.Builder()
                        .nIn(LSTM_LAYER_1_SIZE)
                        .nOut(LSTM_LAYER_2_SIZE)
                        .activation(Activation.TANH)
                        .gateActivationFunction(Activation.HARDSIGMOID)
                        .dropOut(DROPOUT_RATIO)
                        .build())
                .layer(2, new DenseLayer.Builder()
                        .nIn(LSTM_LAYER_2_SIZE)
                        .nOut(DENSE_LAYER_SIZE)
                        .activation(Activation.RELU)
                        .build())
                .layer(3, new RnnOutputLayer.Builder()
                        .nIn(DENSE_LAYER_SIZE)
                        .nOut(nOut)
                        .activation(Activation.IDENTITY)
                        .lossFunction(LossFunctions.LossFunction.MSE)
                        .build())
                .backpropType(BackpropType.TruncatedBPTT)
                .tBPTTForwardLength(TRUNCATED_BPTT_LENGTH)
                .tBPTTBackwardLength(TRUNCATED_BPTT_LENGTH)
                .pretrain(false)
                .backprop(true)
                .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        return net;
    }
}
