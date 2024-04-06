package online.mwang.stockTrading.web.controller;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.base.Response;
import online.mwang.stockTrading.web.bean.po.DailyItem;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.query.StockInfoQuery;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
    private final StockInfoService stockInfoService;

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
        Page<StockInfo> pageResult = stockInfoService.page(Page.of(query.getCurrent(), query.getPageSize()), queryWrapper);
        List<StockInfo> collect = pageResult.getRecords().stream().peek(o -> {
            List<DailyItem> priceList = JSON.parseArray(o.getPrices(), DailyItem.class);
            final double maxPrice = priceList.stream().mapToDouble(DailyItem::getItem).max().orElse(0.0);
            final double minPrice = priceList.stream().mapToDouble(DailyItem::getItem).min().orElse(0.0);
            o.setPricesList(priceList);
            o.setMaxPrice(Math.ceil(maxPrice));
            o.setMinPrice(Math.floor(minPrice));
            List<DailyItem> rateList = JSON.parseArray(o.getIncreaseRate(), DailyItem.class);
            final double maxRate = rateList.stream().mapToDouble(DailyItem::getItem).max().orElse(0.0);
            final double minRate = rateList.stream().mapToDouble(DailyItem::getItem).min().orElse(0.0);
            o.setIncreaseRateList(rateList);
            o.setMaxIncrease(maxRate);
            o.setMinIncrease(minRate);
        }).collect(Collectors.toList());
        return Response.success(collect, pageResult.getTotal());
    }
}
