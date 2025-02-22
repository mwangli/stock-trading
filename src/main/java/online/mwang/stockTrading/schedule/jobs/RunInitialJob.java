package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IStockService;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.service.OrderInfoService;
import online.mwang.stockTrading.web.service.TradingRecordService;
import online.mwang.stockTrading.web.utils.RequestUtils;
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

    private final IStockService stockService;
    private final OrderInfoService orderInfoService;
    private final TradingRecordService tradingRecordService;
    private final RequestUtils requestUtils;

    @Override
    public void run() {
        initHistoryPriceData();
    }

    private void initHistoryPriceData() {
        // 首次初始化执行，写入4000支股票，每只股票约500条数据
        List<StockInfo> stockInfoList = stockService.getDataList();
        List<String> codes = mongoTemplate.findDistinct(new Query(), "code", TRAIN_COLLECTION_NAME, String.class);
        stockInfoList.forEach(s -> {
            if (!codes.contains(s.getCode())) {
                List<StockPrices> historyPrices = stockService.getHistoryPrices(s.getCode());
                historyPrices.forEach(p -> p.setName(s.getName()));
                mongoTemplate.insert(historyPrices, TRAIN_COLLECTION_NAME);
                log.info("股票[{}-{}]，{}条历史数据写入完成！", s.getName(), s.getCode(), historyPrices.size());
            }
        });
        log.info("共写入了{}支股票历史数据", stockInfoList.size());
    }
}
