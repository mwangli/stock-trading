package online.mwang.stockTrading.model.predict;


import com.alibaba.fastjson.JSON;
import javafx.util.Pair;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IModelService;
import online.mwang.stockTrading.model.representation.PriceCategory;
import online.mwang.stockTrading.model.representation.StockData;
import online.mwang.stockTrading.model.representation.StockDataSetIterator;
import online.mwang.stockTrading.web.bean.base.BusinessException;
import online.mwang.stockTrading.web.bean.po.StockTestPrice;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by zhanghao on 26/7/17.
 * Modified by zhanghao on 28/9/17.
 *
 * @author ZHANG HAO
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockPricePrediction implements IModelService {

    private static int exampleLength = 22; // time series length, assume 22 working days per month
    private final MongoTemplate mongoTemplate;

    //    private static final Logger log = LoggerFactory.getLogger(StockPricePrediction.class);
    private final StringRedisTemplate redisTemplate;

    public List<StockTestPrice> train(List<StockData> dataList, String stockCode) throws IOException {
//        String file = new ClassPathResource("prices-split-adjusted.csv").getFile().getAbsolutePath();
        String file = new ClassPathResource("history_price_002153.csv").getFile().getAbsolutePath();
//        String symbol = "GOOG"; // stock name
        int batchSize = 64; // mini-batch size
        double splitRatio = 0.8; // 90% for training, 10% for testing
        int epochs = 100; // training epochs

        log.info("Create dataSet iterator...");
        PriceCategory category = PriceCategory.ALL; // CLOSE: predict close price
        StockDataSetIterator iterator = new StockDataSetIterator(dataList, file, stockCode, batchSize, exampleLength, splitRatio, category);
        log.info("Load test dataset...");
        List<Pair<INDArray, INDArray>> test = iterator.getTestDataSet();

        log.info("Build lstm networks...");
        MultiLayerNetwork net = RecurrentNets.buildLstmNetworks(iterator.inputColumns(), iterator.totalOutcomes());
        net.summary();

        log.info("Training...");
        for (int i = 0; i < epochs; i++) {
            while (iterator.hasNext()) net.fit(iterator.next()); // fit model using mini-batch data
            iterator.reset(); // reset iterator
            net.rnnClearPreviousState(); // clear previous state
        }

        log.info("Saving model...");
        File locationToSave = new File("/model/model_".concat(stockCode).concat(".zip"));
//         //saveUpdater: i.e., the state for Momentum, RMSProp, Adagrad etc. Save this to train your network more in the future
        final File parentFile = locationToSave.getParentFile();
        if (!parentFile.exists() && !parentFile.mkdirs()) throw new RuntimeException("文件夹创建失败!");
        ModelSerializer.writeModel(net, locationToSave, true);

//        log.info("Load model...");
//        net = ModelSerializer.restoreMultiLayerNetwork(locationToSave);

        log.info("Testing...");
//        if (category.equals(PriceCategory.ALL)) {
        INDArray max = Nd4j.create(iterator.getMaxArray());
        INDArray min = Nd4j.create(iterator.getMinArray());

        redisTemplate.opsForHash().put("minMaxArray_" + stockCode, "min", JSON.toJSONString(iterator.getMinArray()));
        redisTemplate.opsForHash().put("minMaxArray_" + stockCode, "max", JSON.toJSONString(iterator.getMaxArray()));

        return predictAllCategories(net, test, iterator.getDateList(), stockCode, max, min);
//        } else {
//            double max = iterator.getMaxNum(category);
//            double min = iterator.getMinNum(category);
//            predictPriceOneAhead(net, test, max, min, category);
//        }
//        log.info("Done...");
        // 保存max min

    }

    /**
     * Predict one feature of a stock one-day ahead
     */
//    private void predictPriceOneAhead(MultiLayerNetwork net, List<Pair<INDArray, INDArray>> testData,List<String> dateList, String stockCode, double max, double min, PriceCategory category) {
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
////        PlotUtil.plot(predicts, actuals, String.valueOf(category));
//    }

    /**
     * Predict all the features (open, close, low, high prices and volume) of a stock one-day ahead
     */
    private List<StockTestPrice> predictAllCategories(MultiLayerNetwork net, List<Pair<INDArray, INDArray>> testData, List<String> dateList, String stockCode, INDArray max, INDArray min) {
        log.info("dataList={}", dateList);
        INDArray[] predicts = new INDArray[testData.size()];
        INDArray[] actuals = new INDArray[testData.size()];
        final List<StockTestPrice> stockTestPrices = new ArrayList();
        for (int i = 0; i < testData.size(); i++) {
            predicts[i] = net.rnnTimeStep(testData.get(i).getKey()).getRow(exampleLength - 1).mul(max.sub(min)).add(min);
            actuals[i] = testData.get(i).getValue();
            final StockTestPrice stockTestPrice = new StockTestPrice();
            stockTestPrice.setPredictPrice1(predicts[i].getDouble(0));
            stockTestPrice.setActualPrice1(actuals[i].getDouble(0));
            stockTestPrice.setPredictPrice2(predicts[i].getDouble(1));
            stockTestPrice.setActualPrice2(actuals[i].getDouble(1));
            stockTestPrice.setDate(dateList.get(i));
            stockTestPrice.setStockCode(stockCode);
            stockTestPrice.setCreateTime(new Date());
            stockTestPrice.setUpdateTime(new Date());
            stockTestPrices.add(stockTestPrice);
        }
        log.info("Print out Predictions and Actual Values...");
        log.info("Predict\tActual");
        for (int i = 0; i < predicts.length; i++) log.info(predicts[i] + "\t" + actuals[i]);
//        log.info("Plot...");
//        for (int n = 0; n < 5; n++) {

//        for (int n = 0; n < 2; n++) {
//        double[] pred = new double[predicts.length];
//        double[] actu = new double[actuals.length];
//        for (int i = 0; i < predicts.length; i++) {
////                pred[i] = predicts[i].getDouble(n);
////                actu[i] = actuals[i].getDouble(n);
//
//        }
////            String name;
//            switch (n) {
//                case 0: name = "Stock OPEN Price"; break;
//                case 1: name = "Stock CLOSE Price"; break;
//                case 2: name = "Stock LOW Price"; break;
//                case 3: name = "Stock HIGH Price"; break;
//                case 4: name = "Stock VOLUME Amount"; break;
//                default: throw new NoSuchElementException();

//            PlotUtil.plot(pred, actu, name);
        return stockTestPrices;

    }

    @SneakyThrows
    private StockTestPrice predictOneHead(String stockCode, List<StockHistoryPrice> historyPrices) {
        if (historyPrices.size() != exampleLength) throw new BusinessException("价格数据错误！");

        File locationToSave = new File("/model/model_".concat(stockCode).concat(".zip"));
//         //saveUpdater: i.e., the state for Momentum, RMSProp, Adagrad etc. Save this to train your network more in the future
//        ModelSerializer.writeModel(net, locationToSave, true);

        log.info("Load model...");
        MultiLayerNetwork net = ModelSerializer.restoreMultiLayerNetwork(locationToSave);

        double[] minArray = (double[]) redisTemplate.opsForHash().get("minMaxArray_" + stockCode, "min");
        double[] maxArray = (double[]) redisTemplate.opsForHash().get("minMaxArray_" + stockCode, "max");
        if (minArray == null || maxArray == null) throw new BusinessException("最大最小值丢失，无法进行归一化！");
        int featureVector = 2;
        double[] input = new double[featureVector * exampleLength];
        for (int i = 0; i < historyPrices.size(); i += 2) {
            StockHistoryPrice historyPrice = historyPrices.get(i);
            input[i] = (historyPrice.getPrice1() - minArray[0]) / (maxArray[0] - minArray[0]);
            input[i + 1] = (historyPrice.getPrice2() - minArray[1]) / (maxArray[1] - minArray[1]);
        }
        INDArray inputArray = Nd4j.create(input, new int[]{0, 22, 2});
        INDArray output = net.rnnTimeStep(inputArray);
        double predictPrice1 = output.getDouble(0, 0);
        double predictPrice2 = output.getDouble(0, 1);
        StockTestPrice stockTestPrice = new StockTestPrice();
        // TODO scaler
        stockTestPrice.setPredictPrice1(predictPrice1 * (maxArray[0] - minArray[0]) + minArray[0]);
        stockTestPrice.setPredictPrice2(predictPrice2 * (maxArray[1] - minArray[1]) + minArray[1]);
        stockTestPrice.setStockCode(stockCode);
//        predictPrice.setDate(stockCode);
        return stockTestPrice;
//        redisTemplate.opsForHash().put("minMaxArray_" + stockCode, "max", iterator.getMaxArray());


//        input.putScalar(new int[]{j - i, 0}, (stock.getOpen() - minArray[0]) / (maxArray[0] - minArray[0]));
//        input.putScalar(new int[]{j - i, 1}, (stock.getClose() - minArray[1]) / (maxArray[1] - minArray[1]));
//        net.rnnTimeStep().getRow(exampleLength - 1).mul(max.sub(min)).add(min);
//        INDArray[] predicts = new INDArray[testData.size()];
//        INDArray[] actuals = new INDArray[testData.size()];
//
//        for (int i = 0; i < testData.size(); i++) {
//            predicts[i] = net.rnnTimeStep(testData.get(i).getKey()).getRow(exampleLength - 1).mul(max.sub(min)).add(min);
//            actuals[i] = testData.get(i).getValue();
//        }
//        log.info("Print out Predictions and Actual Values...");
//        log.info("Predict\tActual");
//        for (int i = 0; i < predicts.length; i++) log.info(predicts[i] + "\t" + actuals[i]);
//        log.info("Plot...");
//        for (int n = 0; n < 5; n++) {

//        ArrayList<PredictPrice> predictPrices = new ArrayList<>();
//        for (int n = 0; n < 2; n++) {
//        double[] pred = new double[predicts.length];
//        double[] actu = new double[actuals.length];
//        PredictPrice predictPrice = new PredictPrice();
//        for (int i = 0; i < predicts.length; i++) {
//                pred[i] = predicts[i].getDouble(n);
//                actu[i] = actuals[i].getDouble(n);
//            predictPrice.setPredictPrice1(predicts[i].getDouble(0));
//            predictPrice.setActualPrice1(actuals[i].getDouble(0));
//            predictPrice.setPredictPrice2(predicts[i].getDouble(1));
//            predictPrice.setActualPrice2(actuals[i].getDouble(1));
//            predictPrice.setDate(dateList.get(i));
//            predictPrice.setStockCode(stockCode);
//            predictPrice.setCreateTime(new Date());
//            predictPrice.setUpdateTime(new Date());
//            predictPrices.add(predictPrice);
//        }
//            String name;
//            switch (n) {
//                case 0: name = "Stock OPEN Price"; break;
//                case 1: name = "Stock CLOSE Price"; break;
//                case 2: name = "Stock LOW Price"; break;
//                case 3: name = "Stock HIGH Price"; break;
//                case 4: name = "Stock VOLUME Amount"; break;
//                default: throw new NoSuchElementException();

//            PlotUtil.plot(pred, actu, name);
//        return predictPrices;
//        return null;
    }

    @SneakyThrows
    @Override
    public List<StockTestPrice> modelTrain(List<StockHistoryPrice> historyPrices, String stockCode) {
        final ArrayList<StockData> dataList = new ArrayList<>();
        for (StockHistoryPrice s : historyPrices) {
            StockData stockData = new StockData();
            stockData.setDate(s.getDate());
            stockData.setSymbol(s.getCode());
            stockData.setOpen(s.getPrice1() == null ? 0 : s.getPrice1());
            stockData.setClose(s.getPrice2() == null ? 0 : s.getPrice2());
            stockData.setHigh(s.getPrice3() == null ? 0 : s.getPrice3());
            stockData.setLow(s.getPrice4() == null ? 0 : s.getPrice4());
            dataList.add(stockData);
        }
        return train(dataList, stockCode);
    }

    @Override
    public StockTestPrice modelPredict(StockHistoryPrice historyPrice) {
        // 找到最近的数据
        String code = historyPrice.getCode();
        Query query = new Query().limit(exampleLength - 1);
        List<StockHistoryPrice> stockHistoryPrices = mongoTemplate.find(query, StockHistoryPrice.class, "historyPrices_" + code);
//        stockHistoryPrices.add(historyPrice);
        StockTestPrice stockTestPrice = predictOneHead(code, stockHistoryPrices);
        log.info("当前股票预测价格为：{},{}", stockTestPrice.getPredictPrice1(), stockTestPrice.getPredictPrice2());
        return stockTestPrice;
    }
}
