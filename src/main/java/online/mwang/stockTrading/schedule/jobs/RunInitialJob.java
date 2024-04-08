package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IModelService;
import online.mwang.stockTrading.schedule.IDataService;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import online.mwang.stockTrading.web.mapper.AccountInfoMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
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
        log.info("执行数据初始化任务");

        // 初始化历史订单
        List<OrderInfo> lastOrders = dataService.getHistoryOrder();

        // 获取全量数据模型做初始化训练
        List<StockHistoryPrice> historyPrices = mongoTemplate.findAll(StockHistoryPrice.class);
        modelService.modelTrain(historyPrices);
    }
}
