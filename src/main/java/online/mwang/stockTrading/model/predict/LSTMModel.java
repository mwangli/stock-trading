package online.mwang.stockTrading.model.predict;


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
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.nd4j.linalg.dataset.api.preprocessor.serializer.NormalizerSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.*;
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
    private static final int EXAMPLE_LENGTH = 22;
    private static final int BATCH_SIZE = 32;
    private static final int INPUT_SIZE = 2;
    private static final int OUTPUT_SIZE = 1;
    private static final double SPLIT_RATIO = 0.8;
    private static final int EPOCHS = 100;
    private static final int SCORE_ITERATIONS = 100;
    private final StringRedisTemplate redisTemplate;
    private final ModelInfoService modelInfoService;
    private final ModelConfig modelConfig;
    public boolean skipTrain = false;

    @Value("${PROFILE}")
    private String profile;

    /**
     * 构建标准时间序列数据输入数据，其中feature包含了label
     * dataList size = [batch,sequenceLength,featureSize]
     * input shape = [batchSize,featureSize,sequenceLength]
     */
    private List<List<List<Writable>>> buildSequenceData(List<StockPrices> stockPrices) {
        List<List<List<Writable>>> data = new ArrayList<>();
        for (int i = 0; i < stockPrices.size() - EXAMPLE_LENGTH; i++) {
            List<List<Writable>> sequences = new ArrayList<>();
            int endIndex = i + EXAMPLE_LENGTH;
            for (int j = i; j < endIndex; j++) {
                List<Writable> features = new ArrayList<>();
                // features 用今天的开盘价,收盘价，来预测下一天的开盘价
                StockPrices currentData = stockPrices.get(j);
                features.add(new DoubleWritable(currentData.getPrice1()));
                features.add(new DoubleWritable(currentData.getPrice2()));
                // labels
                StockPrices nextData = stockPrices.get(j + 1);
                features.add(new DoubleWritable(nextData.getPrice1()));
                sequences.add(features);
            }
            data.add(sequences);
        }
        return data;
    }

    public List<StockPrices> train2(List<StockPrices> dataList) throws IOException {
        // 切分数据
        long splitIndex = Math.round(dataList.size() * SPLIT_RATIO);
        List<List<List<Writable>>> allData = buildSequenceData(dataList);
        List<List<List<Writable>>> trainData = allData.stream().limit(splitIndex).collect(Collectors.toList());
        List<List<List<Writable>>> testData = allData.stream().skip(splitIndex).collect(Collectors.toList());
        DataSetIterator trainIter = new SequenceRecordReaderDataSetIterator(new InMemorySequenceRecordReader(trainData), BATCH_SIZE, -1, 2, true);
        DataSetIterator testIter = new SequenceRecordReaderDataSetIterator(new InMemorySequenceRecordReader(testData), 1, -1, 2, true);
        DataSetIterator allIter = new SequenceRecordReaderDataSetIterator(new InMemorySequenceRecordReader(allData), 1, -1, 2, true);
        // 归一化
        NormalizerMinMaxScaler minMaxScaler = new NormalizerMinMaxScaler(0, 1);
        minMaxScaler.fitLabel(true);
        minMaxScaler.fit(allIter);
        trainIter.setPreProcessor(minMaxScaler);
        testIter.setPreProcessor(minMaxScaler);
        // 加载模型
        String stockCode = dataList.get(0).getCode();
        String stockName = dataList.get(0).getName();
        File modelFile = new File("model/model_".concat(stockCode).concat(".zip"));
        MultiLayerNetwork net = modelFile.exists() ? ModelSerializer.restoreMultiLayerNetwork(modelFile) : modelConfig.getNetModel(INPUT_SIZE, OUTPUT_SIZE);
//        net.setListeners(new ScoreIterationListener(SCORE_ITERATIONS));
        saveModelInfo(stockCode, modelFile, net, null, "0");
        // 训练模型
        long start = System.currentTimeMillis();
        if (!skipTrain) net.fit(trainIter, EPOCHS);
        long end = System.currentTimeMillis();
        String timePeriod = DateUtils.timeConvertor(end - start);
        log.info("模型训练完成，共耗时:{}", timePeriod);
        // 保存模型
        final File parentFile = modelFile.getParentFile();
        if (!parentFile.exists() && !parentFile.mkdirs()) throw new RuntimeException("文件夹创建失败!");
        ModelSerializer.writeModel(net, modelFile.getAbsolutePath(), true);
        File scalerFile = new File("model/scaler_".concat(stockCode).concat(".zip"));
        NormalizerSerializer.getDefault().write(minMaxScaler, scalerFile);
        saveModelInfo(stockCode, modelFile, net, timePeriod, "1");
        // 测试结果
        List<String> dateList = dataList.stream().map(StockPrices::getDate).collect(Collectors.toList());
        ArrayList<StockPrices> testPredictData = new ArrayList<>();
        int dateStartIndex = (int) splitIndex + EXAMPLE_LENGTH;
        while (testIter.hasNext()) {
            DataSet testDateSet = testIter.next();
            INDArray input = testDateSet.getFeatures();
            INDArray labels = testDateSet.getLabels();
            INDArray output = net.rnnTimeStep(input);
            minMaxScaler.revertLabels(labels);
            minMaxScaler.revertLabels(output);
            double predictValue = output.getDouble(EXAMPLE_LENGTH - 1);
            double actualValue = labels.getDouble(EXAMPLE_LENGTH - 1);
            String date = dateList.get(dateStartIndex++);
            boolean debug = "dev".equalsIgnoreCase(profile);
            if (debug) log.info("date = {}, actualValue = {}, predictValue = {}", date, actualValue, predictValue);
            testPredictData.add(new StockPrices(stockCode, stockName, date, predictValue));
        }
        return testPredictData;
    }

    private void saveModelInfo(String stockCode, File modelFile, MultiLayerNetwork net, String timePeriod, String status) {
        LambdaQueryWrapper<ModelInfo> queryWrapper = new LambdaQueryWrapper<ModelInfo>().eq(ModelInfo::getCode, stockCode);
        ModelInfo findInfo = modelInfoService.getOne(queryWrapper);
        if (findInfo == null) {
            ModelInfo modelInfo = new ModelInfo();
            modelInfo.setCode(stockCode);
            modelInfo.setName(modelFile.getName());
            long paramsSize = Stream.of(net.getLayers()).mapToLong(Model::numParams).sum();
            modelInfo.setParamsSize(String.valueOf(paramsSize));
            modelInfo.setFilePath(modelFile.getPath());
            modelInfo.setStatus(status);
            modelInfo.setTrainPeriod("");
            modelInfo.setScore(0.0);
            modelInfo.setTrainTimes(0);
            modelInfo.setTestDeviation(0.0);
            modelInfo.setValidateDeviation(0.0);
            modelInfo.setCreateTime(new Date());
            modelInfoService.save(modelInfo);
        } else {
            findInfo.setStatus(status);
            if (timePeriod != null) findInfo.setTrainPeriod(timePeriod);
            findInfo.setTrainTimes(findInfo.getTrainTimes() + EPOCHS);
            double fileSize = (double) modelFile.length() / (1024 * 1024);
            findInfo.setFileSize(String.format("%.2fM", fileSize));
            findInfo.setUpdateTime(new Date());
            modelInfoService.updateById(findInfo);
        }
    }

    @SneakyThrows
    public StockPrices predictOneHead(List<StockPrices> historyPrices) {
        // 加载模型
        if (historyPrices.size() != EXAMPLE_LENGTH + 1) throw new BusinessException("价格数据时间序列步长错误！");
        String stockCode = historyPrices.get(0).getCode();
        File modelFile = new File("model/model_".concat(stockCode).concat(".zip"));
        if (!modelFile.exists()) return null;
        MultiLayerNetwork net = ModelSerializer.restoreMultiLayerNetwork(modelFile);
        // 构造输入集
        SequenceRecordReader recordReader = new InMemorySequenceRecordReader(buildSequenceData(historyPrices));
        DataSetIterator dataSetIterator = new SequenceRecordReaderDataSetIterator(recordReader, 1, -1, 2, true);
        // 加载归一化器
        File scalerFile = new File("model/scaler_".concat(stockCode).concat(".zip"));
        NormalizerMinMaxScaler minMaxScaler = NormalizerSerializer.getDefault().restore(scalerFile);
        dataSetIterator.setPreProcessor(minMaxScaler);
        DataSet dataSet = dataSetIterator.next();
        // 模型预测
        INDArray output = net.rnnTimeStep(dataSet.getFeatures());
        minMaxScaler.revertLabels(output);
        double predictValue = output.getDouble(EXAMPLE_LENGTH - 1);
        // 返回结果
        StockPrices stockPredictPrice = new StockPrices();
        stockPredictPrice.setPrice1(predictValue);
        stockPredictPrice.setCode(stockCode);
        return stockPredictPrice;
    }
}
