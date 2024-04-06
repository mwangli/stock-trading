package online.mwang.stockTrading.predict.model;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import javafx.util.Pair;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.predict.data.PriceCategory;
import online.mwang.stockTrading.predict.data.StockDataSetIterator;
import online.mwang.stockTrading.predict.utils.PlotUtil;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by zhanghao on 26/7/17.
 * Modified by zhanghao on 28/9/17.
 *
 * @author ZHANG HAO
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockPricePrediction {

    private static final int VECTOR_SIZE = 2; // time series length, assume 22 working days per month
    private static int exampleLength = 22; // time series length, assume 22 working days per month
    private final StringRedisTemplate redisTemplate;
    @Value("${PROFILE}")
    private String profile;

    /**
     * Predict one feature of a stock one-day ahead
     */
//    private static void predictPriceOneAhead(MultiLayerNetwork net, List<Pair<INDArray, INDArray>> testData, double max, double min, PriceCategory category) {
//        double[] predicts = new double[testData.size()];
//        double[] actuals = new double[testData.size()];
//        for (int i = 0; i < testData.size(); i++) {
//            predicts[i] = net.rnnTimeStep(testData.get(i).getKey()).getDouble(exampleLength - 1) * (max - min) + min;
//            actuals[i] = testData.get(i).getValue().getDouble(0);
//        }
//        log.info("Print out Predictions and Actual Values...");
//        log.info("Predict,Actual");
//        for (int i = 0; i < predicts.length; i++) log.info(predicts[i] + "," + actuals[i]);
//        log.info("Plot...");
//        PlotUtil.plot(predicts, actuals, String.valueOf(category));
//    }

    /**
     * Predict all the features (open, close, low, high prices and volume) of a stock one-day ahead
     */
    private static void predictAllCategories(MultiLayerNetwork net, List<Pair<INDArray, INDArray>> testData, INDArray max, INDArray min) {
//        INDArray[] predicts = new INDArray[testData.size()];
//        INDArray[] actuals = new INDArray[testData.size()];
//        for (int i = 0; i < testData.size(); i++) {
//            predicts[i] = net.rnnTimeStep(testData.get(i).getKey()).getRow(exampleLength - 1).mul(max.sub(min)).add(min);
//            actuals[i] = testData.get(i).getValue();
//        }
//        log.info("Print out Predictions and Actual Values...");
//        log.info("Predict\tActual");
//        for (int i = 0; i < predicts.length; i++) log.info(predicts[i] + "\t" + actuals[i]);
//        log.info("Plot...");
//        for (int n = 0; n < VECTOR_SIZE; n++) {
//            double[] pred = new double[predicts.length];
//            double[] actu = new double[actuals.length];
//            for (int i = 0; i < predicts.length; i++) {
//                pred[i] = predicts[i].getDouble(n);
//                actu[i] = actuals[i].getDouble(n);
//            }
//            PlotUtil.plot(pred, actu, "Predict_");
//        }
    }

    @SneakyThrows
    public void modelTrain(String stockCode) {
        String filePath = new File("data/history_price_" + stockCode + ".csv").getAbsolutePath();
        if (profile.equalsIgnoreCase("prod")) {
            filePath = new File("/root/history_price_" + stockCode + ".csv").getAbsolutePath();
        }
        int batchSize = 64; // mini-batch size
        double splitRatio = 0.9; // 90% for training, 10% for testing
        int epochs = 100; // training epochs

        log.info("Create dataSet iterator...");
        PriceCategory category = PriceCategory.ALL; // CLOSE: predict close price
        StockDataSetIterator iterator = new StockDataSetIterator(filePath, stockCode, batchSize, exampleLength, splitRatio, category);

        log.info("Build lstm networks...");
        MultiLayerNetwork net = RecurrentNets.buildLstmNetworks(iterator.inputColumns(), iterator.totalOutcomes());

        log.info("Training...");
        for (int i = 0; i < epochs; i++) {
            while (iterator.hasNext()) net.fit(iterator.next()); // fit model using mini-batch data
            iterator.reset(); // reset iterator
            net.rnnClearPreviousState(); // clear previous state
        }

//        iteratorHashMap.put(stockCode, iterator);
        List<Pair<INDArray, INDArray>> testDataSet = iterator.getTestDataSet();
        INDArray lastTestInput = testDataSet.get(testDataSet.size() - 1).getKey();
        List<List<Double>> lastInput = new ArrayList<>();
        for (int i = 0; i < exampleLength - 1; i++) {
            double nextV1 = lastTestInput.getScalar(i + 1, 0).getDouble(0);
            double nextV2 = lastTestInput.getScalar(i + 1, 1).getDouble(0);
            List<Double> doubles = Arrays.asList(nextV1, nextV2);
            lastInput.add(doubles);
        }

        redisTemplate.opsForValue().set("lastInput_" + stockCode, JSON.toJSONString(lastInput), 3, TimeUnit.DAYS);
        double[] minArray = iterator.getMinArray();
        double[] maxArray = iterator.getMaxArray();
        redisTemplate.opsForValue().set("minArray_" + stockCode, JSON.toJSONString(minArray), 3, TimeUnit.DAYS);
        redisTemplate.opsForValue().set("maxArray_" + stockCode, JSON.toJSONString(maxArray), 3, TimeUnit.DAYS);

        log.info("Saving model...");
        String savePath = new File("model/model_".concat(stockCode).concat(".zip")).getAbsolutePath();
        if (profile.equalsIgnoreCase("prod")) {
            savePath = new File("/root/model_".concat(stockCode).concat(".zip")).getAbsolutePath();
        }
        // saveUpdater: i.e., the state for Momentum, RMSProp, Adagrad etc. Save this to train your network more in the future
        ModelSerializer.writeModel(net, savePath, true);
        redisTemplate.opsForValue().set(profile + "_trainedStockList_" + stockCode, stockCode,3, TimeUnit.DAYS);

        log.info("Testing...");

//        if (category.equals(PriceCategory.ALL)) {
        List<Pair<INDArray, INDArray>> testData = iterator.getTestDataSet();
        INDArray max = Nd4j.create(iterator.getMaxArray());
        INDArray min = Nd4j.create(iterator.getMinArray());
        INDArray[] predicts = new INDArray[testData.size()];
        INDArray[] actuals = new INDArray[testData.size()];
        for (int i = 0; i < testData.size(); i++) {
            predicts[i] = net.rnnTimeStep(testData.get(i).getKey()).getRow(exampleLength - 1).mul(max.sub(min)).add(min);
            actuals[i] = testData.get(i).getValue();
        }
        log.info("Print out Predictions and Actual Values...");
        log.info("Predict\tActual");
        for (int i = 0; i < predicts.length; i++) log.info(predicts[i] + "\t" + actuals[i]);
        log.info("Plot...");
        for (int n = 0; n < VECTOR_SIZE; n++) {
            double[] pred = new double[predicts.length];
            double[] actu = new double[actuals.length];
            for (int i = 0; i < predicts.length; i++) {
                pred[i] = predicts[i].getDouble(n);
                actu[i] = actuals[i].getDouble(n);
            }
            String fileName = "data/Predict_" + stockCode + ".png";
            if (profile.equalsIgnoreCase("prod")) {
                fileName = "/root/Predict_" + stockCode + ".png";
            }
            PlotUtil.plot(pred, actu, fileName);
        }
//        } else {
//            double max = iterator.getMaxNum(category);
//            double min = iterator.getMinNum(category);
//            predictPriceOneAhead(net, test, max, min, category);
//        }
        log.info("Done...");
    }

    @SneakyThrows
    public double[] modelPredict(String stockCode, Double price1, Double price2) {
        log.info("Load model...");

        String modelPath = new File("model/model_".concat(stockCode).concat(".zip")).getAbsolutePath();
        if (profile.equalsIgnoreCase("prod")) {
            modelPath = new File("/root/model_".concat(stockCode).concat(".zip")).getAbsolutePath();
        }
        MultiLayerNetwork net = ModelSerializer.restoreMultiLayerNetwork(modelPath);

//        redisTemplate.opsForValue().set("lastInput_" + stockCode, JSON.toJSONString(lastInput));
//        double[] minArray = iterator.getMinArray();
//        double[] maxArray = iterator.getMaxArray();
//        redisTemplate.opsForValue().set("minArray_" + stockCode, JSON.toJSONString(minArray));
//        redisTemplate.opsForValue().set("maxArray" + stockCode, JSON.toJSONString(maxArray));

        String lastInputString = redisTemplate.opsForValue().get("lastInput_" + stockCode);
        JSONArray inputList = JSON.parseArray(lastInputString);
        log.info("lastInputString = {}", lastInputString);
        INDArray input = Nd4j.create(22, 2);
        Double pre = 0.0;
        for (int i = 1; i < inputList.size(); i++) {
            JSONArray inputValues = inputList.getJSONArray(i);
            Double v1 = inputValues.getDouble(0);
            Double v2 = inputValues.getDouble(1);
            input.putScalar(i - 1, 0, v1);
            input.putScalar(i - 1, 1, v2);
        }
        input.putScalar(inputList.size() - 1, 0, inputList.getJSONArray(inputList.size() - 1).getDouble(0));
        input.putScalar(inputList.size() - 1, 1, inputList.getJSONArray(inputList.size() - 1).getDouble(1));
//        double[] minArray = iterator.getMinArray();
//        double[] maxArray = iterator.getMaxArray();
//        if (price1 != 0 && price2 != 0) {
//            lastTestInput.put(exampleLength - 1, 0, (price1 - minArray[0]) / (maxArray[0] - minArray[0]));
//            lastTestInput.put(exampleLength - 1, 1, (price2 - minArray[1]) / (maxArray[1] - minArray[1]));
//        }
        String minArrayString = redisTemplate.opsForValue().get("minArray_" + stockCode);
        String maxArrayString = redisTemplate.opsForValue().get("maxArray_" + stockCode);
        JSONArray minArray = JSON.parseArray(minArrayString);
        JSONArray maxArray = JSON.parseArray(maxArrayString);
        Double minValue1 = minArray.getDouble(0);
        Double minValue2 = minArray.getDouble(1);
        Double maxValue1 = maxArray.getDouble(0);
        Double maxValue2 = maxArray.getDouble(1);
        double lastInputV1 = (price1 - minValue1) / (maxValue1 - minValue1);
        double lastInputV2 = (price2 - minValue2) / (maxValue2 - minValue2);
        input.putScalar(21, 0, lastInputV1);
        input.putScalar(21, 1, lastInputV2);
        log.info("input = {}", input);
        INDArray output = net.rnnTimeStep(input);
        log.info("output = {}", output);
        double predictV1 = output.getScalar(0).getDouble(0);
        double predictV2 = output.getScalar(1).getDouble(0);

        double predictPrice1 = predictV1 * (maxValue1 - minValue1) + minValue1;
        double predictPrice2 = predictV2 * (maxValue2 - minValue2) + minValue2;
        log.info("predictPrice1 = {}, predictPrice2= {}", predictPrice1, predictPrice2);
        return new double[]{predictPrice1, predictPrice2};
    }

}
