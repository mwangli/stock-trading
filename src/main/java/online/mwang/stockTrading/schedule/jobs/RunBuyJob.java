package online.mwang.stockTrading.schedule.jobs;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IStockService;
import online.mwang.stockTrading.web.bean.base.BusinessException;
import online.mwang.stockTrading.web.bean.po.*;
import online.mwang.stockTrading.web.mapper.AccountInfoMapper;
import online.mwang.stockTrading.web.mapper.ScoreStrategyMapper;
import online.mwang.stockTrading.web.mapper.StockInfoMapper;
import online.mwang.stockTrading.web.service.OrderInfoService;
import online.mwang.stockTrading.web.service.TradingRecordService;
import online.mwang.stockTrading.web.utils.DateUtils;
import online.mwang.stockTrading.web.utils.SleepUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunBuyJob extends BaseJob {

    public static final int MAX_HOLD_NUMBER = 200;
    public static final int MIN_HOLD_NUMBER = 100;
    public static final double LOW_PRICE_LIMIT = 5.0;
    public static final int NEED_COUNT = 1;
    public static final double BUY_PERCENT = 0.03;
    public static final double AMOUNT_USED_RATE = 0.8;
    private final IStockService dataService;
    private final TradingRecordService tradingRecordService;
    private final StockInfoMapper stockInfoMapper;
    private final OrderInfoService orderInfoService;
    private final ScoreStrategyMapper strategyMapper;
    private final SleepUtils sleepUtils;
    private final AccountInfoMapper accountInfoMapper;

    @SneakyThrows
    @Override
    public void run() {
        // 获取最新的账户资金信息
        AccountInfo accountInfo = dataService.getAccountInfo();
        final Double totalAvailableAmount = accountInfo.getAvailableAmount();
        // 计算此次可用资金
        double availableAmount = totalAvailableAmount / NEED_COUNT;
        // 计算可买入股票价格区间
        ArrayList<double[]> priceRanges = new ArrayList<>();
        for (int buyNumber = MIN_HOLD_NUMBER; buyNumber <= MAX_HOLD_NUMBER; buyNumber += 100) {
            double highPrice = availableAmount / buyNumber;
            double lowPrice = highPrice * AMOUNT_USED_RATE;
            if (lowPrice > LOW_PRICE_LIMIT) priceRanges.add(new double[]{lowPrice, highPrice});
        }
        log.info("当前可用资金{}元, 可买入价格区间列表为{}", availableAmount, JSON.toJSONString(priceRanges));
        // 选择有交易权限合适价格区间的数据,按评分排序分组
        final LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<StockInfo>()
                .eq(StockInfo::getDeleted, "1").eq(StockInfo::getPermission, "1")
                .orderByDesc(StockInfo::getScore);
        priceRanges.forEach(range -> queryWrapper.ge(StockInfo::getPrice, range[0]).le(StockInfo::getPrice, range[1]).or());
        final Page<StockInfo> pageResult = stockInfoMapper.selectPage(Page.of(1, 10), queryWrapper);
        final List<StockInfo> limitStockList = pageResult.getRecords();
        // 多支股票并行买入
        CountDownLatch countDownLatch = new CountDownLatch(NEED_COUNT);
        ArrayList<ReentrantLock> locks = new ArrayList<>();
        for (int i = 0; i < NEED_COUNT; i++) locks.add(new ReentrantLock());
        ReentrantLock reentrantLock = new ReentrantLock();
        limitStockList.forEach(stockInfo -> new Thread(() -> buyStock(stockInfo, accountInfo, countDownLatch, locks)).start());
        countDownLatch.await();
        log.info("所有股票买入完成!");
    }

    private void buyStock(StockInfo stockInfo, AccountInfo accountInfo, CountDownLatch countDownLatch, List<ReentrantLock> locks) {
        int priceCount = 1;
        double priceTotal = 0.0;
        Double nowPrice;
        while (countDownLatch.getCount() > 0 && DateUtils.inTradingTimes2()) {
            sleepUtils.second(30);
            double priceAvg = priceTotal / priceCount;
            nowPrice = dataService.getNowPrice(stockInfo.getCode());
            priceTotal += nowPrice;
            priceCount++;
            if (priceCount > 60 && nowPrice < priceAvg - priceAvg * BUY_PERCENT || DateUtils.isDeadLine2()) {
                if (DateUtils.isDeadLine2()) log.info("交易时间段即将结束！");
                log.info("开始进行买入");
                countDownLatch.countDown();
                double buyNumber = (accountInfo.getAvailableAmount() / nowPrice / 100) * 100;
                String buyNo;
                Boolean success;
                // 获取可用资源锁
                ReentrantLock availableLock = locks.stream().filter(l -> !l.isLocked()).findAny().orElse(null);
                if (availableLock == null) {
                    log.info("未获取到可用资源锁，等待下次购买");
                    continue;
                }
                availableLock.lock();
                try {
                    log.info("获取到资源锁，开始进行买入");
                    JSONObject result = dataService.buySale("B", stockInfo.getCode(), nowPrice, buyNumber);
                    buyNo = result.getString("ANSWERNO");
                    if (buyNo == null) throw new BusinessException("买入订单提交失败");
                    success = dataService.waitOrderStatus(buyNo);
                    if (success == null) throw new BusinessException("撤单失败，无可用资金");
                    log.info("同步买入结束...");
                } finally {
                    availableLock.unlock();
                }
                if (!success) {
                    log.info("撤单成功，重新尝试买入。");
                    continue;
                }
                // 保存订单信息
                final Date now = new Date();
                OrderInfo orderInfo = new OrderInfo();
                orderInfo.setStatus("1");
                orderInfo.setCode(stockInfo.getCode());
                orderInfo.setName(stockInfo.getName());
                orderInfo.setPrice(nowPrice);
                orderInfo.setNumber(buyNumber);
                orderInfo.setType("买入");
                orderInfo.setAnswerNo(buyNo);
                orderInfo.setCreateTime(now);
                orderInfo.setUpdateTime(now);
                orderInfo.setDate(DateUtils.format1(now));
                orderInfo.setTime(DateUtils.format2(now));
                orderInfoService.save(orderInfo);
                // 买入成功后,保存交易数据
                final TradingRecord record = new TradingRecord();
                record.setCode(stockInfo.getCode());
                record.setName(stockInfo.getName());
                record.setBuyPrice(stockInfo.getPrice());
                record.setBuyNumber(buyNumber);
                record.setBuyNo(buyNo);
                final double amount = stockInfo.getPrice() * buyNumber;
                record.setBuyAmount(amount + dataService.getPeeAmount(amount));
                record.setBuyDate(now);
                record.setBuyDateString(DateUtils.dateFormat.format(now));
                record.setSold("0");
                record.setCreateTime(now);
                record.setUpdateTime(now);
                // 保存模型策略信息，以备后续数据分析和模型优化
                final ModelStrategy strategy = strategyMapper.getSelectedStrategy();
                record.setStrategyId(strategy == null ? 0 : strategy.getId());
                record.setStrategyName(strategy == null ? "默认策略" : strategy.getName());
                tradingRecordService.save(record);
                // 更新账户资金状态
                AccountInfo newAccountInfo = dataService.getAccountInfo();
                newAccountInfo.setCreateTime(new Date());
                newAccountInfo.setUpdateTime(new Date());
                accountInfoMapper.insert(newAccountInfo);
                // 更新交易次数
                stockInfo.setBuySaleCount(stockInfo.getBuySaleCount() + 1);
                stockInfoMapper.updateById(stockInfo);
                countDownLatch.countDown();
                log.info("成功买入股票[{}-{}], 买入价格:{},买入数量:{},买入金额:{}", record.getCode(), record.getName(), record.getBuyPrice(), record.getBuyNumber(), record.getBuyAmount());
            }
        }
    }
}

