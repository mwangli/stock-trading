package online.mwang.stockTrading.web.job;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private final AllJobs jobs;
    private final StockInfoService stockInfoService;

    @Override
    public void run() {
        // 同步股票数据，处理新股票和退市股票，取两个集合的差集
        List<StockInfo> newInfos = jobs.getDataList();
        List<StockInfo> dataList = stockInfoService.list();
        final Set<String> newCodeSet = newInfos.stream().map(StockInfo::getCode).collect(Collectors.toSet());
        final Set<String> oldCodeSet = dataList.stream().map(StockInfo::getCode).collect(Collectors.toSet());
        final Set<String> toInsetCodes = newCodeSet.stream().filter(code -> !oldCodeSet.contains(code)).collect(Collectors.toSet());
        final Set<String> toDeleteCodes = oldCodeSet.stream().filter(code -> !newCodeSet.contains(code)).collect(Collectors.toSet());
        final List<StockInfo> insertList = newInfos.stream().filter(s -> toInsetCodes.contains(s.getCode())).collect(Collectors.toList());
        final List<StockInfo> deleteList = dataList.stream().filter(s -> toDeleteCodes.contains(s.getCode())).collect(Collectors.toList());
        log.info("新增股票列表:{}", insertList);
        stockInfoService.saveBatch(insertList);
        log.info("删除股票列表:{}", deleteList);
        stockInfoService.removeByIds(deleteList.stream().map(StockInfo::getId).collect(Collectors.toList()));
    }
}
