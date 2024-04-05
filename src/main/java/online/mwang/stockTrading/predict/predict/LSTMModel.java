package online.mwang.stockTrading.predict.predict;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.predict.data.DataProcessIterator;
import online.mwang.stockTrading.predict.model.ModelConfig;
import online.mwang.stockTrading.predict.utils.PlotUtil;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.iter.NdIndexIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
public class LSTMModel {

    private static final int WINDOW_LENGTH = 22;
    private static final int BATCH_SIZE = 32;
    private static final double SPLIT_RATIO = 0.9;
    private static final int EPOCHS = 128;

    private static final String resourceBaseDir = "src/main/resources/";
    private static final String priceFileName = "data/history_price_";
    private static final String priceFileNameSuffix = ".csv";
    private final StringRedisTemplate redisTemplate;


    @Value("${PROFILE}")
    private String profile;

    public String getBaseDir() {
        if (profile.equalsIgnoreCase("Prod")) return "/root/";
        return resourceBaseDir;
    }

    @SneakyThrows
    public void modelTrain(String stockCode) {

        File dataFile = new File(getBaseDir() + priceFileName + stockCode + priceFileNameSuffix);
        log.info("Create dataSet iterator...");
        DataProcessIterator iterator = new DataProcessIterator(dataFile.getAbsolutePath(), BATCH_SIZE, WINDOW_LENGTH, SPLIT_RATIO);
        log.info("Load test dataset...");

        log.info("Build lstm networks...");
        MultiLayerNetwork net = ModelConfig.buildLstmNetworks(iterator.inputColumns(), iterator.totalOutcomes());
        net.summary();

        log.info("Training...");
        for (int i = 0; i < EPOCHS; i++) {
            while (iterator.hasNext()) net.fit(iterator.next()); // fit model using mini-batch data
            iterator.reset(); // reset iterator
            net.rnnClearPreviousState(); // clear previous state
        }
        log.info("股票模型-{}，训练完成!", stockCode);
//        dataProcessIteratorHashMap.put(stockCode, iterator);
        // 保存最大值最小值，用来做归一化处理
        double min = iterator.getMinNum();
        double max = iterator.getMaxNum();
        redisTemplate.opsForValue().set(stockCode, String.valueOf(min).concat(",").concat(String.valueOf(max)));
        // 保存最后一组数据用来做预测
        List<Pair<INDArray, INDArray>> testDataSet = iterator.getTestDataSet();
        INDArray testData = testDataSet.get(testDataSet.size() - 1).getKey();
        NdIndexIterator ndIndexIterator = new NdIndexIterator(WINDOW_LENGTH);
        ArrayList<Double> doubleData = new ArrayList<>();
        while (ndIndexIterator.hasNext()) {
            long[] nextIndex = ndIndexIterator.next();
            double data = testData.getDouble(nextIndex);
            doubleData.add(data);
        }
        redisTemplate.opsForValue().set("lastData_" + stockCode, String.join(",", JSON.toJSONString(doubleData)));
        log.info("Saving model...");
//        String modelPath = new ClassPathResource("model/model_".concat(stockCode).concat(".zip")).getFile().getAbsolutePath();
        // saveUpdater: i.e., the state for Momentum, RMSProp, Adagrad etc. Save this to train your network more in the future
        String modelFilePath = new File(getBaseDir() + "model/model_".concat(stockCode).concat(".zip")).getAbsolutePath();
        ModelSerializer.writeModel(net, modelFilePath, true);
        log.info("股票模型-{}，保存成功!", stockCode);
        // 模型测试
//        if (profile.equalsIgnoreCase("dev"))
//            modelTest(net, iterator.getTestDataSet(), iterator.getMaxNum(), iterator.getMinNum());
    }


    @SneakyThrows
    public double modelPredict(String stockCode, double nowPrice) {
        log.info("Load model...");
        String modelPath = new File(getBaseDir() + "model/model_".concat(stockCode).concat(".zip")).getAbsolutePath();
        MultiLayerNetwork net = ModelSerializer.restoreMultiLayerNetwork(modelPath);
        log.info("Testing...");
        // 从redis中获取最大值，最小值用来做归一化处理
        String minMaxString = redisTemplate.opsForValue().get(stockCode);
        String[] minMaxSplit = minMaxString.split(",");
        double min = Double.parseDouble(minMaxSplit[0]);
        double max = Double.parseDouble(minMaxSplit[1]);
        // 获取测试集的最后一组数据
        String lastDataString = redisTemplate.opsForValue().get("lastData_" + stockCode);
        log.info("获取到当前股票的最近的输入:{}", lastDataString);
        List<Double> doubles = JSON.parseArray(lastDataString, Double.class);
        double[] dataArray = new double[WINDOW_LENGTH];
        for (int i = 0; i < dataArray.length - 1; i++) {
            dataArray[i] = doubles.get(i + 1);
        }
        dataArray[WINDOW_LENGTH - 1] = (nowPrice - min) / (max - min);
        log.info("填充最后一次价格的输入项:{}", dataArray);
        INDArray newArray = Nd4j.create(dataArray, new int[]{22, 1});
        INDArray predictArray = net.rnnTimeStep(newArray);
        double predictValue = predictArray.getDouble(WINDOW_LENGTH - 1) * (max - min) + min;
        log.info("预测下一个值为：{}", predictValue);
        return predictValue;
    }


    private void modelTest(MultiLayerNetwork net, List<Pair<INDArray, INDArray>> testData, double max, double min) {
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
        PlotUtil.plot(predicts, actuals, "Price");
    }

}
