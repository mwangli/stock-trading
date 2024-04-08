package online.mwang.stockTrading.model.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.data.StockData;
import online.mwang.stockTrading.model.data.StockDataSetIterator;
import online.mwang.stockTrading.model.IModelService;
import online.mwang.stockTrading.model.utils.PlotUtil;
import online.mwang.stockTrading.model.data.PriceCategory;
import online.mwang.stockTrading.web.bean.po.PredictPrice;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import org.deeplearning4j.api.storage.StatsStorage;
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
import org.deeplearning4j.ui.api.UIServer;
import org.deeplearning4j.ui.stats.StatsListener;
import org.deeplearning4j.ui.storage.InMemoryStatsStorage;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.primitives.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by zhanghao on 26/7/17.
 * Modified by zhanghao on 28/9/17.
 *
 * @author ZHANG HAO
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LSTMServiceImpl implements IModelService {


    private static final double learningRate = 0.05;
    private static final int iterations = 1;
    private static final int seed = 12345;

    private static final int lstmLayer1Size = 256;
    private static final int lstmLayer2Size = 256;
    private static final int denseLayerSize = 32;
    private static final double dropoutRatio = 0.2;
    private static final int truncatedBPTTLength = 22;
    private static final int VECTOR_SIZE = 2; // time series length, assume 22 working days per month
    private static final int exampleLength = 22; // time series length, assume 22 working days per month
    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;
    @Value("${PROFILE}")
    private String profile;


    @SneakyThrows
    @Override
    public void modelTrain(List<StockHistoryPrice> historyPrices) {
//        String filePath = new File("data/history_price_" + stockCode + ".csv").getAbsolutePath();
//        if (profile.equalsIgnoreCase("prod")) {
//            filePath = new File("/root/history_price_" + stockCode + ".csv").getAbsolutePath();
//        }
        int batchSize = 64; // mini-batch size
        double splitRatio = 0.9; // 90% for training, 10% for testing
        int epochs = 1; // training epochs

        log.info("Create dataSet iterator...");
        PriceCategory category = PriceCategory.ALL; // CLOSE: predict close price


        List<StockData> stockDataList = historyPrices.stream().map(this::mapToStockData).collect(Collectors.toList());
        log.info("stockDataList size = {}", stockDataList.size());
        StockDataSetIterator iterator = new StockDataSetIterator(stockDataList, batchSize, exampleLength, splitRatio, category);
        // 计算最大值和最小
        double max1 = stockDataList.stream().mapToDouble(StockData::getPrice1).max().orElse(0.0);
        double max2 = stockDataList.stream().mapToDouble(StockData::getPrice2).max().orElse(0.0);
        double min1 = stockDataList.stream().mapToDouble(StockData::getPrice1).min().orElse(0.0);
        double min2 = stockDataList.stream().mapToDouble(StockData::getPrice2).min().orElse(0.0);
        iterator.setMinArray(new double[]{min1, min2});
        iterator.setMaxArray(new double[]{max1, max2});
        log.info("Build lstm networks...");
        MultiLayerNetwork net = buildLstmNetworks(iterator.inputColumns(), iterator.totalOutcomes());

        if ("dev".equalsIgnoreCase(profile)) {
//             初始化用户界面后端
            UIServer uiServer = UIServer.getInstance();
            StatsStorage statsStorage = new InMemoryStatsStorage();
            uiServer.attach(statsStorage);
            net.setListeners(new StatsListener(statsStorage));
        }
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

//        redisTemplate.opsForValue().set("lastInput_" + stockCode, JSON.toJSONString(lastInput), 3, TimeUnit.DAYS);
        double[] minArray = iterator.getMinArray();
        double[] maxArray = iterator.getMaxArray();
//        redisTemplate.opsForValue().set("minArray_" + stockCode, JSON.toJSONString(minArray), 3, TimeUnit.DAYS);
//        redisTemplate.opsForValue().set("maxArray_" + stockCode, JSON.toJSONString(maxArray), 3, TimeUnit.DAYS);

        log.info("Saving model...");
        String savePath = new File("model/model_".concat(".zip")).getAbsolutePath();
        if (profile.equalsIgnoreCase("prod")) {
            savePath = new File("/root/model_".concat(".zip")).getAbsolutePath();
        }
        // saveUpdater: i.e., the state for Momentum, RMSProp, Adagrad etc. Save this to train your network more in the future
        ModelSerializer.writeModel(net, savePath, true);
//        redisTemplate.opsForValue().set(profile + "_trainedStockList_" + stockCode, stockCode, 3, TimeUnit.DAYS);

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
            String fileName = "data/Predict_" + "" + ".png";
            if (profile.equalsIgnoreCase("prod")) {
                fileName = "/root/Predict_" + "" + ".png";
            }
            if (profile.equalsIgnoreCase("dev")) {
                PlotUtil.plot(pred, actu, fileName);
            }
        }
//        } else {
//            double max = iterator.getMaxNum(category);
//            double min = iterator.getMinNum(category);
//            predictPriceOneAhead(net, test, max, min, category);
//        }
        log.info("Done...");
    }

    @Override
    @SneakyThrows
    public PredictPrice modelPredict(StockHistoryPrice historyPrice) {
        log.info("Load model...");
        final String stockCode = historyPrice.getCode();
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
        double lastInputV1 = (historyPrice.getPrice1() - minValue1) / (maxValue1 - minValue1);
        double lastInputV2 = (historyPrice.getPrice2() - minValue2) / (maxValue2 - minValue2);
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
        PredictPrice predictPrice = new PredictPrice();
        predictPrice.setPredictPrice1(predictPrice1);
        predictPrice.setPredictPrice2(predictPrice2);
        return predictPrice;
    }

    private StockData mapToStockData(StockHistoryPrice historyPrice) {
        StockData stockData = new StockData();
        stockData.setCode(historyPrice.getCode());
        stockData.setName(historyPrice.getName());
        stockData.setDate(historyPrice.getDate());
        stockData.setPrice1(historyPrice.getPrice1());
        stockData.setPrice2(historyPrice.getPrice2());
        stockData.setPrice3(historyPrice.getPrice3());
        stockData.setPrice4(historyPrice.getPrice4());
        return stockData;
    }


    private MultiLayerNetwork buildLstmNetworks(int nIn, int nOut) {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(seed)
                .iterations(iterations)
                .learningRate(learningRate)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .weightInit(WeightInit.XAVIER)
                .updater(Updater.RMSPROP)
                .regularization(true)
                .l2(1e-4)
                .list()
                .layer(0, new GravesLSTM.Builder()
                        .nIn(nIn)
                        .nOut(lstmLayer1Size)
                        .activation(Activation.TANH)
                        .gateActivationFunction(Activation.HARDSIGMOID)
                        .dropOut(dropoutRatio)
                        .build())
                .layer(1, new GravesLSTM.Builder()
                        .nIn(lstmLayer1Size)
                        .nOut(lstmLayer2Size)
                        .activation(Activation.TANH)
                        .gateActivationFunction(Activation.HARDSIGMOID)
                        .dropOut(dropoutRatio)
                        .build())
                .layer(2, new DenseLayer.Builder()
                        .nIn(lstmLayer2Size)
                        .nOut(denseLayerSize)
                        .activation(Activation.RELU)
                        .build())
                .layer(3, new RnnOutputLayer.Builder()
                        .nIn(denseLayerSize)
                        .nOut(nOut)
                        .activation(Activation.IDENTITY)
                        .lossFunction(LossFunctions.LossFunction.MSE)
                        .build())
                .backpropType(BackpropType.TruncatedBPTT)
                .tBPTTForwardLength(truncatedBPTTLength)
                .tBPTTBackwardLength(truncatedBPTTLength)
                .pretrain(false)
                .backprop(true)
                .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        log.info(net.summary());
        net.setListeners(new ScoreIterationListener(100));
        return net;
    }


}
