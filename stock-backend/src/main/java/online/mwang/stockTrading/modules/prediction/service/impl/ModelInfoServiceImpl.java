package online.mwang.stockTrading.modules.prediction.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import online.mwang.stockTrading.modules.prediction.entity.ModelInfo;
import online.mwang.stockTrading.modules.datacollection.entity.StockPrices;
import online.mwang.stockTrading.modules.prediction.mapper.ModelInfoMapper;
import online.mwang.stockTrading.modules.prediction.service.ModelInfoService;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

/**
 * 模型信息服务实现
 */
@Service
@RequiredArgsConstructor
public class ModelInfoServiceImpl extends ServiceImpl<ModelInfoMapper, ModelInfo> implements ModelInfoService {

    private final static String TRAIN_COLLECTION_NAME = "stockHistoryPrice";
    private final MongoTemplate mongoTemplate;

    @Override
    public List<StockPrices> getHistoryData(List<StockPrices> pricesList) {
        if (CollectionUtils.isEmpty(pricesList)) return Collections.emptyList();
        String stockCode = pricesList.get(0).getCode();
        String maxDate = pricesList.stream().map(StockPrices::getDate).max(String::compareTo).orElse("");
        String minDate = pricesList.stream().map(StockPrices::getDate).min(String::compareTo).orElse("");
        Query historyQuery = new Query(Criteria.where("code").is(stockCode).and("date").lte(maxDate).gte(minDate)).with(Sort.by(Sort.Direction.ASC,"date"));
        return mongoTemplate.find(historyQuery, StockPrices.class, TRAIN_COLLECTION_NAME);
    }
}
