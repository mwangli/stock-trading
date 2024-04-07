package online.mwang.stockTrading.web.job;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.po.*;
import online.mwang.stockTrading.web.mapper.ScoreStrategyMapper;
import online.mwang.stockTrading.web.mapper.StockInfoMapper;
import online.mwang.stockTrading.web.service.TradingRecordService;
import online.mwang.stockTrading.web.utils.DateUtils;
import online.mwang.stockTrading.web.utils.RequestUtils;
import org.springframework.stereotype.Component;

import java.util.*;

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

    private final AllJobs jobs;
    private final RunNowJob runNowJob;
    private final TradingRecordService tradingRecordService;
    private final StockInfoMapper stockInfoMapper;
    private final ScoreStrategyMapper strategyMapper;
    private final RequestUtils requestUtils;
    public  static final int MAX_HOLD_NUMBER = 100;
    public  static final int MIN_HOLD_NUMBER = 100;
    public  static final int MAX_HOLD_STOCKS = 6;
    public  static final double HIGH_PRICE_PERCENT = 0.8;
    public  static final double LOW_PRICE_PERCENT = 0.8;
    public  static final double LOW_PRICE_LIMIT = 5.0;
    public  static final int BUY_RETRY_TIMES = 4;
    public  static final int BUY_RETRY_LIMIT = 100;

    @Override
    public void run(String runningId) {
        log.info("开始执行买入任务====================================");
        buy(runningId);
        log.info("买入任务执行完毕====================================");
    }

    public void buy(String runningId) {
        int time = 0;
        while (time++ < BUY_RETRY_TIMES) {
            log.info("第{}次尝试买入股票---------", time);
            // 查询持仓股票数量
            final long holdCount = tradingRecordService.list(new LambdaQueryWrapper<TradingRecord>().eq(TradingRecord::getSold, "0")).size();
            final long needCount =MAX_HOLD_STOCKS - holdCount;
            if (needCount <= 0) {
                log.info("持仓股票数量已达到最大值:{},无需购买!", MAX_HOLD_STOCKS);
                return;
            }
            if (jobs. checkSoldToday("0")) {
                log.info("今天已经有买入记录了,无需重复购买！");
                return;
            }
            // 撤销所有未成功订单,回收可用资金
            if (!jobs.waitOrderCancel()) {
                log.info("存在未撤销失败订单,取消购买任务！");
                return;
            }
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
            double availableAmount = totalAvailableAmount / needCount;
            availableAmount = Math.min(availableAmount, maxAmount);
            // 计算可买入股票价格区间
            final double highPrice = (availableAmount / MIN_HOLD_NUMBER) / HIGH_PRICE_PERCENT;
            final double lowPrice = (availableAmount / MAX_HOLD_NUMBER) * LOW_PRICE_PERCENT;
            log.info("当前可用资金{}元, 可买入价格区间[{},{}]", availableAmount, lowPrice, highPrice);
            if (lowPrice < LOW_PRICE_LIMIT) {
                log.info("可用资金资金不足,取消购买任务！");
                return;
            }
            //  更新实时价格
            runNowJob.updateNowPrice();
            // 选择有交易权限合适价格区间的数据,按评分排序分组
            final LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<StockInfo>()
                    .eq(StockInfo::getDeleted, "1").eq(StockInfo::getPermission, "1")
                    .ge(StockInfo::getPrice, lowPrice).le(StockInfo::getPrice, highPrice)
                    .orderByDesc(StockInfo::getScore);
//            final Page<StockInfo> page = new Page<>(time, BUY_RETRY_LIMIT);
//            final Page<StockInfo> pageResult = stockInfoMapper.selectPage(page, queryWrapper);
//            final List<StockInfo> limitList = pageResult.getRecords();
            final List<StockInfo> limitList = stockInfoMapper.selectList(queryWrapper);
            if (limitList.size() < BUY_RETRY_LIMIT) {
                log.info("可买入股票数量不足{},取消购买任务！", BUY_RETRY_LIMIT);
                return;
            }
            // 随机选择一支买入
            StockInfo best = limitList.get(Math.abs(Objects.hashCode(System.currentTimeMillis()) % limitList.size()));
            if (jobs.checkBuyCode(best.getCode())) {
                log.info("当前股票[{}-{}]已经持有,尝试买入下一组股票", best.getCode(), best.getName());
                continue;
            }
            log.info("当前买入最佳股票[{}-{}],价格:{},评分:{}", best.getCode(), best.getName(), best.getPrice(), best.getScore());
            // 等待最佳买入时机
            if (jobs.enableBuyWaiting) {
                best = jobs.waitingBestPrice(best, runningId);
                if (best == null) {
                    log.info("不在交易时间段内，取消买入任务！");
                    return;
                }
            }
            final int maxBuyNumber = (int) (availableAmount / best.getPrice());
            final int buyNumber = (maxBuyNumber / 100) * 100;
            JSONObject res = jobs.buySale("B", best.getCode(), best.getPrice(), (double) buyNumber);
            String buyNo = res.getString("ANSWERNO");
            if (buyNo == null) {
                log.info("当前股票[{}-{}]买入失败,尝试买入下一组股票!", best.getCode(), best.getName());
                continue;
            }
            // 查询买入结果
            final Boolean success = jobs.waitOrderCancel(buyNo);
            if (success == null) {
                log.info("当前股票[{}-{}].订单撤销失败,取消买入任务！", best.getCode(), best.getName());
                return;
            }
            if (!success) {
                // 如果交易不成功,撤单后再次尝试卖出
                log.info("当前买入交易不成功,尝试买入下一组股票。");
                continue;
            }
            // 买入成功后,保存交易数据
            final TradingRecord record = new TradingRecord();
            record.setCode(best.getCode());
            record.setName(best.getName());
            record.setBuyPrice(best.getPrice());
            record.setBuyNumber((double) buyNumber);
            record.setBuyNo(buyNo);
            final double amount = best.getPrice() * buyNumber;
            record.setBuyAmount(amount + jobs.getPeeAmount(amount));
            final Date now = new Date();
            record.setBuyDate(now);
            record.setBuyDateString(DateUtils.dateFormat.format(now));
            record.setSold("0");
            record.setCreateTime(now);
            record.setUpdateTime(now);
            // 保存选股策略ID
            final ScoreStrategy strategy = strategyMapper.getSelectedStrategy();
            record.setStrategyId(strategy == null ? 0 : strategy.getId());
            record.setStrategyName(strategy == null ? "默认策略" : strategy.getName());
            tradingRecordService.save(record);
            // 更新账户资金
            jobs.getAmount();
            // 更新交易次数
            final StockInfo stockInfo = stockInfoMapper.selectByCode(best.getCode());
            stockInfo.setBuySaleCount(stockInfo.getBuySaleCount() + 1);
            stockInfoMapper.updateById(stockInfo);
            log.info("成功买入股票[{}-{}], 买入价格:{},买入数量:{},买入金额:{}", record.getCode(), record.getName(), record.getBuyPrice(), record.getBuyNumber(), record.getBuyAmount());
            return;
        }
    }






}
