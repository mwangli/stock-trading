package online.mwang.stockTrading.web.job;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.po.AccountInfo;
import online.mwang.stockTrading.web.bean.po.ModelStrategy;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.TradingRecord;
import online.mwang.stockTrading.web.mapper.ScoreStrategyMapper;
import online.mwang.stockTrading.web.mapper.StockInfoMapper;
import online.mwang.stockTrading.web.service.TradingRecordService;
import online.mwang.stockTrading.web.utils.DateUtils;
import online.mwang.stockTrading.web.utils.RequestUtils;
import online.mwang.stockTrading.web.utils.SleepUtils;
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
public class RunBuyJob extends BaseJob {

    public static final int MAX_HOLD_NUMBER = 100;
    public static final int MIN_HOLD_NUMBER = 100;
    public static final int MAX_HOLD_STOCKS = 6;
    public static final double HIGH_PRICE_PERCENT = 0.8;
    public static final double LOW_PRICE_PERCENT = 0.8;
    public static final double LOW_PRICE_LIMIT = 5.0;
    public static final int BUY_RETRY_TIMES = 4;
    public static final int BUY_RETRY_LIMIT = 10;
    public static final int NEED_COUNT = 1;
    public static final double BUY_PERCENT = 0.03;
    public static final double SALE_PERCENT = 0.03;
    private final AllJobs jobs;
    private final RunStockJob runStockJob;
    private final TradingRecordService tradingRecordService;
    private final StockInfoMapper stockInfoMapper;
    private final ScoreStrategyMapper strategyMapper;
    private final RequestUtils requestUtils;
    private final SleepUtils sleepUtils;

    @SneakyThrows
    @Override
    public void run() {
        // 撤销所有未成功订单,回收可用资金
//            if (!jobs.waitOrderCancel()) {
//                log.info("存在未撤销失败订单,取消购买任务！");
//                return;
//            }
        // 更新账户可用资金
        final AccountInfo accountInfo = jobs.getAmount();
        if (accountInfo == null) {
            log.info("更新账户可用资金失败,取消购买任务");
            return;
        }
        final AccountInfo accountAmount = jobs.getAccountAmount(accountInfo);
        final Double totalAvailableAmount = accountAmount.getAvailableAmount();
        final Double totalAmount = accountAmount.getTotalAmount();
        final double maxAmount = totalAmount / MAX_HOLD_STOCKS;
        // 计算此次可用资金
        double availableAmount = totalAvailableAmount / NEED_COUNT;
        availableAmount = Math.min(availableAmount, maxAmount);
        // 计算可买入股票价格区间
        final double highPrice = (availableAmount / MIN_HOLD_NUMBER) / HIGH_PRICE_PERCENT;
        final double lowPrice = (availableAmount / MAX_HOLD_NUMBER) * LOW_PRICE_PERCENT;
        log.info("当前可用资金{}元, 可买入价格区间[{},{}]", availableAmount, lowPrice, highPrice);
        if (lowPrice < LOW_PRICE_LIMIT) {
            log.info("可用资金资金不足,取消购买任务！");
            return;
        }
        // 选择有交易权限合适价格区间的数据,按评分排序分组
        final LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<StockInfo>()
                .eq(StockInfo::getDeleted, "1").eq(StockInfo::getPermission, "1")
                .ge(StockInfo::getPrice, lowPrice).le(StockInfo::getPrice, highPrice)
                .orderByDesc(StockInfo::getScore);
        final Page<StockInfo> page = new Page<>(1, 10);
        final Page<StockInfo> pageResult = stockInfoMapper.selectPage(page, queryWrapper);
        final List<StockInfo> limitStockList = pageResult.getRecords();
        final List<StockInfo> limitList = stockInfoMapper.selectList(queryWrapper);
        if (limitList.size() < BUY_RETRY_LIMIT) {
            log.info("可买入股票数量不足{},取消购买任务！", BUY_RETRY_LIMIT);
            return;
        }
        // 多支股票并行买入
        CountDownLatch countDownLatch = new CountDownLatch(NEED_COUNT);
        limitStockList.forEach(stockInfo -> new Thread(() -> buyStock(stockInfo, accountInfo, countDownLatch)).start());
        countDownLatch.await();
    }

