package online.mwang.stockTrading.model.predict;

import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.learning.config.Nesterovs;
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

    public MultiLayerNetwork getNetModel(int inputNum, int outNum) {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(12345)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .weightInit(WeightInit.XAVIER).updater(new Nesterovs(0.0015, 0.9))
                .list()
                .layer(new LSTM.Builder().activation(Activation.TANH).nOut(128).gateActivationFunction(Activation.SIGMOID).dropOut(0.2).nIn(inputNum).build())
                .layer(new RnnOutputLayer.Builder(LossFunctions.LossFunction.MSE).activation(Activation.IDENTITY).nOut(outNum).build())
                .build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        return net;
    }


}
