package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.data.IDataService;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Set;
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

    private final IDataService dataService;
    private final StockInfoService stockInfoService;

    @Override
    public void run() {
        // 同步股票数据，处理新股票和退市股票，取两个集合的差集
        List<StockInfo> newInfos = dataService.getDataList();
        List<StockInfo> dataList = stockInfoService.list();
        final Set<String> newCodeSet = newInfos.stream().map(StockInfo::getCode).collect(Collectors.toSet());
        final Set<String> oldCodeSet = dataList.stream().map(StockInfo::getCode).collect(Collectors.toSet());
        final Set<String> toInsetCodes = newCodeSet.stream().filter(code -> !oldCodeSet.contains(code)).collect(Collectors.toSet());
        final Set<String> toDeleteCodes = oldCodeSet.stream().filter(code -> !newCodeSet.contains(code)).collect(Collectors.toSet());
        final List<StockInfo> insertList = newInfos.stream().filter(s -> toInsetCodes.contains(s.getCode())).collect(Collectors.toList());
        final List<StockInfo> deleteList = dataList.stream().filter(s -> toDeleteCodes.contains(s.getCode())).collect(Collectors.toList());
        log.info("新增股票数量:{}", insertList.size());
        insertList.forEach(this::fixProps);
        stockInfoService.saveBatch(insertList);
        log.info("删除股票数量:{}", deleteList.size());
        stockInfoService.removeByIds(deleteList.stream().map(StockInfo::getId).collect(Collectors.toList()));
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
