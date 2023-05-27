package online.mwang.foundtrading.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import online.mwang.foundtrading.bean.base.Response;
import online.mwang.foundtrading.bean.po.AccountInfo;
import online.mwang.foundtrading.bean.po.AnalysisData;
import online.mwang.foundtrading.bean.po.FoundTradingRecord;
import online.mwang.foundtrading.bean.po.Point;
import online.mwang.foundtrading.bean.query.FoundTradingQuery;
import online.mwang.foundtrading.mapper.AccountInfoMapper;
import online.mwang.foundtrading.service.FoundTradingService;
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
@RequestMapping("foundTrading")
@RequiredArgsConstructor
public class FoundTradingController {

    private final static Integer LIMIT_SIZE = 15;
    private final FoundTradingService foundTradingService;
    private final AccountInfoMapper accountInfoMapper;

    @PostMapping
    public Boolean createFound(@RequestBody FoundTradingRecord foundTradingRecord) {
        return foundTradingService.save(foundTradingRecord);
    }

    @PutMapping
    public Boolean updateFound(@RequestBody FoundTradingRecord foundTradingRecord) {
        return foundTradingService.updateById(foundTradingRecord);
    }

    @DeleteMapping("{id}")
    public Boolean deleteFound(@PathVariable String id) {
        return foundTradingService.removeById(id);
    }

    @GetMapping
    public Response<List<FoundTradingRecord>> listFound(FoundTradingQuery query) {
        Page<FoundTradingRecord> page = new Page<>(query.getCurrent(), query.getPageSize());
        LambdaQueryWrapper<FoundTradingRecord> queryWrapper = new QueryWrapper<FoundTradingRecord>().lambda()
                .like(ObjectUtils.isNotNull(query.getCode()), FoundTradingRecord::getCode, query.getCode())
                .like(ObjectUtils.isNotNull(query.getName()), FoundTradingRecord::getCode, query.getName())
                .like(ObjectUtils.isNotNull(query.getBuyDate()), FoundTradingRecord::getBuyDate, query.getBuyDate())
                .like(ObjectUtils.isNotNull(query.getSalDate()), FoundTradingRecord::getSaleDate, query.getSalDate())
                .eq(ObjectUtils.isNotNull(query.getHoldDays()), FoundTradingRecord::getHoldDays, query.getHoldDays())
                .eq(ObjectUtils.isNotNull(query.getSold()), FoundTradingRecord::getSold, query.getSold())
                .orderBy(true, "ascend".equals(query.getSortOrder()), FoundTradingRecord.getOrder(query.getSortKey()));
        Page<FoundTradingRecord> pageResult = foundTradingService.page(page, queryWrapper);
        return Response.success(pageResult.getRecords(), pageResult.getTotal());
    }

    @GetMapping("analysis")
    public Response<AnalysisData> analysis() {
        final AnalysisData data = new AnalysisData();
        // 获取所有已卖出的股票，按更新时间倒序
        final List<FoundTradingRecord> sortedSoldList = foundTradingService.list(new QueryWrapper<FoundTradingRecord>().lambda().eq(FoundTradingRecord::getSold, "1").orderByDesc(true, FoundTradingRecord.getOrder("")));
        // 获取最近收益金额
        sortedSoldList.stream().findFirst().ifPresent(o -> data.setIncome(o.getIncome()));
        // 获取上次收益金额
        sortedSoldList.stream().skip(1).findFirst().ifPresent(o -> data.setPreIncome(o.getIncome()));
        // 获取累计收益
        data.setTotalIncome(sortedSoldList.stream().mapToDouble(FoundTradingRecord::getIncome).sum());
        // 获取平均收益
        data.setAvgIncome(sortedSoldList.stream().mapToDouble(FoundTradingRecord::getIncome).average().orElse(0.0));
        // 获取收益率
        sortedSoldList.stream().findFirst().ifPresent(o -> data.setIncomeRate(o.getIncomeRate()));
        // 获取日收益率
        sortedSoldList.stream().findFirst().ifPresent(o -> data.setDailyIncomeRate(o.getDailyIncomeRate()));
        // 查询账户金额
        accountInfoMapper.selectList(new QueryWrapper<AccountInfo>().lambda().orderByDesc(AccountInfo::getUpdateTime)).stream().findFirst().ifPresent(data::setAccountInfo);
        // 查询收益列表
        data.setIncomeList(sortedSoldList.stream().limit(LIMIT_SIZE).sorted(Comparator.comparing(FoundTradingRecord::getUpdateTime)).map(o -> new Point(o.getId().toString(), o.getIncome())).collect(Collectors.toList()));
        // 查询收益率列表
        data.setRateList(sortedSoldList.stream().limit(LIMIT_SIZE).sorted(Comparator.comparing(FoundTradingRecord::getUpdateTime)).map(o -> new Point(o.getId().toString(), o.getIncomeRate())).collect(Collectors.toList()));
        // 查询日收益率列表
        data.setDailyRateList(sortedSoldList.stream().limit(LIMIT_SIZE).sorted(Comparator.comparing(FoundTradingRecord::getUpdateTime)).map(o -> new Point(o.getId().toString(), o.getDailyIncomeRate())).collect(Collectors.toList()));
        // 获取平均收益率
        data.setAvgRate(sortedSoldList.stream().mapToDouble(FoundTradingRecord::getIncomeRate).average().orElse(0.0));
        // 获取平均日收益率
        data.setAvgDailyRate(sortedSoldList.stream().mapToDouble(FoundTradingRecord::getDailyIncomeRate).average().orElse(0.0));
        // 收益排行
        data.setIncomeOrder(sortedSoldList.stream().sorted(Comparator.comparing(FoundTradingRecord::getIncome).reversed()).limit(7).map(o -> new Point(o.getCode().concat("-").concat(o.getName()), o.getIncome())).collect(Collectors.toList()));
        // 收益率排行
        data.setRateOrder(sortedSoldList.stream().sorted(Comparator.comparing(FoundTradingRecord::getIncomeRate).reversed()).limit(10).map(o -> new Point(o.getCode().concat("-").concat(o.getName()), o.getIncome(), o.getIncomeRate().toString(), o.getHoldDays().toString(), o.getDailyIncomeRate().toString())).collect(Collectors.toList()));
        // 日收益率排行
        data.setDailyRateOrder(sortedSoldList.stream().sorted(Comparator.comparing(FoundTradingRecord::getDailyIncomeRate).reversed()).limit(7).map(o -> new Point(o.getCode().concat("-").concat(o.getName()), o.getDailyIncomeRate())).collect(Collectors.toList()));
        // 持有天數分组统计列表
        ArrayList<Point> holdDaysCountList = new ArrayList<>();
        sortedSoldList.stream().collect(Collectors.groupingBy(FoundTradingRecord::getHoldDays, Collectors.summarizingInt(o -> 1))).forEach((k, v) -> holdDaysCountList.add(new Point("持有天数" + k.toString(), (double) v.getSum())));
        data.setHoldDaysList(holdDaysCountList.stream().sorted(Comparator.comparingDouble(Point::getY).reversed()).collect(Collectors.toList()));
        return Response.success(data);
    }
}
