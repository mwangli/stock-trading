package online.mwang.stockTrading.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import online.mwang.stockTrading.schedule.IStockService;
import online.mwang.stockTrading.web.bean.base.Response;
import online.mwang.stockTrading.web.bean.po.AccountInfo;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.TradingRecord;
import online.mwang.stockTrading.web.bean.query.FoundTradingQuery;
import online.mwang.stockTrading.web.bean.vo.AnalysisData;
import online.mwang.stockTrading.web.bean.vo.Point;
import online.mwang.stockTrading.web.mapper.AccountInfoMapper;
import online.mwang.stockTrading.web.mapper.StockInfoMapper;
import online.mwang.stockTrading.web.service.TradingRecordService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 10:56
 * @description: FoundTradingController
 */
@RestController
@RequestMapping("/tradingRecord")
@RequiredArgsConstructor
public class TradingRecordController {

    private final static String ASCEND = "ascend";
    private final TradingRecordService tradingRecordService;
    private final AccountInfoMapper accountInfoMapper;
    private final StockInfoMapper stockInfoMapper;
    private final IStockService dataService;

    @PostMapping("/create")
    public Boolean create(@RequestBody TradingRecord tradingRecord) {
        return tradingRecordService.save(tradingRecord);
    }

    @PutMapping("/update")
    public Boolean update(@RequestBody TradingRecord tradingRecord) {
        return tradingRecordService.updateById(tradingRecord);
    }

    @DeleteMapping("/delete/{id}")
    public Boolean delete(@PathVariable String id) {
        return tradingRecordService.removeById(id);
    }

    @SneakyThrows
    @GetMapping("/list")
    public Response<List<TradingRecord>> listFound(FoundTradingQuery query) {
        LambdaQueryWrapper<TradingRecord> queryWrapper = new QueryWrapper<TradingRecord>().lambda()
                .like(ObjectUtils.isNotNull(query.getCode()), TradingRecord::getCode, query.getCode())
                .like(ObjectUtils.isNotNull(query.getName()), TradingRecord::getCode, query.getName())
                .ge(ObjectUtils.isNotNull(query.getBuyDate()), TradingRecord::getBuyDate, query.getBuyDate())
                .le(ObjectUtils.isNotNull(query.getBuyDate()), TradingRecord::getBuyDate, DateUtils.getNextDay(query.getBuyDate()))
                .ge((ObjectUtils.isNotNull(query.getSaleDate())), TradingRecord::getSaleDate, query.getSaleDate())
                .le((ObjectUtils.isNotNull(query.getSaleDate())), TradingRecord::getSaleDate, DateUtils.getNextDay(query.getSaleDate()))
                .eq(ObjectUtils.isNotNull(query.getHoldDays()), TradingRecord::getHoldDays, query.getHoldDays())
                .eq(ObjectUtils.isNotNull(query.getSold()), TradingRecord::getSold, query.getSold())
                .orderBy(true, ASCEND.equals(query.getSortOrder()), TradingRecord.getOrder(query.getSortKey()));
        Page<TradingRecord> pageResult = tradingRecordService.page(Page.of(query.getCurrent(), query.getPageSize()), queryWrapper);
        return Response.success(pageResult.getRecords(), pageResult.getTotal());
    }

    @GetMapping("/analysis")
    public Response<AnalysisData> analysis(String startDate, String endDate) {
        final AnalysisData data = new AnalysisData();
        // 获取所有已卖出的股票，按更新时间倒序
        LambdaQueryWrapper<TradingRecord> queryWrapper = new LambdaQueryWrapper<TradingRecord>().eq(TradingRecord::getSold, "1")
                .ge(startDate != null, TradingRecord::getSaleDateString, startDate)
                .le(endDate != null, TradingRecord::getSaleDateString, endDate)
                .orderByDesc(TradingRecord::getSaleDateString);
        List<TradingRecord> sortedSoldList = tradingRecordService.list(queryWrapper);
        if (startDate == null || endDate == null)
            sortedSoldList = sortedSoldList.stream().limit(30).collect(Collectors.toList());
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
        data.setRateOrder(sortedSoldList.stream().sorted(Comparator.comparing(TradingRecord::getIncomeRate).reversed()).limit(10).map(o -> new Point(o.getCode().concat("-").concat(o.getName()), o.getIncome(), o.getIncomeRate().toString(), o.getHoldDays().toString(), o.getDailyIncomeRate().toString(), "")).collect(Collectors.toList()));
        // 日收益率排行
        data.setDailyRateOrder(sortedSoldList.stream().sorted(Comparator.comparing(TradingRecord::getDailyIncomeRate).reversed()).limit(7).map(o -> new Point(o.getCode().concat("-").concat(o.getName()), o.getDailyIncomeRate())).collect(Collectors.toList()));
        // 收益范围分组
        ArrayList<Point> holdDaysCountList = new ArrayList<>();
        sortedSoldList.stream().map(this::getIncomeRange).collect(Collectors.groupingBy((a) -> a, Collectors.summarizingInt(o -> 1))).forEach((k, v) -> holdDaysCountList.add(new Point(k, (double) v.getSum())));
        data.setHoldDaysList(holdDaysCountList.stream().sorted(Comparator.comparing(Point::getX)).peek(p -> p.setX(p.getX().split(":")[1])).collect(Collectors.toList()));
        // 获取日收益率排行
        data.setExpectList(sortedSoldList.stream().sorted(Comparator.comparing(TradingRecord::getDailyIncomeRate).reversed()).limit(10).collect(Collectors.toList()));
        return Response.success(data);
    }

    public String getIncomeRange(TradingRecord record) {
        Double income = record.getIncome();
        if (income > 0 && income <= 10) return "01:[0,10]";
        if (income > 10 && income <= 20) return "02:[10,20]";
        if (income > 20 && income <= 50) return "03:[20,50]";
        if (income > 50 && income <= 100) return "04:[50,100]";
//        if (income > 100 && income <= 500) return "[100,500]";
        if (income > 100) return "05:[100,#]";
        if (income <= 0 && income > -10) return "06:[-10,0]";
        if (income <= -10 && income > -20) return "07:[-20,-10]";
        if (income <= -20 && income > -50) return "08:[-50,-20]";
        if (income <= -50 && income > -100) return "09:[-100,-50]";
//        if (income <= -100 && income > -500) return "[-500,-100]";
        if (income <= -100) return "10:[#,-100]";
        return "11:[#,#]";
    }
}
