package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IStockService;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunStockJob extends BaseJob {

    private final IStockService stockService;
    private final StockInfoService stockInfoService;

    @Override
    public void run() {
        // 同步股票数据，处理新股票和退市股票，取两个集合的差集
        List<StockInfo> newInfos = stockService.getDataList();
        List<StockInfo> dataList = stockInfoService.list();
        List<StockInfo> insertList = newInfos.stream().filter(s -> dataList.stream().noneMatch(o -> o.getCode().equals(s.getCode()))).collect(Collectors.toList());
        log.info("新增股票数量:{}", insertList.size());
        insertList.forEach(this::fixProps);
        stockInfoService.saveBatch(insertList);
        // 同步每只股票最新的当前价格
        List<StockInfo> list = dataList.stream().peek(stockInfo -> newInfos.stream().filter(info -> info.getCode().equals(stockInfo.getCode())).findFirst().ifPresent(p -> {
            stockInfo.setPrice(p.getPrice());
            stockInfo.setIncrease(p.getIncrease());
            stockInfo.setUpdateTime(new Date());
        })).collect(Collectors.toList());
        stockInfoService.updateBatchById(list);
    }

    private void fixProps(StockInfo stockInfo) {
        stockInfo.setCreateTime(new Date());
        stockInfo.setUpdateTime(new Date());
        stockInfo.setDeleted("1");
        stockInfo.setPermission("1");
        stockInfo.setBuySaleCount(0);
        stockInfo.setScore(0.0);
    }
}
