package online.mwang.stockTrading.schedule.jobs;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IStockService;
import online.mwang.stockTrading.web.bean.po.AccountInfo;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.TradingRecord;
import online.mwang.stockTrading.web.mapper.AccountInfoMapper;
import online.mwang.stockTrading.web.mapper.ModelInfoMapper;
import online.mwang.stockTrading.web.mapper.StockInfoMapper;
import online.mwang.stockTrading.web.service.OrderInfoService;
import online.mwang.stockTrading.web.service.TradingRecordService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
public class RunBuyJob extends BaseJob {

    public static final int MAX_HOLD_NUMBER = 100;
    public static final int MIN_HOLD_NUMBER = 100;
    public static final double LOW_PRICE_LIMIT = 6.0;
    public static final int NEED_COUNT = 1;
    public static final double AMOUNT_USED_RATE = 0.8;
    public static final long WAITING_SECONDS = 20;
    public static final long WAITING_COUNT_SKIP = 10;
    public static final long THREAD_COUNT = 10;
    public static final double BUY_PERCENT = 0.01;
    private final IStockService stockService;
    private final TradingRecordService tradingRecordService;
    private final StockInfoMapper stockInfoMapper;
    private final OrderInfoService orderInfoService;
    private final ModelInfoMapper strategyMapper;
    private final AccountInfoMapper accountInfoMapper;

    private boolean isInterrupted = false;
    public boolean skipWaiting = false;

    @Override
    public void interrupt() {
        log.info("正在尝试终止股票买入任务...");
        isInterrupted = true;
    }

    @SneakyThrows
    @Override
    public void run() {
        // 获取最新的账户资金信息
        AccountInfo accountInfo = stockService.getAccountInfo();
        accountInfoMapper.insert(accountInfo);
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
        if (priceRanges.size() == 0) {
            log.info("可用资金不足，无法进行购买任务！");
            return;
        }
        // 选择有交易权限合适价格区间的数据,按评分排序分组
        LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<StockInfo>()
                .eq(StockInfo::getDeleted, "1").eq(StockInfo::getPermission, "1")
                .orderByDesc(StockInfo::getScore);
        priceRanges.forEach(range -> queryWrapper.ge(StockInfo::getPrice, range[0]).le(StockInfo::getPrice, range[1]).or());
        List<StockInfo> stockInfoList = stockInfoMapper.selectPage(Page.of(1, 10), queryWrapper).getRecords();
        // 多支股票并行买入
        CountDownLatch countDownLatch = new CountDownLatch(NEED_COUNT);
        stockInfoList.forEach(s -> {
            sleepUtils.second(WAITING_SECONDS / THREAD_COUNT);
            cachedThreadPool.submit(() -> buyStock(s, availableAmount, countDownLatch));
        });
        countDownLatch.await();
        log.info("所有股票买入完成!");
    }

    private void buyStock(StockInfo stockInfo, Double availableAmount, CountDownLatch countDownLatch) {
        log.info("开始进行[{}-{}]股票买入!", stockInfo.getName(), stockInfo.getCode());
        int priceCount = 0;
        double priceTotal = 0.0;
        boolean finished = false;
        int failedCount = 0;
        while (!finished && failedCount < 10) {
            try {
                sleepUtils.second(WAITING_SECONDS);
                if (isInterrupted || countDownLatch.getCount() <= 0) break;
                double nowPrice = stockService.getNowPrice(stockInfo.getCode());
                priceCount++;
                priceTotal += nowPrice;
                double priceAvg = priceTotal / priceCount;
                double expectedBuyPrice = priceAvg * (1 - BUY_PERCENT);
                log.info("当前股票[{}-{}],最新价格为:{}，平均价格为:{}，期望买入价格为:{}", stockInfo.getName(), stockInfo.getCode(), nowPrice, String.format("%.2f", priceAvg), String.format("%.2f", expectedBuyPrice));
                if ((priceCount >= WAITING_COUNT_SKIP && nowPrice <= expectedBuyPrice) || DateUtils.isDeadLine2() || skipWaiting) {
                    if (DateUtils.isDeadLine2()) log.info("交易时间段即将结束！");
                    log.info("当前股票[{}-{}],开始进行买入!", stockInfo.getName(), stockInfo.getCode());
                    double buyNumber = (int) (availableAmount / nowPrice / 100) * 100;
                    JSONObject result = stockService.buySale("B", stockInfo.getCode(), nowPrice, buyNumber);
                    String buyNo = result.getString("ANSWERNO");
                    if (buyNo == null) {
                        log.info("当前股票[{}-{}]买入订单提交失败!", stockInfo.getName(), stockInfo.getCode());
                        failedCount++;
                        continue;
                    }
                    log.info("当前股票[{}-{}],买入订单提交成功，订单编号为：{}", stockInfo.getName(), stockInfo.getCode(), buyNo);
                    if (stockService.waitSuccess(buyNo)) {
                        saveData(stockInfo, buyNo, nowPrice, buyNumber);
                        countDownLatch.countDown();
                        finished = true;
                        log.info("成功买入股票[{}-{}], 买入价格:{},买入数量:{}", stockInfo.getCode(), stockInfo.getName(), nowPrice, buyNumber);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                log.info("当前股票[{}-{}],购买任务异常!", stockInfo.getCode(), stockInfo.getName());
            }
        }
    }

    private void saveData(StockInfo stockInfo, String buyNo, Double nowPrice, Double buyNumber) {
        // 保存订单信息
        final Date now = new Date();
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setStatus("1");
        orderInfo.setCode(stockInfo.getCode());
        orderInfo.setName(stockInfo.getName());
        orderInfo.setPrice(nowPrice);
        orderInfo.setNumber(buyNumber);
        double amount = nowPrice * buyNumber;
        Double peer = stockService.getPeeAmount(amount);
        orderInfo.setPeer(peer);
        orderInfo.setAmount(amount + peer);
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
        record.setBuyPrice(nowPrice);
        record.setBuyNumber(buyNumber);
        record.setBuyNo(buyNo);
        record.setBuyAmount(amount + peer);
        record.setBuyDate(now);
        record.setBuyDateString(DateUtils.dateFormat.format(now));
        record.setSold("0");
        record.setCreateTime(now);
        record.setUpdateTime(now);
        tradingRecordService.save(record);
        // 更新账户资金状态
        AccountInfo newAccountInfo = stockService.getAccountInfo();
        newAccountInfo.setCreateTime(now);
        newAccountInfo.setUpdateTime(now);
        accountInfoMapper.insert(newAccountInfo);
        // 更新交易次数
        stockInfo.setBuySaleCount(stockInfo.getBuySaleCount() + 1);
        stockInfoMapper.updateById(stockInfo);
    }
}

