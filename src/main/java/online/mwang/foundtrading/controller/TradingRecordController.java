package online.mwang.foundtrading.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import online.mwang.foundtrading.bean.base.Response;
import online.mwang.foundtrading.bean.po.*;
import online.mwang.foundtrading.bean.query.FoundTradingQuery;
import online.mwang.foundtrading.job.DailyJob;
import online.mwang.foundtrading.mapper.AccountInfoMapper;
import online.mwang.foundtrading.mapper.StockInfoMapper;
import online.mwang.foundtrading.service.TradingRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 10:56
 * @description: FoundTradingController
 */
@RestController
@RequestMapping("foundTrading")
@RequiredArgsConstructor
public class TradingRecordController {

    private final static String ASCEND = "ascend";
    private final TradingRecordService tradingRecordService;
    private final AccountInfoMapper accountInfoMapper;
    private final StockInfoMapper stockInfoMapper;
    private final DailyJob dailyJob;

    @PostMapping
    public Boolean create(@RequestBody TradingRecord tradingRecord) {
        return tradingRecordService.save(tradingRecord);
    }

    @PutMapping
    public Boolean update(@RequestBody TradingRecord tradingRecord) {
        return tradingRecordService.updateById(tradingRecord);
    }

    @DeleteMapping("{id}")
    public Boolean delete(@PathVariable String id) {
        return tradingRecordService.removeById(id);
    }

    @GetMapping
    public Response<List<TradingRecord>> listFound(FoundTradingQuery query) {
        LambdaQueryWrapper<TradingRecord> queryWrapper = new QueryWrapper<TradingRecord>().lambda()
                .like(ObjectUtils.isNotNull(query.getCode()), TradingRecord::getCode, query.getCode())
                .like(ObjectUtils.isNotNull(query.getName()), TradingRecord::getCode, query.getName())
                .like(ObjectUtils.isNotNull(query.getStrategyName()), TradingRecord::getStrategyName, query.getStrategyName())
                .eq(ObjectUtils.isNotNull(query.getBuyDate()), TradingRecord::getBuyDateString, query.getBuyDate().replaceAll("-", ""))
                .eq((ObjectUtils.isNotNull(query.getSalDate())), TradingRecord::getSaleDateString, query.getSalDate().replaceAll("-", ""))
                .eq(ObjectUtils.isNotNull(query.getHoldDays()), TradingRecord::getHoldDays, query.getHoldDays())
                .eq(ObjectUtils.isNotNull(query.getSold()), TradingRecord::getSold, query.getSold())
                .orderBy(ObjectUtils.isNotNull(query.getSortOrder()), ASCEND.equals(query.getSortOrder()), TradingRecord.getOrder(query.getSortKey()));
        Page<TradingRecord> pageResult = tradingRecordService.page(Page.of(query.getCurrent(), query.getPageSize()), queryWrapper);
        return Response.success(pageResult.getRecords(), pageResult.getTotal());
    }

