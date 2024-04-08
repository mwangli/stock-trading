package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IModelService;
import online.mwang.stockTrading.schedule.IDataService;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import online.mwang.stockTrading.web.mapper.AccountInfoMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunInitialJob extends BaseJob {

    private final IDataService dataService;
    private final AccountInfoMapper accountInfoMapper;
    private final IModelService modelService;
    private final MongoTemplate mongoTemplate;

    @Override
    public void run() {

        // 获取全量的历史数据信息写入MongoDB
        // 先判断是否有数据存在，防止误操作写入重复数据
        long count = mongoTemplate.count(new Query(), StockHistoryPrice.class);
        if (count == 0) {
            List<StockHistoryPrice> historyPrices = dataService.getAllHistoryPrices();
            mongoTemplate.save(historyPrices);
            log.info("开始执行全量历史数据写入操作。");
        } else {
            log.info("Mongo数据库中存在历史数据，无需进行初始化");
        }
        // 获取全量数据模型做初始化训练
        if (modelService.isPresent()) {
            log.info("模型已经存在，无需进行初始化训练");
        } else {
            log.info("开始进行模型初始化训练");
            List<StockHistoryPrice> historyPrices = mongoTemplate.findAll(StockHistoryPrice.class);
            modelService.modelTrain(historyPrices);
        }
    }
}
