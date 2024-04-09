package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IModelService;
import online.mwang.stockTrading.schedule.IDataService;
import online.mwang.stockTrading.model.impl.LSTMServiceImpl;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunTrainJob extends BaseJob {

    private final IDataService dataService;
    private final IModelService modelService;
    private final StockInfoService stockInfoService;
    private final MongoTemplate mongoTemplate;


    @Override
    void run() {
        // 获取最新的数据集，给模型进行增量训练
        // 从mongo中获取所有股票今天最新价格  数据
//        Query query = new Query(Criteria.where("date").is(DateUtils.format1(new Date())));
//        List<StockHistoryPrice> historyPrices = mongoTemplate.find(query, StockHistoryPrice.class);
        Set<String> collectionNames = mongoTemplate.getCollectionNames();
        collectionNames.forEach(collectionName->{
            List<StockHistoryPrice> historyPrices = mongoTemplate.find(new Query(), StockHistoryPrice.class, collectionName);
//            historyPrices.stream()
            // TODO
        });
//        modelService.modelTrain(historyPrices);
    }
}
