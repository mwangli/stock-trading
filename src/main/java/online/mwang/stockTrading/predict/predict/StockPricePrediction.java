package online.mwang.stockTrading.predict.predict;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.predict.data.DataProcessIterator;
import online.mwang.stockTrading.predict.model.ModelConfig;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.io.ClassPathResource;
import org.nd4j.linalg.primitives.Pair;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * Created by zhanghao on 26/7/17.
 * Modified by zhanghao on 28/9/17.
 *
 * @author ZHANG HAO
 */
@Slf4j
@Component
public class StockPricePrediction {

    private static final int WINDOW_LENGTH = 22;
    private static final int BATCH_SIZE = 32;
    private static final double SPLIT_RATIO = 0.9;
    private static final int EPOCHS = 1;

    /**
     * Predict one feature of a stock one-day ahead
     */
    private static void predictPriceOneAhead(MultiLayerNetwork net, List<Pair<INDArray, INDArray>> testData, double max, double min) {
        double[] predicts = new double[testData.size()];
        double[] actuals = new double[testData.size()];
        for (int i = 0; i < testData.size(); i++) {
            predicts[i] = net.rnnTimeStep(testData.get(i).getKey()).getDouble(WINDOW_LENGTH - 1) * (max - min) + min;
            actuals[i] = testData.get(i).getValue().getDouble(0);
        }
        log.info("Print out Predictions and Actual Values...");
        log.info("Predict,Actual");
        for (int i = 0; i < predicts.length; i++) log.info(predicts[i] + "," + actuals[i]);
        log.info("Plot...");
//        PlotUtil.plot(predicts, actuals, "Price");
    }

    @SneakyThrows
    public void predictPrice(String stockCode) {

        String dataFilePath = new ClassPathResource("data/history_price_" + stockCode + ".csv").getFile().getAbsolutePath();
        log.info("Create dataSet iterator...");
        DataProcessIterator iterator = new DataProcessIterator(dataFilePath, BATCH_SIZE, WINDOW_LENGTH, SPLIT_RATIO);
        log.info("Load test dataset...");
        List<Pair<INDArray, INDArray>> test = iterator.getTestDataSet();

        log.info("Build lstm networks...");
        MultiLayerNetwork net = ModelConfig.buildLstmNetworks(iterator.inputColumns(), iterator.totalOutcomes());

        log.info("Training...");
        for (int i = 0; i < EPOCHS; i++) {
            while (iterator.hasNext()) net.fit(iterator.next()); // fit model using mini-batch data
            iterator.reset(); // reset iterator
            net.rnnClearPreviousState(); // clear previous state
        }

        log.info("Saving model...");
//        String modelPath = new ClassPathResource("model/model_".concat(stockCode).concat(".zip")).getFile().getAbsolutePath();
        // saveUpdater: i.e., the state for Momentum, RMSProp, Adagrad etc. Save this to train your network more in the future
        File modelFile = new File("src/main/resources/model".concat(stockCode).concat(".zip"));
        ModelSerializer.writeModel(net, modelFile, true);

        log.info("Load model...");
        net = ModelSerializer.restoreMultiLayerNetwork(modelFile);

        log.info("Testing...");
        double max = iterator.getMaxNum();
        double min = iterator.getMinNum();
        predictPriceOneAhead(net, test, max, min);

        log.info("Done...");
    }
}
