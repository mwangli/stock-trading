package online.mwang.stockTrading.model.predict;


import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import javafx.util.Pair;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.component.StockDataIterator;
import online.mwang.stockTrading.web.bean.base.BusinessException;
import online.mwang.stockTrading.web.bean.po.ModelInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.service.ModelInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.datavec.api.records.reader.SequenceRecordReader;
import org.datavec.api.records.reader.impl.inmemory.InMemorySequenceRecordReader;
import org.datavec.api.writable.DoubleWritable;
import org.datavec.api.writable.Writable;
import org.deeplearning4j.datasets.datavec.SequenceRecordReaderDataSetIterator;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.BackpropType;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.RmsProp;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author 13255
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LSTMModel {

    private static final int SEED = 9786;
    private static final double LEARNING_RATE = 0.005;
    private static final int LSTM_LAYER_1_SIZE = 128;
    private static final int LSTM_LAYER_2_SIZE = 128;
    private static final int DENSE_LAYER_SIZE = 32;
    private static final double DROPOUT_RATIO = 0.2;
    private static final int SCORE_ITERATIONS = 100;
    private static final int EXAMPLE_LENGTH = 22;
    private static final int BATCH_SIZE = 32;
    private static final int FEATURE_VECTOR = 2;
    private static final double SPLIT_RATIO = 0.8;
    private static final int EPOCHS = 100;
    private final StringRedisTemplate redisTemplate;
    private final ModelInfoService modelInfoService;
    public boolean skipTrain = false;

    private MultiLayerNetwork getModel(int inputNum, int outputNum) {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(SEED).optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).weightInit(WeightInit.XAVIER)
                .updater(new RmsProp.Builder().learningRate(LEARNING_RATE).build()).l2(1e-4).list()
                .layer(new LSTM.Builder().nIn(inputNum).nOut(LSTM_LAYER_1_SIZE).activation(Activation.TANH).gateActivationFunction(Activation.HARDSIGMOID).dropOut(DROPOUT_RATIO).build())
                .layer(new LSTM.Builder().nOut(LSTM_LAYER_2_SIZE).activation(Activation.TANH).gateActivationFunction(Activation.HARDSIGMOID).dropOut(DROPOUT_RATIO).build())
                .layer(new DenseLayer.Builder().nOut(DENSE_LAYER_SIZE).activation(Activation.TANH).build())
                .layer(new RnnOutputLayer.Builder().nOut(outputNum).activation(Activation.IDENTITY).lossFunction(LossFunctions.LossFunction.MSE).build())
                .backpropType(BackpropType.TruncatedBPTT).tBPTTForwardLength(EXAMPLE_LENGTH).tBPTTBackwardLength(EXAMPLE_LENGTH).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.setListeners(new ScoreIterationListener(SCORE_ITERATIONS));
        net.init();
        return net;
    }


    public List<StockPrices> train2(List<StockPrices> dataList) throws IOException {


        StockDataIterator iterator = new StockDataIterator(dataList);


        NormalizerMinMaxScaler minMaxScaler = new NormalizerMinMaxScaler(0, 1);

        minMaxScaler.fitLabel(true);
        minMaxScaler.fit(iterator);              //Collect training data statistics
//        trainIter.reset();
//        log.info("Load test dataset...");
//        List<Pair<INDArray, INDArray>> test = iterator.getTestDataSet();

        File modelFile = new File("model/model_".concat(dataList.get(0).getCode()).concat(".zip"));
//        final File parentFile = modelFile.getParentFile();
//        if (!parentFile.exists() && !parentFile.mkdirs()) throw new RuntimeException("文件夹创建失败!");
        MultiLayerNetwork net;
        if (modelFile.exists()) {
            net = ModelSerializer.restoreMultiLayerNetwork(modelFile);
        } else {
            net = getModel(FEATURE_VECTOR, FEATURE_VECTOR);
        }
        iterator.setPreProcessor(minMaxScaler);
//        testIter.setPreProcessor(minMaxScaler);
        log.info("模型训练开始...");
        long start = System.currentTimeMillis();
        if (!skipTrain) net.fit(iterator, EPOCHS);
        long end = System.currentTimeMillis();
        String timePeriod = DateUtils.timeConvertor(end - start);
        log.info("模型训练完成，共耗时:{}", timePeriod);

        ModelSerializer.writeModel(net, modelFile.getAbsolutePath(), true);
        saveModelInfo(modelFile, net, timePeriod);

//        while (testIter.hasNext()) {
//            INDArray output = net.rnnTimeStep(testIter.next().getFeatures());
//            log.info("test output = {}", output);
//        }
        return null;
    }
