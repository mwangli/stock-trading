package online.mwang.stockTrading.model.predict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.po.ModelInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.service.ModelInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import online.mwang.stockTrading.web.utils.GridFsUtils;
import org.datavec.api.records.reader.SequenceRecordReader;
import org.datavec.api.records.reader.impl.inmemory.InMemorySequenceRecordReader;
import org.datavec.api.writable.DoubleWritable;
import org.datavec.api.writable.Writable;
import org.deeplearning4j.datasets.datavec.SequenceRecordReaderDataSetIterator;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

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
    private final MongoTemplate mongoTemplate;
    private final ModelInfoService modelInfoService;
    private final ModelConfig modelConfig;
    private final GridFsUtils gridFsUtils;

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

    public List<StockPrices> train2(List<StockPrices> dataList) {
        // 切分数据
        List<List<List<Writable>>> allData = buildSequenceData(dataList);
        if (allData.size() <= EXAMPLE_LENGTH) return Collections.emptyList();
        long splitIndex = Math.round(allData.size() * SPLIT_RATIO);
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
        String modelFileName = "model_".concat(stockCode).concat(".zip");
        MultiLayerNetwork model = gridFsUtils.readModelFromMongo(modelFileName);
        MultiLayerNetwork net = model != null ? model : modelConfig.getNetModel(INPUT_SIZE, OUTPUT_SIZE);
        net.setListeners(new ScoreIterationListener(SCORE_ITERATIONS));
        saveModelInfo(stockCode, stockName, "0秒", 0, "0");
        // 训练模型
        long start = System.currentTimeMillis();
        net.fit(trainIter, EPOCHS);
        long end = System.currentTimeMillis();
        String timePeriod = DateUtils.timeConvertor(end - start);
        log.info("模型训练完成，共耗时:{}", timePeriod);
        // 保存模型
        gridFsUtils.saveModelToMongo(net, modelFileName);
        String scalerFileName = "scaler_".concat(stockCode).concat(".zip");
        gridFsUtils.saveScalerToMongo(minMaxScaler, scalerFileName);
        saveModelInfo(stockCode, stockName, timePeriod, EPOCHS, "1");
        // 测试结果
        List<String> dateList = dataList.stream().map(StockPrices::getDate).collect(Collectors.toList());
        ArrayList<StockPrices> testPredictData = new ArrayList<>();
        int dateStartIndex = (int) splitIndex + EXAMPLE_LENGTH;
        while (testIter.hasNext()) {
            DataSet testDateSet = testIter.next();
            INDArray input = testDateSet.getFeatures();
            INDArray output = net.rnnTimeStep(input);
            minMaxScaler.revertLabels(output);
            double predictValue = output.getDouble(EXAMPLE_LENGTH - 1);
            String date = dateList.get(dateStartIndex++);
            testPredictData.add(new StockPrices(stockCode, stockName, date, predictValue));
        }
        return testPredictData;
    }

    private void saveModelInfo(String stockCode, String stockName, String timePeriod, Integer trainTimes, String status) {
        LambdaQueryWrapper<ModelInfo> queryWrapper = new LambdaQueryWrapper<ModelInfo>().eq(ModelInfo::getCode, stockCode);
        ModelInfo findInfo = modelInfoService.getOne(queryWrapper);
        if (findInfo == null) {
            ModelInfo modelInfo = new ModelInfo();
            modelInfo.setCode(stockCode);
            modelInfo.setName(stockName);
            modelInfo.setStatus(status);
            modelInfo.setScore(0.0);
            modelInfo.setTrainTimes(0);
            modelInfo.setTrainPeriod("0秒");
            modelInfo.setTestDeviation(0.0);
            modelInfo.setCreateTime(new Date());
            modelInfo.setUpdateTime(new Date());
            modelInfoService.save(modelInfo);
        } else {
            findInfo.setStatus(status);
            findInfo.setTrainPeriod(timePeriod);
            findInfo.setTrainTimes(findInfo.getTrainTimes() + trainTimes);
            findInfo.setUpdateTime(new Date());
            modelInfoService.updateById(findInfo);
        }
    }

    @SneakyThrows
    public StockPrices predictOneHead(List<StockPrices> historyPrices) {
        // 构造输入集
        SequenceRecordReader recordReader = new InMemorySequenceRecordReader(buildSequenceData(historyPrices));
        DataSetIterator dataSetIterator = new SequenceRecordReaderDataSetIterator(recordReader, 1, -1, 2, true);
        // 加载模型
        String stockCode = historyPrices.get(0).getCode();
        String stockName = historyPrices.get(0).getName();
        String modelFileName = "model_".concat(stockCode).concat(".zip");
        MultiLayerNetwork model = gridFsUtils.readModelFromMongo(modelFileName);
        if (model == null) return null;
        // 加载归一化器
        String scalerFileName = "scaler_".concat(stockCode).concat(".zip");
        NormalizerMinMaxScaler minMaxScaler = gridFsUtils.readScalerFromMongo(scalerFileName);
        if (minMaxScaler == null) return null;
        dataSetIterator.setPreProcessor(minMaxScaler);
        DataSet dataSet = dataSetIterator.next();
        // 模型预测
        INDArray output = model.rnnTimeStep(dataSet.getFeatures());
        minMaxScaler.revertLabels(output);
        double predictValue = output.getDouble(EXAMPLE_LENGTH - 1);
        // 返回结果
        return new StockPrices(stockCode, stockName, "", predictValue);
    }
}
