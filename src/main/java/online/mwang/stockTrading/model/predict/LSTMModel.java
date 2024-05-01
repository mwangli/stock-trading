package online.mwang.stockTrading.model.predict;


import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.nd4j.linalg.learning.config.RmsProp;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
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
    private static final int INPUT_SIZE = 1;
    private static final int OUTPUT_SIZE = 1;
    private static final double SPLIT_RATIO = 0.8;
    private static final int EPOCHS = 100;
    private final StringRedisTemplate redisTemplate;
    private final ModelInfoService modelInfoService;
    public boolean skipTrain = false;

    @Value("${PROFILE}")
    private String profile;

    private MultiLayerNetwork getModel() {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(SEED).optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).weightInit(WeightInit.XAVIER)
                .updater(new RmsProp.Builder().learningRate(LEARNING_RATE).build()).l2(1e-4).list()
                .layer(new LSTM.Builder().nIn(INPUT_SIZE).nOut(LSTM_LAYER_1_SIZE).activation(Activation.TANH).gateActivationFunction(Activation.HARDSIGMOID).dropOut(DROPOUT_RATIO).build())
                .layer(new LSTM.Builder().nOut(LSTM_LAYER_2_SIZE).activation(Activation.TANH).gateActivationFunction(Activation.HARDSIGMOID).dropOut(DROPOUT_RATIO).build())
                .layer(new DenseLayer.Builder().nOut(DENSE_LAYER_SIZE).activation(Activation.TANH).build())
                .layer(new RnnOutputLayer.Builder().nOut(OUTPUT_SIZE).activation(Activation.IDENTITY).lossFunction(LossFunctions.LossFunction.MSE).build())
                .backpropType(BackpropType.TruncatedBPTT).tBPTTForwardLength(EXAMPLE_LENGTH).tBPTTBackwardLength(EXAMPLE_LENGTH).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.setListeners(new ScoreIterationListener(SCORE_ITERATIONS));
        net.init();
        return net;
    }

    /**
     * 构建标准时间序列数据输入数据，其中feature包含了label
     * INDArray shape = [batchSize,featureSize,sequenceLength]
     */
    private List<List<List<Writable>>> buildSequenceData(List<StockPrices> stockPrices) {
        List<List<List<Writable>>> data = new ArrayList<>();
        for (int i = 0; i < stockPrices.size() - EXAMPLE_LENGTH; i++) {
            List<List<Writable>> sequences = new ArrayList<>();
            int endIndex = i + EXAMPLE_LENGTH;
            for (int j = i; j < endIndex; j++) {
                List<Writable> features = new ArrayList<>();
                // features 用今天的开盘价，收盘价，日增长率来预测下一天的日增长率
                StockPrices currentData = stockPrices.get(j);
//                features.add(new DoubleWritable(currentData.getPrice1()));
//                features.add(new DoubleWritable(currentData.getPrice2()));
                features.add(new DoubleWritable(currentData.getIncreaseRate()));
                // labels
                StockPrices nextData = stockPrices.get(j + 1);
                features.add(new DoubleWritable(nextData.getIncreaseRate()));
                sequences.add(features);
            }
            data.add(sequences);
        }
        return data;
    }

    public List<StockPrices> train2(List<StockPrices> dataList) throws IOException {
        boolean debug = "dev".equalsIgnoreCase(profile);
        // 保存日期
        List<String> dateList = dataList.stream().map(StockPrices::getDate).collect(Collectors.toList());
        // 切分数据
        List<List<List<Writable>>> allData = buildSequenceData(dataList);
        long splitIndex = Math.round(dataList.size() * SPLIT_RATIO);
        List<List<List<Writable>>> trainData = allData.stream().limit(splitIndex).collect(Collectors.toList());
        List<List<List<Writable>>> testData = allData.stream().skip(splitIndex).collect(Collectors.toList());
        // 归一化器
        NormalizerMinMaxScaler minMaxScaler = new NormalizerMinMaxScaler(0, 1);
        minMaxScaler.fitLabel(true);
        // 训练数据
        SequenceRecordReader trainRecordReader = new InMemorySequenceRecordReader(trainData);
        DataSetIterator trainIter = new SequenceRecordReaderDataSetIterator(trainRecordReader, BATCH_SIZE, -1, 1, true);
        minMaxScaler.fit(trainIter);
        trainIter.setPreProcessor(minMaxScaler);
        // 测试数据
        SequenceRecordReader testRecordReader = new InMemorySequenceRecordReader(testData);
        DataSetIterator testIter = new SequenceRecordReaderDataSetIterator(testRecordReader, 1, -1, 1, true);
        minMaxScaler.fit(testIter);
        testIter.setPreProcessor(minMaxScaler);
        // 加载模型
        String stockCode = dataList.get(0).getCode();
        File modelFile = new File("model/model_".concat(stockCode).concat(".zip"));
        MultiLayerNetwork net = modelFile.exists() ? ModelSerializer.restoreMultiLayerNetwork(modelFile) : getModel();
        // 训练模型
        saveModelInfo(modelFile, net, null, "0");
        long start = System.currentTimeMillis();
        if (!skipTrain) net.fit(trainIter, EPOCHS);
        long end = System.currentTimeMillis();
        String timePeriod = DateUtils.timeConvertor(end - start);
        log.info("模型训练完成，共耗时:{}", timePeriod);
        saveModelInfo(modelFile, net, timePeriod, "1");
        // 保存模型
        final File parentFile = modelFile.getParentFile();
        if (!parentFile.exists() && !parentFile.mkdirs()) throw new RuntimeException("文件夹创建失败!");
        ModelSerializer.writeModel(net, modelFile.getAbsolutePath(), true);
        redisTemplate.opsForHash().put("model:".concat(stockCode), "minMaxScaler", JSON.toJSONString(minMaxScaler));
        // 测试结果
        ArrayList<StockPrices> testPredictData = new ArrayList<>();
        int dateStartIndex = (int) splitIndex + EXAMPLE_LENGTH;
        while (testIter.hasNext()) {
            DataSet testDateSet = testIter.next();
            INDArray input = testDateSet.getFeatures();
            INDArray labels = testDateSet.getLabels();
            INDArray output = net.rnnTimeStep(input);
            if (debug) log.info("input = {}", input);
            if (debug) log.info("output = {}", output);
            if (debug) log.info("labels = {}", labels);
            minMaxScaler.revertLabels(labels);
            minMaxScaler.revertLabels(output);
            if (debug) log.info("revert output = {}", output);
            if (debug) log.info("revert labels = {}", labels);
            double predictValue = output.getDouble(EXAMPLE_LENGTH - 1);
            double actualValue = labels.getDouble(EXAMPLE_LENGTH - 1);
            String date = dateList.get(dateStartIndex++);
            if (debug) log.info("date = {}, actualValue = {}, predictValue = {}", date, actualValue, predictValue);
            StockPrices stockPrices = new StockPrices();
            stockPrices.setCode(stockCode);
            String stockName = dataList.get(0).getName();
            stockPrices.setName(stockName);
            stockPrices.setIncreaseRate(predictValue);
            stockPrices.setDate(date);
            testPredictData.add(stockPrices);
        }
        return testPredictData;
    }

    private void saveModelInfo(File modelFile, MultiLayerNetwork net, String timePeriod, String status) {
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
            modelInfo.setStatus(status);
            modelInfo.setScore(0.0);
            modelInfo.setTestDeviation(0.0);
            modelInfo.setValidateDeviation(0.0);
            modelInfo.setCreateTime(new Date());
            modelInfo.setUpdateTime(new Date());
            modelInfoService.save(modelInfo);
        } else {
            findInfo.setTrainTimes(findInfo.getTrainTimes() + EPOCHS);
            if (timePeriod != null) findInfo.setTrainPeriod(timePeriod);
            findInfo.setStatus(status);
            findInfo.setUpdateTime(new Date());
            modelInfoService.updateById(findInfo);
        }
    }

    @SneakyThrows
    public StockPrices predictOneHead(List<StockPrices> historyPrices) {
        // 加载模型
        if (historyPrices.size() != EXAMPLE_LENGTH) throw new BusinessException("价格数据时间序列步长错误！");
        String stockCode = historyPrices.get(0).getCode();
        File modelFile = new File("model/model_".concat(stockCode).concat(".zip"));
        if (!modelFile.exists()) throw new BusinessException("模型文件丢失！");
        MultiLayerNetwork net = ModelSerializer.restoreMultiLayerNetwork(modelFile);
        // 构造输入集
        SequenceRecordReader recordReader = new InMemorySequenceRecordReader(buildSequenceData(historyPrices));
        DataSetIterator dataSetIterator = new SequenceRecordReaderDataSetIterator(recordReader, 1, -1, 3, true);
        // 加载归一化器
        String minMaxScalerString = (String) redisTemplate.opsForHash().get("model:".concat(stockCode), "minMaxScaler");
        if (minMaxScalerString == null) throw new BusinessException("归一化器丢失！");
        NormalizerMinMaxScaler minMaxScaler = JSON.parseObject(minMaxScalerString, NormalizerMinMaxScaler.class);
        dataSetIterator.setPreProcessor(minMaxScaler);
        DataSet dataSet = dataSetIterator.next();
        // 模型预测
        INDArray output = net.rnnTimeStep(dataSet.getFeatures());
        minMaxScaler.revertLabels(output);
        double predictValue = output.getDouble(EXAMPLE_LENGTH - 1);
        // 返回结果
        StockPrices stockPredictPrice = new StockPrices();
        stockPredictPrice.setIncreaseRate(predictValue);
        stockPredictPrice.setCode(stockCode);
        return stockPredictPrice;
    }
}