    @GetMapping("analysis")
    public Response<AnalysisData> analysis(String startDate, String endDate) {
        final AnalysisData data = new AnalysisData();
        // 获取所有已卖出的股票，按更新时间倒序
        LambdaQueryWrapper<TradingRecord> queryWrapper = new LambdaQueryWrapper<TradingRecord>().eq(TradingRecord::getSold, "1")
                .ge(startDate != null, TradingRecord::getSaleDateString, startDate)
                .le(endDate != null, TradingRecord::getSaleDateString, endDate)
                .orderByDesc(TradingRecord::getSaleDateString);
        final List<TradingRecord> sortedSoldList = tradingRecordService.list(queryWrapper);
        // 获取最近收益金额
        sortedSoldList.stream().findFirst().ifPresent(o -> data.setIncome(o.getIncome()));
        // 获取上次收益金额
        sortedSoldList.stream().skip(1).findFirst().ifPresent(o -> data.setPreIncome(o.getIncome()));
        // 获取累计收益
        data.setTotalIncome(sortedSoldList.stream().mapToDouble(TradingRecord::getIncome).sum());
        // 获取平均收益
        data.setAvgIncome(sortedSoldList.stream().mapToDouble(TradingRecord::getIncome).average().orElse(0.0));
        // 获取收益率
        sortedSoldList.stream().findFirst().ifPresent(o -> data.setIncomeRate(o.getIncomeRate()));
        // 获取日收益率
        sortedSoldList.stream().findFirst().ifPresent(o -> data.setDailyIncomeRate(o.getDailyIncomeRate()));
        // 查询账户金额
        accountInfoMapper.selectList(new QueryWrapper<AccountInfo>().lambda().orderByDesc(AccountInfo::getUpdateTime)).stream().findFirst().ifPresent(data::setAccountInfo);
        // 查询收益列表
        data.setIncomeList(sortedSoldList.stream().sorted(Comparator.comparing(TradingRecord::getSaleDateString)).map(o -> new Point(o.getSaleDateString(), o.getIncome())).collect(Collectors.toList()));
        // 查询收益率列表
        data.setRateList(sortedSoldList.stream().sorted(Comparator.comparing(TradingRecord::getSaleDateString)).map(o -> new Point(o.getSaleDateString(), o.getIncomeRate())).collect(Collectors.toList()));
        // 查询日收益率列表
        data.setDailyRateList(sortedSoldList.stream().sorted(Comparator.comparing(TradingRecord::getSaleDateString)).map(o -> new Point(o.getSaleDateString(), o.getDailyIncomeRate())).collect(Collectors.toList()));
        // 获取平均收益率
        data.setAvgRate(sortedSoldList.stream().mapToDouble(TradingRecord::getIncomeRate).average().orElse(0.0));
        // 获取平均日收益率
        data.setAvgDailyRate(sortedSoldList.stream().mapToDouble(TradingRecord::getDailyIncomeRate).average().orElse(0.0));
        // 收益排行
        data.setIncomeOrder(sortedSoldList.stream().sorted(Comparator.comparing(TradingRecord::getIncome).reversed()).limit(7).map(o -> new Point(o.getCode().concat("-").concat(o.getName()), o.getIncome())).collect(Collectors.toList()));
        // 收益率排行
        data.setRateOrder(sortedSoldList.stream().sorted(Comparator.comparing(TradingRecord::getIncomeRate).reversed()).limit(10).map(o -> new Point(o.getCode().concat("-").concat(o.getName()), o.getIncome(), o.getIncomeRate().toString(), o.getHoldDays().toString(), o.getDailyIncomeRate().toString())).collect(Collectors.toList()));
        // 日收益率排行
        data.setDailyRateOrder(sortedSoldList.stream().sorted(Comparator.comparing(TradingRecord::getDailyIncomeRate).reversed()).limit(7).map(o -> new Point(o.getCode().concat("-").concat(o.getName()), o.getDailyIncomeRate())).collect(Collectors.toList()));
        // 持有天數分组统计列表
        ArrayList<Point> holdDaysCountList = new ArrayList<>();
        sortedSoldList.stream().collect(Collectors.groupingBy(TradingRecord::getHoldDays, Collectors.summarizingInt(o -> 1))).forEach((k, v) -> holdDaysCountList.add(new Point("天数" + k.toString(), (double) v.getSum())));
        data.setHoldDaysList(holdDaysCountList.stream().sorted(Comparator.comparingDouble(Point::getY).reversed()).collect(Collectors.toList()));
        // 获取期望持仓日收益率排行
        data.setExpectList(getExpectedIncome());
        return Response.success(data);
    }

    public List<TradingRecord> getExpectedIncome() {
        return tradingRecordService.list(new LambdaQueryWrapper<TradingRecord>().eq(TradingRecord::getSold, "0")).stream().peek(record -> {
            final StockInfo stockInfo = stockInfoMapper.selectByCode(record.getCode());
            if (stockInfo != null) {
                record.setName(record.getCode().concat("-").concat(record.getName()));
                record.setSalePrice(stockInfo.getPrice());
                final double amount = record.getBuyNumber() * record.getSalePrice();
                final double saleAmount = amount - dailyJob.getPeeAmount(amount);
                record.setIncome(saleAmount - record.getBuyAmount());
                record.setIncomeRate(record.getIncome() / record.getBuyAmount() * 100);
                record.setHoldDays(dailyJob.diffDate(record.getBuyDate(), new Date()));
                record.setDailyIncomeRate(record.getIncomeRate() / Math.max(record.getHoldDays(), 1));
            }
        }).sorted(Comparator.comparing(TradingRecord::getDailyIncomeRate).reversed()).collect(Collectors.toList());
//        Calendar instance = Calendar.getInstance();
    }


}
