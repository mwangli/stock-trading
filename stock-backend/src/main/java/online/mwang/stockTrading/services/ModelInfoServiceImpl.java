package online.mwang.stockTrading.services;

import lombok.RequiredArgsConstructor;
import online.mwang.stockTrading.entities.ModelInfo;
import online.mwang.stockTrading.entities.StockPrices;
import online.mwang.stockTrading.repositories.ModelInfoRepository;
import online.mwang.stockTrading.services.ModelInfoService;
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
public class ModelInfoServiceImpl implements ModelInfoService {

    private final static String TRAIN_COLLECTION_NAME = "stockHistoryPrice";
    private final MongoTemplate mongoTemplate;
    private final ModelInfoRepository modelInfoRepository;

    @Override
    public void save(ModelInfo modelInfo) {
        modelInfoRepository.save(modelInfo);
    }

    @Override
    public ModelInfo findById(Long id) {
        return modelInfoRepository.findById(id);
    }

    @Override
    public ModelInfo update(ModelInfo modelInfo) {
        return modelInfoRepository.update(modelInfo);
    }

    @Override
    public void delete(ModelInfo modelInfo) {
        modelInfoRepository.delete(modelInfo);
    }

    @Override
    public List<ModelInfo> findAll() {
        return modelInfoRepository.findAll();
    }

    @Override
    public void resetStatus() {
        modelInfoRepository.resetStatus();
    }

    @Override
    public Long count() {
        return modelInfoRepository.count();
    }

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
