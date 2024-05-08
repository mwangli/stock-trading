package online.mwang.stockTrading.web.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import online.mwang.stockTrading.web.bean.po.ModelInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.mapper.ModelInfoMapper;
import online.mwang.stockTrading.web.service.ModelInfoService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModelInfoServiceImpl extends ServiceImpl<ModelInfoMapper, ModelInfo> implements ModelInfoService {

    private static final String TEST_COLLECTION_NAME = "stockTestPrice";
    private static final String VALIDATE_COLLECTION_NAME = "stockPredictPrice";
    private static final String TRAIN_COLLECTION_NAME = "stockHistoryPrice";

    private final ModelInfoMapper modelInfoMapper;
    private final MongoTemplate mongoTemplate;

    @Override
    public void updateModelScore(String stockCode) {
        // 计算测试误差和验证误差
        double testDeviation = calculateDeviation(stockCode, TEST_COLLECTION_NAME);
        double validateDeviation = calculateDeviation(stockCode, VALIDATE_COLLECTION_NAME);
        double score1 = (1 - testDeviation) * 100;
        double score2 = (1 - validateDeviation) * 100;
        double finalScore = score1 * 0.5 + score2 * 0.5;
        LambdaQueryWrapper<ModelInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModelInfo::getCode, stockCode);
        ModelInfo modelInfo = modelInfoMapper.selectOne(queryWrapper);
        modelInfo.setTestDeviation(testDeviation);
        modelInfo.setValidateDeviation(validateDeviation);
        modelInfo.setScore(finalScore);
        modelInfoMapper.updateById(modelInfo);
    }

    private double calculateDeviation(String stockCode, String collectionName) {
        // 获取测试集数据
        List<StockPrices> pricesList = mongoTemplate.find(new Query(Criteria.where("code").is(stockCode)), StockPrices.class, collectionName);
        if (CollectionUtils.isEmpty(pricesList)) return 1;
        String maxDate = pricesList.stream().map(StockPrices::getDate).max(String::compareTo).orElse("");
        String minDate = pricesList.stream().map(StockPrices::getDate).min(String::compareTo).orElse("");
        Query historyQuery = new Query(Criteria.where("code").is(stockCode).and("date").lte(maxDate).gte(minDate));
        List<StockPrices> historyPrices = mongoTemplate.find(historyQuery, StockPrices.class, TRAIN_COLLECTION_NAME);
        setIncreaseRate(historyPrices);
        setIncreaseRate(pricesList);
        // 计算测试集误差和评分
        int mistakeCount = 0;
        for (int i = 1; i < pricesList.size(); i++) {
            Double testIncrease = pricesList.get(i).getIncreaseRate();
            Double actualIncrease = historyPrices.get(i).getIncreaseRate();
            if (hasMistake(testIncrease, actualIncrease)) mistakeCount++;
        }
        return (double) mistakeCount / pricesList.size();
    }

    // 计算日增长率
    private void setIncreaseRate(List<StockPrices> stockPrices) {
        for (int i = 1; i < stockPrices.size(); i++) {
            double curPrice = stockPrices.get(i).getPrice1();
            double prePrice = stockPrices.get(i - 1).getPrice1();
            double increaseRate = prePrice == 0 ? 0 : (curPrice - prePrice) / prePrice;
            stockPrices.get(i).setIncreaseRate(increaseRate);
        }
    }

    // 判断两个数符号是否相同
    private boolean hasMistake(double num1, double num2) {
        if (num1 > 0) {
            return num2 <= 0;
        } else {
            return (num2 > 0);
        }
    }
}