//
//    public List<StockPrices> train(List<StockData> dataList) throws IOException {
//        log.info("Create dataSet iterator...");
//        StockDataSetIterator iterator = new StockDataSetIterator(dataList, BATCH_SIZE, EXAMPLE_LENGTH, SPLIT_RATIO);
//        iterator.setPreProcessor(new NormalizerMinMaxScaler(0, 1));
//        log.info("Load test dataset...");
//        List<Pair<INDArray, INDArray>> test = iterator.getTestDataSet();
//        log.info("Build lstm networks...");
//        String stockCode = dataList.get(0).getCode();
//        String stockName = dataList.get(0).getName();
//        File modelFile = new File("model/model_".concat(stockCode).concat(".zip"));
//        final File parentFile = modelFile.getParentFile();
//        if (!parentFile.exists() && !parentFile.mkdirs()) throw new RuntimeException("文件夹创建失败!");
//        MultiLayerNetwork net;
//        if (modelFile.exists()) {
//            net = ModelSerializer.restoreMultiLayerNetwork(modelFile);
//        } else {
//            net = getModel(iterator.inputColumns(), iterator.totalOutcomes());
//        }
//        log.info("股票[{}-{}],模型训练开始...", stockName, stockCode);
//        long start = System.currentTimeMillis();
//        if (!skipTrain) net.fit(iterator, EPOCHS);
//        long end = System.currentTimeMillis();
//        String timePeriod = DateUtils.timeConvertor(end - start);
//        log.info("股票[{}-{}],模型训练完成，共耗时:{}", stockName, stockCode, timePeriod);
//        log.info("Saving model...");
//        ModelSerializer.writeModel(net, modelFile.getAbsolutePath(), true);
//        saveModelInfo(modelFile, net, timePeriod);
//        log.info("Testing...");
//        INDArray max = Nd4j.create(iterator.getMaxArray());
//        INDArray min = Nd4j.create(iterator.getMinArray());
//        redisTemplate.opsForHash().put("model:" + stockCode, "minArray", JSON.toJSONString(iterator.getMinArray()));
//        redisTemplate.opsForHash().put("model:" + stockCode, "maxArray", JSON.toJSONString(iterator.getMaxArray()));
//        redisTemplate.opsForHash().put("model:" + stockCode, "lastUpdateTime", DateUtils.dateFormat.format(new Date()));
//        return predictTestDataSet(net, test, iterator.getDateList(), stockCode, stockName, max, min);
//    }

    private void saveModelInfo(File modelFile, MultiLayerNetwork net, String timePeriod) {
        String name = modelFile.getName();
        LambdaQueryWrapper<ModelInfo> queryWrapper = new LambdaQueryWrapper<ModelInfo>().eq(ModelInfo::getName, name);
        ModelInfo findInfo = modelInfoService.getOne(queryWrapper);
        if (findInfo == null) {
            ModelInfo modelInfo = new ModelInfo();
            modelInfo.setName(name);
            long paramsSize = Stream.of(net.getLayers()).mapToLong(Model::numParams).sum();
            modelInfo.setParamsSize(String.valueOf(paramsSize));
            modelInfo.setFilePath(modelFile.getPath());
            modelInfo.setFileSize(String.format("%.2fM", (double) modelFile.length() / (1024 * 1024)));
            modelInfo.setTrainPeriod(timePeriod);
            modelInfo.setTrainTimes(EPOCHS);
            modelInfo.setStatus("1");
            modelInfo.setScore(0.0);
            modelInfo.setTestDeviation(0.0);
            modelInfo.setValidateDeviation(0.0);
            modelInfo.setCreateTime(new Date());
            modelInfo.setUpdateTime(new Date());
            modelInfoService.save(modelInfo);
        } else {
            findInfo.setTrainTimes(findInfo.getTrainTimes() + EPOCHS);
            findInfo.setTrainPeriod(timePeriod);
            findInfo.setUpdateTime(new Date());
            modelInfoService.updateById(findInfo);
        }
    }

    /**
     * Predict all the features (open, close, low, high prices and volume) of a stock one-day ahead
     */
    private List<StockPrices> predictTestDataSet(MultiLayerNetwork net, List<Pair<INDArray, INDArray>> testData, List<String> dateList, String stockCode, String stockName, INDArray max, INDArray min) {
        INDArray[] predicts = new INDArray[testData.size()];
        List<StockPrices> stockTestPrices = new ArrayList<>(100);
        for (int i = 0; i < testData.size(); i++) {
            predicts[i] = net.rnnTimeStep(testData.get(i).getKey()).getRow(EXAMPLE_LENGTH - 1).mul(max.sub(min)).add(min);
            final StockPrices stockTestPrice = new StockPrices();
            stockTestPrice.setPrice1(Double.parseDouble(String.format("%.2f", predicts[i].getDouble(0))));
            stockTestPrice.setPrice2(Double.parseDouble(String.format("%.2f", predicts[i].getDouble(1))));
            stockTestPrice.setDate(dateList.get(i));
            stockTestPrice.setCode(stockCode);
            stockTestPrice.setName(stockName);
            stockTestPrices.add(stockTestPrice);
        }
        return stockTestPrices;
    }

    @SneakyThrows
    public StockPrices predictOneHead(List<StockPrices> historyPrices) {
        // 加载模型
        if (historyPrices.size() != EXAMPLE_LENGTH) throw new BusinessException("价格数据时间序列步长错误！");
        String stockCode = historyPrices.get(0).getCode();
        File modelFile = new File("model/model_".concat(stockCode).concat(".zip"));
        if (!modelFile.exists()) {
            log.info("未找到模型文件：{}，跳过此次预测！", modelFile.getName());
            return null;
        }
        MultiLayerNetwork net = ModelSerializer.restoreMultiLayerNetwork(modelFile);
        // 构造输入集
        String minArrayString = (String) redisTemplate.opsForHash().get("model:" + stockCode, "minArray");
        String maxArrayString = (String) redisTemplate.opsForHash().get("model:" + stockCode, "maxArray");
        if (minArrayString == null || maxArrayString == null) throw new BusinessException("最大最小值丢失，无法进行归一化！");
        List<Double> minArray = JSON.parseArray(minArrayString, Double.class);
        List<Double> maxArray = JSON.parseArray(maxArrayString, Double.class);
        int featureVector = 2;
        double[] input = new double[featureVector * EXAMPLE_LENGTH];
        for (int i = 0; i < historyPrices.size(); i += 2) {
            StockPrices historyPrice = historyPrices.get(i);
            input[i] = (historyPrice.getPrice1() - minArray.get(0)) / (maxArray.get(0) - minArray.get(0));
            input[i + 1] = (historyPrice.getPrice2() - minArray.get(1)) / (maxArray.get(1) - minArray.get(1));
        }
        INDArray inputArray = Nd4j.create(input, new int[]{22, 2});
        // 模型预测
        INDArray output = net.rnnTimeStep(inputArray);
        double predictPrice1 = output.getDouble(0, 0);
        double predictPrice2 = output.getDouble(0, 1);
        StockPrices stockPredictPrice = new StockPrices();
        stockPredictPrice.setPrice1(predictPrice1 * (maxArray.get(0) - minArray.get(0)) + minArray.get(0));
        stockPredictPrice.setPrice2(predictPrice2 * (maxArray.get(1) - minArray.get(1)) + minArray.get(1));
        stockPredictPrice.setCode(stockCode);
        return stockPredictPrice;
    }
}
