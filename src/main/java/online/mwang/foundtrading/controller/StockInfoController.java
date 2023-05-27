package online.mwang.foundtrading.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.base.Response;
import online.mwang.foundtrading.bean.po.FoundTradingRecord;
import online.mwang.foundtrading.bean.po.StockInfo;
import online.mwang.foundtrading.bean.query.StockInfoQuery;
import online.mwang.foundtrading.job.DailyJob;
import online.mwang.foundtrading.service.FoundTradingService;
import online.mwang.foundtrading.service.StockInfoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 10:56
 * @description: FoundTradingController
 */
@Slf4j
@RestController
@RequestMapping("stockInfo")
@RequiredArgsConstructor
public class StockInfoController {

    private final static String ASCEND = "ascend";
    private final DailyJob dailyJob;
    private final StockInfoService stockInfoService;
    private final FoundTradingService FoundTradingService;

    @GetMapping("refresh")
    public Response<Void> refreshStockInfo() {
        dailyJob.saveDate(dailyJob.updateDataPrice());
        return Response.success();
    }

    @GetMapping
    public Response<List<StockInfo>> listStockInfo(StockInfoQuery query) {
        LambdaQueryWrapper<StockInfo> queryWrapper = new QueryWrapper<StockInfo>().lambda()
                .like(ObjectUtils.isNotNull(query.getCode()), StockInfo::getCode, query.getCode())
                .like(ObjectUtils.isNotNull(query.getName()), StockInfo::getName, query.getName())
                .like(ObjectUtils.isNotNull(query.getMarket()), StockInfo::getMarket, query.getName())
                .eq(ObjectUtils.isNotNull(query.getPermission()), StockInfo::getPermission, query.getPermission())
                .eq(ObjectUtils.isNotNull(query.getBuySaleCount()), StockInfo::getBuySaleCount, query.getBuySaleCount())
                .ge(ObjectUtils.isNotNull(query.getPriceLow()), StockInfo::getPrice, query.getPriceLow())
                .le(ObjectUtils.isNotNull(query.getPriceHigh()), StockInfo::getPrice, query.getPriceHigh())
                .orderBy(true, ASCEND.equals(query.getSortOrder()), StockInfo.getOrder(query.getSortKey()));
        Page<StockInfo> pageResult = stockInfoService.page(new Page<>(query.getCurrent(), query.getPageSize()), queryWrapper);
        return Response.success(pageResult.getRecords(), pageResult.getTotal());
    }

    @GetMapping("sync")
    public Response<Void> syncBuySaleCount() {
        List<FoundTradingRecord> list = FoundTradingService.list();
        Map<String, IntSummaryStatistics> collect = list.stream().collect(Collectors.groupingBy(FoundTradingRecord::getCode, Collectors.summarizingInt((o) -> o.getSold().equals("1") ? 2 : 1)));
        collect.forEach((code, accumulate) -> {
            StockInfo stockInfo = new StockInfo();
            stockInfo.setBuySaleCount((int) accumulate.getSum());
            stockInfoService.update(stockInfo, new QueryWrapper<StockInfo>().lambda().eq(StockInfo::getCode, code));
            log.info("同步股票[{}]交易次数", code);
        });
        return Response.success();
    }
}
