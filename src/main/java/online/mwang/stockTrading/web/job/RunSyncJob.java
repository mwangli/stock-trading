package online.mwang.stockTrading.web.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.TradingRecord;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.service.TradingRecordService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
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
public class RunSyncJob extends BaseJob {

    private final AllJobs jobs;
    private final TradingRecordService tradingRecordService;
    private final StockInfoService stockInfoService;

    @Override
    public void run(String runningId) {
        log.info("同步订单任务执行开始====================================");
        syncBuySaleRecord();
        syncBuySaleCount();
        log.info("同步订单执行结束====================================");
    }

    @SneakyThrows
    public void syncBuySaleRecord() {
        // 请求数据
        List<OrderInfo> lastOrders = jobs.getLastOrder();
        List<OrderInfo> todayOrders = jobs.getTodayOrder();
        lastOrders.addAll(todayOrders);
        for (OrderInfo order : lastOrders) {
            final String date = order.getDate();
            final String time = order.getTime();
            final String answerNo = order.getAnswerNo();
            final String code = order.getCode();
            final String name = order.getName();
            final String type = order.getType();
            final Double price = order.getPrice();
            Double number = order.getNumber();
            final String dateString = date + (time.length() < 6 ? ("0" + time) : time);
            if ("买入".equals(type)) {
                // 查询买入订单信息是否存在
                final LambdaQueryWrapper<TradingRecord> lambdaQueryWrapper = new QueryWrapper<TradingRecord>().lambda()
                        .eq(TradingRecord::getCode, code).like(TradingRecord::getBuyNo, answerNo);
                final TradingRecord selectedOrder = tradingRecordService.getOne(lambdaQueryWrapper);
                if (selectedOrder == null) {
                    log.info("当前股票[{}-{}]买入记录不存在,新增买入记录", name, code);
                    // 合并多个买入订单
                    final LambdaQueryWrapper<TradingRecord> queryWrapper = new QueryWrapper<TradingRecord>().lambda()
                            .eq(TradingRecord::getCode, code).eq(TradingRecord::getSold, "0");
                    final TradingRecord selectedRecord = tradingRecordService.getOne(queryWrapper);
                    if (selectedRecord == null) {
                        final TradingRecord record = new TradingRecord();
                        record.setCode(code);
                        record.setName(name);
                        record.setBuyPrice(price);
                        record.setBuyNumber(number);
                        final double amount = price * number;
                        record.setBuyAmount(amount + jobs.getPeeAmount(amount));
                        final Date buyDate = DateUtils.dateTimeFormat.parse(dateString);
                        record.setBuyDate(buyDate);
                        record.setBuyDateString(date);
                        record.setBuyNo(answerNo);
                        record.setSold("0");
                        record.setStrategyId(0L);
                        record.setStrategyName("默认策略");
                        final Date now = new Date();
                        record.setCreateTime(now);
                        record.setUpdateTime(now);
                        tradingRecordService.save(record);
                    } else {
                        selectedRecord.setBuyPrice(price);
                        selectedRecord.setBuyNumber(number + selectedRecord.getBuyNumber());
                        final double amount = price * number;
                        selectedRecord.setBuyAmount(selectedRecord.getBuyAmount() + amount + jobs.getPeeAmount(amount));
                        final Date buyDate = DateUtils.dateTimeFormat.parse(dateString);
                        selectedRecord.setBuyDate(buyDate);
                        selectedRecord.setBuyDateString(date);
                        selectedRecord.setBuyNo(selectedRecord.getBuyNo() + "," + answerNo);
                        final Date now = new Date();
                        selectedRecord.setUpdateTime(now);
                        tradingRecordService.updateById(selectedRecord);
                    }
                }
            }
            if ("卖出".equals(type)) {
                // 查询卖出订单信息是否存在
                final LambdaQueryWrapper<TradingRecord> lambdaQueryWrapper = new QueryWrapper<TradingRecord>().lambda()
                        .eq(TradingRecord::getCode, code).eq(TradingRecord::getSaleNo, answerNo);
                final TradingRecord selectedOrder = tradingRecordService.getOne(lambdaQueryWrapper);
                if (selectedOrder == null) {
                    log.info("当前股票[{}-{}]卖出记录不存在,新增卖出记录", name, code);
                    final LambdaQueryWrapper<TradingRecord> queryWrapper = new QueryWrapper<TradingRecord>().lambda()
                            .eq(TradingRecord::getCode, code).eq(TradingRecord::getSold, "0");
                    final TradingRecord record = tradingRecordService.getOne(queryWrapper);
                    if (record == null) {
                        log.error("当前股票[{}-{}]没有查询到买入记录,卖出记录同步失败！", name, code);
                        continue;
                    }
                    record.setSalePrice(price);
                    number = Math.abs(number);
                    record.setSaleNumber(number);
                    final double amount = price * number;
                    record.setSaleAmount(amount - jobs.getPeeAmount(amount));
                    final Date saleDate = DateUtils.dateTimeFormat.parse(dateString);
                    record.setSaleDate(saleDate);
                    record.setSaleDateString(date);
                    record.setSold("1");
                    record.setSaleNo(answerNo);
                    final Date now = new Date();
                    record.setUpdateTime(now);
                    // 计算收益和日收益率
                    double income = record.getSaleAmount() - record.getBuyAmount();
                    record.setIncome(income);
                    int dateDiff = jobs.diffDate(record.getBuyDate(), record.getSaleDate());
                    record.setHoldDays(dateDiff);
                    double incomeRate = income / record.getBuyAmount() * 100;
                    record.setIncomeRate(incomeRate);
                    final double dailyIncomeRate = incomeRate / dateDiff;
                    record.setDailyIncomeRate(dailyIncomeRate);
                    tradingRecordService.update(record, queryWrapper);
                }
            }
        }
        log.info("共同步{}条订单交易记录", lastOrders.size());
    }

    @SneakyThrows
    public void syncBuySaleCount() {
        List<TradingRecord> list = tradingRecordService.list();
        Map<String, IntSummaryStatistics> collect = list.stream().collect(Collectors.groupingBy(TradingRecord::getCode, Collectors.summarizingInt((o) -> "1".equals(o.getSold()) ? 2 : 1)));
        collect.forEach((code, accumulate) -> {
            StockInfo stockInfo = new StockInfo();
            stockInfo.setBuySaleCount((int) accumulate.getSum());
            stockInfoService.update(stockInfo, new QueryWrapper<StockInfo>().lambda().eq(StockInfo::getCode, code));
        });
        log.info("共同步{}条股票交易次数", collect.size());
    }

}
