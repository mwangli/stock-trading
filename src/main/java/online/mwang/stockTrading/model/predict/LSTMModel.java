package online.mwang.stockTrading.model.predict;


import com.alibaba.fastjson.JSON;
import javafx.util.Pair;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.representation.StockData;
import online.mwang.stockTrading.model.representation.StockDataSetIterator;
import online.mwang.stockTrading.web.bean.base.BusinessException;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author 13255
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LSTMModel {

    private static final int EXAMPLE_LENGTH = 22;
    private static final int BATCH_SIZE = 64;
    private static final double SPLIT_RATIO = 0.8;
    private static final int EPOCHS = 100;
    private static final int SCORE_ITERATIONS = 100;
    private final ModelConfig modelConfig;
    private final StringRedisTemplate redisTemplate;
    public boolean skipTrain = false;

    public List<StockPrices> train(List<StockData> dataList) throws IOException {
        log.info("Create dataSet iterator...");
        StockDataSetIterator iterator = new StockDataSetIterator(dataList, BATCH_SIZE, EXAMPLE_LENGTH, SPLIT_RATIO);
        log.info("Load test dataset...");
        List<Pair<INDArray, INDArray>> test = iterator.getTestDataSet();
        log.info("Build lstm networks...");

        String stockCode = dataList.get(0).getCode();
        File locationToSave = new File("/model/model_".concat(stockCode).concat(".zip"));
        MultiLayerNetwork net;
        if (locationToSave.exists()) {
            net = ModelSerializer.restoreMultiLayerNetwork(locationToSave);
        } else {
            net = modelConfig.getModel(iterator.inputColumns(), iterator.totalOutcomes());
        }
        net.setListeners(new ScoreIterationListener(SCORE_ITERATIONS));
        net.summary();
        log.info("Training...");
        for (int i = 0; i < EPOCHS; i++) {
            if (skipTrain) break;
            while (iterator.hasNext()) net.fit(iterator.next());
            iterator.reset();
            net.rnnClearPreviousState();
        }
        log.info("Saving model...");
        final File parentFile = locationToSave.getParentFile();
        if (!parentFile.exists() && !parentFile.mkdirs()) throw new RuntimeException("文件夹创建失败!");
        ModelSerializer.writeModel(net, locationToSave, true);
        log.info("Testing...");
        INDArray max = Nd4j.create(iterator.getMaxArray());
        INDArray min = Nd4j.create(iterator.getMinArray());
        redisTemplate.opsForHash().put("model:" + stockCode, "minArray", JSON.toJSONString(iterator.getMinArray()));
        redisTemplate.opsForHash().put("model:" + stockCode, "maxArray", JSON.toJSONString(iterator.getMaxArray()));
        return predictAllCategories(net, test, iterator.getDateList(), stockCode, max, min);
    }

    /**
     * Predict all the features (open, close, low, high prices and volume) of a stock one-day ahead
     */
    private List<StockPrices> predictAllCategories(MultiLayerNetwork net, List<Pair<INDArray, INDArray>> testData, List<String> dateList, String stockCode, INDArray max, INDArray min) {
        INDArray[] predicts = new INDArray[testData.size()];
        List<StockPrices> stockTestPrices = new ArrayList(100);
        for (int i = 0; i < testData.size(); i++) {
            predicts[i] = net.rnnTimeStep(testData.get(i).getKey()).getRow(EXAMPLE_LENGTH - 1).mul(max.sub(min)).add(min);
            final StockPrices stockTestPrice = new StockPrices();
            stockTestPrice.setPrice1(Double.parseDouble(String.format("%.2f", predicts[i].getDouble(0))));
            stockTestPrice.setPrice2(Double.parseDouble(String.format("%.2f", predicts[i].getDouble(1))));
            stockTestPrice.setDate(dateList.get(i));
            stockTestPrice.setCode(stockCode);
            stockTestPrices.add(stockTestPrice);
        }
        return stockTestPrices;
    }

    @SneakyThrows
    public StockPrices predictOneHead(List<StockPrices> historyPrices) {
        // 加载模型
        if (historyPrices.size() != EXAMPLE_LENGTH) throw new BusinessException("价格数据时间序列步长错误！");
        String stockCode = historyPrices.get(0).getCode();
        File locationToSave = new File("/model/model_".concat(stockCode).concat(".zip"));
        File parentFile = locationToSave.getParentFile();
        if (!parentFile.exists() && !parentFile.mkdirs()) throw new BusinessException("文件夹创建失败！");
        MultiLayerNetwork net = ModelSerializer.restoreMultiLayerNetwork(locationToSave);
        // 构造输入集
        double[] minArray = (double[]) redisTemplate.opsForHash().get("model:" + stockCode, ":minArray");
        double[] maxArray = (double[]) redisTemplate.opsForHash().get("model:" + stockCode, "maxArray");
        if (minArray == null || maxArray == null) throw new BusinessException("最大最小值丢失，无法进行归一化！");
        int featureVector = 2;
        double[] input = new double[featureVector * EXAMPLE_LENGTH];
        for (int i = 0; i < historyPrices.size(); i += 2) {
            StockPrices historyPrice = historyPrices.get(i);
            input[i] = (historyPrice.getPrice1() - minArray[0]) / (maxArray[0] - minArray[0]);
            input[i + 1] = (historyPrice.getPrice2() - minArray[1]) / (maxArray[1] - minArray[1]);
        }
        INDArray inputArray = Nd4j.create(input, new int[]{0, 22, 2});
        // 模型预测
        INDArray output = net.rnnTimeStep(inputArray);
        double predictPrice1 = output.getDouble(0, 0);
        double predictPrice2 = output.getDouble(0, 1);
        StockPrices stockPredictPrice = new StockPrices();
        stockPredictPrice.setPrice1(predictPrice1 * (maxArray[0] - minArray[0]) + minArray[0]);
        stockPredictPrice.setPrice2(predictPrice2 * (maxArray[1] - minArray[1]) + minArray[1]);
        stockPredictPrice.setCode(stockCode);
        return stockPredictPrice;
    }
}
