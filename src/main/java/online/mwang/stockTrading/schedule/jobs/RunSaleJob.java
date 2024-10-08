package online.mwang.stockTrading.schedule.jobs;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IStockService;
import online.mwang.stockTrading.web.bean.po.AccountInfo;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.TradingRecord;
import online.mwang.stockTrading.web.mapper.AccountInfoMapper;
import online.mwang.stockTrading.web.service.OrderInfoService;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.service.TradingRecordService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunSaleJob extends BaseJob {

    public static final long WAITING_SECONDS = 10;
    public static final long WAITING_COUNT_SKIP = 5;
    public static final double SALE_PERCENT = 0.005;
    private final IStockService stockService;
    private final TradingRecordService tradingRecordService;
    private final StockInfoService stockInfoService;
    private final OrderInfoService orderInfoService;
    private final AccountInfoMapper accountInfoMapper;
    private boolean isInterrupted = false;
    public boolean skipWaiting = true;

    @Override
    public void interrupt() {
        log.info("正在尝试终止股票卖出任务...");
        isInterrupted = true;
    }

    @SneakyThrows
    @Override
    public void run() {
        // 查询所有未卖出股票
        LambdaQueryWrapper<TradingRecord> queryWrapper = new LambdaQueryWrapper<TradingRecord>().eq(TradingRecord::getSold, "0");
        List<TradingRecord> tradingRecords = tradingRecordService.list(queryWrapper);
        // 同时卖出卖出持有股票
        CountDownLatch countDownLatch = new CountDownLatch(tradingRecords.size());
        tradingRecords.forEach(r -> cachedThreadPool.submit(() -> saleStock(r, countDownLatch)));
        countDownLatch.await();
        log.info("所有股票卖出完成!");
    }

    private void saleStock(TradingRecord record, CountDownLatch countDownLatch) {
        log.info("当前股票[{}-{}]开始进行卖出!", record.getName(), record.getCode());
        int priceCount = 0;
        double priceTotal = 0.0;
        boolean finished = false;
        int failedCount = 0;
        while (!finished && failedCount < 10) {
            try {
                sleepUtils.second(WAITING_SECONDS);
                if (isInterrupted) break;
                double nowPrice = stockService.getNowPrice(record.getCode());
                priceCount++;
                priceTotal += nowPrice;
                double priceAvg = priceTotal / priceCount;
                double expectedPrice = priceAvg + priceAvg * SALE_PERCENT;
                log.info("当前股票[{}-{}],最新价格为:{}，平均价格为:{}，买入价格为:{}", record.getName(), record.getCode(), String.format("%.2f", nowPrice), String.format("%.2f", priceAvg), String.format("%.2f", record.getBuyPrice()));
                if ((priceCount >= WAITING_COUNT_SKIP && nowPrice >= expectedPrice) || DateUtils.isDeadLine1() || skipWaiting) {
                    if (DateUtils.isDeadLine1()) log.info("交易时间段即将结束");
                    log.info(",当前股票[{}-{}],开始进行卖出", record.getName(), record.getCode());
                    JSONObject result = stockService.buySale("S", record.getCode(), nowPrice, record.getBuyNumber());
                    String saleNo = result.getString("ANSWERNO");
                    if (saleNo == null) {
                        log.info("当前股票[{}-{}]卖出订单提交失败!", record.getName(), record.getCode());
                        failedCount++;
                        continue;
                    }
                    log.info("当前股票[{}-{}],卖出订单提交成功,订单编号为：{}", record.getName(), record.getCode(), saleNo);
                    if (stockService.waitSuccess(saleNo)) {
                        saveData(record, saleNo, nowPrice);
                        countDownLatch.countDown();
                        finished = true;
                        log.info("成功卖出股票[{}-{}], 卖出金额为:{}, 收益为:{},日收益率为:{}。", record.getCode(), record.getName(), record.getSaleAmount(), record.getIncome(), record.getDailyIncomeRate());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                log.info("当前股票[{}-{}]，卖出任务异常!", record.getCode(), record.getName());
            }
        }
    }

    private void saveData(TradingRecord record, String saleNo, Double nowPrice) {
        // 保存订单信息
        final Date now = new Date();
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setStatus("1");
        orderInfo.setCode(record.getCode());
        orderInfo.setName(record.getName());
        orderInfo.setPrice(nowPrice);
        orderInfo.setNumber(record.getBuyNumber());
        double amount = nowPrice * record.getBuyNumber();
        Double peer = stockService.getPeeAmount(amount);
        orderInfo.setPeer(peer);
        orderInfo.setAmount(amount - peer);
        orderInfo.setType("卖出");
        orderInfo.setAnswerNo(saleNo);
        orderInfo.setCreateTime(now);
        orderInfo.setUpdateTime(now);
        orderInfo.setDate(DateUtils.format1(now));
        orderInfo.setTime(DateUtils.format2(now));
        orderInfoService.save(orderInfo);
        // 保存交易记录
        record.setSold("1");
        record.setSaleNo(saleNo);
        record.setSaleDate(now);
        record.setSalePrice(nowPrice);
        record.setSaleNumber(record.getBuyNumber());
        record.setSaleDateString(DateUtils.dateFormat.format(now));
        record.setUpdateTime(now);
        // 计算收益率并更新交易记录
        double saleAmount = amount - peer;
        double income = saleAmount - record.getBuyAmount();
        double incomeRate = income / record.getBuyAmount() * 100;
        record.setSaleAmount(saleAmount);
        record.setIncome(income);
        record.setIncomeRate(incomeRate);
        record.setHoldDays(1);
        record.setDailyIncomeRate(incomeRate);
        tradingRecordService.updateById(record);
        // 更新账户资金
        AccountInfo accountInfo = stockService.getAccountInfo();
        accountInfo.setCreateTime(new Date());
        accountInfo.setUpdateTime(new Date());
        accountInfoMapper.insert(accountInfo);
        // 增加股票交易次数
        StockInfo stockInfo = stockInfoService.getOne(new LambdaQueryWrapper<StockInfo>().eq(StockInfo::getCode, record.getCode()));
        stockInfo.setBuySaleCount(stockInfo.getBuySaleCount() + 1);
        stockInfoService.updateById(stockInfo);
    }
}