    private void buyStock(StockInfo stockInfo, AccountInfo accountInfo, CountDownLatch countDownLatch) {
        // 买入原则：以尽可能的低的价格买入
        // 当出现某一次的价格，远远低于前面一段时间的平均值，则进行买入
        double priceCount = 1;
        double priceTotal = jobs.getNowPrice(stockInfo.getCode());
        while (countDownLatch.getCount() > 0) {
            // 每隔30秒获取一次最新的价格
            sleepUtils.second(30);
//            log.info("当前买入最佳股票[{}-{}],价格:{},评分:{}", stockInfo.getCode(), stockInfo.getName(), stockInfo.getPrice(), stockInfo.getScore());
            Double nowPrice = jobs.getNowPrice(stockInfo.getCode());
            double priceAvg = priceTotal / priceCount;
            priceTotal += nowPrice;
            priceCount++;
            if (priceCount > 60 && nowPrice <= priceAvg - priceAvg * BUY_PERCENT) {
                log.info("当前股票{}-{}出现最佳价格，开始提交买入订单，当前价格为{}，前段时间平均价格为{}", stockInfo.getName(), stockInfo.getCode(), nowPrice, priceAvg);
                final int maxBuyNumber = (int) (accountInfo.getAvailableAmount() / stockInfo.getPrice());
                final int buyNumber = (maxBuyNumber / 100) * 100;
                JSONObject res = jobs.buySale("B", stockInfo.getCode(), stockInfo.getPrice(), (double) buyNumber);
                String buyNo = res.getString("ANSWERNO");
                if (buyNo == null) {
                    log.info("当前股票[{}-{}]提交买入订单失败,尝试下次买入股票!", stockInfo.getCode(), stockInfo.getName());
                    continue;
                }
                log.info("当前股票[{}-{}]提交买入订单成功,订单编号为：{}!", stockInfo.getCode(), stockInfo.getName(), buyNo);
                // 查询买入结果
                final Boolean success = jobs.waitOrderStatus(buyNo);
                if (success == null) {
                    log.info("当前股票[{}-{}].订单撤销失败,取消买入任务！", stockInfo.getCode(), stockInfo.getName());
                    return;
                }
                if (!success) {
                    // 如果交易不成功,撤单后再次尝试卖出
                    log.info("当前买入交易不成功,尝试下次买入股票。");
                    continue;
                }
                // 买入成功后,保存交易数据
                final TradingRecord record = new TradingRecord();
                record.setCode(stockInfo.getCode());
                record.setName(stockInfo.getName());
                record.setBuyPrice(stockInfo.getPrice());
                record.setBuyNumber((double) buyNumber);
                record.setBuyNo(buyNo);
                final double amount = stockInfo.getPrice() * buyNumber;
                record.setBuyAmount(amount + jobs.getPeeAmount(amount));
                final Date now = new Date();
                record.setBuyDate(now);
                record.setBuyDateString(DateUtils.dateFormat.format(now));
                record.setSold("0");
                record.setCreateTime(now);
                record.setUpdateTime(now);
                // 保存选股策略ID
                final ModelStrategy strategy = strategyMapper.getSelectedStrategy();
                record.setStrategyId(strategy == null ? 0 : strategy.getId());
                record.setStrategyName(strategy == null ? "默认策略" : strategy.getName());
                tradingRecordService.save(record);
                // 更新账户资金
                jobs.getAmount();
                // 更新交易次数
                stockInfo.setBuySaleCount(stockInfo.getBuySaleCount() + 1);
                stockInfoMapper.updateById(stockInfo);
                log.info("成功买入股票[{}-{}], 买入价格:{},买入数量:{},买入金额:{}", record.getCode(), record.getName(), record.getBuyPrice(), record.getBuyNumber(), record.getBuyAmount());
            }
        }

    }
}
